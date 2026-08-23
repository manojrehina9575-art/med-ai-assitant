package com.medai.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogWriter writer;

    /**
     * Records an audited action.
     *
     * <p>Hands the entry to {@link AuditLogWriter} and returns. This used to be an {@code @Async}
     * method that opened its own transaction, bound the tenant with a {@code set_config} round
     * trip, and inserted a single row — three round trips and a pooled connection for every
     * controller call in the application, reads included. Under load the audit executor saturated
     * and {@code CallerRunsPolicy} handed all of that back to the request thread, so the cost
     * landed on clinicians exactly when the system was busiest.
     *
     * <p>The tenant is still passed in rather than read from {@code TenantContext}: the write
     * happens on the flusher thread, where the caller's ThreadLocal does not exist.
     */
    public void record(UUID tenantId, UUID userId, String action, String entityType, UUID entityId,
                       Map<String, Object> details, String ipAddress, String userAgent) {
        if (tenantId == null) {
            return;
        }

        writer.submit(new AuditLogWriter.Entry(
                tenantId, userId, action, entityType, entityId,
                details, ipAddress, userAgent,
                // Stamped here, not at insert time: the entry records when the action happened,
                // not when the buffer happened to be flushed.
                Instant.now()));
    }
}
