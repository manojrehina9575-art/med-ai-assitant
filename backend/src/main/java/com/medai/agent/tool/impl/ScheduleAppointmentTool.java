package com.medai.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.medai.agent.tool.ClinicalTool;
import com.medai.clinical.entity.Appointment;
import com.medai.clinical.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduleAppointmentTool implements ClinicalTool {

    private final AppointmentRepository appointmentRepository;

    @Override
    public String getName() {
        return "scheduleAppointment";
    }

    @Override
    public String getDescription() {
        return "Schedule a clinic follow-up or diagnostic appointment for the patient.";
    }

    @Override
    public boolean requiresConfirmation() {
        return true; // Critical action requiring practitioner confirmation
    }

    @Override
    public String getInputSchemaJson() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "appointmentType": { "type": "string", "description": "Type of appointment (e.g., Clinical Follow-up, Post-Op Check, Radiology Review)" },
                    "scheduledDate": { "type": "string", "description": "ISO-8601 date string or relative days like +7d" },
                    "durationMinutes": { "type": "integer", "description": "Duration in minutes (default 30)" },
                    "notes": { "type": "string", "description": "Clinical follow-up objectives or special instructions" }
                  },
                  "required": ["appointmentType"]
                }
                """;
    }

    @Override
    public ToolResult execute(UUID tenantId, UUID doctorId, UUID patientId, JsonNode inputParams) {
        try {
            String apptType = inputParams.has("appointmentType") ? inputParams.get("appointmentType").asText() : "Clinical Follow-up";
            int duration = inputParams.has("durationMinutes") ? inputParams.get("durationMinutes").asInt(30) : 30;
            String notes = inputParams.has("notes") ? inputParams.get("notes").asText() : null;

            Instant scheduledAt = Instant.now().plus(7, ChronoUnit.DAYS); // default 1 week
            if (inputParams.has("scheduledDate") && !inputParams.get("scheduledDate").asText().isBlank()) {
                String dateStr = inputParams.get("scheduledDate").asText().trim();
                try {
                    if (dateStr.startsWith("+") && dateStr.endsWith("d")) {
                        int days = Integer.parseInt(dateStr.substring(1, dateStr.length() - 1));
                        scheduledAt = Instant.now().plus(days, ChronoUnit.DAYS);
                    } else {
                        scheduledAt = Instant.parse(dateStr);
                    }
                } catch (Exception ignored) {
                    scheduledAt = Instant.now().plus(7, ChronoUnit.DAYS);
                }
            }

            Appointment appt = Appointment.builder()
                    .tenantId(tenantId)
                    .patientId(patientId)
                    .doctorId(doctorId)
                    .appointmentType(apptType)
                    .scheduledAt(scheduledAt)
                    .durationMinutes(duration)
                    .status("SCHEDULED")
                    .notes(notes)
                    .build();

            Appointment saved = appointmentRepository.save(appt);
            log.info("Appointment scheduled: {} for patient: {}", saved.getId(), patientId);

            Map<String, Object> data = new HashMap<>();
            data.put("appointmentId", saved.getId().toString());
            data.put("appointmentType", saved.getAppointmentType());
            data.put("scheduledAt", saved.getScheduledAt().toString());
            data.put("durationMinutes", saved.getDurationMinutes());
            data.put("status", saved.getStatus());

            return ToolResult.success(
                    String.format("Scheduled %s on %s", apptType, saved.getScheduledAt()),
                    data
            );
        } catch (Exception e) {
            log.error("Failed to schedule appointment: {}", e.getMessage(), e);
            return ToolResult.failure("Appointment scheduling failed: " + e.getMessage());
        }
    }
}
