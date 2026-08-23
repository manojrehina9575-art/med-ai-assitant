package com.medai.finetuning.ab.repository;

import com.medai.finetuning.ab.entity.AbExperiment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AbExperimentRepository extends JpaRepository<AbExperiment, UUID> {
    List<AbExperiment> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<AbExperiment> findByTenantIdAndModalityAndStatus(UUID tenantId, String modality, String status);
    List<AbExperiment> findByTenantIdAndStatus(UUID tenantId, String status);
}
