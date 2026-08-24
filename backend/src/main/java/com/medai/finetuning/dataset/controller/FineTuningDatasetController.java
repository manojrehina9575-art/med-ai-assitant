package com.medai.finetuning.dataset.controller;

import com.medai.finetuning.dataset.service.FineTuningDatasetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finetuning/dataset")
@RequiredArgsConstructor
@Tag(name = "Fine-Tuning Dataset Export", description = "De-identified medical training dataset preparation")
public class FineTuningDatasetController {

    private final FineTuningDatasetService datasetService;

    /**
     * The export to use. Its target is what a clinician signed, not what the model produced.
     */
    @GetMapping("/reviewed")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Export clinician-signed reports as training data",
               description = "Targets the signed content, so an edited report contributes a real "
                             + "model error beside its real correction. Rejected drafts are "
                             + "excluded. Prefer this over /preview, which trains on unreviewed "
                             + "output and therefore teaches the model its own mistakes.")
    public ResponseEntity<FineTuningDatasetService.DatasetExportSummary> reviewedDataset(
            @RequestParam(defaultValue = "OPENAI_JSONL") String format,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(datasetService.exportReviewedDataset(format, limit));
    }

    @GetMapping("/preview")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Preview de-identified training dataset from raw model output",
               description = "Uses unreviewed model output as the training target, which reinforces "
                             + "the model's own errors. Use /reviewed unless no sign-off history "
                             + "exists yet.")
    public ResponseEntity<FineTuningDatasetService.DatasetExportSummary> previewDataset(
            @RequestParam(defaultValue = "OPENAI_JSONL") String format,
            @RequestParam(defaultValue = "ALL") String modality,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(datasetService.exportTrainingDataset(format, modality, limit));
    }

    @GetMapping("/download")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Download JSONL training dataset file")
    public ResponseEntity<byte[]> downloadDataset(
            @RequestParam(defaultValue = "OPENAI_JSONL") String format,
            @RequestParam(defaultValue = "ALL") String modality,
            @RequestParam(defaultValue = "500") int limit) {
        FineTuningDatasetService.DatasetExportSummary export = datasetService.exportTrainingDataset(format, modality, limit);
        byte[] bytes = export.getJsonlContent().getBytes();

        String filename = "medai_training_" + modality.toLowerCase() + "_" + System.currentTimeMillis() + ".jsonl";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }
}
