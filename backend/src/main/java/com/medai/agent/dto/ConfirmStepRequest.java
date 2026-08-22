package com.medai.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfirmStepRequest {
    @NotNull(message = "Approved status is required")
    private Boolean approved;

    // Optional updated parameters modified by practitioner before approval
    private JsonNode modifiedInputPayload;

    private String rejectionReason;
}
