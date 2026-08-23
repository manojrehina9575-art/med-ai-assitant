package com.medai.notification.event;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.notification.service.NotificationService;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    /**
     * Fires asynchronously after an analysis completes so the notification write
     * does not extend the analysis transaction's critical path.
     */
    @Async
    @EventListener
    public void onAnalysisCompleted(AnalysisCompletedEvent event) {
        AnalysisRequest req  = event.getAnalysisRequest();
        if (req == null || event.getRequestedByUserId() == null) return;

        // Restore tenant context for this async thread
        TenantContext.setCurrentTenantId(req.getTenantId());
        try {
            if (event.isSuccess()) {
                String urgency  = req.getUrgency() != null ? req.getUrgency().toUpperCase() : "ROUTINE";
                boolean critical = "CRITICAL".equals(urgency) || "URGENT".equals(urgency);

                String severity  = critical ? "CRITICAL" : "INFO";
                String typeLabel = critical ? "CRITICAL_FINDING" : "ANALYSIS_COMPLETE";
                String title     = critical
                        ? "⚠ Critical Finding — " + req.getAnalysisType().name()
                        : "Analysis Complete — " + req.getAnalysisType().name();
                String message   = critical
                        ? "A critical finding was detected in the AI analysis. Immediate clinician review is required."
                        : "AI analysis completed successfully. Results are ready for review.";

                notificationService.createNotification(
                        event.getRequestedByUserId(),
                        typeLabel,
                        title,
                        message,
                        severity,
                        "ANALYSIS",
                        req.getId()
                );
            } else {
                notificationService.createNotification(
                        event.getRequestedByUserId(),
                        "ANALYSIS_FAILED",
                        "Analysis Failed — " + req.getAnalysisType().name(),
                        "The AI analysis could not be completed. " +
                                (req.getErrorMessage() != null ? req.getErrorMessage() : "Please retry."),
                        "WARNING",
                        "ANALYSIS",
                        req.getId()
                );
            }
        } catch (Exception ex) {
            log.warn("Failed to create notification for analysis {}: {}", req.getId(), ex.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
