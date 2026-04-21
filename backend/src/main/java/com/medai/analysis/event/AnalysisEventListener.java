package com.medai.analysis.event;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisType;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.analysis.service.BloodReportAnalysisService;
import com.medai.analysis.service.CombinedAnalysisService;
import com.medai.analysis.service.ImageAnalysisService;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisEventListener {

    private final ImageAnalysisService imageAnalysisService;
    private final BloodReportAnalysisService bloodReportAnalysisService;
    private final CombinedAnalysisService combinedAnalysisService;
    private final AnalysisRequestRepository analysisRequestRepository;

    @Async
    @EventListener
    public void handleAnalysisRequest(AnalysisRequestEvent event) {
        log.info("Processing analysis request {} for tenant {}", event.getAnalysisRequestId(), event.getTenantId());
        try {
            TenantContext.setTenantId(event.getTenantId());

            AnalysisRequest request = analysisRequestRepository.findById(event.getAnalysisRequestId())
                    .orElseThrow(() -> new RuntimeException("Analysis request not found"));

            AnalysisType type = request.getAnalysisType();
            switch (type) {
                case IMAGE_ANALYSIS -> imageAnalysisService.analyzeImage(event.getAnalysisRequestId());
                case BLOOD_REPORT -> bloodReportAnalysisService.analyzeBloodReport(event.getAnalysisRequestId());
                case COMBINED -> combinedAnalysisService.analyzeCombined(event.getAnalysisRequestId());
            }
        } catch (Exception e) {
            log.error("Failed to process analysis request {}: {}", event.getAnalysisRequestId(), e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
