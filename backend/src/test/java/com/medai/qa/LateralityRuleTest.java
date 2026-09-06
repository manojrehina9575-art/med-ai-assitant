package com.medai.qa;

import com.medai.qa.model.QaIssue;
import com.medai.qa.rules.LateralityRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LateralityRuleTest {

    private final LateralityRule rule = new LateralityRule();

    @Test
    @DisplayName("flags explicit right-left conflict for the same humerus fracture")
    void flagsExplicitOppositeSidesForSameFinding() {
        List<QaIssue> issues = rule.evaluate(
                "Comminuted fracture of the proximal right humerus.",
                "Comminuted fracture of the proximal left humerus.");

        assertThat(issues).hasSize(1);
        assertThat(issues.getFirst().message()).doesNotContain("safe", "correct", "verified");
    }

    @Test
    @DisplayName("flags conflicts case-insensitively")
    void flagsCaseInsensitiveOppositeSides() {
        assertThat(rule.evaluate(
                "RIGHT femoral neck fracture.",
                "left femoral neck fracture.")).hasSize(1);
    }

    @Test
    @DisplayName("flags common Rt and Lt abbreviations")
    void flagsRtLtAbbreviations() {
        assertThat(rule.evaluate(
                "Rt shoulder fracture.",
                "Lt shoulder fracture.")).hasSize(1);
    }

    @Test
    @DisplayName("flags standalone R and L clinical shorthand")
    void flagsStandaloneSingleLetterShorthand() {
        assertThat(rule.evaluate(
                "R knee effusion.",
                "L knee effusion.")).hasSize(1);
    }

    @Test
    @DisplayName("does not flag same-side statements")
    void ignoresSameSideStatements() {
        assertThat(rule.evaluate(
                "Right proximal humerus fracture.",
                "Right humeral fracture.")).isEmpty();
    }

    @Test
    @DisplayName("does not flag different structures")
    void ignoresDifferentStructures() {
        assertThat(rule.evaluate(
                "Right renal cyst.",
                "Small left pleural effusion.")).isEmpty();
    }

    @Test
    @DisplayName("does not treat bilateral wording as a conflict")
    void ignoresBilateralWording() {
        assertThat(rule.evaluate(
                "Bilateral pleural effusions.",
                "Bilateral pleural effusions.")).isEmpty();
    }

    @Test
    @DisplayName("does not infer conflict when only one section has laterality")
    void ignoresReportsWithoutLaterality() {
        assertThat(rule.evaluate(
                "Proximal humerus fracture.",
                "Humeral fracture.")).isEmpty();
    }

    @Test
    @DisplayName("does not match accidental words containing r or l")
    void ignoresAccidentalLetterMatchesInsideWords() {
        assertThat(rule.evaluate(
                "Large renal lesion.",
                "Renal lesion.")).isEmpty();
    }
}
