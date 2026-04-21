package com.medai.analysis.repository;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, UUID> {

    Page<AnalysisRequest> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId, Pageable pageable);

    Page<AnalysisRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<AnalysisRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AnalysisRequest> findByStatusAndRetryCountLessThan(AnalysisStatus status, Integer maxRetries);

    long countByTenantIdAndPatientId(UUID tenantId, UUID patientId);

    List<AnalysisRequest> findByTenantIdAndMedicalFileId(UUID tenantId, UUID medicalFileId);
}
