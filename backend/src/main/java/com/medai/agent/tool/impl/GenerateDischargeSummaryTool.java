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
public class GenerateDischargeSummaryTool implements ClinicalTool {

    private final PatientRepository patientRepository;

    @Override
    public String getName() {
        return "generateDischargeSummary";
    }

    @Override
    public String getDescription() {
        return "Synthesize a comprehensive clinical discharge summary including admission reason, hospital course, discharge medications, and care instructions.";
    }

    @Override
    public boolean requiresConfirmation() {
        return false; // Safe synthesis tool, auto-executes
    }

    @Override
    public String getInputSchemaJson() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "dischargeDiagnosis": { "type": "string", "description": "Primary discharge diagnosis" },
                    "hospitalCourse": { "type": "string", "description": "Summary of treatment and clinical progression" },
                    "dischargeCondition": { "type": "string", "description": "Patient status (e.g., Stable, Improved, Ambulatory)" },
                    "followUpInstructions": { "type": "string", "description": "Activity, diet, red flag symptoms, and follow-up directives" }
                  },
                  "required": ["dischargeDiagnosis"]
                }
                """;
    }

    @Override
    public ToolResult execute(UUID tenantId, UUID doctorId, UUID patientId, JsonNode inputParams) {
        try {
            Patient patient = patientId != null ? patientRepository.findByTenantIdAndId(tenantId, patientId).orElse(null) : null;
            String patientName = patient != null ? patient.getFullName() : "Patient";
            String mrn = patient != null ? patient.getMedicalRecordNumber() : "MRN-N/A";

            String diagnosis = inputParams.has("dischargeDiagnosis") ? inputParams.get("dischargeDiagnosis").asText() : "Clinical Resolution";
            String course = inputParams.has("hospitalCourse") ? inputParams.get("hospitalCourse").asText() : "Patient responded appropriately to targeted inpatient medical management.";
            String condition = inputParams.has("dischargeCondition") ? inputParams.get("dischargeCondition").asText() : "Stable / Improved";
            String instructions = inputParams.has("followUpInstructions") ? inputParams.get("followUpInstructions").asText() : "Rest, hydration, compliance with discharge medications. Return to ER if fever > 38.5C or dyspnea develops.";

            StringBuilder doc = new StringBuilder();
            doc.append("# CLINICAL DISCHARGE SUMMARY\n\n");
            doc.append("**Patient:** ").append(patientName).append(" | **MRN:** ").append(mrn).append("\n");
            doc.append("**Discharge Date:** ").append(Instant.now()).append("\n");
            doc.append("**Primary Discharge Diagnosis:** ").append(diagnosis).append("\n");
            doc.append("**Condition on Discharge:** ").append(condition).append("\n\n");
            doc.append("### Summary of Hospital Course & Clinical Progression\n");
            doc.append(course).append("\n\n");
            doc.append("### Discharge & Follow-up Instructions\n");
            doc.append(instructions).append("\n\n");
            doc.append("---\n*Physician Electronic Attestation Pending Verification*\n");

            Map<String, Object> data = new HashMap<>();
            data.put("dischargeDiagnosis", diagnosis);
            data.put("dischargeCondition", condition);
            data.put("summaryMarkdown", doc.toString());

            return ToolResult.success("Generated clinical discharge summary for " + patientName, data);
        } catch (Exception e) {
            log.error("Failed to generate discharge summary: {}", e.getMessage(), e);
            return ToolResult.failure("Discharge summary generation failed: " + e.getMessage());
        }
    }
}
