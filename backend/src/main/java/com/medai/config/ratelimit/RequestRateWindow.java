package com.medai.config.ratelimit;

import java.util.UUID;

/**
 * Counts a tenant's requests inside the current one-minute window.
 *
 * <p>Two implementations, chosen by whether Redis is configured. The distinction matters more than
 * it looks: the in-memory window is per-instance, so with the Kubernetes HPA scaling the backend
 * to ten replicas the effective ceiling was ten times the configured one — and this window is the
 * guard against a runaway loop burning the provider's quota, which is precisely the failure that
 * arrives all at once and on every replica.
 */
public interface RequestRateWindow extends AutoCloseable {

    /**
     * Increments the tenant's counter for the current minute and returns the new value.
     *
     * @return requests seen in this window, including this one
     */
    int incrementAndCount(UUID tenantId);

    /** Reported at startup, so which one is live is never a guess. */
    String describe();

    /** Releases any connection held. The in-memory window holds none. */
    @Override
    default void close() {
        // Nothing to release.
    }
}
