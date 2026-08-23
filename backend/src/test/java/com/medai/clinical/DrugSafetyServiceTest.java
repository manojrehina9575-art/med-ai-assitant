package com.medai.clinical;

import com.medai.clinical.safety.DrugKnowledgeBase;
import com.medai.clinical.safety.DrugSafetyFinding;
import com.medai.clinical.safety.DrugSafetyService;
import com.medai.clinical.safety.DrugSafetyService.ProposedMedication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The cases the old substring check got wrong, and the ones a prescription checker has to get
 * right. Plain unit tests — the knowledge base has no dependencies, so there is no reason to pay
 * for a Spring context.
 */
class DrugSafetyServiceTest {

    private final DrugSafetyService service = new DrugSafetyService(new DrugKnowledgeBase());

    private static ProposedMedication med(String name, String dose, String freq) {
        return new ProposedMedication(name, dose, freq, "7 days");
    }

    private static boolean hasCode(DrugSafetyService.Assessment a, String code) {
        return a.findings().stream().anyMatch(f -> f.code().equals(code));
    }

    @Nested
    @DisplayName("Allergies")
    class Allergies {

        /**
         * The case that motivated all of this. {@code "amoxicillin".contains("penicillin")} is
         * false, so the old check passed a beta-lactam to a patient documented as allergic to
         * penicillin and wrote the prescription.
         */
        @Test
        @DisplayName("Penicillin allergy blocks amoxicillin")
        void penicillinAllergyBlocksAmoxicillin() {
            var assessment = service.assess(
                    List.of(med("Amoxicillin", "500mg", "TID")),
                    List.of("Penicillin"));

            assertTrue(assessment.requiresAcknowledgement());
            assertTrue(hasCode(assessment, "ALLERGY_CLASS"));
            assertEquals(DrugSafetyFinding.Severity.CONTRAINDICATED,
                    assessment.findings().get(0).severity());
        }

        @Test
        @DisplayName("Plural allergy spelling still matches — allergy lists say 'penicillins'")
        void pluralAllergySpellingMatches() {
            var assessment = service.assess(
                    List.of(med("Piperacillin-Tazobactam", "4.5g", "Q8H")),
                    List.of("penicillins"));

            assertTrue(assessment.requiresAcknowledgement());
        }

        /**
         * Brand on one side, generic on the other. The old check compared the two strings
         * directly and found nothing in common.
         */
        @Test
        @DisplayName("Bactrim allergy blocks sulfamethoxazole-trimethoprim")
        void brandAllergyBlocksGeneric() {
            var assessment = service.assess(
                    List.of(med("Sulfamethoxazole-Trimethoprim", "800mg", "BID")),
                    List.of("Bactrim"));

            assertTrue(assessment.requiresAcknowledgement());
        }

        @Test
        @DisplayName("Two drugs sharing a class: ibuprofen allergy blocks naproxen")
        void sameClassIsBlocked() {
            var assessment = service.assess(
                    List.of(med("Naproxen", "500mg", "BID")),
                    List.of("Ibuprofen"));

            assertTrue(hasCode(assessment, "ALLERGY_SAME_CLASS"));
        }

        /**
         * Cross-reactivity is real but low, so it warns rather than refusing outright — the
         * decision belongs to the prescriber, who is the only one who knows what the documented
         * reaction actually was.
         */
        @Test
        @DisplayName("Penicillin allergy warns, but does not forbid, a cephalosporin")
        void crossReactivityWarnsRatherThanForbids() {
            var assessment = service.assess(
                    List.of(med("Ceftriaxone", "1g", "OD")),
                    List.of("Penicillin"));

            assertTrue(hasCode(assessment, "ALLERGY_CROSS_REACTIVE"));
            assertTrue(assessment.findings().stream()
                    .filter(f -> f.code().equals("ALLERGY_CROSS_REACTIVE"))
                    .allMatch(f -> f.severity() == DrugSafetyFinding.Severity.MAJOR));
        }

