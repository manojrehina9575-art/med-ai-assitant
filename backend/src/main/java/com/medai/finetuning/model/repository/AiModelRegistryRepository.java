package com.medai.finetuning.model.repository;

import com.medai.finetuning.model.entity.AiModelRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AiModelRegistryRepository extends JpaRepository<AiModelRegistry, UUID> {
    Optional<AiModelRegistry> findByModelId(String modelId);

    @Query("SELECT m FROM AiModelRegistry m WHERE (m.tenantId IS NULL OR m.tenantId = :tenantId) AND m.active = true ORDER BY m.createdAt DESC")
    List<AiModelRegistry> findAllAvailableForTenant(@Param("tenantId") UUID tenantId);

    List<AiModelRegistry> findByStatus(String status);
}
