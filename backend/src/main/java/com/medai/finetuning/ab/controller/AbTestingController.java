package com.medai.finetuning.ab.controller;

import com.medai.finetuning.ab.dto.EvaluationRequest;
import com.medai.finetuning.ab.dto.ExperimentMetricsSummary;
import com.medai.finetuning.ab.dto.ExperimentRequest;
import com.medai.finetuning.ab.entity.AbExperiment;
import com.medai.finetuning.ab.entity.AbExperimentEvaluation;
import com.medai.finetuning.ab.service.AbTestingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/finetuning/experiments")
@RequiredArgsConstructor
@Tag(name = "A/B Testing & Evaluation", description = "A/B Testing Experiments between Base & Fine-Tuned LLMs")
public class AbTestingController {

    private final AbTestingService abTestingService;

    @PostMapping
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Create an A/B experiment")
    public ResponseEntity<AbExperiment> createExperiment(@Valid @RequestBody ExperimentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(abTestingService.createExperiment(request));
    }

    @GetMapping
    @Operation(summary = "List all A/B experiments for current tenant")
    public ResponseEntity<List<AbExperiment>> listExperiments() {
        return ResponseEntity.ok(abTestingService.listExperiments());
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "Get comparative metrics and winner for an experiment")
    public ResponseEntity<ExperimentMetricsSummary> getSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(abTestingService.getExperimentSummary(id));
    }

    @PostMapping("/evaluations")
    @Operation(summary = "Submit clinician evaluation feedback on a model output")
    public ResponseEntity<AbExperimentEvaluation> recordEvaluation(@Valid @RequestBody EvaluationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(abTestingService.recordEvaluation(request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    @Operation(summary = "Update experiment status (ACTIVE, PAUSED, COMPLETED)")
    public ResponseEntity<AbExperiment> updateStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(abTestingService.updateStatus(id, status));
    }
}