        /**
         * The old check's other failure direction: {@code "acetaminophen".contains("ace")} is
         * true, so an "ACE inhibitor" allergy flagged paracetamol.
         */
        @Test
        @DisplayName("Unrelated drugs do not match on a shared substring")
        void noFalsePositiveOnSubstring() {
            var assessment = service.assess(
                    List.of(med("Acetaminophen", "500mg", "QID")),
                    List.of("ACE inhibitors"));

            assertFalse(hasCode(assessment, "ALLERGY_DIRECT"));
            assertFalse(hasCode(assessment, "ALLERGY_CLASS"));
            assertFalse(hasCode(assessment, "ALLERGY_SAME_CLASS"));
        }

        @Test
        @DisplayName("No allergies documented, no allergy findings")
        void noAllergiesIsClear() {
            var assessment = service.assess(
                    List.of(med("Amoxicillin", "500mg", "TID")), List.of());

            assertFalse(assessment.requiresAcknowledgement());
        }
    }

    @Nested
    @DisplayName("Interactions")
    class Interactions {

        @Test
        @DisplayName("Warfarin plus an NSAID is a major bleeding risk")
        void warfarinPlusNsaid() {
            var assessment = service.assess(
                    List.of(med("Warfarin", "5mg", "OD"), med("Ibuprofen", "400mg", "TID")),
                    List.of());

            assertTrue(hasCode(assessment, "INTERACTION"));
            assertTrue(assessment.requiresAcknowledgement());
        }

        @Test
        @DisplayName("Interactions resolve through brand names too")
        void interactionThroughBrandNames() {
            var assessment = service.assess(
                    List.of(med("Coumadin", "5mg", "OD"), med("Advil", "400mg", "TID")),
                    List.of());

            assertTrue(hasCode(assessment, "INTERACTION"));
        }

        @Test
        @DisplayName("Interactions resolve through drug class, not just exact ingredient")
        void interactionThroughClass() {
            // simvastatin + clarithromycin is matched by the simvastatin/macrolide rule.
            var assessment = service.assess(
                    List.of(med("Simvastatin", "40mg", "OD"), med("Clarithromycin", "500mg", "BID")),
                    List.of());

            assertTrue(hasCode(assessment, "INTERACTION"));
        }

        @Test
        @DisplayName("An opioid with a benzodiazepine is flagged")
        void opioidPlusBenzodiazepine() {
            var assessment = service.assess(
                    List.of(med("Oxycodone", "5mg", "Q6H"), med("Lorazepam", "1mg", "BID")),
                    List.of());

            assertTrue(hasCode(assessment, "INTERACTION"));
        }

        @Test
        @DisplayName("Unrelated drugs produce no interaction")
        void noSpuriousInteraction() {
            var assessment = service.assess(
                    List.of(med("Amoxicillin", "500mg", "TID"), med("Acetaminophen", "500mg", "QID")),
                    List.of());

            assertFalse(hasCode(assessment, "INTERACTION"));
        }
    }

    @Nested
    @DisplayName("Duplicates and doses")
    class DuplicatesAndDoses {

        @Test
        @DisplayName("The same ingredient under two names is caught")
        void duplicateAcrossBrandAndGeneric() {
            var assessment = service.assess(
                    List.of(med("Tylenol", "500mg", "QID"), med("Paracetamol", "500mg", "QID")),
                    List.of());

            assertTrue(hasCode(assessment, "DUPLICATE_INGREDIENT"));
        }

        @Test
        @DisplayName("Two drugs from one class is flagged as duplicate therapy")
        void duplicateClass() {
            var assessment = service.assess(
                    List.of(med("Lisinopril", "10mg", "OD"), med("Ramipril", "5mg", "OD")),
                    List.of());

            assertTrue(hasCode(assessment, "DUPLICATE_CLASS"));
        }

        /**
         * 1g four times a day is 4g — at the ceiling, not over it. Two such lines is 8g, which is
         * hepatotoxic and is the classic avoidable paracetamol overdose.
         */
        @Test
        @DisplayName("Daily totals are summed across lines, not judged per line")
        void doseIsSummedAcrossLines() {
            var assessment = service.assess(
                    List.of(med("Paracetamol", "1g", "QID"), med("Tylenol", "1g", "QID")),
                    List.of());

            assertTrue(hasCode(assessment, "DOSE_EXCEEDS_MAXIMUM"));
        }

