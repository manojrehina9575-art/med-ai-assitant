package com.medai.finetuning.ab.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentRequest {
    @NotBlank(message = "Experiment name is required")
    private String name;

    private String description;

    @NotBlank(message = "Model A (Baseline) ID is required")
    private String modelAId;

    @NotBlank(message = "Model B (Variant) ID is required")
    private String modelBId;

    @Min(0)
    @Max(100)
    @Builder.Default
    private int trafficSplitPercent = 50;

    @Builder.Default
    private String modality = "ALL";
}
