package com.medai.compliance.consent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsentResponse {
    private UUID id;
    private UUID tenantId;
    private UUID patientId;
    private String patientName;
    private String purpose;
    private String status;
    private String signerName;
    private String signerRelationship;
    private Instant grantedAt;
    private Instant expiresAt;
    private Instant revokedAt;
    private String signatureHash;
    private String notes;
}
