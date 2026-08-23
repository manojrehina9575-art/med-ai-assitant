package com.medai.compliance.retention.service;

import com.medai.compliance.retention.entity.DataRetentionPolicy;
import com.medai.compliance.retention.entity.RetentionPurgeLog;
import com.medai.compliance.retention.repository.RetentionPolicyRepository;
import com.medai.compliance.retention.repository.RetentionPurgeLogRepository;
import com.medai.tenant.TenantContext;
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

    private final RetentionPolicyRepository policyRepository;
    private final RetentionPurgeLogRepository logRepository;
    private final JdbcTemplate jdbcTemplate;

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
                        .auditLogRetentionDays(365)
                        .analysisRetentionDays(730)
                        .chatSessionRetentionDays(180)
                        .softDeletePurgeDays(30)
                        .autoPurgeEnabled(false)
                        .build()));
    }

    @Transactional
    public DataRetentionPolicy updatePolicy(DataRetentionPolicy policyUpdate) {
        UUID tenantId = TenantContext.requireTenantId();
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
     * Executes daily automated purge for all tenants with autoPurgeEnabled=true.
     */
    @Scheduled(cron = "${app.retention.cron:0 0 2 * * *}") // Runs daily at 2:00 AM
    public void runScheduledRetentionPurge() {
        log.info("Running scheduled data retention purge job...");
        List<DataRetentionPolicy> enabledPolicies = policyRepository.findByAutoPurgeEnabledTrue();
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

        // 1. Purge old audit logs
        Instant auditCutoff = now.minus(policy.getAuditLogRetentionDays(), ChronoUnit.DAYS);
        int auditCount = 0;
        try {
            auditCount = jdbcTemplate.update(
                    "DELETE FROM audit_logs WHERE tenant_id = ? AND timestamp < ?",
                    tenantId, auditCutoff
            );
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
