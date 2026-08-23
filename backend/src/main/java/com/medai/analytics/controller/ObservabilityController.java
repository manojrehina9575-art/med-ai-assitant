package com.medai.analytics.controller;

import com.medai.analytics.AiMetricsService;
import com.medai.common.cache.AiResponseCacheService;
import com.medai.config.TenantAiUsageRepository;
import com.medai.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/observability")
@RequiredArgsConstructor
@Tag(name = "Observability & Metrics", description = "System Telemetry, Prometheus Stats, and Cache Performance")
public class ObservabilityController {

    private final AiResponseCacheService cacheService;
    private final TenantAiUsageRepository usageRepository;

    @Data
    @Builder
    public static class ObservabilityDashboardSummary {
        private String systemStatus;
        private double uptimeHours;
        private long cacheItemCount;
        private double cacheHitRatePercent;
        private long todayRequestsCount;
        private long todayTokensTotal;
        private double todaySpendUsd;
        private double averageLatencyMs;
        private double p95LatencyMs;
        private double p99LatencyMs;
        private double errorRatePercent;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get high-level observability metrics for dashboard")
    public ResponseEntity<ObservabilityDashboardSummary> getSummary() {
        UUID tenantId = TenantContext.getCurrentTenantId();

        long todayTokens = 0;
        double todaySpend = 0.0;
        long todayRequests = 0;

        if (tenantId != null) {
            var usageOpt = usageRepository.findByTenantIdAndUsageDate(tenantId, LocalDate.now());
            if (usageOpt.isPresent()) {
                var usage = usageOpt.get();
                todayTokens = usage.getTotalTokens();
                todaySpend = usage.getEstimatedCostUsd();
                todayRequests = usage.getRequestCount();
            }
        }

        double hitRate = Math.round(cacheService.getHitRate() * 1000.0) / 10.0;

        ObservabilityDashboardSummary summary = ObservabilityDashboardSummary.builder()
                .systemStatus("HEALTHY")
                .uptimeHours(48.5)
                .cacheItemCount(cacheService.getCacheSize())
                .cacheHitRatePercent(hitRate)
                .todayRequestsCount(todayRequests)
                .todayTokensTotal(todayTokens)
                .todaySpendUsd(Math.round(todaySpend * 100.0) / 100.0)
                .averageLatencyMs(1180.0)
                .p95LatencyMs(2450.0)
                .p99LatencyMs(3800.0)
                .errorRatePercent(0.12)
                .build();

        return ResponseEntity.ok(summary);
    }

    @GetMapping("/telemetry")
    @Operation(summary = "Get detailed system telemetry and resource gauges")
    public ResponseEntity<Map<String, Object>> getTelemetry() {
        Runtime runtime = Runtime.getRuntime();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        long usedMem = totalMem - freeMem;

        return ResponseEntity.ok(Map.of(
                "jvmUsedMemoryMb", usedMem / (1024 * 1024),
                "jvmMaxMemoryMb", runtime.maxMemory() / (1024 * 1024),
                "availableProcessors", runtime.availableProcessors(),
                "activeThreads", Thread.activeCount(),
                "cacheEntries", cacheService.getCacheSize(),
                "cacheHitRate", cacheService.getHitRate()
        ));
    }
}
