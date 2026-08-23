package com.medai.compliance.consent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patient_consents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "purpose", nullable = false, length = 50)
    private String purpose; // AI_ANALYSIS, RESEARCH_USE, DATA_SHARING, MODEL_TRAINING

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "GRANTED"; // GRANTED, REVOKED, EXPIRED

    @Column(name = "signer_name", nullable = false)
    private String signerName;

    @Column(name = "signer_relationship", nullable = false, length = 50)
    @Builder.Default
    private String signerRelationship = "PATIENT"; // PATIENT, GUARDIAN, POWER_OF_ATTORNEY

    @Column(name = "granted_at", nullable = false)
    @Builder.Default
    private Instant grantedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "signature_hash")
    private String signatureHash;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
