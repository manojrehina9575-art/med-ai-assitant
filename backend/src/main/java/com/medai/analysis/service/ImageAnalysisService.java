package com.medai.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.analysis.dto.AnalysisResultDto;
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
import com.medai.notification.event.AnalysisCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageAnalysisService {

    private final ChatClient chatClient;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final MedicalFileRepository medicalFileRepository;
    private final StorageService storageService;
    private final RateLimitService rateLimitService;
    private final AnalysisFailureRecorder failureRecorder;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.chat.options.model:qwen/qwen3.6-27b}")
    private String modelName;

    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.chat.options.max-tokens:4096}")
    private Integer maxTokens;

    private static final String IMAGE_ANALYSIS_PROMPT = """
            You are an expert radiologist AI assistant. Analyze the provided medical image carefully.

            Patient clinical notes: %s

            Provide your analysis as a JSON object with this exact structure:
            {
              "findings": [
                {
                  "region": "anatomical region affected",
                  "description": "detailed description of the finding",
                  "severity": "NORMAL | MILD | MODERATE | SEVERE | CRITICAL",
                  "confidence": 0.0 to 1.0
                }
              ],
              "impression": "overall clinical impression summary",
              "icd10Codes": ["relevant ICD-10 codes"],
              "recommendations": ["recommended follow-up actions"],
              "urgency": "ROUTINE | URGENT | CRITICAL",
              "abstained": false,
              "abstentionReason": null
            }

            Important guidelines:
            - Base every finding solely on what is visible in the image provided
            - Be thorough but precise in findings
            - Include confidence scores for each finding
            - Provide relevant ICD-10 codes
            - Clearly state urgency level
            - Always include at least one finding even if normal
            - Return ONLY valid JSON, no markdown or extra text

            DECLINING TO INTERPRET:
            You are expected to decline rather than guess. Set "abstained": true with a short
            "abstentionReason", omit findings, and set urgency to ROUTINE when any of these hold:
            - The image is unreadable, truncated, or too low in quality to interpret
            - The image is not a medical image, or not the modality the request describes
            - The study is outside what you can responsibly interpret without prior imaging,
              clinical context, or a specialist read
            Declining is a correct and expected answer. A confident interpretation of a study you
            cannot actually read is the most harmful output you can produce here, and an abstention
            routes the study to a human immediately rather than burying the problem in a finding.
            """;

    /**
     * Analyses a medical image.
     *
     * <p>The image must reach the model as pixels. If it cannot be decoded, or the model call
     * fails, the analysis is failed with a specific reason — it is never answered from the
     * filename, which would produce fabricated radiological findings indistinguishable from
     * real ones.
     */
    public AnalysisResultDto analyzeImage(UUID analysisRequestId) {
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

            // An image study analysed from a text layer would be a report about a report.
            if (!prepared.isVision()) {
                throw new UnreadableInputException(
                        "Image analysis requires a readable image. '" + medicalFile.getOriginalFileName()
                        + "' yielded only text — use blood report analysis for text documents.");
            }
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

            // /no_think disables the Qwen reasoning model's <think> chain-of-thought, so the
            // response is the JSON answer directly (also saves output tokens under the TPM cap).
            String prompt = String.format(IMAGE_ANALYSIS_PROMPT, clinicalNotes) + "\n\n/no_think";

            // Force JSON-object output so the (reasoning) model returns structured JSON as its
            // content rather than a free-form <think> chain-of-thought.
            OpenAiChatOptions jsonOptions = OpenAiChatOptions.builder()
                    .withModel(modelName)
                    .withMaxTokens(maxTokens)
                    .withResponseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build())
                    .build();

            Media[] media = prepared.images().stream()
                    .map(image -> new Media(prepared.mimeType(), image))
                    .toArray(Media[]::new);

            ChatResponse response = chatClient.prompt()
                    .options(jsonOptions)
                    .user(u -> u.text(prompt).media(media))
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

            // Extract the JSON object (tolerates markdown fences, prose, or reasoning preambles)
            String jsonContent = AiJsonExtractor.extractJsonObject(content);

            AnalysisResultDto result;
            try {
                result = objectMapper.readValue(jsonContent, AnalysisResultDto.class);
            } catch (JsonProcessingException pe) {
                log.warn("Failed to parse AI response for request {} (completionTokens={}). Raw content (first 3000 chars): {}",
                        analysisRequestId, completionTokens,
                        content.length() > 3000 ? content.substring(0, 3000) + "…[truncated]" : content);
                throw pe;
            }
            // Persist canonical JSON (re-serialized from the DTO) so the JSONB column never
            // receives trailing prose/backticks the model may have appended after the object.
            String canonicalJson = objectMapper.writeValueAsString(result);

            request.setStatus(AnalysisStatus.COMPLETED);
            request.setResult(canonicalJson);
            // An abstention is a completed call with a considered refusal, not a failure. It is
            // recorded so it can be excluded from billing and from training data, and so the
            // refusal rate is measurable rather than invisible.
            boolean abstained = Boolean.TRUE.equals(result.getAbstained());
            request.setAbstained(abstained);
            request.setAbstentionReason(abstained ? result.getAbstentionReason() : null);
            request.setUrgency(abstained ? "ROUTINE" : result.getUrgency());
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

            // Publish event so the notification subsystem can fire without coupling this service
            eventPublisher.publishEvent(new AnalysisCompletedEvent(this, request, request.getRequestedBy(), true));

            if (abstained) {
                log.info("Analysis {} abstained: {}", analysisRequestId, result.getAbstentionReason());
            }

            log.info("Analysis completed for request {} — modality={}, images={}, urgency={}, findings={}, tokens={}",
                    analysisRequestId, prepared.modality(), media.length, result.getUrgency(),
                    result.getFindings() != null ? result.getFindings().size() : 0, totalTokens);

            return result;

        } catch (JsonProcessingException e) {
            failureRecorder.recordTransient(request, "The model returned a response that could not be parsed as JSON: "
                                                     + e.getOriginalMessage());
            throw new IllegalStateException("Failed to parse analysis result", e);
        } catch (Exception e) {
            failureRecorder.recordTransient(request, describe(e));
            throw new IllegalStateException("Image analysis failed: " + e.getMessage(), e);
        }
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return (message != null && !message.isBlank())
                ? message
                : e.getClass().getSimpleName() + " during image analysis";
    }
}
