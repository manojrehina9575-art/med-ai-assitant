package com.medai.chat.enums;

public enum SafetyFlag {
    RED_FLAG_EMERGENCY,
    ALLERGY_CONFLICT_DETECTED,
    POTENTIAL_INJECTION,
    OUT_OF_DOMAIN,
    OFF_LABEL_USE,
    UNCERTAINTY_DISCLAIMER,

    /**
     * The model call failed and this turn contains no generated content.
     *
     * <p>A failed turn used to be stored as an ordinary assistant message carrying the provider's
     * exception text, which made it indistinguishable from a real answer both to the reader and to
     * every metric over the table.
     */
    GENERATION_FAILED
}