        @Test
        @DisplayName("A dose at the ceiling is not flagged")
        void doseAtCeilingIsFine() {
            var assessment = service.assess(
                    List.of(med("Paracetamol", "1g", "QID")), List.of());

            assertFalse(hasCode(assessment, "DOSE_EXCEEDS_MAXIMUM"));
        }

        @Test
        @DisplayName("Ibuprofen 800mg QID exceeds the 3200mg/day maximum")
        void ibuprofenOverMaximum() {
            var assessment = service.assess(
                    List.of(med("Ibuprofen", "800mg", "Q4H")), List.of());

            assertTrue(hasCode(assessment, "DOSE_EXCEEDS_MAXIMUM"));
        }

        /**
         * "as needed" has no defined daily total, so there is nothing to compare against. Guessing
         * one would produce a confident number that is not true.
         */
        @Test
        @DisplayName("PRN dosing yields no dose finding rather than a guessed one")
        void prnIsNotGuessed() {
            var assessment = service.assess(
                    List.of(med("Paracetamol", "1g", "PRN")), List.of());

            assertFalse(hasCode(assessment, "DOSE_EXCEEDS_MAXIMUM"));
        }

        @Test
        @DisplayName("Frequency matching prefers the most specific key")
        void frequencyMatchingIsSpecific() {
            // "give twice daily" must resolve to 2/day, not to 1 via the shorter "daily" key.
            var twiceDaily = service.assess(
                    List.of(med("Simvastatin", "30mg", "give twice daily")), List.of());
            assertTrue(hasCode(twiceDaily, "DOSE_EXCEEDS_MAXIMUM"),
                    "60mg/day is over the 40mg simvastatin maximum");

            var onceDaily = service.assess(
                    List.of(med("Simvastatin", "30mg", "give once daily")), List.of());
            assertFalse(hasCode(onceDaily, "DOSE_EXCEEDS_MAXIMUM"));
        }
    }

    @Nested
    @DisplayName("Honesty about coverage")
    class Coverage {

        /**
         * Silence about a drug the checker does not know reads exactly like a clean bill of
         * health, which is the most dangerous output this class could produce.
         */
        @Test
        @DisplayName("An unknown drug is reported as unchecked, not passed silently")
        void unknownDrugIsReported() {
            var assessment = service.assess(
                    List.of(med("Zyxomycin", "100mg", "BID")), List.of("Penicillin"));

            assertTrue(hasCode(assessment, "UNRECOGNISED_MEDICATION"));
            assertEquals(List.of("Zyxomycin"), assessment.unrecognised());
        }

        @Test
        @DisplayName("Renally cleared drugs prompt for an eGFR the system does not hold")
        void renalAdjustmentIsFlagged() {
            var assessment = service.assess(
                    List.of(med("Metformin", "500mg", "BID")), List.of());

            assertTrue(hasCode(assessment, "RENAL_ADJUSTMENT_UNVERIFIED"));
            // Advisory only — it must not block an otherwise ordinary prescription.
            assertFalse(assessment.requiresAcknowledgement());
        }

        @Test
        @DisplayName("Dose and form noise does not defeat normalisation")
        void normalisationStripsNoise() {
            var assessment = service.assess(
                    List.of(med("Amoxil 500mg capsules", "500mg", "TID")),
                    List.of("Penicillin"));

            assertTrue(assessment.requiresAcknowledgement());
            assertEquals(List.of("amoxicillin"), assessment.normalisedIngredients());
        }

        @Test
        @DisplayName("Findings are ordered most severe first")
        void findingsAreOrdered() {
            var assessment = service.assess(
                    List.of(med("Amoxicillin", "500mg", "TID"), med("Metformin", "500mg", "BID")),
                    List.of("Penicillin"));

            assertEquals(DrugSafetyFinding.Severity.CONTRAINDICATED,
                    assessment.findings().get(0).severity());
        }

        @Test
        @DisplayName("Blocking codes are exactly what a prescriber must acknowledge")
        void blockingCodesAreActionable() {
            var assessment = service.assess(
                    List.of(med("Amoxicillin", "500mg", "TID")), List.of("Penicillin"));

            assertTrue(assessment.blockingCodes().contains("ALLERGY_CLASS"));
            assertFalse(assessment.blockingCodes().contains("RENAL_ADJUSTMENT_UNVERIFIED"));
        }
    }
}
