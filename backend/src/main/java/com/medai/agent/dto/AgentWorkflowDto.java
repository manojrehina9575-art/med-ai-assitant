package com.medai.agent.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AgentWorkflowDto {
    private UUID id;
    private UUID patientId;
    private String patientName;
    private String patientMrn;
    private String goal;
    private String status;
    private String planSummary;
    private String finalOutput;
    private List<AgentWorkflowStepDto> steps;
    private Instant createdAt;
    private Instant updatedAt;
}
