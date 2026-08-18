package com.medai.analysis.job;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.tenant.TenantSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Claims a batch of resumable analyses in one short transaction.
 *
 * <p>Kept separate from {@link AnalysisJobReaper} so the claim commits — and releases its row
 * locks — before any AI call starts. Holding {@code FOR UPDATE} locks across a multi-second
 * model call would block every other instance's reaper.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisJobClaimer {

    private final AnalysisRequestRepository analysisRequestRepository;
    private final TenantSession tenantSession;

    /** An analysis claimed for processing. */
    public record Claim(UUID analysisRequestId, UUID tenantId) {
    }

    /**
     * Marks up to {@code batchSize} eligible analyses as PROCESSING and returns them.
     *
     * <p>A row found already PROCESSING is a crash-interrupted job: its attempt counter is
     * incremented so a job that reliably kills its worker eventually lands in FAILED instead of
     * looping forever.
     */
    @Transactional
    public List<Claim> claimBatch(int batchSize, Duration staleAfter) {
        // Scanning for stalled work spans every tenant, which row-level security correctly
        // forbids by default. Opt in for this transaction only.
        tenantSession.beginMaintenance();

        Instant now = Instant.now();
        List<AnalysisRequest> rows = analysisRequestRepository.claimResumableAnalyses(now.minus(staleAfter), batchSize);

        List<Claim> claims = new ArrayList<>(rows.size());
        for (AnalysisRequest row : rows) {
            boolean interrupted = row.getStatus() == AnalysisStatus.PROCESSING;

            if (interrupted) {
                int attempts = row.getRetryCount() + 1;
                row.setRetryCount(attempts);
                if (attempts >= row.getMaxRetries()) {
                    row.setStatus(AnalysisStatus.FAILED);
                    row.setErrorMessage("Processing was interrupted repeatedly and did not complete after "
                                        + attempts + " attempt(s).");
                    row.setProcessingCompletedAt(now);
                    analysisRequestRepository.save(row);
                    log.error("Analysis {} abandoned after {} interrupted attempt(s)", row.getId(), attempts);
                    continue;
                }
                log.warn("Analysis {} was interrupted mid-processing; resuming (attempt {}/{})",
                        row.getId(), attempts + 1, row.getMaxRetries());
            }

            row.setStatus(AnalysisStatus.PROCESSING);
            row.setProcessingStartedAt(now);
            analysisRequestRepository.save(row);
            claims.add(new Claim(row.getId(), row.getTenantId()));
        }
        return claims;
    }
}
