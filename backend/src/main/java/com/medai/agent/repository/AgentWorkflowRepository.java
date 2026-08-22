package com.medai.agent.repository;

import com.medai.agent.entity.AgentWorkflow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentWorkflowRepository extends JpaRepository<AgentWorkflow, UUID> {
    Page<AgentWorkflow> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    Page<AgentWorkflow> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId, Pageable pageable);
    Optional<AgentWorkflow> findByTenantIdAndId(UUID tenantId, UUID id);
}
