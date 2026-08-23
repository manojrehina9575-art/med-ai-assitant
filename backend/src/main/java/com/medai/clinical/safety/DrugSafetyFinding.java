package com.medai.clinical.safety;

import java.util.List;

/**
 * One thing the safety checker found wrong with a proposed prescription.
 *
 * @param severity  how the prescription is allowed to proceed, if at all
 * @param code      stable machine-readable identifier, e.g. {@code ALLERGY_CROSS_REACTIVE}
 * @param message   what a clinician needs to read
 * @param drugs     the normalised ingredient names this finding is about
 */
public record DrugSafetyFinding(
        Severity severity,
        String code,
        String message,
        List<String> drugs
) {

    public enum Severity {
        /**
         * A documented allergy to the drug, or to a class it cross-reacts with. The prescription
         * is refused outright; only an explicit, attributed override writes it.
         */
        CONTRAINDICATED,

        /**
         * A dangerous interaction, a duplicate within one class, or a dose above the accepted
         * daily maximum. Refused unless explicitly acknowledged.
         */
        MAJOR,

        /**
         * Worth reading before signing, but not a reason to block: renal-adjustment reminders,
         * moderate interactions.
         */
        MODERATE
    }

    public static DrugSafetyFinding contraindicated(String code, String message, String... drugs) {
        return new DrugSafetyFinding(Severity.CONTRAINDICATED, code, message, List.of(drugs));
    }

    public static DrugSafetyFinding major(String code, String message, String... drugs) {
        return new DrugSafetyFinding(Severity.MAJOR, code, message, List.of(drugs));
    }

    public static DrugSafetyFinding moderate(String code, String message, String... drugs) {
        return new DrugSafetyFinding(Severity.MODERATE, code, message, List.of(drugs));
    }

    public boolean blocking() {
        return severity == Severity.CONTRAINDICATED || severity == Severity.MAJOR;
    }
}
