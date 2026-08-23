package com.medai.finetuning.dataset.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.enums.AnalysisType;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.compliance.consent.service.ConsentService;
import com.medai.compliance.phi.PhiRedactionService;
import com.medai.tenant.TenantContext;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FineTuningDatasetService {

    private final AnalysisRequestRepository analysisRequestRepository;
    private final ConsentService consentService;
    private final PhiRedactionService phiRedactionService;
    private final ObjectMapper objectMapper;

    @Data
    @Builder
    public static class DatasetExportSummary {
        private int totalRecordsScanned;
        private int eligibleRecordsCount;
        private int consentSkippedCount;
        private int totalPhiEntitiesRedacted;
        private String format; // "OPENAI_JSONL" or "ALPACA_JSONL"
        private String jsonlContent;
        private Instant exportedAt;
    }

    @Transactional(readOnly = true)
    public DatasetExportSummary exportTrainingDataset(String format, String modality, int limit) {
        UUID tenantId = TenantContext.requireTenantId();
        List<AnalysisRequest> analyses = analysisRequestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        List<Map<String, Object>> trainingPairs = new ArrayList<>();
        int totalScanned = 0;
        int eligible = 0;
        int consentSkipped = 0;
        int phiRedactedTotal = 0;

        for (AnalysisRequest ar : analyses) {
            totalScanned++;
            if (ar.getStatus() != AnalysisStatus.COMPLETED) {
                continue;
            }

            // Filter modality if specified
            if (modality != null && !modality.isBlank() && !"ALL".equalsIgnoreCase(modality)) {
                if (ar.getAnalysisType() == null || !ar.getAnalysisType().name().equalsIgnoreCase(modality)) {
                    continue;
                }
            }

            // Verify Patient Consent for MODEL_TRAINING / RESEARCH_USE
            boolean hasConsent = consentService.hasValidConsent(ar.getPatientId(), "MODEL_TRAINING")
                    || consentService.hasValidConsent(ar.getPatientId(), "RESEARCH_USE");

            if (!hasConsent) {
                consentSkipped++;
                continue;
            }

            String clinicalNotes = ar.getClinicalNotes() != null ? ar.getClinicalNotes() : "Routine evaluation";
            String resultJson = ar.getResult() != null ? ar.getResult() : "";

            if (resultJson.isBlank()) continue;

            // PHI scrub both prompt and completion
            PhiRedactionService.RedactionResult scrubbedPrompt = phiRedactionService.redact(clinicalNotes);
            PhiRedactionService.RedactionResult scrubbedResult = phiRedactionService.redact(resultJson);

            phiRedactedTotal += scrubbedPrompt.getTotalRedactionsCount() + scrubbedResult.getTotalRedactionsCount();

            if ("ALPACA_JSONL".equalsIgnoreCase(format)) {
                Map<String, Object> record = new HashMap<>();
                record.put("instruction", "Analyze the clinical diagnostic data and provide structured clinical findings and impressions.");
                record.put("input", scrubbedPrompt.getRedactedText());
                record.put("output", scrubbedResult.getRedactedText());
                trainingPairs.add(record);
            } else {
                // Default: OpenAI Chat JSONL format
                Map<String, Object> openaiRecord = new HashMap<>();
                List<Map<String, String>> messages = List.of(
                        Map.of("role", "system", "content", "You are an expert AI clinical diagnostic specialist providing structured medical analysis."),
                        Map.of("role", "user", "content", "Clinical Notes / Study Context: " + scrubbedPrompt.getRedactedText()),
                        Map.of("role", "assistant", "content", scrubbedResult.getRedactedText())
                );
                openaiRecord.put("messages", messages);
                trainingPairs.add(openaiRecord);
            }

            eligible++;
            if (eligible >= limit) break;
        }

        // Convert list of objects to newline-delimited JSON
        StringBuilder jsonl = new StringBuilder();
        for (Map<String, Object> pair : trainingPairs) {
            try {
                jsonl.append(objectMapper.writeValueAsString(pair)).append("\n");
            } catch (Exception e) {
                log.error("Failed to serialize training record: {}", e.getMessage());
            }
        }

        log.info("Fine-tuning dataset exported: {} eligible records ({} skipped due to consent)", eligible, consentSkipped);

        return DatasetExportSummary.builder()
                .totalRecordsScanned(totalScanned)
                .eligibleRecordsCount(eligible)
                .consentSkippedCount(consentSkipped)
                .totalPhiEntitiesRedacted(phiRedactedTotal)
                .format(format != null ? format : "OPENAI_JSONL")
                .jsonlContent(jsonl.toString())
                .exportedAt(Instant.now())
                .build();
    }
}
