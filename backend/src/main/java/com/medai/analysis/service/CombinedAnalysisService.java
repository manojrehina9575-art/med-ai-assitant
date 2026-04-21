package com.medai.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.analysis.dto.AnalysisResultDto;
import com.medai.analysis.dto.BloodReportResultDto;
import com.medai.analysis.dto.CombinedAnalysisResultDto;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.enums.AnalysisType;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ObjectMapper objectMapper;

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
            - Cross-reference imaging findings with lab values
            - Identify patterns across both modalities
            - Highlight any discrepancies between image and lab findings
            - Provide differential diagnoses with confidence scores
            - Always flag critical findings prominently
            - Consider the patient's medical history if available
            - Return ONLY valid JSON, no markdown or extra text
            """;

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public CombinedAnalysisResultDto analyzeCombined(UUID analysisRequestId) {
        AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId)
                .orElseThrow(() -> new RuntimeException("Analysis request not found: " + analysisRequestId));

        request.setStatus(AnalysisStatus.PROCESSING);
        request.setProcessingStartedAt(Instant.now());
        analysisRequestRepository.save(request);

        try {
            // Get patient info
            Patient patient = patientRepository.findById(request.getPatientId())
                    .orElseThrow(() -> new RuntimeException("Patient not found"));
            String patientInfo = formatPatientInfo(patient);

            // Get previous analyses for this patient
            List<AnalysisRequest> previousAnalyses = analysisRequestRepository
                    .findByTenantIdAndMedicalFileId(request.getTenantId(), request.getMedicalFileId())
                    .stream()
                    .filter(a -> a.getStatus() == AnalysisStatus.COMPLETED && !a.getId().equals(request.getId()))
                    .toList();

            // Also get all completed analyses for the patient
            var allPatientAnalyses = analysisRequestRepository
                    .findByTenantIdAndPatientIdOrderByCreatedAtDesc(request.getTenantId(), request.getPatientId(),
                            org.springframework.data.domain.PageRequest.of(0, 20))
                    .getContent()
                    .stream()
                    .filter(a -> a.getStatus() == AnalysisStatus.COMPLETED && !a.getId().equals(request.getId()))
                    .toList();

            String imageResults = formatAnalysisByType(allPatientAnalyses, AnalysisType.IMAGE_ANALYSIS);
            String bloodResults = formatAnalysisByType(allPatientAnalyses, AnalysisType.BLOOD_REPORT);

            String clinicalNotes = request.getClinicalNotes() != null
                    ? request.getClinicalNotes() : "No additional clinical notes.";

            String prompt = String.format(COMBINED_ANALYSIS_PROMPT, patientInfo, clinicalNotes, imageResults, bloodResults);

            ChatClient chatClient = chatClientBuilder.build();
            ChatResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            String content = response.getResult().getOutput().getText();

            Integer promptTokens = null;
            Integer completionTokens = null;
            Integer totalTokens = null;
            BigDecimal cost = null;

            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                promptTokens = (int) usage.getPromptTokens();
                completionTokens = (int) usage.getCompletionTokens();
                totalTokens = (int) usage.getTotalTokens();
                cost = BigDecimal.valueOf(promptTokens * 2.50 / 1_000_000 + completionTokens * 10.0 / 1_000_000)
                        .setScale(6, RoundingMode.HALF_UP);
            }

            String jsonContent = stripMarkdownCodeBlock(content);
            CombinedAnalysisResultDto result = objectMapper.readValue(jsonContent, CombinedAnalysisResultDto.class);

            request.setStatus(AnalysisStatus.COMPLETED);
            request.setResult(jsonContent);
            request.setUrgency(result.getUrgency());
            request.setModelUsed("gpt-4o");
            request.setPromptTokens(promptTokens);
            request.setCompletionTokens(completionTokens);
            request.setTotalTokens(totalTokens);
            request.setEstimatedCost(cost);
            request.setProcessingCompletedAt(Instant.now());
            analysisRequestRepository.save(request);

            log.info("Combined analysis completed for request {} — urgency={}, diagnoses={}, confidence={}, tokens={}",
                    analysisRequestId, result.getUrgency(),
                    result.getDiagnoses() != null ? result.getDiagnoses().size() : 0,
                    result.getConfidenceScore(), totalTokens);

            return result;

        } catch (JsonProcessingException e) {
            handleFailure(request, "Failed to parse AI response: " + e.getMessage());
            throw new RuntimeException("Failed to parse combined analysis result", e);
        } catch (Exception e) {
            handleFailure(request, e.getMessage());
            throw new RuntimeException("Combined analysis failed: " + e.getMessage(), e);
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
                .filter(a -> a.getAnalysisType() == type && a.getResult() != null)
                .map(a -> a.getResult())
                .toList();
        if (results.isEmpty()) return "No " + type.name().toLowerCase().replace('_', ' ') + " results available.";
        return String.join("\n---\n", results);
    }

    private String stripMarkdownCodeBlock(String content) {
        String stripped = content.strip();
        if (stripped.startsWith("```json")) stripped = stripped.substring(7);
        else if (stripped.startsWith("```")) stripped = stripped.substring(3);
        if (stripped.endsWith("```")) stripped = stripped.substring(0, stripped.length() - 3);
        return stripped.strip();
    }

    private void handleFailure(AnalysisRequest request, String errorMessage) {
        request.setRetryCount(request.getRetryCount() + 1);
        if (request.getRetryCount() >= request.getMaxRetries()) {
            request.setStatus(AnalysisStatus.FAILED);
        } else {
            request.setStatus(AnalysisStatus.PENDING);
        }
        request.setErrorMessage(errorMessage);
        request.setProcessingCompletedAt(Instant.now());
        analysisRequestRepository.save(request);
        log.error("Combined analysis failed for request {} (retry {}/{}): {}",
                request.getId(), request.getRetryCount(), request.getMaxRetries(), errorMessage);
    }
}
