package com.medai.analysis.event;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.enums.AnalysisType;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.analysis.service.BloodReportAnalysisService;
import com.medai.analysis.service.CombinedAnalysisService;
import com.medai.analysis.service.ImageAnalysisService;
import com.medai.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.sql.PreparedStatement;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisEventListener {

    private final ImageAnalysisService imageAnalysisService;
    private final BloodReportAnalysisService bloodReportAnalysisService;
    private final CombinedAnalysisService combinedAnalysisService;
    private final AnalysisRequestRepository analysisRequestRepository;
    private final EntityManager entityManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAnalysisRequest(AnalysisRequestEvent event) {
        log.info("Processing analysis request {} for tenant {}", event.getAnalysisRequestId(), event.getTenantId());
        try {
            TenantContext.setCurrentTenantId(event.getTenantId());

            // Initialize Hibernate filter and PostgreSQL RLS on the async thread's session
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", event.getTenantId());
            session.doWork(connection -> {
                try (PreparedStatement stmt = connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
                    stmt.setString(1, event.getTenantId().toString());
                    stmt.execute();
                }
            });

            AnalysisRequest request = analysisRequestRepository.findById(event.getAnalysisRequestId())
                    .orElseThrow(() -> new RuntimeException("Analysis request not found: " + event.getAnalysisRequestId()));

            AnalysisType type = request.getAnalysisType();
            switch (type) {
                case IMAGE_ANALYSIS -> imageAnalysisService.analyzeImage(event.getAnalysisRequestId());
                case BLOOD_REPORT -> bloodReportAnalysisService.analyzeBloodReport(event.getAnalysisRequestId());
                case COMBINED -> combinedAnalysisService.analyzeCombined(event.getAnalysisRequestId());
            }
        } catch (Exception e) {
            log.error("Failed to process analysis request {}: {}", event.getAnalysisRequestId(), e.getMessage(), e);
            try {
                analysisRequestRepository.findById(event.getAnalysisRequestId()).ifPresent(req -> {
                    req.setStatus(AnalysisStatus.FAILED);
                    req.setErrorMessage(e.getMessage() != null ? e.getMessage() : "Analysis failed unexpectedly");
                    req.setProcessingCompletedAt(Instant.now());
                    analysisRequestRepository.save(req);
                });
            } catch (Exception ex) {
                log.error("Failed to update status for failed analysis {}: {}", event.getAnalysisRequestId(), ex.getMessage());
            }
        } finally {
            TenantContext.clear();
        }
    }
}
