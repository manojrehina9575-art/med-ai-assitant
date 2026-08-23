package com.medai.compliance.consent.controller;

import com.medai.compliance.consent.dto.ConsentRequest;
import com.medai.compliance.consent.dto.ConsentResponse;
import com.medai.compliance.consent.service.ConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/compliance/consents")
@RequiredArgsConstructor
@Tag(name = "Compliance & Consent Management", description = "GDPR & HIPAA Patient Consent Lifecycle")
public class ConsentController {

    private final ConsentService consentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    @Operation(summary = "Grant or update patient consent")
    public ResponseEntity<ConsentResponse> grantConsent(@Valid @RequestBody ConsentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(consentService.grantConsent(request));
    }

    @PostMapping("/{consentId}/revoke")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    @Operation(summary = "Revoke patient consent")
    public ResponseEntity<ConsentResponse> revokeConsent(
            @PathVariable UUID consentId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(consentService.revokeConsent(consentId, reason));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    @Operation(summary = "Get all consent records for a patient")
    public ResponseEntity<List<ConsentResponse>> getPatientConsents(@PathVariable UUID patientId) {
        return ResponseEntity.ok(consentService.getConsentsForPatient(patientId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    @Operation(summary = "List all consent records for current tenant")
    public ResponseEntity<List<ConsentResponse>> getAllConsents() {
        return ResponseEntity.ok(consentService.getAllConsentsForTenant());
    }

    @GetMapping("/patient/{patientId}/verify")
    @Operation(summary = "Verify if active consent exists for purpose")
    public ResponseEntity<Map<String, Boolean>> verifyConsent(
            @PathVariable UUID patientId,
            @RequestParam String purpose) {
        boolean valid = consentService.hasValidConsent(patientId, purpose);
        return ResponseEntity.ok(Map.of("hasValidConsent", valid));
    }
}
