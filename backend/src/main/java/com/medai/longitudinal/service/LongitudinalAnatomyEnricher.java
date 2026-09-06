package com.medai.longitudinal.service;

import com.medai.anatomy.service.AnatomyService;
import com.medai.longitudinal.model.FindingComparison;
import com.medai.longitudinal.model.LongitudinalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Attaches shared {@link com.medai.anatomy.model.AnatomyTarget} metadata to an already-classified
 * longitudinal result.
 *
 * <p>Deliberately a separate step after {@code FindingComparator}: matching, measurement
 * normalisation and change classification stay free of anatomy concerns, so enrichment cannot
 * change a {@code FindingChangeType} or a measurement. Missing or uncatalogued anatomy simply
 * leaves the target null — the comparison itself is never suppressed or downgraded.
 */
@Component
@RequiredArgsConstructor
public class LongitudinalAnatomyEnricher {

    private final AnatomyService anatomyService;

    public LongitudinalResult enrich(LongitudinalResult result) {
        if (result == null || result.comparisons().isEmpty()) {
            return result;
        }

        List<FindingComparison> enriched = result.comparisons().stream()
                .map(this::enrichComparison)
                .toList();

        // The existing summary is carried over unchanged: it is derived from change types, and this
        // step does not touch them.
        return new LongitudinalResult(
                result.currentReportId(),
                result.priorReportId(),
                enriched,
                result.summary(),
                result.evaluatedAt());
    }

    private FindingComparison enrichComparison(FindingComparison comparison) {
        return comparison.withAnatomyTargets(
                anatomyService.targetFor(comparison.currentFinding()).orElse(null),
                anatomyService.targetFor(comparison.priorFinding()).orElse(null));
    }
}
