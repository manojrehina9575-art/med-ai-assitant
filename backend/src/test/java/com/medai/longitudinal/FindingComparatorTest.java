package com.medai.longitudinal;

import com.medai.finding.extraction.DeterministicFindingExtractor;
import com.medai.finding.model.AnatomicalRegion;
import com.medai.finding.model.AnatomicalSide;
import com.medai.finding.model.AnatomicalStructure;
import com.medai.finding.model.FindingSourceSection;
import com.medai.finding.model.FindingType;
import com.medai.finding.model.StructuredFinding;
import com.medai.finding.normalization.AnatomyNormalizer;
import com.medai.finding.normalization.FindingTypeNormalizer;
import com.medai.longitudinal.comparison.FindingComparator;
import com.medai.longitudinal.comparison.MeasurementComparator;
import com.medai.longitudinal.matching.FindingMatcher;
import com.medai.longitudinal.model.FindingChangeType;
import com.medai.longitudinal.model.FindingComparison;
import com.medai.longitudinal.model.LongitudinalResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FindingComparatorTest {

    private static final UUID CURRENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PRIOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final DeterministicFindingExtractor extractor = new DeterministicFindingExtractor(
            new FindingTypeNormalizer(),
            new AnatomyNormalizer());
    private final FindingComparator comparator = new FindingComparator(
            new FindingMatcher(),
            new MeasurementComparator());

    @Test
    @DisplayName("classifies increased nodule measurement")
    void classifiesIncreasedMeasurement() {
        LongitudinalResult result = compare(
                "5 mm left upper lobe pulmonary nodule.",
                "8 mm left upper lobe pulmonary nodule.");

        FindingComparison comparison = onlyComparison(result);
        assertThat(comparison.changeType()).isEqualTo(FindingChangeType.INCREASED);
        assertThat(comparison.priorMeasurementMm()).isEqualByComparingTo("5");
        assertThat(comparison.currentMeasurementMm()).isEqualByComparingTo("8");
        assertThat(comparison.measurementDeltaMm()).isEqualByComparingTo("3");
        assertThat(comparison.currentFinding().findingType()).isEqualTo(FindingType.NODULE);
        assertThat(comparison.currentFinding().anatomy()).isEqualTo(AnatomicalStructure.LUNG);
        assertThat(comparison.currentFinding().side()).isEqualTo(AnatomicalSide.LEFT);
        assertThat(comparison.currentFinding().region()).isEqualTo(AnatomicalRegion.UPPER);
    }

    @Test
    @DisplayName("classifies decreased nodule measurement")
    void classifiesDecreasedMeasurement() {
        LongitudinalResult result = compare(
                "12 mm left pulmonary nodule.",
                "8 mm left pulmonary nodule.");

        FindingComparison comparison = onlyComparison(result);
        assertThat(comparison.changeType()).isEqualTo(FindingChangeType.DECREASED);
        assertThat(comparison.measurementDeltaMm()).isEqualByComparingTo("-4");
    }

    @Test
    @DisplayName("normalizes cm and mm before classifying unchanged")
    void normalizesCentimetersAndMillimeters() {
        LongitudinalResult result = compare(
                "1 cm left pulmonary nodule.",
                "10 mm left pulmonary nodule.");

        FindingComparison comparison = onlyComparison(result);
        assertThat(comparison.changeType()).isEqualTo(FindingChangeType.UNCHANGED);
        assertThat(comparison.priorMeasurementMm()).isEqualByComparingTo("10");
        assertThat(comparison.currentMeasurementMm()).isEqualByComparingTo("10");
        assertThat(comparison.measurementDeltaMm()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("classifies present prior and absent current finding as resolved")
    void classifiesResolvedFinding() {
        LongitudinalResult result = compare(
                "Small right pleural effusion.",
                "No right pleural effusion.");

        FindingComparison comparison = onlyComparison(result);
        assertThat(comparison.changeType()).isEqualTo(FindingChangeType.RESOLVED);
    }

    @Test
    @DisplayName("classifies absent prior and present current finding as new")
    void classifiesNewAfterPriorNegation() {
        LongitudinalResult result = compare(
                "No right pleural effusion.",
                "Small right pleural effusion.");

        FindingComparison comparison = onlyComparison(result);
        assertThat(comparison.changeType()).isEqualTo(FindingChangeType.NEW);
    }

    @Test
    @DisplayName("classifies current finding with no matching prior as new")
    void classifiesCurrentFindingWithNoPriorMatchAsNew() {
        LongitudinalResult result = compare(
                "",
                "Left pulmonary nodule.");

        FindingComparison comparison = onlyComparison(result);
        assertThat(comparison.changeType()).isEqualTo(FindingChangeType.NEW);
        assertThat(comparison.priorFinding()).isNull();
        assertThat(comparison.currentFinding()).isNotNull();
    }

    @Test
    @DisplayName("does not merge different anatomy and side into one changed lesion")
    void doesNotMergeDifferentAnatomyAndSide() {
        LongitudinalResult result = compare(
                "Right renal lesion.",
                "Left pulmonary lesion.");

        assertThat(result.comparisons()).hasSize(2);
        assertThat(result.comparisons()).noneSatisfy(comparison -> {
            assertThat(comparison.priorFinding()).isNotNull();
            assertThat(comparison.currentFinding()).isNotNull();
        });
        assertThat(result.comparisons()).extracting(FindingComparison::changeType)
                .containsExactlyInAnyOrder(FindingChangeType.NEW, FindingChangeType.INDETERMINATE);
    }

    @Test
    @DisplayName("handles multiple matched findings independently")
    void handlesMultipleFindings() {
        LongitudinalResult result = compare(
                "5 mm left upper lobe pulmonary nodule. 9 mm right lower lobe pulmonary nodule.",
                "6 mm left upper lobe pulmonary nodule. 9 mm right lower lobe pulmonary nodule.");

        assertThat(result.comparisons()).hasSize(2);
        assertThat(result.comparisons()).extracting(FindingComparison::changeType)
                .containsExactlyInAnyOrder(FindingChangeType.INCREASED, FindingChangeType.UNCHANGED);
        assertThat(result.summary().increasedFindings()).isEqualTo(1);
        assertThat(result.summary().unchangedFindings()).isEqualTo(1);
    }

    private LongitudinalResult compare(String priorText, String currentText) {
        return comparator.compare(CURRENT_ID, PRIOR_ID, findings(priorText), findings(currentText));
    }

    private List<StructuredFinding> findings(String text) {
        return extractor.extract(FindingSourceSection.UNKNOWN, text);
    }

    private FindingComparison onlyComparison(LongitudinalResult result) {
        assertThat(result.comparisons()).hasSize(1);
        return result.comparisons().getFirst();
    }
}
