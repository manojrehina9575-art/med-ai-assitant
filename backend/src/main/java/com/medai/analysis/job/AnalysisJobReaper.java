package com.medai.analysis.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Resumes analyses that would otherwise never finish.
 *
 * <p>Before this existed, a transient failure set the row back to PENDING and no code path ever
 * read that status again — the analysis was stranded. Anything in flight during a restart was
 * stranded the same way, since the job only existed as an in-memory Spring event. The
 * {@code @Retryable} annotations that were supposed to cover this never ran: nothing in the
 * application enabled Spring Retry's annotation processing, so they created no proxy at all.
 *
 * <p>Concurrency is bounded by the batch size and the sequential loop below: at most
 * {@code batchSize} model calls are in flight per instance per tick.
 */
@Component
@ConditionalOnProperty(name = "app.analysis.reaper.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class AnalysisJobReaper {

    private final AnalysisJobClaimer claimer;
    private final AnalysisDispatcher dispatcher;

    @Value("${app.analysis.reaper.batch-size:5}")
    private int batchSize;

    @Value("${app.analysis.reaper.stale-after-minutes:10}")
    private long staleAfterMinutes;

    @Scheduled(
            initialDelayString = "${app.analysis.reaper.initial-delay-ms:30000}",
            fixedDelayString = "${app.analysis.reaper.interval-ms:60000}")
    public void resumeStalledAnalyses() {
        List<AnalysisJobClaimer.Claim> claims;
        try {
            claims = claimer.claimBatch(batchSize, Duration.ofMinutes(staleAfterMinutes));
        } catch (Exception e) {
            log.error("Could not claim analyses for retry: {}", e.getMessage(), e);
            return;
        }

        if (claims.isEmpty()) {
            return;
        }

        log.info("Resuming {} stalled analysis job(s)", claims.size());
        for (AnalysisJobClaimer.Claim claim : claims) {
            dispatcher.dispatch(claim.analysisRequestId(), claim.tenantId());
        }
    }
}
