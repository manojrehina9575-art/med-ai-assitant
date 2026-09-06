package com.medai.finding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.finding.extraction.DeterministicFindingExtractor;
import com.medai.finding.extraction.ReportSectionParser;
import com.medai.finding.model.*;
import com.medai.finding.normalization.AnatomyNormalizer;
import com.medai.finding.normalization.FindingTypeNormalizer;
import com.medai.finding.service.FindingExtractionService;
import com.medai.report.entity.ReportReview;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FindingExtractionServiceTest {

    private final DeterministicFindingExtractor extractor = new DeterministicFindingExtractor(
            new FindingTypeNormalizer(),
            new AnatomyNormalizer());
    private final FindingExtractionService service = new FindingExtractionService(
            new ReportSectionParser(new ObjectMapper()),
            extractor);

    @Test
    @DisplayName("extracts asserted right proximal humerus fracture")
    void extractsAssertedFracture() {
        StructuredFinding finding = only("Comminuted fracture of the proximal right humerus.");

        assertFinding(
                finding,
                FindingType.FRACTURE,
                AnatomicalStructure.HUMERUS,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.PROXIMAL,
                FindingStatus.PRESENT,
                FindingCertainty.ASSERTED);
        assertThat(finding.sourceText()).isEqualTo("Comminuted fracture of the proximal right humerus.");
        assertThat(finding.startOffset()).isZero();
        assertThat(finding.endOffset()).isEqualTo("Comminuted fracture of the proximal right humerus.".length());
    }

    @Test
    @DisplayName("extracts negated left femur fracture as absent")
    void extractsNegatedFracture() {
        StructuredFinding finding = only("No acute fracture of the left femur.");

        assertFinding(
                finding,
                FindingType.FRACTURE,
                AnatomicalStructure.FEMUR,
                AnatomicalSide.LEFT,
                AnatomicalRegion.UNSPECIFIED,
                FindingStatus.ABSENT,
                FindingCertainty.ASSERTED);
    }

    @Test
    @DisplayName("extracts possible left pulmonary nodule")
    void extractsPossiblePulmonaryNodule() {
        StructuredFinding finding = only("Possible left pulmonary nodule.");

        assertFinding(
                finding,
                FindingType.NODULE,
                AnatomicalStructure.LUNG,
                AnatomicalSide.LEFT,
                AnatomicalRegion.UNSPECIFIED,
                FindingStatus.PRESENT,
                FindingCertainty.POSSIBLE);
    }

    @Test
    @DisplayName("extracts right renal lesion as kidney anatomy")
    void extractsRenalLesion() {
        StructuredFinding finding = only("Right renal lesion.");

        assertFinding(
                finding,
                FindingType.LESION,
                AnatomicalStructure.KIDNEY,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.UNSPECIFIED,
                FindingStatus.PRESENT,
                FindingCertainty.ASSERTED);
    }

    @Test
    @DisplayName("extracts right brain aneurysm")
    void extractsBrainAneurysm() {
        StructuredFinding finding = only("Right brain aneurysm.");

        assertFinding(
                finding,
                FindingType.ANEURYSM,
                AnatomicalStructure.BRAIN,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.UNSPECIFIED,
                FindingStatus.PRESENT,
                FindingCertainty.ASSERTED);
    }

    @Test
    @DisplayName("extracts left pleural effusion")
    void extractsPleuralEffusion() {
        StructuredFinding finding = only("Small left pleural effusion.");

        assertFinding(
                finding,
                FindingType.EFFUSION,
                AnatomicalStructure.PLEURA,
                AnatomicalSide.LEFT,
                AnatomicalRegion.UNSPECIFIED,
                FindingStatus.PRESENT,
                FindingCertainty.ASSERTED);
    }

    @Test
    @DisplayName("extracts pleural effusion with unspecified side when none is stated")
    void extractsNoPleuralEffusionWithoutSide() {
        StructuredFinding finding = only("No pleural effusion.");

        assertFinding(
                finding,
                FindingType.EFFUSION,
                AnatomicalStructure.PLEURA,
                AnatomicalSide.UNSPECIFIED,
                AnatomicalRegion.UNSPECIFIED,
                FindingStatus.ABSENT,
                FindingCertainty.ASSERTED);
    }

    @Test
    @DisplayName("extracts right shoulder dislocation")
    void extractsShoulderDislocation() {
        StructuredFinding finding = only("Right shoulder dislocation.");

        assertFinding(
                finding,
                FindingType.DISLOCATION,
                AnatomicalStructure.SHOULDER,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.UNSPECIFIED,
                FindingStatus.PRESENT,
                FindingCertainty.ASSERTED);
    }

    @Test
    @DisplayName("does not fabricate anatomy for generic negated dislocation")
    void doesNotFabricateAnatomy() {
        StructuredFinding finding = only("No acute dislocation.");

        assertFinding(
                finding,
                FindingType.DISLOCATION,
                null,
                AnatomicalSide.UNSPECIFIED,
                AnatomicalRegion.UNSPECIFIED,
                FindingStatus.ABSENT,
                FindingCertainty.ASSERTED);
    }

    @Test
    @DisplayName("does not interpret letters inside words as laterality")
    void doesNotInferLateralityFromLettersInsideWords() {
        StructuredFinding finding = only("Large renal lesion.");

        assertFinding(
                finding,
                FindingType.LESION,
                AnatomicalStructure.KIDNEY,
                AnatomicalSide.UNSPECIFIED,
                AnatomicalRegion.UNSPECIFIED,
                FindingStatus.PRESENT,
                FindingCertainty.ASSERTED);
    }

    @Test
    @DisplayName("extracts bilateral pleural effusion")
    void extractsBilateralPleuralEffusion() {
        StructuredFinding finding = only("Bilateral pleural effusions.");

        assertFinding(
                finding,
                FindingType.EFFUSION,
                AnatomicalStructure.PLEURA,
                AnatomicalSide.BILATERAL,
                AnatomicalRegion.UNSPECIFIED,
                FindingStatus.PRESENT,
                FindingCertainty.ASSERTED);
    }

    @Test
    @DisplayName("preserves source sections from headed report text")
    void preservesSourceSections() {
        List<StructuredFinding> findings = service.extract("""
                FINDINGS:
                There is a comminuted fracture involving the proximal right humerus.

                IMPRESSION:
                Comminuted fracture of the proximal right humerus.
                """);

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(StructuredFinding::sourceSection)
                .containsExactly(FindingSourceSection.FINDINGS, FindingSourceSection.IMPRESSION);
        assertThat(findings).extracting(StructuredFinding::sourceText)
                .containsExactly(
                        "There is a comminuted fracture involving the proximal right humerus.",
                        "Comminuted fracture of the proximal right humerus.");
    }

    @Test
    @DisplayName("returns no findings for unsupported sentences")
    void ignoresUnsupportedSentence() {
        assertThat(service.extract("Cardiomediastinal silhouette is stable.")).isEmpty();
    }

    @Test
    @DisplayName("extracts simple millimetre measurements when deterministic")
    void extractsMeasurement() {
        StructuredFinding finding = only("Possible 8 mm left upper lobe pulmonary nodule.");

        assertFinding(
                finding,
                FindingType.NODULE,
                AnatomicalStructure.LUNG,
                AnatomicalSide.LEFT,
                AnatomicalRegion.UPPER,
                FindingStatus.PRESENT,
                FindingCertainty.POSSIBLE);
        assertThat(finding.measurement()).isEqualTo(8.0d);
        assertThat(finding.unit()).isEqualTo("mm");
    }

    @Test
    @DisplayName("extracts from ReportReview draft JSON without persistence")
    void extractsFromReportReviewDraftJson() {
        ReportReview review = ReportReview.builder()
                .status("DRAFT")
                .draftContent("""
                        {
                          "findings": [
                            { "description": "Comminuted fracture involving the proximal right humerus." }
                          ],
                          "impression": "Comminuted fracture of the proximal right humerus."
                        }
                        """)
                .finalContent("No acute fracture.")
                .build();

        List<StructuredFinding> findings = service.extract(review);

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(StructuredFinding::sourceSection)
                .containsExactly(FindingSourceSection.FINDINGS, FindingSourceSection.IMPRESSION);
        assertThat(findings).allSatisfy(finding -> assertThat(finding.findingType()).isEqualTo(FindingType.FRACTURE));
    }

    private StructuredFinding only(String text) {
        List<StructuredFinding> findings = extractor.extract(FindingSourceSection.FINDINGS, text);
        assertThat(findings).hasSize(1);
        return findings.getFirst();
    }

    private void assertFinding(
            StructuredFinding finding,
            FindingType findingType,
            AnatomicalStructure anatomy,
            AnatomicalSide side,
            AnatomicalRegion region,
            FindingStatus status,
            FindingCertainty certainty
    ) {
        assertThat(finding.findingType()).isEqualTo(findingType);
        assertThat(finding.anatomy()).isEqualTo(anatomy);
        assertThat(finding.side()).isEqualTo(side);
        assertThat(finding.region()).isEqualTo(region);
        assertThat(finding.status()).isEqualTo(status);
        assertThat(finding.certainty()).isEqualTo(certainty);
    }
}
