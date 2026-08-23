package com.medai.finetuning.ab.service;

import com.medai.finetuning.ab.dto.EvaluationRequest;
import com.medai.finetuning.ab.dto.ExperimentMetricsSummary;
import com.medai.finetuning.ab.dto.ExperimentRequest;
import com.medai.finetuning.ab.entity.AbExperiment;
import com.medai.finetuning.ab.entity.AbExperimentEvaluation;
import com.medai.finetuning.ab.repository.AbExperimentEvaluationRepository;
import com.medai.finetuning.ab.repository.AbExperimentRepository;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AbTestingService {

    private final AbExperimentRepository experimentRepository;
    private final AbExperimentEvaluationRepository evaluationRepository;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public AbExperiment createExperiment(ExperimentRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        AbExperiment experiment = AbExperiment.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .modelAId(request.getModelAId())
                .modelBId(request.getModelBId())
                .trafficSplitPercent(request.getTrafficSplitPercent())
                .modality(request.getModality() != null ? request.getModality() : "ALL")
                .status("ACTIVE")
                .startDate(Instant.now())
                .build();

        AbExperiment saved = experimentRepository.save(experiment);
        log.info("Created A/B experiment '{}' ({} vs {}) for tenant {}", saved.getName(), saved.getModelAId(), saved.getModelBId(), tenantId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AbExperiment> listExperiments() {
        UUID tenantId = TenantContext.requireTenantId();
        return experimentRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    /**
     * Resolves which model variant to use based on active A/B experiments.
     * Returns selected modelId, or empty if no active experiment matches.
     */
    @Transactional(readOnly = true)
    public Optional<String> routeModel(String modality) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (tenantId == null) return Optional.empty();

        Optional<AbExperiment> expOpt = experimentRepository.findByTenantIdAndModalityAndStatus(tenantId, modality, "ACTIVE");
        if (expOpt.isEmpty()) {
            expOpt = experimentRepository.findByTenantIdAndModalityAndStatus(tenantId, "ALL", "ACTIVE");
        }
        if (expOpt.isEmpty()) return Optional.empty();

        AbExperiment exp = expOpt.get();
        int roll = random.nextInt(100);
        if (roll < exp.getTrafficSplitPercent()) {
            return Optional.of(exp.getModelBId()); // Model B (Variant)
        } else {
            return Optional.of(exp.getModelAId()); // Model A (Baseline)
        }
    }

    @Transactional
    public AbExperimentEvaluation recordEvaluation(EvaluationRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        AbExperimentEvaluation eval = AbExperimentEvaluation.builder()
                .tenantId(tenantId)
                .experimentId(request.getExperimentId())
                .assignedVariant(request.getAssignedVariant() != null ? request.getAssignedVariant() : "A")
                .modelUsed(request.getModelUsed())
                .latencyMs(request.getLatencyMs())
                .tokenCount(request.getTokenCount())
                .userRating(request.getUserRating())
                .accurate(request.getAccurate())
                .feedbackNotes(request.getFeedbackNotes())
                .build();

        return evaluationRepository.save(eval);
    }

    @Transactional(readOnly = true)
    public ExperimentMetricsSummary getExperimentSummary(UUID experimentId) {
        UUID tenantId = TenantContext.requireTenantId();
        AbExperiment exp = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found"));

        List<AbExperimentEvaluation> evals = evaluationRepository.findByExperimentId(experimentId);

        List<AbExperimentEvaluation> evalsA = evals.stream().filter(e -> "A".equalsIgnoreCase(e.getAssignedVariant())).toList();
        List<AbExperimentEvaluation> evalsB = evals.stream().filter(e -> "B".equalsIgnoreCase(e.getAssignedVariant())).toList();

        double avgRatingA = evalsA.stream().filter(e -> e.getUserRating() != null).mapToInt(AbExperimentEvaluation::getUserRating).average().orElse(0.0);
        double avgRatingB = evalsB.stream().filter(e -> e.getUserRating() != null).mapToInt(AbExperimentEvaluation::getUserRating).average().orElse(0.0);

        long accCountA = evalsA.stream().filter(e -> Boolean.TRUE.equals(e.getAccurate())).count();
        long accCountB = evalsB.stream().filter(e -> Boolean.TRUE.equals(e.getAccurate())).count();

        double accRateA = evalsA.isEmpty() ? 0.0 : (double) accCountA / evalsA.size() * 100.0;
        double accRateB = evalsB.isEmpty() ? 0.0 : (double) accCountB / evalsB.size() * 100.0;

        double avgLatA = evalsA.stream().filter(e -> e.getLatencyMs() != null).mapToLong(AbExperimentEvaluation::getLatencyMs).average().orElse(0.0);
        double avgLatB = evalsB.stream().filter(e -> e.getLatencyMs() != null).mapToLong(AbExperimentEvaluation::getLatencyMs).average().orElse(0.0);

        String winner = "INSUFFICIENT_DATA";
        if (evals.size() >= 5) {
            if (avgRatingB > avgRatingA && accRateB >= accRateA) {
                winner = "VARIANT_B (Fine-Tuned)";
            } else if (avgRatingA > avgRatingB) {
                winner = "VARIANT_A (Baseline)";
            } else {
                winner = "EQUIVALENT";
            }
        }

        return ExperimentMetricsSummary.builder()
                .experiment(exp)
                .totalEvaluations(evals.size())
                .variantACount(evalsA.size())
                .variantAAvgRating(Math.round(avgRatingA * 100.0) / 100.0)
                .variantAAccuracyRate(Math.round(accRateA * 10.0) / 10.0)
                .variantAAvgLatencyMs(Math.round(avgLatA))
                .variantBCount(evalsB.size())
                .variantBAvgRating(Math.round(avgRatingB * 100.0) / 100.0)
                .variantBAccuracyRate(Math.round(accRateB * 10.0) / 10.0)
                .variantBAvgLatencyMs(Math.round(avgLatB))
                .winner(winner)
                .build();
    }

    @Transactional
    public AbExperiment updateStatus(UUID experimentId, String status) {
        AbExperiment exp = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new IllegalArgumentException("Experiment not found"));
        exp.setStatus(status);
        if ("COMPLETED".equalsIgnoreCase(status)) {
            exp.setEndDate(Instant.now());
        }
        return experimentRepository.save(exp);
    }
}
