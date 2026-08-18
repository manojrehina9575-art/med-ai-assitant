package com.medai.analysis.util;

/**
 * Thrown when a medical file cannot be turned into something the model can genuinely read.
 *
 * <p>This is a <em>terminal</em> failure: retrying will not help, because the input itself is
 * the problem. The analysis must be marked FAILED rather than answered from the filename,
 * which would produce clinical content the model never derived from the patient's file.
 */
public class UnreadableInputException extends RuntimeException {

    public UnreadableInputException(String message) {
        super(message);
    }

    public UnreadableInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
