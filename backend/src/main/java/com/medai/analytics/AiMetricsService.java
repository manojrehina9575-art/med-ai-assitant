package com.medai.analytics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordAiRequest(String tenantId, String model, String modality, String status, long latencyMs, int promptTokens, int completionTokens, double costUsd) {
        try {
            // 1. Counter: total AI requests
            Counter.builder("medai.ai.requests.total")
                    .tag("tenant", tenantId != null ? tenantId : "unknown")
                    .tag("model", model != null ? model : "default")
                    .tag("modality", modality != null ? modality : "general")
                    .tag("status", status != null ? status : "SUCCESS")
                    .description("Total AI diagnostic and inference requests")
                    .register(meterRegistry)
                    .increment();

            // 2. Counter: prompt and completion tokens
            Counter.builder("medai.ai.tokens.total")
                    .tag("tenant", tenantId != null ? tenantId : "unknown")
                    .tag("model", model != null ? model : "default")
                    .tag("type", "prompt")
                    .register(meterRegistry)
                    .increment(promptTokens);

            Counter.builder("medai.ai.tokens.total")
                    .tag("tenant", tenantId != null ? tenantId : "unknown")
                    .tag("model", model != null ? model : "default")
                    .tag("type", "completion")
                    .register(meterRegistry)
                    .increment(completionTokens);

            // 3. Counter: cost in USD
            Counter.builder("medai.ai.cost.usd")
                    .tag("tenant", tenantId != null ? tenantId : "unknown")
                    .tag("model", model != null ? model : "default")
                    .register(meterRegistry)
                    .increment(costUsd);

            // 4. Timer: latency
            Timer.builder("medai.ai.latency")
                    .tag("tenant", tenantId != null ? tenantId : "unknown")
                    .tag("model", model != null ? model : "default")
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry)
                    .record(latencyMs, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            log.warn("Failed to record Micrometer metric: {}", e.getMessage());
        }
    }

    public void recordPhiRedaction(String tenantId, String entityType, int count) {
        try {
            Counter.builder("medai.phi.redactions.total")
                    .tag("tenant", tenantId != null ? tenantId : "unknown")
                    .tag("entity_type", entityType != null ? entityType : "GENERAL")
                    .register(meterRegistry)
                    .increment(count);
        } catch (Exception e) {
            log.warn("Failed to record PHI metric: {}", e.getMessage());
        }
    }

    public void recordCacheAccess(boolean isHit) {
        try {
            Counter.builder("medai.cache.access.total")
                    .tag("result", isHit ? "HIT" : "MISS")
                    .register(meterRegistry)
                    .increment();
        } catch (Exception e) {
            log.warn("Failed to record cache metric: {}", e.getMessage());
        }
    }
}
