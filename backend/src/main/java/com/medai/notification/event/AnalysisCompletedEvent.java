package com.medai.notification.event;

import com.medai.analysis.entity.AnalysisRequest;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when an analysis reaches a terminal state (COMPLETED or FAILED).
 * The NotificationEventListener picks this up and creates in-app notifications
 * without coupling the analysis services to the notification subsystem.
 */
@Getter
public class AnalysisCompletedEvent extends ApplicationEvent {

    private final AnalysisRequest analysisRequest;
    /** User who requested the analysis — they receive the notification */
    private final java.util.UUID requestedByUserId;
    private final boolean success;

    public AnalysisCompletedEvent(Object source,
                                   AnalysisRequest analysisRequest,
                                   java.util.UUID requestedByUserId,
                                   boolean success) {
        super(source);
        this.analysisRequest  = analysisRequest;
        this.requestedByUserId = requestedByUserId;
        this.success          = success;
    }
}
