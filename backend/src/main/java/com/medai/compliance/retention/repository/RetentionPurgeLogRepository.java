package com.medai.compliance.retention.repository;

import com.medai.compliance.retention.entity.RetentionPurgeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RetentionPurgeLogRepository extends JpaRepository<RetentionPurgeLog, UUID> {
    List<RetentionPurgeLog> findByTenantIdOrderByExecutedAtDesc(UUID tenantId);
}
