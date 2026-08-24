package com.medai.report.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A critical finding awaiting acknowledgement.
 *
 * <p>Exists because a banner on a screen nobody was looking at does not discharge a
 * notification-and-acknowledgement duty. The record is the point: who was told, when, whether they
 * responded, and what they did about it.
 */
@Entity
@Table(name = "critical_result_escalations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CriticalResultEscalation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(nullable = false, length = 20)
    private String urgency;

    @Column(name = "finding_summary", nullable = false, columnDefinition = "TEXT")
    private String findingSummary;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    /** Rises each time the acknowledgement deadline passes without one. */
    @Column(name = "escalation_level", nullable = false)
    @Builder.Default
    private Short escalationLevel = 0;

    @Column(name = "last_notified_at", nullable = false)
    @Builder.Default
    private Instant lastNotifiedAt = Instant.now();

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /** An acknowledgement with no action recorded is a click, not a clinical response. */
    @Column(name = "action_taken", columnDefinition = "TEXT")
    private String actionTaken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
