package com.medai.longitudinal.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LongitudinalResult(
        UUID currentReportId,
        UUID priorReportId,
        List<FindingComparison> comparisons,
        LongitudinalSummary summary,
        Instant evaluatedAt
) {
    public LongitudinalResult {
        comparisons = comparisons == null ? List.of() : List.copyOf(comparisons);
        summary = summary == null ? LongitudinalSummary.from(comparisons) : summary;
        evaluatedAt = evaluatedAt == null ? Instant.now() : evaluatedAt;
    }

    public static LongitudinalResult from(
            UUID currentReportId,
            UUID priorReportId,
            List<FindingComparison> comparisons,
            Instant evaluatedAt
    ) {
        List<FindingComparison> immutableComparisons = List.copyOf(comparisons);
        return new LongitudinalResult(
                currentReportId,
                priorReportId,
                immutableComparisons,
                LongitudinalSummary.from(immutableComparisons),
                evaluatedAt);
    }
}
