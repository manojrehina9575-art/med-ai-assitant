package com.medai.longitudinal;

import com.medai.finding.model.AnatomicalRegion;
import com.medai.finding.model.AnatomicalSide;
import com.medai.finding.model.AnatomicalStructure;
import com.medai.finding.model.FindingCertainty;
import com.medai.finding.model.FindingSourceSection;
import com.medai.finding.model.FindingStatus;
import com.medai.finding.model.FindingType;
import com.medai.finding.model.StructuredFinding;
import com.medai.longitudinal.matching.FindingMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FindingMatcherTest {

    private final FindingMatcher matcher = new FindingMatcher();

    @Test
    @DisplayName("same finding type, anatomy, side and region matches")
    void matchesSameFindingAnatomySideAndRegion() {
        StructuredFinding prior = finding("prior", FindingType.NODULE,
                AnatomicalStructure.LUNG, AnatomicalSide.LEFT, AnatomicalRegion.UPPER);
        StructuredFinding current = finding("current", FindingType.NODULE,
                AnatomicalStructure.LUNG, AnatomicalSide.LEFT, AnatomicalRegion.UPPER);

        FindingMatcher.MatchResult result = matcher.match(List.of(prior), List.of(current));

        assertThat(result.matches()).singleElement().satisfies(match -> {
            assertThat(match.priorFinding()).isEqualTo(prior);
            assertThat(match.currentFinding()).isEqualTo(current);
        });
        assertThat(result.unmatchedPrior()).isEmpty();
        assertThat(result.unmatchedCurrent()).isEmpty();
    }

    @Test
    @DisplayName("different anatomy does not match")
    void doesNotMatchDifferentAnatomy() {
        StructuredFinding prior = finding("prior", FindingType.LESION,
                AnatomicalStructure.KIDNEY, AnatomicalSide.RIGHT, AnatomicalRegion.UNSPECIFIED);
        StructuredFinding current = finding("current", FindingType.LESION,
                AnatomicalStructure.LUNG, AnatomicalSide.RIGHT, AnatomicalRegion.UNSPECIFIED);

        FindingMatcher.MatchResult result = matcher.match(List.of(prior), List.of(current));

        assertThat(result.matches()).isEmpty();
        assertThat(result.unmatchedPrior()).containsExactly(prior);
        assertThat(result.unmatchedCurrent()).containsExactly(current);
    }

    @Test
    @DisplayName("different side does not match")
    void doesNotMatchDifferentSide() {
        StructuredFinding prior = finding("prior", FindingType.NODULE,
                AnatomicalStructure.LUNG, AnatomicalSide.LEFT, AnatomicalRegion.UPPER);
        StructuredFinding current = finding("current", FindingType.NODULE,
                AnatomicalStructure.LUNG, AnatomicalSide.RIGHT, AnatomicalRegion.UPPER);

        FindingMatcher.MatchResult result = matcher.match(List.of(prior), List.of(current));

        assertThat(result.matches()).isEmpty();
        assertThat(result.unmatchedPrior()).containsExactly(prior);
        assertThat(result.unmatchedCurrent()).containsExactly(current);
    }

    @Test
    @DisplayName("multiple findings produce deterministic one-to-one matches")
    void matchesMultipleFindingsOneToOne() {
        StructuredFinding priorLeft = finding("prior-left", FindingType.NODULE,
                AnatomicalStructure.LUNG, AnatomicalSide.LEFT, AnatomicalRegion.UPPER);
        StructuredFinding priorRight = finding("prior-right", FindingType.NODULE,
                AnatomicalStructure.LUNG, AnatomicalSide.RIGHT, AnatomicalRegion.LOWER);
        StructuredFinding currentLeft = finding("current-left", FindingType.NODULE,
                AnatomicalStructure.LUNG, AnatomicalSide.LEFT, AnatomicalRegion.UPPER);
        StructuredFinding currentRight = finding("current-right", FindingType.NODULE,
                AnatomicalStructure.LUNG, AnatomicalSide.RIGHT, AnatomicalRegion.LOWER);

        FindingMatcher.MatchResult result = matcher.match(
                List.of(priorLeft, priorRight),
                List.of(currentLeft, currentRight));

        assertThat(result.matches()).hasSize(2);
        assertThat(result.matches()).extracting(match -> match.priorFinding().id())
                .containsExactlyInAnyOrder("prior-left", "prior-right");
        assertThat(result.matches()).extracting(match -> match.currentFinding().id())
                .containsExactlyInAnyOrder("current-left", "current-right");
        assertThat(result.unmatchedPrior()).isEmpty();
        assertThat(result.unmatchedCurrent()).isEmpty();
    }

    @Test
    @DisplayName("ambiguous multiple candidates remain unmatched")
    void handlesAmbiguousCandidatesConservatively() {
        StructuredFinding prior = finding("prior", FindingType.NODULE,
                AnatomicalStructure.LUNG, AnatomicalSide.LEFT, AnatomicalRegion.UNSPECIFIED);
        StructuredFinding currentUpper = finding("current-upper", FindingType.NODULE,
                AnatomicalStructure.LUNG, AnatomicalSide.LEFT, AnatomicalRegion.UPPER);
        StructuredFinding currentLower = finding("current-lower", FindingType.NODULE,
                AnatomicalStructure.LUNG, AnatomicalSide.LEFT, AnatomicalRegion.LOWER);

        FindingMatcher.MatchResult result = matcher.match(List.of(prior), List.of(currentUpper, currentLower));

        assertThat(result.matches()).isEmpty();
        assertThat(result.unmatchedPrior()).containsExactly(prior);
        assertThat(result.unmatchedCurrent()).containsExactly(currentUpper, currentLower);
    }

    private StructuredFinding finding(
            String id,
            FindingType type,
            AnatomicalStructure anatomy,
            AnatomicalSide side,
            AnatomicalRegion region
    ) {
        return new StructuredFinding(
                id,
                type,
                anatomy,
                anatomy.name().toLowerCase(),
                side,
                region,
                FindingStatus.PRESENT,
                FindingCertainty.ASSERTED,
                null,
                null,
                FindingSourceSection.FINDINGS,
                id,
                0,
                id.length(),
                Set.of(type.name(), anatomy.name(), side.name(), region.name(), FindingStatus.PRESENT.name()));
    }
}
