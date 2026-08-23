package com.medai.compliance.retention.service;

import com.medai.compliance.retention.entity.DataRetentionPolicy;
import com.medai.compliance.retention.entity.RetentionPurgeLog;
import com.medai.compliance.retention.repository.RetentionPolicyRepository;
import com.medai.compliance.retention.repository.RetentionPurgeLogRepository;
import com.medai.common.exception.BadRequestException;
import com.medai.tenant.TenantContext;
import com.medai.tenant.TenantSession;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataRetentionService {

    /**
     * HIPAA 164.316(b)(2)(i): documentation, which includes the audit trail, must be retained for
     * six years. A tenant may keep audit logs for longer than this; it may not choose less.
     *
     * <p>Enforced here so the caller gets a clear rejection, and again as a CHECK constraint and
     * inside {@code purge_audit_logs()} (V16) so that neither a direct database edit nor a future
     * code path can go under it.
     */
    public static final int MIN_AUDIT_RETENTION_DAYS = 2190;

    private final RetentionPolicyRepository policyRepository;
    private final RetentionPurgeLogRepository logRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TenantSession tenantSession;

    @Data
    @Builder
    public static class PurgeSummary {
        private UUID tenantId;
        private int auditLogsPurged;
        private int chatMessagesPurged;
        private Instant executedAt;
    }

    @Transactional
    public DataRetentionPolicy getOrCreatePolicyForTenant() {
        UUID tenantId = TenantContext.requireTenantId();
        return policyRepository.findByTenantId(tenantId)
                .orElseGet(() -> policyRepository.save(DataRetentionPolicy.builder()
                        .tenantId(tenantId)
                        .auditLogRetentionDays(MIN_AUDIT_RETENTION_DAYS)
                        .analysisRetentionDays(730)
                        .chatSessionRetentionDays(180)
                        .softDeletePurgeDays(30)
                        .autoPurgeEnabled(false)
                        .build()));
    }

    @Transactional
    public DataRetentionPolicy updatePolicy(DataRetentionPolicy policyUpdate) {
        if (policyUpdate.getAuditLogRetentionDays() < MIN_AUDIT_RETENTION_DAYS) {
            throw new BadRequestException(
                    "Audit log retention must be at least " + MIN_AUDIT_RETENTION_DAYS
                    + " days (6 years) under HIPAA 164.316(b)(2)(i). Requested: "
                    + policyUpdate.getAuditLogRetentionDays() + " days.");
        }

        DataRetentionPolicy policy = getOrCreatePolicyForTenant();
        policy.setAuditLogRetentionDays(policyUpdate.getAuditLogRetentionDays());
        policy.setAnalysisRetentionDays(policyUpdate.getAnalysisRetentionDays());
        policy.setChatSessionRetentionDays(policyUpdate.getChatSessionRetentionDays());
        policy.setSoftDeletePurgeDays(policyUpdate.getSoftDeletePurgeDays());
        policy.setAutoPurgeEnabled(policyUpdate.isAutoPurgeEnabled());
        return policyRepository.save(policy);
    }

    @Transactional
    public PurgeSummary executeManualPurge() {
        UUID tenantId = TenantContext.requireTenantId();
        DataRetentionPolicy policy = getOrCreatePolicyForTenant();
        return executePurgeForTenant(policy);
    }

    @Transactional(readOnly = true)
    public List<RetentionPurgeLog> getPurgeLogs() {
        UUID tenantId = TenantContext.requireTenantId();
        return logRepository.findByTenantIdOrderByExecutedAtDesc(tenantId);
    }

    /**
     * Finds every tenant that has opted into automatic purging.
     *
     * <p>Deliberately its own transaction: the scan spans all tenants, which row-level security
     * forbids by default, so it opts into maintenance access for this transaction only. Before
     * V16 repaired the policies on {@code data_retention_policies} this query silently returned
     * nothing and the scheduled purge did no work for anyone.
     */
    @Transactional(readOnly = true)
    public List<DataRetentionPolicy> findTenantsWithAutoPurge() {
        tenantSession.beginMaintenance();
        return policyRepository.findByAutoPurgeEnabledTrue();
    }

    /**
     * Executes daily automated purge for all tenants with autoPurgeEnabled=true.
     */
    @Scheduled(cron = "${app.retention.cron:0 0 2 * * *}") // Runs daily at 2:00 AM
    public void runScheduledRetentionPurge() {
        log.info("Running scheduled data retention purge job...");
        List<DataRetentionPolicy> enabledPolicies = findTenantsWithAutoPurge();
        for (DataRetentionPolicy policy : enabledPolicies) {
            try {
                TenantContext.setCurrentTenantId(policy.getTenantId());
                executePurgeForTenant(policy);
            } catch (Exception e) {
                log.error("Failed scheduled purge for tenant {}: {}", policy.getTenantId(), e.getMessage(), e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    private PurgeSummary executePurgeForTenant(DataRetentionPolicy policy) {
        UUID tenantId = policy.getTenantId();
        Instant now = Instant.now();

        // 1. Archive and purge audit logs older than the statutory floor.
        //
        // This goes through purge_audit_logs() rather than a DELETE because the application role
        // no longer has DELETE on audit_logs (V16) — deliberately, since an audit trail the
        // application can erase is not an audit trail. The function archives every row before
        // removing it and clamps the cutoff to six years regardless of what is passed in.
        //
        // The DELETE this replaces named a `timestamp` column that does not exist; the column is
        // `created_at`. Every audit purge since V15 therefore threw and was swallowed into a
        // FAILED row in retention_purge_logs.
        int effectiveAuditDays = Math.max(policy.getAuditLogRetentionDays(), MIN_AUDIT_RETENTION_DAYS);
        Instant auditCutoff = now.minus(effectiveAuditDays, ChronoUnit.DAYS);
        int auditCount = 0;
        try {
            Integer purged = jdbcTemplate.queryForObject(
                    "SELECT purge_audit_logs(?, ?)", Integer.class, tenantId, auditCutoff);
            auditCount = purged != null ? purged : 0;
            logRepository.save(RetentionPurgeLog.builder()
                    .tenantId(tenantId)
                    .entityType("AUDIT_LOGS")
                    .recordsPurgedCount(auditCount)
                    .status("SUCCESS")
                    .build());
        } catch (Exception e) {
            log.error("Error purging audit logs for tenant {}: {}", tenantId, e.getMessage());
            logRepository.save(RetentionPurgeLog.builder()
                    .tenantId(tenantId)
                    .entityType("AUDIT_LOGS")
                    .recordsPurgedCount(0)
                    .status("FAILED")
                    .errorDetails(e.getMessage())
                    .build());
        }

        // 2. Purge old chat messages
        Instant chatCutoff = now.minus(policy.getChatSessionRetentionDays(), ChronoUnit.DAYS);
        int chatCount = 0;
        try {
            chatCount = jdbcTemplate.update(
                    "DELETE FROM chat_messages WHERE tenant_id = ? AND created_at < ?",
                    tenantId, chatCutoff
            );
            logRepository.save(RetentionPurgeLog.builder()
                    .tenantId(tenantId)
                    .entityType("CHAT_MESSAGES")
                    .recordsPurgedCount(chatCount)
                    .status("SUCCESS")
                    .build());
        } catch (Exception e) {
            log.error("Error purging chat messages for tenant {}: {}", tenantId, e.getMessage());
        }

        policy.setLastPurgeAt(now);
        policyRepository.save(policy);

        log.info("Retention purge completed for tenant {}: {} audit logs, {} chat messages purged",
                tenantId, auditCount, chatCount);

        return PurgeSummary.builder()
                .tenantId(tenantId)
                .auditLogsPurged(auditCount)
                .chatMessagesPurged(chatCount)
                .executedAt(now)
                .build();
    }
}
