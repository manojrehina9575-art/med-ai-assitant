package com.medai.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.analysis.dto.AnalysisResultDto;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.upload.entity.MedicalFile;
import com.medai.upload.repository.MedicalFileRepository;
import com.medai.upload.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.Media;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageAnalysisService {

    private final ChatClient.Builder chatClientBuilder;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final MedicalFileRepository medicalFileRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

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
              "urgency": "ROUTINE | URGENT | CRITICAL"
            }
            
            Important guidelines:
            - Be thorough but precise in findings
            - Include confidence scores for each finding
            - Provide relevant ICD-10 codes
            - Clearly state urgency level
            - If the image quality is poor or unreadable, state that clearly
            - Always include at least one finding even if normal
            - Return ONLY valid JSON, no markdown or extra text
            """;

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public AnalysisResultDto analyzeImage(UUID analysisRequestId) {
        AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId)
                .orElseThrow(() -> new RuntimeException("Analysis request not found: " + analysisRequestId));

        MedicalFile medicalFile = medicalFileRepository.findById(request.getMedicalFileId())
                .orElseThrow(() -> new RuntimeException("Medical file not found: " + request.getMedicalFileId()));

        // Update status to PROCESSING
        request.setStatus(AnalysisStatus.PROCESSING);
        request.setProcessingStartedAt(Instant.now());
        analysisRequestRepository.save(request);

        try {
            // Load the image from storage
            Resource imageResource = storageService.retrieve(medicalFile.getStoragePath());
            MimeType mimeType = MimeType.valueOf(medicalFile.getContentType() != null
                    ? medicalFile.getContentType() : "image/jpeg");

            String clinicalNotes = request.getClinicalNotes() != null
                    ? request.getClinicalNotes() : "No additional clinical notes provided.";

            String prompt = String.format(IMAGE_ANALYSIS_PROMPT, clinicalNotes);

            // Call GPT-4o Vision
            ChatClient chatClient = chatClientBuilder.build();
            ChatResponse response = chatClient.prompt()
                    .user(u -> u.text(prompt)
                            .media(new Media(mimeType, imageResource)))
                    .call()
                    .chatResponse();

            String content = response.getResult().getOutput().getText();
            String modelUsed = response.getMetadata() != null ? "gpt-4o" : "gpt-4o";

            // Parse usage
            Integer promptTokens = null;
            Integer completionTokens = null;
            Integer totalTokens = null;
            BigDecimal cost = null;

            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                var usage = response.getMetadata().getUsage();
                promptTokens = (int) usage.getPromptTokens();
                completionTokens = (int) usage.getCompletionTokens();
                totalTokens = (int) usage.getTotalTokens();
                // GPT-4o pricing: ~$2.50/1M input, ~$10/1M output
                cost = BigDecimal.valueOf(promptTokens * 2.50 / 1_000_000 + completionTokens * 10.0 / 1_000_000)
                        .setScale(6, RoundingMode.HALF_UP);
            }

            // Clean JSON response (strip markdown code blocks if present)
            String jsonContent = content.strip();
            if (jsonContent.startsWith("```json")) {
                jsonContent = jsonContent.substring(7);
            } else if (jsonContent.startsWith("```")) {
                jsonContent = jsonContent.substring(3);
            }
            if (jsonContent.endsWith("```")) {
                jsonContent = jsonContent.substring(0, jsonContent.length() - 3);
            }
            jsonContent = jsonContent.strip();

            AnalysisResultDto result = objectMapper.readValue(jsonContent, AnalysisResultDto.class);

            // Update the analysis request
            request.setStatus(AnalysisStatus.COMPLETED);
            request.setResult(jsonContent);
            request.setUrgency(result.getUrgency());
            request.setModelUsed(modelUsed);
            request.setPromptTokens(promptTokens);
            request.setCompletionTokens(completionTokens);
            request.setTotalTokens(totalTokens);
            request.setEstimatedCost(cost);
            request.setProcessingCompletedAt(Instant.now());
            analysisRequestRepository.save(request);

            log.info("Analysis completed for request {} — urgency={}, findings={}, tokens={}",
                    analysisRequestId, result.getUrgency(),
                    result.getFindings() != null ? result.getFindings().size() : 0, totalTokens);

            return result;

        } catch (JsonProcessingException e) {
            handleFailure(request, "Failed to parse AI response: " + e.getMessage());
            throw new RuntimeException("Failed to parse analysis result", e);
        } catch (IOException e) {
            handleFailure(request, "Failed to read image file: " + e.getMessage());
            throw new RuntimeException("Failed to read image for analysis", e);
        } catch (Exception e) {
            handleFailure(request, e.getMessage());
            throw new RuntimeException("Analysis failed: " + e.getMessage(), e);
        }
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
        log.error("Analysis failed for request {} (retry {}/{}): {}",
                request.getId(), request.getRetryCount(), request.getMaxRetries(), errorMessage);
    }
}
