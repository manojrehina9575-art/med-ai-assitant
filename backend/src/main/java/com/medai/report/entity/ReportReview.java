package com.medai.report.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * The review lifecycle of one AI-drafted report.
 *
 * <p>Sign-off, audit record and training label in one row, because a reviewer accepting,
 * correcting or rejecting a draft is one interaction. Splitting them would mean three writes that
 * can disagree with each other.
 */
@Entity
@Table(name = "report_reviews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReportReview {

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
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "claimed_by")
    private UUID claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "signed_by")
    private UUID signedBy;

    @Column(name = "signed_at")
    private Instant signedAt;

    /** ACCEPTED, EDITED or REJECTED — the training signal as well as the clinical decision. */
    @Column(name = "review_action", length = 20)
    private String reviewAction;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * What the model produced, frozen when the review was opened. The analysis row can be retried
     * and overwritten; what the clinician actually saw must not move under them afterwards.
     */
    @Column(name = "draft_content", columnDefinition = "TEXT")
    private String draftContent;

    /** What the clinician signed. Equal to the draft for an ACCEPTED review. */
    @Column(name = "final_content", columnDefinition = "TEXT")
    private String finalContent;

    /** Set when this review supersedes a signed one. A signed report is never edited in place. */
    @Column(name = "amends_review_id")
    private UUID amendsReviewId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
