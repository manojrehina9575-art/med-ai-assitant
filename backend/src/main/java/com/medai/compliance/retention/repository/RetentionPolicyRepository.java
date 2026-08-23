package com.medai.compliance.retention.repository;

import com.medai.compliance.retention.entity.DataRetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionPolicyRepository extends JpaRepository<DataRetentionPolicy, UUID> {
    Optional<DataRetentionPolicy> findByTenantId(UUID tenantId);
    List<DataRetentionPolicy> findByAutoPurgeEnabledTrue();
}
