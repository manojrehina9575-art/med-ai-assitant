package com.medai.finding.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record StructuredFinding(
        String id,
        FindingType findingType,
        AnatomicalStructure anatomy,
        String anatomyText,
        AnatomicalSide side,
        AnatomicalRegion region,
        FindingStatus status,
        FindingCertainty certainty,
        Double measurement,
        String unit,
        FindingSourceSection sourceSection,
        String sourceText,
        Integer startOffset,
        Integer endOffset,
        Set<String> normalizedTerms
) {
    public StructuredFinding {
        findingType = Objects.requireNonNull(findingType, "findingType is required");
        side = side == null ? AnatomicalSide.UNSPECIFIED : side;
        region = region == null ? AnatomicalRegion.UNSPECIFIED : region;
        status = status == null ? FindingStatus.PRESENT : status;
        certainty = certainty == null ? FindingCertainty.ASSERTED : certainty;
        sourceSection = sourceSection == null ? FindingSourceSection.UNKNOWN : sourceSection;
        sourceText = sourceText == null ? "" : sourceText.strip();
        normalizedTerms = normalizedTerms == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(normalizedTerms));
    }
}
