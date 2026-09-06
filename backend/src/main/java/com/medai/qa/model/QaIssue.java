package com.medai.qa.model;

import java.util.List;

public record QaIssue(
        String id,
        QaIssueType type,
        QaSeverity severity,
        String message,
        String findingText,
        String impressionText,
        String sectionA,
        String sectionB,
        LateralitySide sideA,
        LateralitySide sideB,
        String anatomyCode,
        String region,
        double confidence,
        String detector,
        String detectorVersion,
        List<QaEvidence> evidence
) {
    public QaIssue {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public QaIssue(
            String id,
            QaIssueType type,
            QaSeverity severity,
            String message,
            String findingText,
            String impressionText,
            String sectionA,
            String sectionB,
            LateralitySide sideA,
            LateralitySide sideB,
            String anatomyCode,
            String region,
            double confidence,
            String detector,
            String detectorVersion
    ) {
        this(
                id,
                type,
                severity,
                message,
                findingText,
                impressionText,
                sectionA,
                sectionB,
                sideA,
                sideB,
                anatomyCode,
                region,
                confidence,
                detector,
                detectorVersion,
                List.of());
    }

    public QaIssue withEvidence(List<QaEvidence> evidence) {
        return new QaIssue(
                id,
                type,
                severity,
                message,
                findingText,
                impressionText,
                sectionA,
                sectionB,
                sideA,
                sideB,
                anatomyCode,
                region,
                confidence,
                detector,
                detectorVersion,
                evidence);
    }
}
