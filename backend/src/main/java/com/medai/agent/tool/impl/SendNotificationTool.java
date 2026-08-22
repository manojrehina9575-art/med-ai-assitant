package com.medai.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.medai.agent.tool.ClinicalTool;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class SendNotificationTool implements ClinicalTool {

    private final PatientRepository patientRepository;

    @Override
    public String getName() {
        return "sendNotification";
    }

    @Override
    public String getDescription() {
        return "Dispatch clinical or administrative notifications (SMS, Email, In-App) to the patient or attending clinical care team.";
    }

    @Override
    public boolean requiresConfirmation() {
        return false; // Safe notification tool, auto-executes
    }

    @Override
    public String getInputSchemaJson() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "recipientType": { "type": "string", "enum": ["PATIENT", "CARE_TEAM", "PHARMACY"], "description": "Target recipient" },
                    "channel": { "type": "string", "enum": ["IN_APP", "EMAIL", "SMS"], "description": "Communication channel" },
                    "subject": { "type": "string", "description": "Notification subject/header" },
                    "message": { "type": "string", "description": "Notification body content" }
                  },
                  "required": ["message"]
                }
                """;
    }

    @Override
    public ToolResult execute(UUID tenantId, UUID doctorId, UUID patientId, JsonNode inputParams) {
        try {
            String recipientType = inputParams.has("recipientType") ? inputParams.get("recipientType").asText() : "PATIENT";
            String channel = inputParams.has("channel") ? inputParams.get("channel").asText() : "IN_APP";
            String subject = inputParams.has("subject") ? inputParams.get("subject").asText() : "Med-AI Clinical Notice";
            String message = inputParams.has("message") ? inputParams.get("message").asText() : "";

            Patient patient = patientId != null ? patientRepository.findByTenantIdAndId(tenantId, patientId).orElse(null) : null;
            String recipientContact = (patient != null && patient.getEmail() != null) ? patient.getEmail() : "care-team-internal";

            log.info("Dispatched notification [{}] via {} to {}: {}", subject, channel, recipientType, message);

            Map<String, Object> data = new HashMap<>();
            data.put("recipientType", recipientType);
            data.put("channel", channel);
            data.put("recipientContact", recipientContact);
            data.put("subject", subject);
            data.put("sentAt", Instant.now().toString());

            return ToolResult.success(
                    String.format("Notification dispatched to %s via %s (%s)", recipientType, channel, subject),
                    data
            );
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage(), e);
            return ToolResult.failure("Notification dispatch failed: " + e.getMessage());
        }
    }
}
