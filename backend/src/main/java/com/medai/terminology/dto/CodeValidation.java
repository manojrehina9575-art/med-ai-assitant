package com.medai.terminology.dto;

import java.util.List;

/**
 * The verdict on one code the model produced.
 *
 * @param code      the code as written by the model
 * @param system    the code system URI it was checked against
 * @param status    VALID, UNKNOWN or MALFORMED
 * @param display   the official display name, when the code resolved
 * @param note      why it did not resolve, when it did not
 * @param suggestions plausible alternatives, when there are any
 */
public record CodeValidation(
        String code,
        String system,
        Status status,
        String display,
        String note,
        List<String> suggestions
) {

    public enum Status {
        /** Resolved against the code system. */
        VALID,
        /** Well-formed, but not something this validator can confirm exists. */
        UNKNOWN,
        /** Not a syntactically valid code in this system at all. */
        MALFORMED
    }

    public boolean isValid() {
        return status == Status.VALID;
    }

    public static CodeValidation valid(String code, String system, String display) {
        return new CodeValidation(code, system, Status.VALID, display, null, List.of());
    }

    public static CodeValidation unknown(String code, String system, String note, List<String> suggestions) {
        return new CodeValidation(code, system, Status.UNKNOWN, null, note, suggestions);
    }

    public static CodeValidation malformed(String code, String system, String note) {
        return new CodeValidation(code, system, Status.MALFORMED, null, note, List.of());
    }
}
