package com.medai.audit.service;

import com.medai.audit.entity.AuditLog;
import com.medai.audit.repository.AuditLogRepository;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(UUID userId, String action, String entityType, UUID entityId,
                    Map<String, Object> details, String ipAddress, String userAgent) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .tenantId(TenantContext.getCurrentTenantId())
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: action={}, entity={}", action, entityType, e);
        }
    }

    public void log(UUID userId, String action, String entityType, UUID entityId) {
        log(userId, action, entityType, entityId, null, null, null);
    }
}
