package com.medai.config;

import com.medai.common.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory per-tenant rate limiter for AI analysis endpoints.
 * Tracks both request count (per minute) and estimated cost (per day).
 *
 * <p>Note: This is a single-node in-memory implementation suitable for MVP.
 * For production with multiple instances, replace with Redis-based rate limiting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RateLimitConfig config;

    // Tenant request tracking: tenantId -> (windowStart, count)
    private final Map<UUID, RateWindow> requestWindows = new ConcurrentHashMap<>();

    // Tenant cost tracking: tenantId -> (dayStart, totalCost)
    private final Map<UUID, CostWindow> costWindows = new ConcurrentHashMap<>();

    /**
     * Check if the tenant is within rate limits before processing an AI request.
     *
     * @throws RateLimitExceededException if limits are exceeded
     */
    public void checkRateLimit(UUID tenantId) {
        checkRequestLimit(tenantId);
        checkCostLimit(tenantId);
    }

    /**
     * Record a completed AI request and its estimated cost.
     */
    public void recordRequest(UUID tenantId, BigDecimal estimatedCost) {
        if (estimatedCost != null && estimatedCost.compareTo(BigDecimal.ZERO) > 0) {
            CostWindow window = costWindows.computeIfAbsent(tenantId,
                    k -> new CostWindow(Instant.now().truncatedTo(ChronoUnit.DAYS)));
            window.addCost(estimatedCost.doubleValue());
        }
    }

    private void checkRequestLimit(UUID tenantId) {
        RateWindow window = requestWindows.compute(tenantId, (k, existing) -> {
            Instant now = Instant.now();
            if (existing == null || existing.isExpired(now)) {
                return new RateWindow(now);
            }
            return existing;
        });

        int count = window.incrementAndGet();
        if (count > config.getMaxRequestsPerMinute()) {
            log.warn("Rate limit exceeded for tenant {} ({} requests/min, limit: {})",
                    tenantId, count, config.getMaxRequestsPerMinute());
            throw new RateLimitExceededException(
                    "AI analysis rate limit exceeded. Maximum " + config.getMaxRequestsPerMinute() +
                    " requests per minute. Please try again shortly.");
        }
    }

    private void checkCostLimit(UUID tenantId) {
        CostWindow window = costWindows.get(tenantId);
        if (window == null) return;

        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        if (!window.dayStart.equals(today)) {
            // New day — reset
            costWindows.put(tenantId, new CostWindow(today));
            return;
        }

        if (window.getTotalCost() >= config.getMaxCostPerDayUsd()) {
            log.warn("Daily cost limit exceeded for tenant {} (${}, limit: ${})",
                    tenantId, String.format("%.2f", window.getTotalCost()), config.getMaxCostPerDayUsd());
            throw new RateLimitExceededException(
                    "Daily AI analysis cost limit exceeded ($" +
                    String.format("%.2f", config.getMaxCostPerDayUsd()) +
                    "). Contact your administrator to increase the limit.");
        }
    }

    /**
     * Sliding window for request count (1-minute window).
     */
    private static class RateWindow {
        private final Instant windowStart;
        private final AtomicInteger count = new AtomicInteger(0);

        RateWindow(Instant windowStart) {
            this.windowStart = windowStart;
        }

        boolean isExpired(Instant now) {
            return now.isAfter(windowStart.plusSeconds(60));
        }

        int incrementAndGet() {
            return count.incrementAndGet();
        }
    }

    /**
     * Daily cost window for tracking estimated USD spend per tenant.
     */
    private static class CostWindow {
        private final Instant dayStart;
        private final AtomicReference<Double> totalCost = new AtomicReference<>(0.0);

        CostWindow(Instant dayStart) {
            this.dayStart = dayStart;
        }

        void addCost(double cost) {
            totalCost.updateAndGet(current -> current + cost);
        }

        double getTotalCost() {
            return totalCost.get();
        }
    }
}
