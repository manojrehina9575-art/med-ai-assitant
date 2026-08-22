package com.medai.agent.repository;

import com.medai.agent.entity.AgentWorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentWorkflowStepRepository extends JpaRepository<AgentWorkflowStep, UUID> {
    List<AgentWorkflowStep> findByWorkflowIdOrderByStepIndexAsc(UUID workflowId);
    Optional<AgentWorkflowStep> findByWorkflowIdAndId(UUID workflowId, UUID id);
    List<AgentWorkflowStep> findByWorkflowIdAndRequiresConfirmationTrueAndConfirmationStatus(UUID workflowId, String confirmationStatus);
}
