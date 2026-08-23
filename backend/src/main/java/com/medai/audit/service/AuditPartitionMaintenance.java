package com.medai.audit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Keeps monthly {@code audit_logs} partitions created ahead of the clock.
 *
 * <p>V17 partitions the audit trail by month and leaves a DEFAULT partition so that a missing
 * month can never make an audit write fail. That default is a safety net, not a destination: rows
 * landing in it lose the pruning and bounded-index-size benefits partitioning exists for, and
 * carving the month out afterwards means briefly detaching the default. Running this daily, with
 * several months of headroom, keeps it empty.
 *
 * <p>Partition creation is DDL and the application role has none, so the work happens inside
 * {@code ensure_audit_partition()}, a SECURITY DEFINER function the application is granted EXECUTE
 * on and nothing else.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditPartitionMaintenance {

    private final JdbcTemplate jdbcTemplate;

    /**
     * How far ahead partitions are kept. Three months means a maintenance outage has to last a
     * full quarter before anything reaches the default partition.
     */
    @Value("${app.audit.partition-months-ahead:3}")
    private int monthsAhead;

    /** Runs at startup so a fresh deployment is never a month behind on its first day. */
    @EventListener(ApplicationReadyEvent.class)
    public void ensurePartitionsOnStartup() {
        ensurePartitions();
    }

    @Scheduled(cron = "${app.audit.partition-cron:0 30 1 * * *}")
    public void ensurePartitionsDaily() {
        ensurePartitions();
    }

    private void ensurePartitions() {
        LocalDate month = LocalDate.now().withDayOfMonth(1);

        for (int i = 0; i <= monthsAhead; i++) {
            LocalDate target = month.plusMonths(i);
            try {
                String partition = jdbcTemplate.queryForObject(
                        "SELECT ensure_audit_partition(?)", String.class, java.sql.Date.valueOf(target));
                log.debug("Audit partition ready: {}", partition);
            } catch (Exception e) {
                // A failure here is not urgent — the default partition catches the rows — but it
                // does mean the trail is drifting into an unpartitioned heap, so it is logged at
                // error rather than swallowed.
                log.error("Could not ensure the audit partition for {}: {}", target, e.getMessage(), e);
            }
        }
    }
}
