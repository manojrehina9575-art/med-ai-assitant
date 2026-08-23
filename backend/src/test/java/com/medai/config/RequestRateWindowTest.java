package com.medai.config;

import com.medai.config.ratelimit.InMemoryRequestRateWindow;
import com.medai.config.ratelimit.RateLimitWindowConfig;
import com.medai.config.ratelimit.RequestRateWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The request window guards the provider's quota against a runaway loop, so its two failure modes
 * both matter: undercounting lets the loop through, and an outage that throws would stop clinical
 * work over a soft control.
 */
class RequestRateWindowTest {

    @Test
    @DisplayName("The in-memory window counts a tenant's requests")
    void inMemoryCounts() {
        RequestRateWindow window = new InMemoryRequestRateWindow();
        UUID tenant = UUID.randomUUID();

        assertEquals(1, window.incrementAndCount(tenant));
        assertEquals(2, window.incrementAndCount(tenant));
        assertEquals(3, window.incrementAndCount(tenant));
    }

    @Test
    @DisplayName("Tenants are counted separately")
    void tenantsAreIndependent() {
        RequestRateWindow window = new InMemoryRequestRateWindow();

        window.incrementAndCount(UUID.randomUUID());
        assertEquals(1, window.incrementAndCount(UUID.randomUUID()));
    }

    @Test
    @DisplayName("Concurrent requests do not lose counts")
    void concurrentIncrementsAreExact() throws InterruptedException {
        RequestRateWindow window = new InMemoryRequestRateWindow();
        UUID tenant = UUID.randomUUID();

        int threads = 16;
        int perThread = 50;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger highest = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            Thread.startVirtualThread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        highest.accumulateAndGet(window.incrementAndCount(tenant), Math::max);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals(threads * perThread, highest.get(),
                "a lost increment is a request that slipped past the limit");
    }

    /**
     * Startup must not depend on Redis being up: failing the context because a rate-limit cache is
     * unreachable would take clinical work down for a soft control.
     */
    @Test
    @DisplayName("An unreachable Redis degrades to the per-instance window instead of failing startup")
    void unreachableRedisFallsBack() {
        RequestRateWindow window = new RateLimitWindowConfig()
                // Port 1 is reserved and nothing listens there.
                .requestRateWindow("127.0.0.1", 1, "");

        assertEquals("in-memory (per-instance)", window.describe());
        assertEquals(1, window.incrementAndCount(UUID.randomUUID()));
    }

    @Test
    @DisplayName("No Redis host configured selects the in-memory window")
    void blankHostSelectsInMemory() {
        assertEquals("in-memory (per-instance)",
                new RateLimitWindowConfig().requestRateWindow("", 6379, "").describe());
    }
}
