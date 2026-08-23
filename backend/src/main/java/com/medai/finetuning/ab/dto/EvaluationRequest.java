package com.medai.finetuning.ab.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRequest {
    @NotNull(message = "Experiment ID is required")
    private UUID experimentId;

    private String assignedVariant; // "A" or "B"
    private String modelUsed;
    private Long latencyMs;
    private Integer tokenCount;

    @Min(1)
    @Max(5)
    private Integer userRating;

    private Boolean accurate;
    private String feedbackNotes;
}
