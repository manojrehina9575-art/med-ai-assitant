package com.medai.analysis.service;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.repository.AnalysisRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Single place where a failed analysis is written back, so the three analysis services
 * cannot drift apart on retry semantics.
 *
 * <p>The distinction that matters: a <b>terminal</b> failure is one where the input itself is the
 * problem (unreadable file, nothing to correlate), and retrying the same input will fail the same
 * way. A <b>transient</b> failure is a provider timeout, rate limit, or malformed response, where
 * the same input may well succeed on the next attempt.
 *
 * <p>Transient failures go back to {@link AnalysisStatus#PENDING} and are picked up by
 * {@link com.medai.analysis.job.AnalysisJobReaper}. Before that reaper existed, PENDING was a
 * dead end — nothing in the system ever read it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisFailureRecorder {

    private final AnalysisRequestRepository analysisRequestRepository;

    /** Marks the analysis FAILED immediately; no retry will be attempted. */
    public void recordTerminal(AnalysisRequest request, String errorMessage) {
        request.setStatus(AnalysisStatus.FAILED);
        request.setErrorMessage(errorMessage);
        request.setProcessingCompletedAt(Instant.now());
        analysisRequestRepository.save(request);
        log.error("Analysis {} failed terminally: {}", request.getId(), errorMessage);
    }

    /** Returns the analysis to PENDING for the reaper to retry, or FAILED once retries run out. */
    public void recordTransient(AnalysisRequest request, String errorMessage) {
        int attempts = request.getRetryCount() + 1;
        request.setRetryCount(attempts);
        request.setErrorMessage(errorMessage);
        request.setProcessingCompletedAt(Instant.now());

        if (attempts >= request.getMaxRetries()) {
            request.setStatus(AnalysisStatus.FAILED);
            log.error("Analysis {} failed after {} attempt(s), giving up: {}",
                    request.getId(), attempts, errorMessage);
        } else {
            request.setStatus(AnalysisStatus.PENDING);
            log.warn("Analysis {} failed on attempt {}/{}, queued for retry: {}",
                    request.getId(), attempts, request.getMaxRetries(), errorMessage);
        }
        analysisRequestRepository.save(request);
    }
}
