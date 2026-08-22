package com.medai.agent.repository;

import com.medai.agent.entity.ToolExecution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ToolExecutionRepository extends JpaRepository<ToolExecution, UUID> {
    Page<ToolExecution> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
