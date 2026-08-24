package com.medai.finetuning.dataset.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.enums.AnalysisType;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.compliance.consent.service.ConsentService;
import com.medai.compliance.phi.PhiRedactionService;
import com.medai.report.entity.ReportReview;
import com.medai.report.repository.ReportReviewRepository;
import com.medai.tenant.TenantContext;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
    private final ReportReviewRepository reviewRepository;
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

    /**
     * Exports clinician-signed reports as training data.
     *
     * <p>This is the export that should be used, and {@link #exportTrainingDataset} is kept only
     * for the raw-output case. The difference matters more than it looks: the raw export takes the
     * model's own unreviewed output as the training target, which teaches the model to reproduce
     * its own mistakes with greater confidence. Training on output nobody checked is worse than
     * not training at all.
     *
     * <p>Here the target is what a named practitioner signed. An EDITED review contributes the
     * most valuable pair in the system — a real model error beside its real correction, produced
     * as a by-product of work someone was doing anyway. REJECTED reviews are excluded entirely:
     * a draft a clinician threw away is not an example of anything worth learning.
     */
    @Transactional(readOnly = true)
    public DatasetExportSummary exportReviewedDataset(String format, int limit) {
        UUID tenantId = TenantContext.requireTenantId();

        List<ReportReview> signed = reviewRepository.findSignedForTraining(
                tenantId, null, PageRequest.of(0, Math.max(limit, 1) * 4));

        List<Map<String, Object>> trainingPairs = new ArrayList<>();
        int scanned = 0;
        int eligible = 0;
        int consentSkipped = 0;
        int rejectedSkipped = 0;
        int corrections = 0;
        int phiRedactedTotal = 0;

        for (ReportReview review : signed) {
            scanned++;

            if ("REJECTED".equals(review.getReviewAction())) {
                rejectedSkipped++;
                continue;
            }
            if (review.getFinalContent() == null || review.getFinalContent().isBlank()) {
                continue;
            }

            boolean hasConsent = consentService.hasValidConsent(review.getPatientId(), "MODEL_TRAINING")
                    || consentService.hasValidConsent(review.getPatientId(), "RESEARCH_USE");
            if (!hasConsent) {
                consentSkipped++;
                continue;
            }

            AnalysisRequest analysis = analysisRequestRepository
                    .findByIdAndTenantId(review.getAnalysisId(), tenantId).orElse(null);
            String clinicalNotes = analysis != null && analysis.getClinicalNotes() != null
                    ? analysis.getClinicalNotes() : "Routine evaluation";

            PhiRedactionService.RedactionResult scrubbedPrompt = phiRedactionService.redact(clinicalNotes);
            PhiRedactionService.RedactionResult scrubbedTarget =
                    phiRedactionService.redact(review.getFinalContent());
            phiRedactedTotal += scrubbedPrompt.getTotalRedactionsCount()
                                + scrubbedTarget.getTotalRedactionsCount();

            if ("EDITED".equals(review.getReviewAction())) {
                corrections++;
            }

            trainingPairs.add(record(format, scrubbedPrompt.getRedactedText(),
                    scrubbedTarget.getRedactedText()));

            eligible++;
            if (eligible >= limit) {
                break;
            }
        }

        log.info("Reviewed dataset exported: {} signed report(s), {} of them clinician corrections "
                 + "({} rejected drafts excluded, {} skipped for consent)",
                eligible, corrections, rejectedSkipped, consentSkipped);

        return DatasetExportSummary.builder()
                .totalRecordsScanned(scanned)
                .eligibleRecordsCount(eligible)
                .consentSkippedCount(consentSkipped)
                .totalPhiEntitiesRedacted(phiRedactedTotal)
                .format(format != null ? format : "OPENAI_JSONL")
                .jsonlContent(toJsonl(trainingPairs))
                .exportedAt(Instant.now())
                .build();
    }

    private Map<String, Object> record(String format, String prompt, String target) {
        if ("ALPACA_JSONL".equalsIgnoreCase(format)) {
            Map<String, Object> record = new HashMap<>();
            record.put("instruction", "Analyze the clinical diagnostic data and provide structured "
                                      + "clinical findings and impressions.");
            record.put("input", prompt);
            record.put("output", target);
            return record;
        }

        Map<String, Object> record = new HashMap<>();
        record.put("messages", List.of(
                Map.of("role", "system", "content",
                        "You are an expert AI clinical diagnostic specialist providing structured medical analysis."),
                Map.of("role", "user", "content", "Clinical Notes / Study Context: " + prompt),
                Map.of("role", "assistant", "content", target)));
        return record;
    }

    private String toJsonl(List<Map<String, Object>> pairs) {
        StringBuilder jsonl = new StringBuilder();
        for (Map<String, Object> pair : pairs) {
            try {
                jsonl.append(objectMapper.writeValueAsString(pair)).append("\n");
            } catch (Exception e) {
                log.error("Failed to serialize training record: {}", e.getMessage());
            }
        }
        return jsonl.toString();
    }

    /**
     * Exports raw model output as training data.
     *
     * <p><strong>Prefer {@link #exportReviewedDataset}.</strong> This takes the model's own
     * unreviewed output as the training target, so it teaches the model to reproduce its own
     * mistakes — including the ones a clinician would have corrected. It remains only for the case
     * where no sign-off history exists yet.
     */
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
