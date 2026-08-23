package com.medai.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.tenant.TenantContext;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Buffers audit entries and writes them in batches.
 *
 * <p>Each entry previously cost its own transaction, its own {@code set_config} to bind the tenant
 * for row-level security, and its own single-row insert — three round trips and a pooled
 * connection per audited call, on a table every controller call writes to. It was already
 * {@code @Async}, so this was off the request thread most of the time; but the audit executor
 * saturates under {@code CallerRunsPolicy}, which means the work fell back onto the request thread
 * exactly when the system was busiest and could least afford it.
 *
 * <p>Batching removes the per-entry cost. Entries land in a bounded queue and a scheduled flush
 * drains them, grouped by tenant, as one multi-row insert per group. A busy second producing two
 * hundred audit rows now costs one bind and one statement per tenant rather than six hundred round
 * trips.
 *
 * <p>Two rules shape everything here, and both come from the same place — an audit trail with gaps
 * is worse than no audit trail, because it is trusted:
 * <ul>
 *   <li>A full queue <em>blocks</em> the caller briefly rather than dropping the entry. Losing an
 *       audit record to a load spike is the failure an investigation cannot recover from.</li>
 *   <li>Anything still buffered at shutdown is flushed synchronously.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogWriter {

    private static final String INSERT_SQL = """
            INSERT INTO audit_logs
                (tenant_id, user_id, action, entity_type, entity_id, details, ip_address, user_agent, created_at)
            VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.audit.queue-capacity:10000}")
    private int queueCapacity;

    /**
     * How long a caller waits for room before writing inline. Long enough to ride out a flush,
     * short enough that a wedged writer cannot stall clinical requests indefinitely.
     */
    @Value("${app.audit.enqueue-timeout-ms:2000}")
    private long enqueueTimeoutMs;

    @Value("${app.audit.max-batch-size:500}")
    private int maxBatchSize;

    private volatile BlockingQueue<Entry> queue;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    /** One buffered audit row. Immutable, so it can cross the thread boundary safely. */
    public record Entry(
            UUID tenantId,
            UUID userId,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> details,
            String ipAddress,
            String userAgent,
            Instant createdAt
    ) {
    }

    private BlockingQueue<Entry> queue() {
        // Built lazily: @Value fields are not populated during construction.
        BlockingQueue<Entry> existing = queue;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (queue == null) {
                queue = new ArrayBlockingQueue<>(queueCapacity > 0 ? queueCapacity : 10_000);
            }
            return queue;
        }
    }

    /** Buffers one entry. Returns as soon as it is queued, which is the common case by far. */
    public void submit(Entry entry) {
        if (shuttingDown.get()) {
            // The flusher may already have run for the last time; write it now rather than queue
            // it somewhere nothing will drain.
            writeBatch(List.of(entry));
            return;
        }

        try {
            if (!queue().offer(entry, enqueueTimeoutMs, TimeUnit.MILLISECONDS)) {
                log.warn("Audit queue full after {}ms; writing this entry inline. The writer is not "
                         + "keeping up — check database latency.", enqueueTimeoutMs);
                writeBatch(List.of(entry));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // An interrupt must not swallow the record.
            writeBatch(List.of(entry));
        }
    }

    /**
     * Drains and writes whatever has accumulated.
     *
     * <p>On an interval rather than on a size threshold alone: a quiet system would otherwise hold
     * a handful of entries indefinitely, and an audit trail lagging by an unbounded amount is not
     * one you can read during an incident.
     */
    @Scheduled(fixedDelayString = "${app.audit.flush-interval-ms:500}")
    public void flush() {
        // A backlog larger than one batch is drained in this pass rather than trickling out over
        // several ticks.
        while (true) {
            List<Entry> batch = new ArrayList<>(maxBatchSize);
            queue().drainTo(batch, maxBatchSize);
            if (batch.isEmpty()) {
                return;
            }
            writeBatch(batch);
        }
    }

    /**
     * Writes a batch, one statement per tenant.
     *
     * <p>Grouped by tenant because row-level security is enforced per connection: the insert must
     * run with {@code app.current_tenant} set to the row's tenant, and one connection can only be
     * bound to one at a time. Maintenance mode would let the whole batch go in a single statement,
     * but audit writes are exactly the wrong place to lift tenant scoping.
     */
    private void writeBatch(List<Entry> batch) {
        Map<UUID, List<Entry>> byTenant = new LinkedHashMap<>();
        for (Entry entry : batch) {
            byTenant.computeIfAbsent(entry.tenantId(), key -> new ArrayList<>()).add(entry);
        }

        byTenant.forEach((tenantId, entries) -> {
            try {
                writeTenantBatch(tenantId, entries);
            } catch (Exception e) {
                // Never fail a clinical operation because its audit row could not be written, but
                // do make the gap loud — a silent audit gap is what an investigation cannot
                // recover from. The entries go to the log so they remain recoverable.
                log.error("AUDIT WRITE FAILED for tenant {}: {} entr(ies) did not reach the "
                          + "database. Entries: {}", tenantId, entries.size(), entries, e);
            }
        });
    }

    /**
     * One tenant binding, one batched statement, no transaction.
     *
     * <p>Binding goes through {@link TenantContext} rather than an explicit {@code set_config},
     * because {@code TenantAwareDataSource} stamps {@code app.current_tenant} on every connection
     * as it leaves the pool. An explicit {@code set_config} on a previous {@code JdbcTemplate}
     * call would be on a connection already returned by the time the insert borrows its own — and
     * an unstamped connection fails the policy's WITH CHECK, so every audit row would be rejected.
     *
     * <p>No {@code @Transactional} either. It would have been inert here: this is invoked from a
     * private method of the same bean, and a self-invocation never passes through the proxy. A
     * single batched insert is one statement and atomic on its own, so the annotation would have
     * bought nothing even had it applied.
     */
    private void writeTenantBatch(UUID tenantId, List<Entry> entries) {
        UUID previous = TenantContext.getCurrentTenantId();
        TenantContext.setCurrentTenantId(tenantId);
        try {
            insertBatch(entries);
        } finally {
            // The flusher thread is pooled and long-lived; leaving a tenant bound on it would
            // stamp the next tenant's batch with this one's id.
            if (previous != null) {
                TenantContext.setCurrentTenantId(previous);
            } else {
                TenantContext.clear();
            }
        }
    }

    private void insertBatch(List<Entry> entries) {
        jdbcTemplate.batchUpdate(INSERT_SQL, entries, entries.size(), (ps, entry) -> {
            ps.setObject(1, entry.tenantId());
            ps.setObject(2, entry.userId());
            ps.setString(3, entry.action());
            ps.setString(4, entry.entityType());
            ps.setObject(5, entry.entityId());
            ps.setString(6, toJson(entry.details()));
            ps.setString(7, entry.ipAddress());
            ps.setString(8, entry.userAgent());
            ps.setTimestamp(9, Timestamp.from(entry.createdAt()));
        });
    }

    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            // The detail map is context, not the record itself. Losing it must not lose the entry.
            log.warn("Could not serialise audit details, storing the reason instead: {}", e.getMessage());
            return "{\"serialisationError\":true}";
        }
    }

    /** Drains everything still buffered. Without this, a restart loses the last half-second. */
    @PreDestroy
    public void drainOnShutdown() {
        shuttingDown.set(true);

        List<Entry> remaining = new ArrayList<>();
        queue().drainTo(remaining);

        if (!remaining.isEmpty()) {
            log.info("Flushing {} buffered audit entr(ies) before shutdown", remaining.size());
            writeBatch(remaining);
        }
    }

    /** Buffered depth, for tests and for the observability page. */
    public int pendingCount() {
        return queue().size();
    }
}
