package com.medai.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.analysis.dto.CombinedAnalysisResultDto;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.enums.AnalysisType;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.analysis.util.AiJsonExtractor;
import com.medai.analysis.util.AnalysisInputPreparer;
import com.medai.analysis.util.UnreadableInputException;
import com.medai.config.RateLimitService;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CombinedAnalysisService {

    private final ChatClient.Builder chatClientBuilder;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final PatientRepository patientRepository;
    private final RateLimitService rateLimitService;
    private final AnalysisFailureRecorder failureRecorder;
    private final ObjectMapper objectMapper;

    /** How many of the patient's most recent completed analyses to correlate. */
    private static final int SOURCE_ANALYSIS_LIMIT = 20;

    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.chat.options.model:qwen/qwen3.6-27b}")
    private String modelName;

    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.chat.options.max-tokens:4096}")
    private Integer maxTokens;

    private static final String COMBINED_ANALYSIS_PROMPT = """
            You are a senior diagnostic physician AI assistant. You have been provided with multiple analysis results
            for the same patient. Combine the imaging findings and blood report results to provide a unified diagnostic assessment.

            Patient Information:
            %s

            Clinical Notes: %s

            Previous Image Analysis Results:
            %s

            Previous Blood Report Results:
            %s

            Provide a combined diagnostic assessment as a JSON object with this exact structure:
            {
              "overallAssessment": "comprehensive summary combining all findings",
              "clinicalCorrelation": "how the image and lab findings correlate with each other",
              "diagnoses": [
                {
                  "diagnosis": "suspected diagnosis",
                  "icd10Code": "ICD-10 code",
                  "confidence": 0.0 to 1.0,
                  "supportingEvidence": ["evidence from image analysis", "evidence from blood reports"]
                }
              ],
              "criticalFindings": ["any urgent/critical findings that need immediate attention"],
              "recommendations": ["recommended next steps, additional tests, referrals"],
              "urgency": "ROUTINE | URGENT | CRITICAL",
              "confidenceScore": 0.0 to 1.0
            }

            Important guidelines:
            - Every diagnosis and every item of supporting evidence must trace back to a finding or lab
              value present in the results above. Do not introduce findings that are not there.
            - If only one modality is available, say so in the clinical correlation and lower the
              confidence score accordingly rather than implying corroboration you do not have.
            - Cross-reference imaging findings with lab values
            - Identify patterns across both modalities
            - Highlight any discrepancies between image and lab findings
            - Provide differential diagnoses with confidence scores
            - Always flag critical findings prominently
            - Consider the patient's medical history if available
            - Return ONLY valid JSON, no markdown or extra text
            """;

    /**
     * Correlates the patient's existing completed analyses into a unified assessment.
     *
     * <p>This is a text-only reasoning step over results produced earlier — it reads no image
     * itself. If the patient has no completed image or blood report analyses, there is nothing to
     * correlate and the request fails; previously the prompt said "no results available" and the
     * model answered anyway, producing a confident assessment built from nothing.
     */
    public CombinedAnalysisResultDto analyzeCombined(UUID analysisRequestId) {
        AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId)
                .orElseThrow(() -> new IllegalStateException("Analysis request not found: " + analysisRequestId));

        request.setStatus(AnalysisStatus.PROCESSING);
        request.setProcessingStartedAt(Instant.now());
        analysisRequestRepository.save(request);

        String patientInfo;
        String imageResults;
        String bloodResults;
        try {
            Patient patient = patientRepository.findById(request.getPatientId())
                    .orElseThrow(() -> new IllegalStateException("Patient not found: " + request.getPatientId()));
            patientInfo = formatPatientInfo(patient);

            List<AnalysisRequest> sources = analysisRequestRepository
                    .findByTenantIdAndPatientIdOrderByCreatedAtDesc(request.getTenantId(), request.getPatientId(),
                            PageRequest.of(0, SOURCE_ANALYSIS_LIMIT))
                    .getContent()
                    .stream()
                    .filter(a -> a.getStatus() == AnalysisStatus.COMPLETED)
                    .filter(a -> a.getResult() != null)
                    .filter(a -> !a.getId().equals(request.getId()))
                    .filter(a -> a.getAnalysisType() != AnalysisType.COMBINED)
                    .toList();

            if (sources.isEmpty()) {
                throw new UnreadableInputException(
                        "Combined analysis needs at least one completed image or blood report analysis for this "
                        + "patient to correlate. Run an image or blood report analysis first.");
            }

            imageResults = formatAnalysisByType(sources, AnalysisType.IMAGE_ANALYSIS);
            bloodResults = formatAnalysisByType(sources, AnalysisType.BLOOD_REPORT);

            log.info("Combined analysis {} correlating {} source analysis result(s)",
                    analysisRequestId, sources.size());

        } catch (UnreadableInputException e) {
            failureRecorder.recordTerminal(request, e.getMessage());
            throw e;
        } catch (Exception e) {
            failureRecorder.recordTerminal(request, "Could not assemble the source analyses: " + e.getMessage());
            throw new IllegalStateException("Could not assemble source analyses for " + analysisRequestId, e);
        }

        try {
            String clinicalNotes = request.getClinicalNotes() != null
                    ? request.getClinicalNotes() : "No additional clinical notes.";

            String prompt = String.format(COMBINED_ANALYSIS_PROMPT, patientInfo, clinicalNotes, imageResults, bloodResults)
                    + "\n\n/no_think";

            OpenAiChatOptions jsonOptions = OpenAiChatOptions.builder()
                    .withModel(modelName)
                    .withMaxTokens(maxTokens)
                    .withResponseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                    .build();

            ChatResponse response = chatClientBuilder.build().prompt()
                    .options(jsonOptions)
                    .user(prompt)
                    .call()
                    .chatResponse();

            String content = response.getResult().getOutput().getContent();

            Integer promptTokens = null;
            Integer completionTokens = null;
            Integer totalTokens = null;
            BigDecimal cost = null;

            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                if (usage.getPromptTokens() != null) {
                    promptTokens = usage.getPromptTokens().intValue();
                }
                if (usage.getGenerationTokens() != null) {
                    completionTokens = usage.getGenerationTokens().intValue();
                }
                if (usage.getTotalTokens() != null) {
                    totalTokens = usage.getTotalTokens().intValue();
                }
                // Priced from the model that actually ran, not a hardcoded GPT-4o rate.
                cost = rateLimitService.estimateCost(modelName, promptTokens, completionTokens);
            }

            String jsonContent = AiJsonExtractor.extractJsonObject(content);
            CombinedAnalysisResultDto result = objectMapper.readValue(jsonContent, CombinedAnalysisResultDto.class);
            // Persist canonical JSON so the JSONB column never receives trailing prose/backticks.
            String canonicalJson = objectMapper.writeValueAsString(result);

            request.setStatus(AnalysisStatus.COMPLETED);
            request.setResult(canonicalJson);
            request.setUrgency(result.getUrgency());
            request.setModelUsed(modelName);
            // Correlation reasons over previously extracted results, never over pixels.
            request.setModalityUsed(AnalysisInputPreparer.Modality.TEXT.name());
            request.setPromptTokens(promptTokens);
            request.setCompletionTokens(completionTokens);
            request.setTotalTokens(totalTokens);
            request.setEstimatedCost(cost);
            request.setErrorMessage(null);
            request.setProcessingCompletedAt(Instant.now());
            analysisRequestRepository.save(request);

            rateLimitService.recordUsage(request.getTenantId(), modelName, promptTokens, completionTokens);

            log.info("Combined analysis completed for request {} — urgency={}, diagnoses={}, confidence={}, tokens={}",
                    analysisRequestId, result.getUrgency(),
                    result.getDiagnoses() != null ? result.getDiagnoses().size() : 0,
                    result.getConfidenceScore(), totalTokens);

            return result;

        } catch (JsonProcessingException e) {
            failureRecorder.recordTransient(request, "The model returned a response that could not be parsed as JSON: "
                                                     + e.getOriginalMessage());
            throw new IllegalStateException("Failed to parse combined analysis result", e);
        } catch (Exception e) {
            failureRecorder.recordTransient(request, describe(e));
            throw new IllegalStateException("Combined analysis failed: " + e.getMessage(), e);
        }
    }

    private String formatPatientInfo(Patient patient) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(patient.getFirstName()).append(" ").append(patient.getLastName());
        if (patient.getDateOfBirth() != null) sb.append(", DOB: ").append(patient.getDateOfBirth());
        if (patient.getGender() != null) sb.append(", Gender: ").append(patient.getGender());
        if (patient.getBloodGroup() != null) sb.append(", Blood Group: ").append(patient.getBloodGroup());
        if (patient.getMedicalHistory() != null && !patient.getMedicalHistory().isEmpty()) {
            sb.append(", Medical History: ").append(String.join(", ", patient.getMedicalHistory()));
        }
        if (patient.getAllergies() != null && !patient.getAllergies().isEmpty()) {
            sb.append(", Allergies: ").append(String.join(", ", patient.getAllergies()));
        }
        return sb.toString();
    }

    private String formatAnalysisByType(List<AnalysisRequest> analyses, AnalysisType type) {
        List<String> results = analyses.stream()
                .filter(a -> a.getAnalysisType() == type)
                .map(AnalysisRequest::getResult)
                .toList();
        if (results.isEmpty()) {
            return "None on record for this patient — do not infer findings for this modality.";
        }
        return String.join("\n---\n", results);
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return (message != null && !message.isBlank())
                ? message
                : e.getClass().getSimpleName() + " during combined analysis";
    }
}
