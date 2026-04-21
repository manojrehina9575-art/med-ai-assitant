package com.medai.analysis.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class AnalysisRequestEvent extends ApplicationEvent {

    private final UUID analysisRequestId;
    private final UUID tenantId;

    public AnalysisRequestEvent(Object source, UUID analysisRequestId, UUID tenantId) {
        super(source);
        this.analysisRequestId = analysisRequestId;
        this.tenantId = tenantId;
    }
}
