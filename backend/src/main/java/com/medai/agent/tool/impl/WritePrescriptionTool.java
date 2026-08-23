package com.medai.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.agent.tool.ClinicalTool;
import com.medai.clinical.entity.Prescription;
import com.medai.clinical.repository.PrescriptionRepository;
import com.medai.clinical.safety.DrugSafetyFinding;
import com.medai.clinical.safety.DrugSafetyService;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Drafts a prescription, and refuses to write one that is unsafe.
 *
 * <p>The previous implementation checked allergies with {@code medicationName.contains(allergy)}
 * and then saved the row with {@code status = ACTIVE} regardless of what it found — the warning
 * was decoration attached to an already-committed prescription. A penicillin allergy did not stop
 * amoxicillin, and nothing at all looked at interactions, duplicates or doses.
 *
 * <p>Now {@link DrugSafetyService} decides. A contraindication or a major finding stops the write.
 * The prescriber can proceed, but only by re-issuing the request with the specific finding codes
 * in {@code acknowledgedWarnings}, which records the override against their user id — an
 * override is a clinical decision and it is never anonymous.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WritePrescriptionTool implements ClinicalTool {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final DrugSafetyService drugSafetyService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "writePrescription";
    }

    @Override
    public String getDescription() {
        return "Draft a prescription. Every medication is checked against the patient's documented "
               + "allergies (including drug-class cross-reactivity), against the other medications on "
               + "the prescription for interactions and duplicate therapy, and against accepted daily "
               + "dose maximums. A contraindicated or major finding blocks the prescription until a "
               + "prescriber explicitly acknowledges it.";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
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
                          "name": { "type": "string", "description": "Medication name (generic preferred; brands are resolved)" },
                          "dosage": { "type": "string", "description": "Dose per administration (e.g., 500mg, 10ml)" },
                          "frequency": { "type": "string", "description": "Frequency (e.g., TID, BID, QD, Q8H, PRN)" },
                          "duration": { "type": "string", "description": "Duration (e.g., 7 days, 10 days)" },
                          "instructions": { "type": "string", "description": "Administration instructions (e.g., Take with food)" }
                        },
                        "required": ["name", "dosage", "frequency", "duration"]
                      }
                    },
                    "notes": { "type": "string", "description": "Special clinical guidance for pharmacist or patient" },
                    "acknowledgedWarnings": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "Safety finding codes the prescriber has reviewed and accepted. Required to override a blocking finding; each code must match one returned by a previous blocked attempt."
                    }
                  },
                  "required": ["medications"]
                }
                """;
    }

    @Override
    public ToolResult execute(UUID tenantId, UUID doctorId, UUID patientId, JsonNode inputParams) {
        try {
            List<DrugSafetyService.ProposedMedication> medications = parseMedications(inputParams);
            if (medications.isEmpty()) {
                return ToolResult.failure("No medications were supplied.");
            }

            if (patientId == null) {
                return ToolResult.failure(
                        "A prescription requires a patient. Without one, no allergy or interaction "
                        + "check is possible and the prescription will not be written.");
            }

            Patient patient = patientRepository.findByTenantIdAndId(tenantId, patientId).orElse(null);
            if (patient == null) {
                return ToolResult.failure("Patient not found: " + patientId);
            }

            DrugSafetyService.Assessment assessment =
                    drugSafetyService.assess(medications, patient.getAllergies());

            Set<String> acknowledged = parseAcknowledged(inputParams);
            Set<String> unacknowledged = new LinkedHashSet<>(assessment.blockingCodes());
            unacknowledged.removeAll(acknowledged);

            if (!unacknowledged.isEmpty()) {
                log.warn("Prescription blocked for patient {} in tenant {}: {}",
                        patientId, tenantId, unacknowledged);
                return blocked(assessment, unacknowledged);
            }

            boolean overridden = !assessment.blockingCodes().isEmpty();

            Prescription prescription = Prescription.builder()
                    .tenantId(tenantId)
                    .patientId(patientId)
                    .doctorId(doctorId)
                    .medications(objectMapper.writeValueAsString(inputParams.get("medications")))
                    .diagnosis(text(inputParams, "diagnosis", "Clinical Prescription"))
                    .status("ACTIVE")
                    .notes(text(inputParams, "notes", null))
                    .safetyStatus(overridden ? "OVERRIDDEN" : assessment.isClear() ? "CLEAR" : "WARNING")
                    .safetyFindings(objectMapper.writeValueAsString(assessment.findings()))
                    .acknowledgedBy(overridden ? doctorId : null)
                    .acknowledgedAt(overridden ? Instant.now() : null)
                    .build();

            Prescription saved = prescriptionRepository.save(prescription);

            if (overridden) {
                log.warn("Prescription {} written for patient {} with acknowledged safety findings {} "
                         + "by doctor {}", saved.getId(), patientId, assessment.blockingCodes(), doctorId);
            } else {
                log.info("Prescription {} written for patient {}", saved.getId(), patientId);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("prescriptionId", saved.getId().toString());
            data.put("diagnosis", saved.getDiagnosis());
            data.put("medications", inputParams.get("medications"));
            data.put("normalisedIngredients", assessment.normalisedIngredients());
            data.put("status", saved.getStatus());
            data.put("safetyStatus", saved.getSafetyStatus());
            data.put("safetyFindings", assessment.findings());

            return ToolResult.success(summarise(medications.size(), saved.getDiagnosis(), assessment, overridden), data);

        } catch (Exception e) {
            log.error("Failed to generate prescription: {}", e.getMessage(), e);
            return ToolResult.failure("Prescription could not be written. The safety check did not complete.");
        }
    }

    /**
     * Refuses the prescription and hands back exactly what the prescriber has to accept to proceed.
     *
     * <p>The codes are returned verbatim so the follow-up call can name them. Requiring the exact
     * codes rather than a blanket "force" flag means a prescriber cannot override a finding they
     * were never shown — a second contraindication appearing after an edit blocks again.
     */
    private ToolResult blocked(DrugSafetyService.Assessment assessment, Set<String> unacknowledged) {
        StringBuilder message = new StringBuilder(
                "Prescription NOT written — unresolved safety findings:\n\n");

        for (DrugSafetyFinding finding : assessment.findings()) {
            if (!finding.blocking()) {
                continue;
            }
            message.append("  [").append(finding.severity()).append("] ")
                   .append(finding.code()).append(" — ")
                   .append(finding.message()).append('\n');
        }

        message.append("\nTo proceed, the prescriber must re-submit with acknowledgedWarnings ")
               .append("containing: ").append(String.join(", ", unacknowledged))
               .append(". The override is recorded against their user id.");

        return ToolResult.failure(message.toString());
    }

    private String summarise(int count, String diagnosis,
                             DrugSafetyService.Assessment assessment, boolean overridden) {
        String base = String.format("Prescribed %d medication(s) for %s", count, diagnosis);
        if (overridden) {
            return base + " — written over " + assessment.blockingCodes().size()
                   + " acknowledged safety finding(s)";
        }
        if (!assessment.isClear()) {
            return base + " — " + assessment.findings().size() + " advisory note(s) attached";
        }
        return base + " — safety check clear";
    }

    private List<DrugSafetyService.ProposedMedication> parseMedications(JsonNode input) {
        JsonNode node = input.get("medications");
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<DrugSafetyService.ProposedMedication> medications = new ArrayList<>();
        for (JsonNode med : node) {
            medications.add(new DrugSafetyService.ProposedMedication(
                    text(med, "name", ""),
                    text(med, "dosage", null),
                    text(med, "frequency", null),
                    text(med, "duration", null)));
        }
        return medications;
    }

    private Set<String> parseAcknowledged(JsonNode input) {
        JsonNode node = input.get("acknowledgedWarnings");
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        node.forEach(n -> codes.add(n.asText().trim()));
        return codes;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : fallback;
    }
}
