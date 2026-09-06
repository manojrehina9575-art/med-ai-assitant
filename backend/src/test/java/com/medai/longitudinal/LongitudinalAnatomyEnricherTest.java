package com.medai.longitudinal;

import com.medai.anatomy.catalog.AnatomyCatalog;
import com.medai.anatomy.mapping.DeterministicFindingAnatomyMapper;
import com.medai.anatomy.model.AnatomyStructure;
import com.medai.anatomy.model.AnatomySystem;
import com.medai.anatomy.model.AnatomyTarget;
import com.medai.anatomy.service.AnatomyService;
import com.medai.finding.extraction.DeterministicFindingExtractor;
import com.medai.finding.model.AnatomicalRegion;
import com.medai.finding.model.AnatomicalSide;
import com.medai.finding.model.FindingSourceSection;
import com.medai.finding.model.StructuredFinding;
import com.medai.finding.normalization.AnatomyNormalizer;
import com.medai.finding.normalization.FindingTypeNormalizer;
import com.medai.longitudinal.comparison.FindingComparator;
import com.medai.longitudinal.comparison.MeasurementComparator;
import com.medai.longitudinal.matching.FindingMatcher;
import com.medai.longitudinal.model.FindingChangeType;
import com.medai.longitudinal.model.FindingComparison;
import com.medai.longitudinal.model.LongitudinalResult;
import com.medai.longitudinal.service.LongitudinalAnatomyEnricher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LongitudinalAnatomyEnricherTest {

    private static final UUID CURRENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PRIOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final DeterministicFindingExtractor extractor = new DeterministicFindingExtractor(
            new FindingTypeNormalizer(),
            new AnatomyNormalizer());
    private final FindingComparator comparator = new FindingComparator(
            new FindingMatcher(),
            new MeasurementComparator());
    private final AnatomyCatalog catalog = new AnatomyCatalog();
    private final LongitudinalAnatomyEnricher enricher = new LongitudinalAnatomyEnricher(
            new AnatomyService(new DeterministicFindingAnatomyMapper(catalog), catalog));

    @Test
    @DisplayName("exposes respiratory anatomy on both sides of an increased pulmonary nodule comparison")
    void exposesAnatomyForIncreasedPulmonaryNodule() {
        FindingComparison comparison = onlyComparison(enrich(
                "5 mm left upper lobe pulmonary nodule.",
                "8 mm left upper lobe pulmonary nodule."));

        assertThat(comparison.changeType()).isEqualTo(FindingChangeType.INCREASED);

        for (AnatomyTarget target : List.of(comparison.priorAnatomyTarget(), comparison.currentAnatomyTarget())) {
            assertThat(target).isNotNull();
            assertThat(target.system()).isEqualTo(AnatomySystem.RESPIRATORY);
            assertThat(target.structureCode()).isEqualTo(AnatomyStructure.LUNG);
            assertThat(target.side()).isEqualTo(AnatomicalSide.LEFT);
            assertThat(target.region()).isEqualTo(AnatomicalRegion.UPPER);
            assertThat(target.viewerKey()).isEqualTo("respiratory.lung.left");
            assertThat(target.displayName()).isEqualTo("Left upper lung");
        }
    }

    @Test
    @DisplayName("exposes a skeletal viewer key for a right humerus comparison")
    void exposesSkeletalViewerKeyForHumerus() {
        FindingComparison comparison = onlyComparison(enrich(
                "Nondisplaced fracture of the proximal right humerus.",
                "Healing fracture of the proximal right humerus."));

        assertThat(comparison.currentAnatomyTarget()).isNotNull();
        assertThat(comparison.currentAnatomyTarget().system()).isEqualTo(AnatomySystem.SKELETAL);
        assertThat(comparison.currentAnatomyTarget().structureCode()).isEqualTo(AnatomyStructure.HUMERUS);
        assertThat(comparison.currentAnatomyTarget().side()).isEqualTo(AnatomicalSide.RIGHT);
        assertThat(comparison.currentAnatomyTarget().viewerKey()).isEqualTo("skeleton.humerus.right");
        assertThat(comparison.priorAnatomyTarget()).isNotNull();
        assertThat(comparison.priorAnatomyTarget().viewerKey()).isEqualTo("skeleton.humerus.right");
    }

    @Test
    @DisplayName("populates only the current target for a NEW finding and never fabricates a prior one")
    void newFindingCarriesOnlyCurrentTarget() {
        LongitudinalResult result = enrich(
                "No acute abnormality.",
                "5 mm left pulmonary nodule.");

        FindingComparison newComparison = result.comparisons().stream()
                .filter(comparison -> comparison.changeType() == FindingChangeType.NEW)
                .findFirst()
                .orElseThrow();

        assertThat(newComparison.priorFinding()).isNull();
        assertThat(newComparison.priorAnatomyTarget()).isNull();
        assertThat(newComparison.currentAnatomyTarget()).isNotNull();
        assertThat(newComparison.currentAnatomyTarget().structureCode()).isEqualTo(AnatomyStructure.LUNG);
    }

    @Test
    @DisplayName("populates only the prior target when the current report has no matching finding")
    void unmatchedPriorCarriesOnlyPriorTarget() {
        LongitudinalResult result = enrich(
                "5 mm left pulmonary nodule.",
                "Right knee effusion.");

        FindingComparison priorOnly = result.comparisons().stream()
                .filter(comparison -> comparison.currentFinding() == null)
                .findFirst()
                .orElseThrow();

        assertThat(priorOnly.currentAnatomyTarget()).isNull();
        assertThat(priorOnly.priorAnatomyTarget()).isNotNull();
        assertThat(priorOnly.priorAnatomyTarget().structureCode()).isEqualTo(AnatomyStructure.LUNG);
    }

    @Test
    @DisplayName("keeps comparisons and their classifications when anatomy is not catalogued")
    void unsupportedAnatomyLeavesTargetsNull() {
        LongitudinalResult raw = compare(
                "5 mm right frontal lobe lesion.",
                "8 mm right frontal lobe lesion.");
        LongitudinalResult enriched = enricher.enrich(raw);

        // Findings with no recognised anatomy do not match each other, so the existing matcher
        // yields two one-sided comparisons. Enrichment must not suppress or downgrade either.
        assertThat(enriched.comparisons()).hasSameSizeAs(raw.comparisons());
        assertThat(enriched.comparisons())
                .extracting(FindingComparison::changeType)
                .containsExactlyElementsOf(raw.comparisons().stream().map(FindingComparison::changeType).toList());
        assertThat(enriched.comparisons())
                .allSatisfy(comparison -> {
                    assertThat(comparison.currentAnatomyTarget()).isNull();
                    assertThat(comparison.priorAnatomyTarget()).isNull();
                });
        assertThat(enriched.comparisons())
                .anySatisfy(comparison -> assertThat(comparison.changeType()).isEqualTo(FindingChangeType.NEW));
        assertThat(enriched.comparisons())
                .anySatisfy(comparison -> {
                    assertThat(comparison.changeType()).isEqualTo(FindingChangeType.INDETERMINATE);
                    assertThat(comparison.priorMeasurementMm()).isEqualByComparingTo("5");
                });
    }

    @Test
    @DisplayName("keeps a bilateral target without inventing a side-specific viewer key")
    void bilateralTargetHasNoViewerKey() {
        FindingComparison comparison = onlyComparison(enrich(
                "Small bilateral pleural effusions.",
                "Moderate bilateral pleural effusions."));

        assertThat(comparison.currentAnatomyTarget()).isNotNull();
        assertThat(comparison.currentAnatomyTarget().structureCode()).isEqualTo(AnatomyStructure.PLEURA);
        assertThat(comparison.currentAnatomyTarget().side()).isEqualTo(AnatomicalSide.BILATERAL);
        assertThat(comparison.currentAnatomyTarget().viewerKey()).isNull();
        assertThat(comparison.priorAnatomyTarget().viewerKey()).isNull();
    }

    @Test
    @DisplayName("enrichment leaves classification, measurements, explanations and summary identical")
    void enrichmentDoesNotChangeComparisonResults() {
        List<String[]> cases = List.of(
                new String[] {"5 mm left upper lobe pulmonary nodule.", "8 mm left upper lobe pulmonary nodule."},
                new String[] {"12 mm left pulmonary nodule.", "8 mm left pulmonary nodule."},
                new String[] {"Right proximal humerus fracture.", "No acute fracture."},
                new String[] {"No acute abnormality.", "Right knee effusion."},
                new String[] {"5 mm right frontal lobe lesion.", "8 mm right frontal lobe lesion."},
                new String[] {"Small bilateral pleural effusions.", "Moderate bilateral pleural effusions."});

        for (String[] reports : cases) {
            LongitudinalResult raw = compare(reports[0], reports[1]);
            LongitudinalResult enriched = enricher.enrich(raw);

            assertThat(enriched.summary()).isEqualTo(raw.summary());
            assertThat(enriched.currentReportId()).isEqualTo(raw.currentReportId());
            assertThat(enriched.priorReportId()).isEqualTo(raw.priorReportId());
            assertThat(enriched.evaluatedAt()).isEqualTo(raw.evaluatedAt());
            assertThat(enriched.comparisons()).hasSameSizeAs(raw.comparisons());

            for (int index = 0; index < raw.comparisons().size(); index++) {
                FindingComparison before = raw.comparisons().get(index);
                FindingComparison after = enriched.comparisons().get(index);

                assertThat(after.changeType()).isEqualTo(before.changeType());
                assertThat(after.priorMeasurementMm()).isEqualTo(before.priorMeasurementMm());
                assertThat(after.currentMeasurementMm()).isEqualTo(before.currentMeasurementMm());
                assertThat(after.measurementDeltaMm()).isEqualTo(before.measurementDeltaMm());
                assertThat(after.explanation()).isEqualTo(before.explanation());
                assertThat(after.currentFinding()).isEqualTo(before.currentFinding());
                assertThat(after.priorFinding()).isEqualTo(before.priorFinding());
                // The unenriched comparison never carries anatomy metadata.
                assertThat(before.currentAnatomyTarget()).isNull();
                assertThat(before.priorAnatomyTarget()).isNull();
            }
        }
    }

    @Test
    @DisplayName("enrichment is a no-op for an empty or null result")
    void enrichmentHandlesEmptyResults() {
        LongitudinalResult empty = LongitudinalResult.from(CURRENT_ID, PRIOR_ID, List.of(), null);

        assertThat(enricher.enrich(empty)).isSameAs(empty);
        assertThat(enricher.enrich(null)).isNull();
    }

    private LongitudinalResult enrich(String priorText, String currentText) {
        return enricher.enrich(compare(priorText, currentText));
    }

    private LongitudinalResult compare(String priorText, String currentText) {
        return comparator.compare(CURRENT_ID, PRIOR_ID, findings(priorText), findings(currentText));
    }

    private List<StructuredFinding> findings(String text) {
        return extractor.extract(FindingSourceSection.FINDINGS, text);
    }

    private FindingComparison onlyComparison(LongitudinalResult result) {
        assertThat(result.comparisons()).hasSize(1);
        return result.comparisons().getFirst();
    }
}
