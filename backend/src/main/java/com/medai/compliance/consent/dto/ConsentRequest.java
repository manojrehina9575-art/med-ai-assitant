package com.medai.compliance.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ConsentRequest {
    @NotNull(message = "Patient ID is required")
    private UUID patientId;

    @NotBlank(message = "Purpose is required (e.g., AI_ANALYSIS, RESEARCH_USE, DATA_SHARING, MODEL_TRAINING)")
    private String purpose;

    @NotBlank(message = "Signer name is required")
    private String signerName;

    @Builder.Default
    private String signerRelationship = "PATIENT";

    private Instant expiresAt;
    private String signatureHash;
    private String notes;
}
