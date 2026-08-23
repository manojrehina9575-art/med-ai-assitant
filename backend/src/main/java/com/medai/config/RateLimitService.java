package com.medai.config;

import com.medai.common.exception.RateLimitExceededException;
import com.medai.config.ratelimit.RequestRateWindow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Per-tenant limits on AI analysis: a request rate and a daily spend cap.
 *
 * <p>Spend is held in the database ({@code tenant_ai_usage_daily}), so the cap survives a restart
 * and is shared by every instance. It used to live in a map, which meant a restart handed every
 * tenant a fresh budget and a second instance doubled the ceiling.
 *
 * <p>The request-per-minute window is shared through Redis when one is configured, and falls back
 * to a per-instance counter when it is not — see {@link RequestRateWindow}. Per-instance was the
 * only option before, which meant the Kubernetes HPA scaling to ten replicas raised the real
 * ceiling tenfold against a limit that looked exact in the configuration file.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RateLimitConfig config;
    private final ModelPricing modelPricing;
    private final TenantAiUsageRepository usageRepository;
    private final RequestRateWindow requestWindow;

    /** @throws RateLimitExceededException if the tenant is over either limit */
    public void checkRateLimit(UUID tenantId) {
        checkRequestLimit(tenantId);
        checkCostLimit(tenantId);
    }

    /**
     * Records a completed AI call against the tenant's daily totals.
     *
     * <p>Runs in its own transaction so that recording usage cannot roll back with — or roll back —
     * the analysis that produced it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUsage(UUID tenantId, String modelId, Integer promptTokens, Integer completionTokens) {
        BigDecimal cost = modelPricing.estimate(modelId, promptTokens, completionTokens);
        try {
            usageRepository.addUsage(
                    tenantId,
                    today(),
                    promptTokens != null ? promptTokens : 0L,
                    completionTokens != null ? completionTokens : 0L,
                    cost);
        } catch (Exception e) {
            log.error("Could not record AI usage for tenant {} (model={}, cost={}): {}",
                    tenantId, modelId, cost, e.getMessage());
        }
    }

    /** Cost of a call, for storing on the analysis row. */
    public BigDecimal estimateCost(String modelId, Integer promptTokens, Integer completionTokens) {
        return modelPricing.estimate(modelId, promptTokens, completionTokens);
    }

    private void checkRequestLimit(UUID tenantId) {
        int count = requestWindow.incrementAndCount(tenantId);
        if (count > config.getMaxRequestsPerMinute()) {
            log.warn("Rate limit exceeded for tenant {} ({} requests/min, limit: {})",
                    tenantId, count, config.getMaxRequestsPerMinute());
            throw new RateLimitExceededException(
                    "AI analysis rate limit exceeded. Maximum " + config.getMaxRequestsPerMinute()
                    + " requests per minute. Please try again shortly.");
        }
    }

    private void checkCostLimit(UUID tenantId) {
        BigDecimal spentToday;
        try {
            spentToday = usageRepository.findCostForDay(tenantId, today());
        } catch (Exception e) {
            // Fail open on a read error: refusing every analysis because a usage lookup failed is
            // worse than briefly not enforcing a soft budget. The failure is logged loudly.
            log.error("Could not read today's AI spend for tenant {}; allowing the request: {}",
                    tenantId, e.getMessage());
            return;
        }

        if (spentToday == null) {
            return;
        }

        BigDecimal limit = BigDecimal.valueOf(config.getMaxCostPerDayUsd());
        if (spentToday.compareTo(limit) >= 0) {
            log.warn("Daily cost limit reached for tenant {} (${} of ${})", tenantId, spentToday, limit);
            throw new RateLimitExceededException(
                    "Daily AI analysis cost limit of $" + String.format("%.2f", config.getMaxCostPerDayUsd())
                    + " reached. Contact your administrator to increase the limit.");
        }
    }

    /** Days roll over at UTC midnight, matching the DATE column the totals are keyed by. */
    private LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

}
