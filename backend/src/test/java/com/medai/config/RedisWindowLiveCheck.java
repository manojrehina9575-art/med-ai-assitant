package com.medai.config;

import com.medai.config.ratelimit.RateLimitWindowConfig;
import com.medai.config.ratelimit.RequestRateWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the Redis window is genuinely shared, which is the entire reason it exists.
 *
 * <p>Runs only when MEDAI_TEST_REDIS_HOST points at a Redis instance, so the default suite stays
 * hermetic. Two independently constructed windows stand in for two replicas: if they do not see
 * each other's counts, the HPA multiplies the configured ceiling exactly as before.
 */
@EnabledIfEnvironmentVariable(named = "MEDAI_TEST_REDIS_HOST", matches = ".+")
class RedisWindowLiveCheck {

    @Test
    @DisplayName("Two instances share one window")
    void twoInstancesShareTheWindow() {
        String host = System.getenv("MEDAI_TEST_REDIS_HOST");
        RateLimitWindowConfig config = new RateLimitWindowConfig();

        RequestRateWindow replicaOne = config.requestRateWindow(host, 6379, "");
        RequestRateWindow replicaTwo = config.requestRateWindow(host, 6379, "");

        assertEquals("redis (shared across instances)", replicaOne.describe());

        UUID tenant = UUID.randomUUID();
        assertEquals(1, replicaOne.incrementAndCount(tenant));
        assertEquals(2, replicaTwo.incrementAndCount(tenant),
                "the second replica must continue the first one's count, not start its own");
        assertEquals(3, replicaOne.incrementAndCount(tenant));

        assertEquals(1, replicaTwo.incrementAndCount(UUID.randomUUID()),
                "a different tenant has its own window");
    }
}
