package com.medai.longitudinal.model;

import com.medai.anatomy.model.AnatomyTarget;
import com.medai.finding.model.StructuredFinding;

import java.math.BigDecimal;

/**
 * One finding-level interval comparison between a prior and a current report.
 *
 * <p>{@code currentAnatomyTarget} / {@code priorAnatomyTarget} are additive review metadata,
 * resolved by the shared anatomy layer after classification. They are {@code null} whenever the
 * corresponding finding is absent or its anatomy cannot be resolved safely, and they never
 * influence {@link #changeType()} or any measurement field.
 */
public record FindingComparison(
        StructuredFinding currentFinding,
        StructuredFinding priorFinding,
        FindingChangeType changeType,
        BigDecimal priorMeasurementMm,
        BigDecimal currentMeasurementMm,
        BigDecimal measurementDeltaMm,
        String explanation,
        AnatomyTarget currentAnatomyTarget,
        AnatomyTarget priorAnatomyTarget
) {
    public FindingComparison {
        explanation = explanation == null ? "" : explanation;
    }

    public FindingComparison(
            StructuredFinding currentFinding,
            StructuredFinding priorFinding,
            FindingChangeType changeType,
            BigDecimal priorMeasurementMm,
            BigDecimal currentMeasurementMm,
            BigDecimal measurementDeltaMm,
            String explanation
    ) {
        this(
                currentFinding,
                priorFinding,
                changeType,
                priorMeasurementMm,
                currentMeasurementMm,
                measurementDeltaMm,
                explanation,
                null,
                null);
    }

    /** Returns a copy carrying anatomy metadata. Classification and measurements are untouched. */
    public FindingComparison withAnatomyTargets(AnatomyTarget currentTarget, AnatomyTarget priorTarget) {
        return new FindingComparison(
                currentFinding,
                priorFinding,
                changeType,
                priorMeasurementMm,
                currentMeasurementMm,
                measurementDeltaMm,
                explanation,
                currentTarget,
                priorTarget);
    }
}
