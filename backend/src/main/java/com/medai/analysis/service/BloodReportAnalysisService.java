package com.medai.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.analysis.dto.BloodReportResultDto;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final ObjectMapper objectMapper;

    private static final String BLOOD_REPORT_PROMPT = """
            You are an expert clinical pathologist AI assistant. Analyze the provided blood report image/document carefully.
            
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
            - Extract ALL numeric values visible in the report
            - Compare each value against its reference range to determine the flag
            - CRITICAL_HIGH or CRITICAL_LOW for values dangerously outside range
            - Include the test panel name (CBC, BMP, LFT, Lipid Panel, etc.)
            - Provide a thorough clinical interpretation
            - Include relevant clinical flags/alerts
            - If the document is unclear or unreadable, state that clearly
            - Return ONLY valid JSON, no markdown or extra text
            """;

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public BloodReportResultDto analyzeBloodReport(UUID analysisRequestId) {
        AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId)
                .orElseThrow(() -> new RuntimeException("Analysis request not found: " + analysisRequestId));

        MedicalFile medicalFile = medicalFileRepository.findById(request.getMedicalFileId())
                .orElseThrow(() -> new RuntimeException("Medical file not found: " + request.getMedicalFileId()));

        request.setStatus(AnalysisStatus.PROCESSING);
        request.setProcessingStartedAt(Instant.now());
        analysisRequestRepository.save(request);

        try {
            Resource fileResource = storageService.retrieveAsResource(medicalFile.getStoragePath());
            MimeType mimeType = MimeType.valueOf(medicalFile.getMimeType() != null
                    ? medicalFile.getMimeType() : "image/jpeg");

            String clinicalNotes = request.getClinicalNotes() != null
                    ? request.getClinicalNotes() : "No additional clinical notes provided.";

            String prompt = String.format(BLOOD_REPORT_PROMPT, clinicalNotes);

            ChatClient chatClient = chatClientBuilder.build();
            ChatResponse response = chatClient.prompt()
                    .user(u -> u.text(prompt)
                            .media(new Media(mimeType, fileResource)))
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
            BloodReportResultDto result = objectMapper.readValue(jsonContent, BloodReportResultDto.class);

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
            request.setResult(jsonContent);
            request.setUrgency(urgency);
            request.setModelUsed("gpt-4o");
            request.setPromptTokens(promptTokens);
            request.setCompletionTokens(completionTokens);
            request.setTotalTokens(totalTokens);
            request.setEstimatedCost(cost);
            request.setProcessingCompletedAt(Instant.now());
            analysisRequestRepository.save(request);

            long abnormalCount = result.getParameters() != null
                    ? result.getParameters().stream().filter(p -> !"NORMAL".equals(p.getFlag())).count() : 0;

            log.info("Blood report analysis completed for request {} — urgency={}, params={}, abnormal={}, tokens={}",
                    analysisRequestId, urgency,
                    result.getParameters() != null ? result.getParameters().size() : 0,
                    abnormalCount, totalTokens);

            return result;

        } catch (JsonProcessingException e) {
            handleFailure(request, "Failed to parse AI response: " + e.getMessage());
            throw new RuntimeException("Failed to parse blood report result", e);
        } catch (Exception e) {
            handleFailure(request, e.getMessage());
            throw new RuntimeException("Blood report analysis failed: " + e.getMessage(), e);
        }
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
        log.error("Blood report analysis failed for request {} (retry {}/{}): {}",
                request.getId(), request.getRetryCount(), request.getMaxRetries(), errorMessage);
    }
}
