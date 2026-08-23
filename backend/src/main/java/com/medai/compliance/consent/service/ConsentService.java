package com.medai.compliance.consent.service;

import com.medai.compliance.consent.dto.ConsentRequest;
import com.medai.compliance.consent.dto.ConsentResponse;
import com.medai.compliance.consent.entity.PatientConsent;
import com.medai.compliance.consent.repository.ConsentRepository;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final PatientRepository patientRepository;

    @Transactional
    public ConsentResponse grantConsent(ConsentRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Patient patient = patientRepository.findByIdAndTenantId(request.getPatientId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + request.getPatientId()));

        PatientConsent consent = consentRepository.findByPatientIdAndPurpose(patient.getId(), request.getPurpose())
                .orElse(PatientConsent.builder()
                        .tenantId(tenantId)
                        .patientId(patient.getId())
                        .purpose(request.getPurpose())
                        .build());

        consent.setStatus("GRANTED");
        consent.setSignerName(request.getSignerName());
        consent.setSignerRelationship(request.getSignerRelationship() != null ? request.getSignerRelationship() : "PATIENT");
        consent.setGrantedAt(Instant.now());
        consent.setExpiresAt(request.getExpiresAt());
        consent.setRevokedAt(null);
        consent.setSignatureHash(request.getSignatureHash());
        consent.setNotes(request.getNotes());

        PatientConsent saved = consentRepository.save(consent);
        log.info("Granted consent {} for patient {} under tenant {}", request.getPurpose(), patient.getId(), tenantId);
        return mapToResponse(saved, patient.getFirstName() + " " + patient.getLastName());
    }

    @Transactional
    public ConsentResponse revokeConsent(UUID consentId, String reason) {
        UUID tenantId = TenantContext.requireTenantId();
        PatientConsent consent = consentRepository.findById(consentId)
                .orElseThrow(() -> new IllegalArgumentException("Consent record not found"));

        if (!consent.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Unauthorized consent access");
        }

        consent.setStatus("REVOKED");
        consent.setRevokedAt(Instant.now());
        if (reason != null && !reason.isBlank()) {
            consent.setNotes((consent.getNotes() != null ? consent.getNotes() + " | " : "") + "Revocation reason: " + reason);
        }

        PatientConsent saved = consentRepository.save(consent);
        Patient patient = patientRepository.findById(saved.getPatientId()).orElse(null);
        String name = patient != null ? patient.getFirstName() + " " + patient.getLastName() : "Unknown";
        log.info("Revoked consent {} for patient {}", consent.getPurpose(), consent.getPatientId());
        return mapToResponse(saved, name);
    }

    @Transactional(readOnly = true)
    public List<ConsentResponse> getConsentsForPatient(UUID patientId) {
        UUID tenantId = TenantContext.requireTenantId();
        Patient patient = patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));
        String patientName = patient.getFirstName() + " " + patient.getLastName();

        return consentRepository.findByPatientId(patientId).stream()
                .map(c -> mapToResponse(c, patientName))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ConsentResponse> getAllConsentsForTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return consentRepository.findByTenantIdOrderByGrantedAtDesc(tenantId).stream()
                .map(c -> {
                    Patient patient = patientRepository.findById(c.getPatientId()).orElse(null);
                    String name = patient != null ? patient.getFirstName() + " " + patient.getLastName() : "Patient " + c.getPatientId();
                    return mapToResponse(c, name);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean hasValidConsent(UUID patientId, String purpose) {
        return consentRepository.findByPatientIdAndPurpose(patientId, purpose)
                .filter(c -> "GRANTED".equalsIgnoreCase(c.getStatus()))
                .filter(c -> c.getExpiresAt() == null || c.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }

    private ConsentResponse mapToResponse(PatientConsent c, String patientName) {
        return ConsentResponse.builder()
                .id(c.getId())
                .tenantId(c.getTenantId())
                .patientId(c.getPatientId())
                .patientName(patientName)
                .purpose(c.getPurpose())
                .status(c.getStatus())
                .signerName(c.getSignerName())
                .signerRelationship(c.getSignerRelationship())
                .grantedAt(c.getGrantedAt())
                .expiresAt(c.getExpiresAt())
                .revokedAt(c.getRevokedAt())
                .signatureHash(c.getSignatureHash())
                .notes(c.getNotes())
                .build();
    }
}
