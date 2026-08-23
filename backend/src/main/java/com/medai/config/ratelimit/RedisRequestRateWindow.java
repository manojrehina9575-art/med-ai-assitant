package com.medai.config.ratelimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Shared request window, so every replica counts against the same ceiling.
 *
 * <p>The key carries the epoch minute, which makes expiry a property of the key rather than
 * something that has to be swept: the window rolls when the minute does, and Redis reclaims the
 * old key on its own.
 */
@Slf4j
public class RedisRequestRateWindow implements RequestRateWindow {

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RequestRateWindow fallback;

    public RedisRequestRateWindow(RedisClient client, RequestRateWindow fallback) {
        this.client = client;
        this.fallback = fallback;
        // Connecting here rather than lazily means a bad host is a startup log line rather than a
        // surprise on the first clinical request.
        this.connection = client.connect();
    }

    @Override
    public int incrementAndCount(UUID tenantId) {
        long minute = Instant.now().truncatedTo(ChronoUnit.MINUTES).getEpochSecond();
        String key = "medai:ratelimit:rpm:" + tenantId + ":" + minute;

        try {
            RedisCommands<String, String> commands = connection.sync();
            Long count = commands.incr(key);

            // Only the first increment needs the TTL. Setting it on every call would slide the
            // window forward on a busy tenant and never let it roll. Two minutes rather than one,
            // so a counter written at :59.9 is still readable at :00.1.
            if (count != null && count == 1L) {
                commands.expire(key, 120);
            }

            return count == null ? 1 : count.intValue();
        } catch (Exception e) {
            // A Redis outage must not stop clinical work. Falling back to the per-instance window
            // keeps a ceiling in place — a loose one — rather than either failing every request or
            // removing the limit entirely.
            log.error("Redis rate-limit window unavailable; using the per-instance window for this "
                      + "request: {}", e.getMessage());
            return fallback.incrementAndCount(tenantId);
        }
    }

    @Override
    public String describe() {
        return "redis (shared across instances)";
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown();
    }
}
