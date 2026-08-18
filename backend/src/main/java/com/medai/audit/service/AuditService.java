package com.medai.audit.service;

import com.medai.audit.entity.AuditLog;
import com.medai.audit.repository.AuditLogRepository;
import com.medai.tenant.TenantSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final TenantSession tenantSession;

    /**
     * Records an audited action.
     *
     * <p>The tenant is passed in rather than read from {@code TenantContext} inside this method:
     * because the write is {@code @Async} it runs on a different thread, where the caller's
     * ThreadLocal does not exist. Reading it here always produced null, and the resulting insert
     * violated the NOT NULL constraint — so every audit write would have failed silently even
     * once something called this.
     */
    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID tenantId, UUID userId, String action, String entityType, UUID entityId,
                       Map<String, Object> details, String ipAddress, String userAgent) {
        if (tenantId == null) {
            return;
        }
        try {
            // Row-level security applies to this insert too, and this thread has no tenant bound.
            tenantSession.bind(tenantId);

            auditLogRepository.save(AuditLog.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build());
        } catch (Exception e) {
            // Never fail a clinical operation because its audit row could not be written, but do
            // make the gap loud — a silent audit gap is the thing an investigation cannot recover from.
            log.error("AUDIT WRITE FAILED action={} entity={}/{} tenant={} user={}: {}",
                    action, entityType, entityId, tenantId, userId, e.getMessage(), e);
        }
    }
}
