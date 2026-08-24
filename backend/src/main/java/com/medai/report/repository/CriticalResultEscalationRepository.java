package com.medai.report.repository;

import com.medai.report.entity.CriticalResultEscalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CriticalResultEscalationRepository extends JpaRepository<CriticalResultEscalation, UUID> {

    Optional<CriticalResultEscalation> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<CriticalResultEscalation> findByTenantIdAndAnalysisId(UUID tenantId, UUID analysisId);

    List<CriticalResultEscalation> findByTenantIdAndStatusOrderByCreatedAtAsc(UUID tenantId, String status);

    /**
     * Unacknowledged escalations past their deadline, across every tenant. The caller opens
     * maintenance access for the scan, then re-notifies each under its own tenant binding.
     */
    List<CriticalResultEscalation> findByStatusAndLastNotifiedAtBefore(String status, Instant before);
}
