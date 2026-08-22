package com.medai.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AgentWorkflowStepDto {
    private UUID id;
    private Integer stepIndex;
    private String toolName;
    private String actionSummary;
    private JsonNode inputPayload;
    private JsonNode outputPayload;
    private Boolean requiresConfirmation;
    private String confirmationStatus;
    private String status;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
