package com.medai.longitudinal.model;

import java.util.List;

public record LongitudinalSummary(
        int newFindings,
        int resolvedFindings,
        int increasedFindings,
        int decreasedFindings,
        int unchangedFindings,
        int changedFindings,
        int indeterminateFindings
) {
    public static LongitudinalSummary from(List<FindingComparison> comparisons) {
        return new LongitudinalSummary(
                count(comparisons, FindingChangeType.NEW),
                count(comparisons, FindingChangeType.RESOLVED),
                count(comparisons, FindingChangeType.INCREASED),
                count(comparisons, FindingChangeType.DECREASED),
                count(comparisons, FindingChangeType.UNCHANGED),
                count(comparisons, FindingChangeType.CHANGED),
                count(comparisons, FindingChangeType.INDETERMINATE));
    }

    private static int count(List<FindingComparison> comparisons, FindingChangeType type) {
        return (int) comparisons.stream()
                .filter(comparison -> comparison.changeType() == type)
                .count();
    }
}
