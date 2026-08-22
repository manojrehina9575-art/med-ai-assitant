package com.medai.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.agent.tool.ClinicalTool;
import com.medai.clinical.entity.Prescription;
import com.medai.clinical.repository.PrescriptionRepository;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class WritePrescriptionTool implements ClinicalTool {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "writePrescription";
    }

    @Override
    public String getDescription() {
        return "Generate a structured prescription with automated drug-allergy cross-checking.";
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
                    "diagnosis": { "type": "string", "description": "Clinical diagnosis (e.g., Community-Acquired Pneumonia)" },
                    "medications": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "name": { "type": "string", "description": "Medication name (generic/brand)" },
                          "dosage": { "type": "string", "description": "Dosage (e.g., 500mg, 10ml)" },
                          "frequency": { "type": "string", "description": "Frequency (e.g., TID, BID, QD, PRN)" },
                          "duration": { "type": "string", "description": "Duration (e.g., 7 days, 10 days)" },
                          "instructions": { "type": "string", "description": "Oral intake instructions (e.g., Take with food)" }
                        },
                        "required": ["name", "dosage", "frequency", "duration"]
                      }
                    },
                    "notes": { "type": "string", "description": "Special clinical guidance for pharmacist or patient" }
                  },
                  "required": ["medications"]
                }
                """;
    }

    @Override
    public ToolResult execute(UUID tenantId, UUID doctorId, UUID patientId, JsonNode inputParams) {
        try {
            // 1. Patient Allergy Safety Check
            List<String> warnings = new ArrayList<>();
            if (patientId != null) {
                Patient patient = patientRepository.findByTenantIdAndId(tenantId, patientId).orElse(null);
                if (patient != null && patient.getAllergies() != null) {
                    List<String> allergies = patient.getAllergies();
                    JsonNode medsNode = inputParams.get("medications");
                    if (medsNode != null && medsNode.isArray()) {
                        for (JsonNode med : medsNode) {
                            String medName = med.has("name") ? med.get("name").asText().toLowerCase() : "";
                            for (String allergy : allergies) {
                                if (!allergy.isBlank() && medName.contains(allergy.toLowerCase().trim())) {
                                    warnings.add(String.format("⚠️ CONTRAINDICATION: Patient has documented allergy to '%s' matching prescribed '%s'", allergy, medName));
                                }
                            }
                        }
                    }
                }
            }

            String medsJson = "[]";
            if (inputParams.has("medications")) {
                medsJson = objectMapper.writeValueAsString(inputParams.get("medications"));
            }

            String diagnosis = inputParams.has("diagnosis") ? inputParams.get("diagnosis").asText() : "Clinical Prescription";
            String notes = inputParams.has("notes") ? inputParams.get("notes").asText() : null;

            Prescription prescription = Prescription.builder()
                    .tenantId(tenantId)
                    .patientId(patientId)
                    .doctorId(doctorId)
                    .medications(medsJson)
                    .diagnosis(diagnosis)
                    .status("ACTIVE")
                    .notes(notes)
                    .build();

            Prescription saved = prescriptionRepository.save(prescription);
            log.info("Prescription generated: {} for patient: {}", saved.getId(), patientId);

            Map<String, Object> data = new HashMap<>();
            data.put("prescriptionId", saved.getId().toString());
            data.put("diagnosis", saved.getDiagnosis());
            data.put("medications", inputParams.get("medications"));
            data.put("status", saved.getStatus());
            data.put("allergyWarnings", warnings);

            String summary = String.format("Prescribed %d medication(s) for %s",
                    inputParams.has("medications") ? inputParams.get("medications").size() : 1,
                    diagnosis);
            if (!warnings.isEmpty()) {
                summary += " (Allergy warnings flagged for review)";
            }

            return ToolResult.success(summary, data);
        } catch (Exception e) {
            log.error("Failed to generate prescription: {}", e.getMessage(), e);
            return ToolResult.failure("Prescription generation failed: " + e.getMessage());
        }
    }
}
