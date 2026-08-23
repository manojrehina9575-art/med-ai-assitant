package com.medai.config.ratelimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Selects the request-rate window: Redis when a host is configured, in-memory otherwise.
 *
 * <p>Redis is optional deliberately. A single-instance deployment does not need it, and making it
 * mandatory would add a hard dependency to the smallest useful configuration. What is not optional
 * is knowing which one is running — the previous arrangement had a Redis Deployment in the
 * Kubernetes manifests, no Redis dependency in the build at all, and an HPA scaling to ten
 * replicas past a per-instance counter. Every part of that looked deliberate.
 *
 * <p>One bean method, not two conditional ones, and no nullable {@code RedisClient} bean: a
 * {@code @Bean} method that returns null registers a placeholder that cannot then satisfy a
 * required injection point, so the "no Redis configured" path would fail the context rather than
 * falling back.
 */
@Configuration
@Slf4j
public class RateLimitWindowConfig {

    @Bean(destroyMethod = "close")
    public RequestRateWindow requestRateWindow(
            @Value("${app.rate-limit.redis.host:}") String host,
            @Value("${app.rate-limit.redis.port:6379}") int port,
            @Value("${app.rate-limit.redis.password:}") String password) {

        InMemoryRequestRateWindow inMemory = new InMemoryRequestRateWindow();

        if (host == null || host.isBlank()) {
            log.warn("""

                    ============================================================================
                     AI request-rate window: in-memory, per-instance.

                     Each instance counts separately, so the effective ceiling is the configured
                     limit multiplied by the number of running replicas. Correct for exactly one
                     instance.

                     Set APP_RATE_LIMIT_REDIS_HOST to share the window across instances. The daily
                     spend cap is unaffected — it lives in the database and is already
                     authoritative across all of them.
                    ============================================================================
                    """);
            return inMemory;
        }

        RedisURI.Builder uri = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                // Short: this sits in front of every AI request, so a slow Redis must degrade to
                // the fallback quickly rather than adding seconds to a clinician's request.
                .withTimeout(Duration.ofSeconds(2));

        if (password != null && !password.isBlank()) {
            uri.withPassword(password.toCharArray());
        }

        RedisClient client = RedisClient.create(uri.build());

        try {
            RedisRequestRateWindow window = new RedisRequestRateWindow(client, inMemory);
            log.info("AI request-rate window: redis at {}:{} (shared across instances)", host, port);
            return window;
        } catch (Exception e) {
            // Startup must not depend on Redis being up. The window degrades to per-instance and
            // says so; failing the whole application because a rate-limit cache is unreachable
            // would take clinical work down for a soft control.
            client.shutdown();
            log.error("Could not connect to the rate-limit Redis at {}:{}; falling back to the "
                      + "per-instance window. The effective ceiling is now the configured limit "
                      + "times the replica count.", host, port, e);
            return inMemory;
        }
    }
}
