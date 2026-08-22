package com.medai.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.medai.agent.tool.ClinicalTool;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class SearchPatientHistoryTool implements ClinicalTool {

    private final PatientRepository patientRepository;
    private final AnalysisRequestRepository analysisRepository;

    @Override
    public String getName() {
        return "searchPatientHistory";
    }

    @Override
    public String getDescription() {
        return "Retrieve the patient's full clinical record, documented allergies, medical history, and recent diagnostic lab/imaging studies.";
    }

    @Override
    public boolean requiresConfirmation() {
        return false; // Non-destructive query tool, auto-executes
    }

    @Override
    public String getInputSchemaJson() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "queryFocus": { "type": "string", "description": "Specific focus area (e.g., allergies, recent imaging, lab trends, surgical history)" }
                  }
                }
                """;
    }

    @Override
    public ToolResult execute(UUID tenantId, UUID doctorId, UUID patientId, JsonNode inputParams) {
        try {
            if (patientId == null) {
                return ToolResult.failure("Patient ID is required to fetch medical history.");
            }

            Patient patient = patientRepository.findByTenantIdAndId(tenantId, patientId)
                    .orElse(null);
            if (patient == null) {
                return ToolResult.failure("Patient not found with ID: " + patientId);
            }

            List<AnalysisRequest> recentAnalyses = analysisRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(
                    tenantId, patientId, PageRequest.of(0, 5)
            ).getContent();

            List<Map<String, Object>> studySummaries = recentAnalyses.stream().map(a -> {
                Map<String, Object> m = new HashMap<>();
                m.put("type", a.getAnalysisType());
                m.put("status", a.getStatus());
                m.put("urgency", a.getUrgency());
                m.put("clinicalNotes", a.getClinicalNotes());
                m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
                return m;
            }).collect(Collectors.toList());

            Map<String, Object> data = new HashMap<>();
            data.put("patientName", patient.getFullName());
            data.put("mrn", patient.getMedicalRecordNumber());
            data.put("gender", patient.getGender());
            data.put("dob", patient.getDateOfBirth() != null ? patient.getDateOfBirth().toString() : null);
            data.put("bloodGroup", patient.getBloodGroup());
            data.put("allergies", patient.getAllergies());
            data.put("medicalHistory", patient.getMedicalHistory());
            data.put("recentDiagnosticStudies", studySummaries);

            return ToolResult.success(
                    String.format("Loaded clinical record for %s (Allergies: %d recorded, History: %d entries)",
                            patient.getFullName(),
                            patient.getAllergies() != null ? patient.getAllergies().size() : 0,
                            patient.getMedicalHistory() != null ? patient.getMedicalHistory().size() : 0),
                    data
            );
        } catch (Exception e) {
            log.error("Failed to query patient history: {}", e.getMessage(), e);
            return ToolResult.failure("Patient history query failed: " + e.getMessage());
        }
    }
}
