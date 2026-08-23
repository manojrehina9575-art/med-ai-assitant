package com.medai.finetuning.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelRegisterRequest {
    @NotBlank(message = "Model ID is required")
    private String modelId;

    @NotBlank(message = "Display name is required")
    private String displayName;

    @NotBlank(message = "Base model is required")
    private String baseModel;

    @Builder.Default
    private String adapterType = "LORA";

    @Builder.Default
    private String status = "READY";

    private Integer loraRank;
    private Integer loraAlpha;
    private Double trainingLoss;
    private Integer trainingSamplesCount;
    private String endpointUrl;
    private String description;
    private boolean tenantPrivate;
}
