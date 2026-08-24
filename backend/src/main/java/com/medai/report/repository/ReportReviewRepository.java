package com.medai.report.repository;

import com.medai.report.entity.ReportReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportReviewRepository extends JpaRepository<ReportReview, UUID> {

    Optional<ReportReview> findByIdAndTenantId(UUID id, UUID tenantId);

    /** The open review for an analysis, if any. At most one exists — enforced in V19. */
    @Query("""
           SELECT r FROM ReportReview r
            WHERE r.tenantId = :tenantId AND r.analysisId = :analysisId
              AND r.status IN ('DRAFT', 'IN_REVIEW')
           """)
    Optional<ReportReview> findOpenByAnalysis(@Param("tenantId") UUID tenantId,
                                              @Param("analysisId") UUID analysisId);

    /** Signed reports for an analysis, newest first — an amendment supersedes its predecessor. */
    List<ReportReview> findByTenantIdAndAnalysisIdAndStatusOrderBySignedAtDesc(
            UUID tenantId, UUID analysisId, String status);

    /** The reading worklist: oldest first, because that is the one that has waited longest. */
    Page<ReportReview> findByTenantIdAndStatusInOrderByCreatedAtAsc(
            UUID tenantId, List<String> statuses, Pageable pageable);

    Page<ReportReview> findByTenantIdAndPatientIdOrderByCreatedAtDesc(
            UUID tenantId, UUID patientId, Pageable pageable);

    /**
     * Signed reviews carrying a clinician's verdict, for the training-data export. An EDITED
     * review is the most valuable record in the system: a real model error and its real
     * correction, produced as a by-product of work someone was doing anyway.
     */
    @Query("""
           SELECT r FROM ReportReview r
            WHERE r.tenantId = :tenantId AND r.status = 'SIGNED'
              AND (:action IS NULL OR r.reviewAction = :action)
            ORDER BY r.signedAt DESC
           """)
    List<ReportReview> findSignedForTraining(@Param("tenantId") UUID tenantId,
                                             @Param("action") String action,
                                             Pageable pageable);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    long countByTenantIdAndReviewAction(UUID tenantId, String reviewAction);
}
