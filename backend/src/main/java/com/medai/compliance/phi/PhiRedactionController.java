package com.medai.compliance.phi;

import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import com.medai.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/compliance/phi")
@RequiredArgsConstructor
@Tag(name = "PHI Redaction & De-identification", description = "HIPAA Safe Harbor PHI Redaction Sandbox")
public class PhiRedactionController {

    private final PhiRedactionService phiRedactionService;
    private final PatientRepository patientRepository;

    @Data
    public static class RedactionTestRequest {
        private String text;
        /**
         * Optional. When given, the patient's own name, MRN, phone, email and address are removed
         * verbatim rather than left to a pattern to guess at — far and away the largest gain in
         * recall available, because the answer is already on the chart.
         */
        private UUID patientId;
    }

    @PostMapping("/sandbox")
    @Operation(summary = "Redact PHI from medical text",
               description = "Pattern-based redaction. The response carries a per-identifier "
                             + "coverage statement: this is not a complete Safe Harbor "
                             + "de-identifier and the response says exactly where it falls short.")
    public ResponseEntity<PhiRedactionService.RedactionResult> redactText(@RequestBody RedactionTestRequest request) {
        return ResponseEntity.ok(
                phiRedactionService.redact(request.getText(), knownIdentifiersFor(request.getPatientId())));
    }

    @GetMapping("/coverage")
    @Operation(summary = "What this redactor does and does not detect",
               description = "Per-identifier coverage against the HIPAA Safe Harbor list.")
    public ResponseEntity<List<PhiRedactionService.CoverageNote>> coverage() {
        return ResponseEntity.ok(phiRedactionService.coverage());
    }

    /** Pulls the identifiers already recorded for a patient, so they need no detection. */
    private List<String> knownIdentifiersFor(UUID patientId) {
        if (patientId == null) {
            return List.of();
        }

        Patient patient = patientRepository
                .findByIdAndTenantId(patientId, TenantContext.requireTenantId())
                .orElse(null);
        if (patient == null) {
            return List.of();
        }

        List<String> identifiers = new ArrayList<>();
        identifiers.add(patient.getFullName());
        identifiers.add(patient.getFirstName());
        identifiers.add(patient.getLastName());
        identifiers.add(patient.getMedicalRecordNumber());
        identifiers.add(patient.getPhone());
        identifiers.add(patient.getEmail());
        identifiers.add(patient.getAddress());
        identifiers.add(patient.getEmergencyContactName());
        identifiers.add(patient.getEmergencyContactPhone());
        return identifiers;
    }

    @Data
    public static class RestoreRequest {
        private String redactedText;
        private Map<String, String> tokenMap;
    }

    @PostMapping("/restore")
    @Operation(summary = "Restore pseudonymized tokens with mapping")
    public ResponseEntity<Map<String, String>> restoreText(@RequestBody RestoreRequest request) {
        String restored = phiRedactionService.restore(request.getRedactedText(), request.getTokenMap());
        return ResponseEntity.ok(Map.of("restoredText", restored));
    }
}
