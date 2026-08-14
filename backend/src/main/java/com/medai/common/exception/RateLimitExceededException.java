package com.medai.common.exception;

/**
 * Thrown when a tenant exceeds the configured AI rate limit.
 * Maps to HTTP 429 Too Many Requests.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
