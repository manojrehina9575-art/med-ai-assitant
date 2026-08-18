package com.medai.analysis.event;

import com.medai.analysis.job.AnalysisDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts an analysis as soon as its request row is committed.
 *
 * <p>This is the fast path only. Durability comes from
 * {@link com.medai.analysis.job.AnalysisJobReaper} — if this thread dies, the process restarts, or
 * the model call fails transiently, the reaper picks the job back up. Both paths share
 * {@link AnalysisDispatcher} so tenant isolation is established identically.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisEventListener {

    private final AnalysisDispatcher dispatcher;

    @Async("analysisExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAnalysisRequest(AnalysisRequestEvent event) {
        dispatcher.dispatch(event.getAnalysisRequestId(), event.getTenantId());
    }
}
