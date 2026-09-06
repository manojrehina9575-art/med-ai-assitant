package com.medai.qa.model;

import com.medai.anatomy.model.AnatomyTarget;
import com.medai.finding.model.AnatomicalRegion;
import com.medai.finding.model.AnatomicalSide;
import com.medai.finding.model.AnatomicalStructure;
import com.medai.finding.model.FindingCertainty;
import com.medai.finding.model.FindingSourceSection;
import com.medai.finding.model.FindingStatus;
import com.medai.finding.model.FindingType;

/**
 * Supporting evidence for a QA issue: the structured finding the issue was raised from.
 *
 * <p>{@code anatomyTarget} is additive and optional. The existing flat anatomy fields are kept
 * exactly as they were so existing clients keep working; the target carries the stable machine
 * identifiers (system, structure code, viewer key) that string fields cannot express. It is
 * {@code null} whenever the anatomy layer cannot resolve a target safely.
 */
public record QaEvidence(
        FindingSourceSection sourceSection,
        FindingType findingType,
        AnatomicalStructure anatomy,
        String anatomyText,
        AnatomicalSide side,
        AnatomicalRegion region,
        FindingStatus status,
        FindingCertainty certainty,
        String sourceText,
        AnatomyTarget anatomyTarget
) {
    public QaEvidence(
            FindingSourceSection sourceSection,
            FindingType findingType,
            AnatomicalStructure anatomy,
            String anatomyText,
            AnatomicalSide side,
            AnatomicalRegion region,
            FindingStatus status,
            FindingCertainty certainty,
            String sourceText
    ) {
        this(sourceSection, findingType, anatomy, anatomyText, side, region, status, certainty, sourceText, null);
    }
}
