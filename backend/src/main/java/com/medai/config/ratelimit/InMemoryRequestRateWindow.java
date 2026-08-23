package com.medai.config.ratelimit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-instance request window. Correct for a single instance, and an undercount for any more.
 *
 * <p>Used when no Redis host is configured. {@link RateLimitWindowConfig} says so loudly at
 * startup, because the failure mode is otherwise silent: nothing looks wrong, the limit is simply
 * N times higher than the number in the configuration file.
 */
public class InMemoryRequestRateWindow implements RequestRateWindow {

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    @Override
    public int incrementAndCount(UUID tenantId) {
        Window window = windows.compute(tenantId, (key, existing) -> {
            Instant now = Instant.now();
            return (existing == null || existing.isExpired(now)) ? new Window(now) : existing;
        });
        return window.count.incrementAndGet();
    }

    @Override
    public String describe() {
        return "in-memory (per-instance)";
    }

    private static final class Window {
        private final Instant start;
        private final AtomicInteger count = new AtomicInteger(0);

        Window(Instant start) {
            this.start = start;
        }

        boolean isExpired(Instant now) {
            return now.isAfter(start.plusSeconds(60));
        }
    }
}
