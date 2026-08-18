package com.medai.analysis.job;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.analysis.service.BloodReportAnalysisService;
import com.medai.analysis.service.CombinedAnalysisService;
import com.medai.analysis.service.ImageAnalysisService;
import com.medai.tenant.TenantContext;
import com.medai.tenant.TenantSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Runs one analysis on a background thread with tenant isolation established.
 *
 * <p>Both entry points — the post-commit event from a fresh request, and the reaper picking up
 * stalled work — go through here, so the tenant filter and RLS session variable are set up
 * identically in each case. Getting that setup wrong on one path only is how a background job
 * ends up reading another hospital's data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisDispatcher {

    private final ImageAnalysisService imageAnalysisService;
    private final BloodReportAnalysisService bloodReportAnalysisService;
    private final CombinedAnalysisService combinedAnalysisService;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final TenantSession tenantSession;

    /**
     * Executes the analysis identified by {@code analysisRequestId} under {@code tenantId}.
     *
     * <p>Exceptions are logged and swallowed: the analysis services have already recorded the
     * failure on the row itself (FAILED, or PENDING for the reaper to retry), so there is nothing
     * for a caller to do with the exception.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(UUID analysisRequestId, UUID tenantId) {
        try {
            // Background threads have no request to hang tenant scoping off, so it is bound
            // explicitly here — for both the Hibernate filter and row-level security.
            tenantSession.bind(tenantId);

            AnalysisRequest request = analysisRequestRepository.findById(analysisRequestId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Analysis request not found: " + analysisRequestId));

            log.info("Dispatching {} analysis {} for tenant {}",
                    request.getAnalysisType(), analysisRequestId, tenantId);

            switch (request.getAnalysisType()) {
                case IMAGE_ANALYSIS -> imageAnalysisService.analyzeImage(analysisRequestId);
                case BLOOD_REPORT -> bloodReportAnalysisService.analyzeBloodReport(analysisRequestId);
                case COMBINED -> combinedAnalysisService.analyzeCombined(analysisRequestId);
            }
        } catch (Exception e) {
            // The status was already written by AnalysisFailureRecorder inside the service.
            log.error("Analysis {} did not complete: {}", analysisRequestId, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

}
