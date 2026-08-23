package com.medai.finetuning.ab.dto;

import com.medai.finetuning.ab.entity.AbExperiment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentMetricsSummary {
    private AbExperiment experiment;
    private int totalEvaluations;

    // Variant A stats (Baseline)
    private int variantACount;
    private double variantAAvgRating;
    private double variantAAccuracyRate;
    private double variantAAvgLatencyMs;

    // Variant B stats (LoRA / Fine-tuned)
    private int variantBCount;
    private double variantBAvgRating;
    private double variantBAccuracyRate;
    private double variantBAvgLatencyMs;

    private String winner; // "VARIANT_A", "VARIANT_B", or "INSUFFICIENT_DATA"
}
