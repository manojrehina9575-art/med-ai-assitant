package com.medai.analysis.repository;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, UUID> {

    Page<AnalysisRequest> findByTenantIdAndPatientIdOrderByCreatedAtDesc(UUID tenantId, UUID patientId, Pageable pageable);

    Page<AnalysisRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Optional<AnalysisRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AnalysisRequest> findByStatusAndRetryCountLessThan(AnalysisStatus status, Integer maxRetries);

    /**
     * Claims analyses that need (re)processing, across all tenants, locking the rows so that
     * multiple application instances never pick up the same job.
     *
     * <p>Two cases are claimed:
     * <ul>
     *   <li>{@code PENDING} with retries remaining — a transient failure queued for another attempt.</li>
     *   <li>{@code PROCESSING} started before {@code staleBefore} — the worker died mid-flight
     *       (crash, restart, or deploy) and nothing would ever finish the job.</li>
     * </ul>
     *
     * <p>This is a native query, so the Hibernate tenant filter does not apply — deliberately, since
     * the reaper works across tenants. Note that it also relies on the application's current
     * database role bypassing row-level security; when the app moves to a non-owner role, this
     * query needs a maintenance role with {@code BYPASSRLS} or a policy that admits it.
     */
    @Query(value = """
            SELECT * FROM analysis_requests
             WHERE (status = 'PENDING' AND retry_count < max_retries)
                OR (status = 'PROCESSING' AND processing_started_at < :staleBefore)
             ORDER BY created_at
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<AnalysisRequest> claimResumableAnalyses(@Param("staleBefore") Instant staleBefore,
                                                 @Param("batchSize") int batchSize);

    long countByTenantIdAndPatientId(UUID tenantId, UUID patientId);

    List<AnalysisRequest> findByTenantIdAndMedicalFileId(UUID tenantId, UUID medicalFileId);
}
