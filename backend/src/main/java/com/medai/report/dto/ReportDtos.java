package com.medai.report.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Sign-off and escalation payloads. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record ReviewView(
            UUID id,
            UUID analysisId,
            UUID patientId,
            String patientName,
            String analysisType,
            String status,
            UUID claimedBy,
            Instant claimedAt,
            UUID signedBy,
            Instant signedAt,
            String reviewAction,
            String rejectionReason,
            String draftContent,
            String finalContent,
            UUID amendsReviewId,
            Instant createdAt
    ) {
    }

    /**
     * @param action        ACCEPTED, EDITED or REJECTED
     * @param finalContent  required for EDITED — the corrected report the clinician is signing
     * @param rejectionReason required for REJECTED
     */
    public record SignRequest(String action, String finalContent, String rejectionReason) {
    }

    public record EscalationView(
            UUID id,
            UUID analysisId,
            UUID patientId,
            String patientName,
            String urgency,
            String findingSummary,
            String status,
            short escalationLevel,
            Instant lastNotifiedAt,
            UUID acknowledgedBy,
            Instant acknowledgedAt,
            String actionTaken,
            Instant createdAt
    ) {
    }

    public record AcknowledgeRequest(String actionTaken) {
    }

    /** Worklist counts, for the dashboard and for a pilot's turnaround metrics. */
    public record WorklistSummary(
            long awaitingReview,
            long inReview,
            long signedToday,
            long openEscalations,
            long acceptedAllTime,
            long editedAllTime,
            long rejectedAllTime
    ) {
    }

    // ── Prior-study comparison ───────────────────────────────────────────────

    /**
     * @param analytes one row per analyte, aligned across studies so a clinician reads a trend
     *                 rather than two reports side by side
     */
    public record ComparisonView(
            UUID patientId,
            String patientName,
            List<StudyRef> studies,
            List<AnalyteTrend> analytes
    ) {
    }

    public record StudyRef(UUID analysisId, String analysisType, Instant performedAt, String status) {
    }

    /**
     * @param delta       change from the previous value, null when there is no prior
     * @param direction   RISING, FALLING, STABLE or NEW
     */
    public record AnalyteTrend(
            String name,
            String loincCode,
            String unit,
            String referenceRange,
            List<AnalytePoint> points,
            Double delta,
            String direction
    ) {
    }

    public record AnalytePoint(UUID analysisId, Instant performedAt, Double value, String flag) {
    }
}
