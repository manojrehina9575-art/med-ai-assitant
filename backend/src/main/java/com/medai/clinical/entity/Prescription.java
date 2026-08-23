package com.medai.clinical.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prescriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String medications = "[]";

    @Column(length = 500)
    private String diagnosis;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * CLEAR, WARNING, or OVERRIDDEN. A prescription cannot reach the table in a contraindicated
     * state — {@code WritePrescriptionTool} refuses to write one — so OVERRIDDEN is the strongest
     * value here, and V16 constrains it to carry attribution.
     */
    @Column(name = "safety_status", nullable = false, length = 30)
    @Builder.Default
    private String safetyStatus = "CLEAR";

    /**
     * The {@code DrugSafetyService} findings as of the moment of writing, serialised verbatim.
     * Re-running the checker later answers a different question: the knowledge base will have
     * changed, and what was known at the time of prescribing is what matters afterwards.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safety_findings", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String safetyFindings = "[]";

    /** The prescriber who accepted a blocking finding. Never null when status is OVERRIDDEN. */
    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
