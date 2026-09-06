package com.medai.longitudinal.comparison;

import com.medai.finding.model.AnatomicalRegion;
import com.medai.finding.model.FindingStatus;
import com.medai.finding.model.StructuredFinding;
import com.medai.longitudinal.matching.FindingMatcher;
import com.medai.longitudinal.model.FindingChangeType;
import com.medai.longitudinal.model.FindingComparison;
import com.medai.longitudinal.model.LongitudinalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FindingComparator {

    private final FindingMatcher findingMatcher;
    private final MeasurementComparator measurementComparator;

    public LongitudinalResult compare(
            UUID currentReportId,
            UUID priorReportId,
            List<StructuredFinding> priorFindings,
            List<StructuredFinding> currentFindings
    ) {
        FindingMatcher.MatchResult matchResult = findingMatcher.match(priorFindings, currentFindings);
        List<FindingComparison> comparisons = new ArrayList<>();

        for (FindingMatcher.Match match : matchResult.matches()) {
            comparisons.add(compareMatched(match.priorFinding(), match.currentFinding()));
        }
        for (StructuredFinding current : matchResult.unmatchedCurrent()) {
            comparisons.add(compareUnmatchedCurrent(current));
        }
        for (StructuredFinding prior : matchResult.unmatchedPrior()) {
            comparisons.add(compareUnmatchedPrior(prior));
        }

        return LongitudinalResult.from(currentReportId, priorReportId, comparisons, Instant.now());
    }

    private FindingComparison compareMatched(StructuredFinding prior, StructuredFinding current) {
        MeasurementComparator.MeasurementComparison measurement = measurementComparator.compare(prior, current)
                .orElse(null);
        FindingChangeType changeType = classify(prior, current, measurement);
        return new FindingComparison(
                current,
                prior,
                changeType,
                measurement == null ? measurementComparator.toMillimeters(prior).orElse(null) : measurement.priorMillimeters(),
                measurement == null ? measurementComparator.toMillimeters(current).orElse(null) : measurement.currentMillimeters(),
                measurement == null ? null : measurement.deltaMillimeters(),
                explanation(changeType, prior, current, measurement));
    }

    private FindingComparison compareUnmatchedCurrent(StructuredFinding current) {
        FindingChangeType changeType = current.status() == FindingStatus.PRESENT
                ? FindingChangeType.NEW
                : FindingChangeType.INDETERMINATE;
        return new FindingComparison(
                current,
                null,
                changeType,
                null,
                measurementComparator.toMillimeters(current).orElse(null),
                null,
                explanation(changeType, null, current, null));
    }

    private FindingComparison compareUnmatchedPrior(StructuredFinding prior) {
        return new FindingComparison(
                null,
                prior,
                FindingChangeType.INDETERMINATE,
                measurementComparator.toMillimeters(prior).orElse(null),
                null,
                null,
                explanation(FindingChangeType.INDETERMINATE, prior, null, null));
    }

    private FindingChangeType classify(
            StructuredFinding prior,
            StructuredFinding current,
            MeasurementComparator.MeasurementComparison measurement
    ) {
        if (prior.status() == FindingStatus.PRESENT && current.status() == FindingStatus.ABSENT) {
            return FindingChangeType.RESOLVED;
        }
        if (prior.status() == FindingStatus.ABSENT && current.status() == FindingStatus.PRESENT) {
            return FindingChangeType.NEW;
        }
        if (prior.status() == FindingStatus.ABSENT && current.status() == FindingStatus.ABSENT) {
            return FindingChangeType.UNCHANGED;
        }

        if (measurement != null) {
            if (measurement.direction() > 0) {
                return FindingChangeType.INCREASED;
            }
            if (measurement.direction() < 0) {
                return FindingChangeType.DECREASED;
            }
        }

        if (importantAttributesChanged(prior, current)) {
            return FindingChangeType.CHANGED;
        }
        return FindingChangeType.UNCHANGED;
    }

    private boolean importantAttributesChanged(StructuredFinding prior, StructuredFinding current) {
        if (prior.certainty() != current.certainty()) {
            return true;
        }
        return prior.region() != AnatomicalRegion.UNSPECIFIED
                && current.region() != AnatomicalRegion.UNSPECIFIED
                && prior.region() != current.region();
    }

    private String explanation(
            FindingChangeType changeType,
            StructuredFinding prior,
            StructuredFinding current,
            MeasurementComparator.MeasurementComparison measurement
    ) {
        String label = findingLabel(current != null ? current : prior);
        return switch (changeType) {
            case INCREASED -> "Potential interval increase: " + label + " changed from "
                    + sourceMeasurement(prior) + " to " + sourceMeasurement(current) + ".";
            case DECREASED -> "Potential interval decrease: " + label + " changed from "
                    + sourceMeasurement(prior) + " to " + sourceMeasurement(current) + ".";
            case RESOLVED -> "Prior finding not present in current report: " + label + ".";
            case NEW -> current != null && prior != null
                    ? "Current report describes " + label + " after prior report negated it."
                    : "Current report contains new unmatched finding: " + label + ".";
            case CHANGED -> "Change detected between report findings for " + label + ".";
            case UNCHANGED -> measurement != null && measurement.direction() == 0
                    ? "No interval change detected for " + label + " (" + sourceMeasurement(prior)
                    + " and " + sourceMeasurement(current) + ")."
                    : "No interval change detected for " + label + ".";
            case INDETERMINATE -> "Insufficient information to classify interval change for " + label + ".";
        };
    }

    private String sourceMeasurement(StructuredFinding finding) {
        if (finding == null || finding.measurement() == null || finding.unit() == null) {
            return "unmeasured";
        }
        return formatNumber(BigDecimal.valueOf(finding.measurement())) + " " + finding.unit().toLowerCase(Locale.ROOT);
    }

    private String findingLabel(StructuredFinding finding) {
        if (finding == null) {
            return "finding";
        }

        List<String> parts = new ArrayList<>();
        switch (finding.side()) {
            case LEFT -> parts.add("left");
            case RIGHT -> parts.add("right");
            case BILATERAL -> parts.add("bilateral");
            case UNSPECIFIED -> {
            }
        }
        if (finding.region() != AnatomicalRegion.UNSPECIFIED) {
            parts.add(readable(finding.region().name()));
        }
        if (finding.anatomy() != null) {
            parts.add(readable(finding.anatomy().name()));
        }
        parts.add(readable(finding.findingType().name()));
        return String.join(" ", parts);
    }

    private String readable(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String formatNumber(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
