package com.medai.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.analysis.dto.BloodReportResultDto;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.analysis.util.AiJsonExtractor;
import com.medai.analysis.util.AnalysisInputPreparer;
import com.medai.analysis.util.UnreadableInputException;
import com.medai.config.RateLimitService;
import com.medai.upload.entity.MedicalFile;
import com.medai.upload.repository.MedicalFileRepository;
import com.medai.upload.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BloodReportAnalysisService {

    private final ChatClient.Builder chatClientBuilder;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final MedicalFileRepository medicalFileRepository;
    private final StorageService storageService;
    private final RateLimitService rateLimitService;
    private final AnalysisFailureRecorder failureRecorder;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.chat.options.model:qwen/qwen3.6-27b}")
    private String modelName;

    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.chat.options.max-tokens:4096}")
    private Integer maxTokens;

    private static final String BLOOD_REPORT_PROMPT = """
            You are an expert clinical pathologist AI assistant. Analyze the blood report provided below.

            Patient clinical notes: %s

            Extract all lab values and provide your analysis as a JSON object with this exact structure:
            {
              "testName": "name of the test panel (e.g. Complete Blood Count, Lipid Panel, Liver Function Test)",
              "parameters": [
                {
                  "name": "parameter name (e.g. WBC, RBC, Hemoglobin)",
                  "value": numeric_value,
                  "unit": "unit of measurement",
                  "referenceRange": "normal range as string (e.g. 4.5-11.0)",
                  "flag": "NORMAL | HIGH | LOW | CRITICAL_HIGH | CRITICAL_LOW"
                }
              ],
              "interpretation": "detailed clinical interpretation of the results",
              "flags": ["summary flags like ANEMIA, INFECTION_LIKELY, LIVER_DYSFUNCTION, RENAL_IMPAIRMENT, etc."]
            }

            Important guidelines:
            - Extract ONLY values that actually appear in the report provided. Never infer, complete,
              or invent a parameter that is not present in the source.
            - Compare each value against its reference range to determine the flag
            - CRITICAL_HIGH or CRITICAL_LOW for values dangerously outside range
            - Include the test panel name (CBC, BMP, LFT, Lipid Panel, etc.)
            - Provide a thorough clinical interpretation
            - Include relevant clinical flags/alerts
            - If the report is unclear or unreadable, return an empty parameters array and say so in
              the interpretation rather than guessing at values
            - Return ONLY valid JSON, no markdown or extra text
            """;

    private static final String EXTRACTED_TEXT_SECTION = """


            BLOOD REPORT DOCUMENT TEXT (extracted from %s):
            ------------------------------------------------
            %s
            ------------------------------------------------
            """;

    /**
     * Analyses a blood report.
     *
     * <p>PDFs with a text layer are extracted with PDFBox and analysed as text; scanned PDFs are
     * rendered to images; photos and DICOM frames go straight to vision. If none of those paths
     * can read the file, the analysis fails with a specific reason. It is never answered from the
     * filename alone — doing so produced complete, plausible, entirely fabricated lab panels.
     */
    public BloodReportResultDto analyzeBloodReport(UUID analysisRequestId) {
        AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId)
                .orElseThrow(() -> new IllegalStateException("Analysis request not found: " + analysisRequestId));

        MedicalFile medicalFile = medicalFileRepository.findById(request.getMedicalFileId())
                .orElseThrow(() -> new IllegalStateException("Medical file not found: " + request.getMedicalFileId()));

        request.setStatus(AnalysisStatus.PROCESSING);
        request.setProcessingStartedAt(Instant.now());
        analysisRequestRepository.save(request);

        AnalysisInputPreparer.PreparedInput prepared;
        try {
            Resource stored = storageService.retrieveAsResource(medicalFile.getStoragePath());
            MimeType declaredMime = MimeType.valueOf(medicalFile.getMimeType() != null
                    ? medicalFile.getMimeType() : "application/octet-stream");
            prepared = AnalysisInputPreparer.prepare(stored, declaredMime, medicalFile.getOriginalFileName());
        } catch (UnreadableInputException e) {
            failureRecorder.recordTerminal(request, e.getMessage());
            throw e;
        } catch (Exception e) {
            failureRecorder.recordTerminal(request, "Could not read the stored file: " + e.getMessage());
            throw new UnreadableInputException("Could not read the stored file for analysis " + analysisRequestId, e);
        }

        try {
            String clinicalNotes = request.getClinicalNotes() != null
                    ? request.getClinicalNotes() : "No additional clinical notes provided.";

            StringBuilder prompt = new StringBuilder(String.format(BLOOD_REPORT_PROMPT, clinicalNotes));
            if (!prepared.isVision()) {
                prompt.append(String.format(EXTRACTED_TEXT_SECTION,
                        medicalFile.getOriginalFileName(), prepared.text()));
            }
            prompt.append("\n\n/no_think");

            OpenAiChatOptions jsonOptions = OpenAiChatOptions.builder()
                    .withModel(modelName)
                    .withMaxTokens(maxTokens)
                    .withResponseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                    .build();

            Media[] media = prepared.images().stream()
                    .map(image -> new Media(prepared.mimeType(), image))
                    .toArray(Media[]::new);

            ChatResponse response = chatClientBuilder.build().prompt()
                    .options(jsonOptions)
                    .user(u -> {
                        u.text(prompt.toString());
                        if (media.length > 0) {
                            u.media(media);
                        }
                    })
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
            BloodReportResultDto result = objectMapper.readValue(jsonContent, BloodReportResultDto.class);
            // Persist canonical JSON so the JSONB column never receives trailing prose/backticks.
            String canonicalJson = objectMapper.writeValueAsString(result);

            // Determine urgency from flags
            String urgency = "ROUTINE";
            if (result.getParameters() != null) {
                boolean hasCritical = result.getParameters().stream()
                        .anyMatch(p -> "CRITICAL_HIGH".equals(p.getFlag()) || "CRITICAL_LOW".equals(p.getFlag()));
                boolean hasAbnormal = result.getParameters().stream()
                        .anyMatch(p -> !"NORMAL".equals(p.getFlag()));
                if (hasCritical) urgency = "CRITICAL";
                else if (hasAbnormal) urgency = "URGENT";
            }

            request.setStatus(AnalysisStatus.COMPLETED);
            request.setResult(canonicalJson);
            request.setUrgency(urgency);
            request.setModelUsed(modelName);
            request.setModalityUsed(prepared.modality().name());
            request.setPromptTokens(promptTokens);
            request.setCompletionTokens(completionTokens);
            request.setTotalTokens(totalTokens);
            request.setEstimatedCost(cost);
            request.setErrorMessage(null);
            request.setProcessingCompletedAt(Instant.now());
            analysisRequestRepository.save(request);

            rateLimitService.recordUsage(request.getTenantId(), modelName, promptTokens, completionTokens);

            long abnormalCount = result.getParameters() != null
                    ? result.getParameters().stream().filter(p -> !"NORMAL".equals(p.getFlag())).count() : 0;

            log.info("Blood report analysis completed for request {} — modality={}, urgency={}, params={}, abnormal={}, tokens={}",
                    analysisRequestId, prepared.modality(), urgency,
                    result.getParameters() != null ? result.getParameters().size() : 0,
                    abnormalCount, totalTokens);

            return result;

        } catch (JsonProcessingException e) {
            failureRecorder.recordTransient(request, "The model returned a response that could not be parsed as JSON: "
                                                     + e.getOriginalMessage());
            throw new IllegalStateException("Failed to parse blood report result", e);
        } catch (Exception e) {
            failureRecorder.recordTransient(request, describe(e));
            throw new IllegalStateException("Blood report analysis failed: " + e.getMessage(), e);
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return (message != null && !message.isBlank())
                ? message
                : e.getClass().getSimpleName() + " during blood report analysis";
    }
}
