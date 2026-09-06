package com.medai.qa.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QaResult(
        UUID reportId,
        QaStatus status,
        List<QaIssue> issues,
        int issueCount,
        Instant evaluatedAt
) {
    public static QaResult from(UUID reportId, List<QaIssue> issues, Instant evaluatedAt) {
        List<QaIssue> immutableIssues = List.copyOf(issues);
        return new QaResult(
                reportId,
                immutableIssues.isEmpty() ? QaStatus.NO_ISSUES : QaStatus.REVIEW_RECOMMENDED,
                immutableIssues,
                immutableIssues.size(),
                evaluatedAt);
    }
}
