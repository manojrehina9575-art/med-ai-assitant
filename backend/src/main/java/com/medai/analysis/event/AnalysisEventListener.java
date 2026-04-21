package com.medai.analysis.event;

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

    @Async
    @EventListener
    public void handleAnalysisRequest(AnalysisRequestEvent event) {
        log.info("Processing analysis request {} for tenant {}", event.getAnalysisRequestId(), event.getTenantId());
        try {
            TenantContext.setTenantId(event.getTenantId());
            imageAnalysisService.analyzeImage(event.getAnalysisRequestId());
        } catch (Exception e) {
            log.error("Failed to process analysis request {}: {}", event.getAnalysisRequestId(), e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
