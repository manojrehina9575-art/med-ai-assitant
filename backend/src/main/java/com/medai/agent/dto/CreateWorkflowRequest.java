package com.medai.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateWorkflowRequest {
    private UUID patientId;

    @NotBlank(message = "Goal is required")
    private String goal;
}
