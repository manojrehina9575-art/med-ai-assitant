package com.medai.finetuning.ab.repository;

import com.medai.finetuning.ab.entity.AbExperimentEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AbExperimentEvaluationRepository extends JpaRepository<AbExperimentEvaluation, UUID> {
    List<AbExperimentEvaluation> findByExperimentId(UUID experimentId);
    List<AbExperimentEvaluation> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
