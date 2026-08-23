package com.medai.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.medai.analytics.AiMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Intelligent In-Memory / Semantic Cache for identical AI inference prompts.
 * Uses SHA-256 key hashing with Tenant-isolation to prevent cross-tenant data leakage.
 */
@Service
@Slf4j
public class AiResponseCacheService {

    private final Cache<String, String> cache;
    private final AiMetricsService metricsService;

    public AiResponseCacheService(AiMetricsService metricsService) {
        this.metricsService = metricsService;
        this.cache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(4, TimeUnit.HOURS)
                .recordStats()
                .build();
    }

    public Optional<String> get(UUID tenantId, String model, String prompt) {
        String key = generateCacheKey(tenantId, model, prompt);
        String cached = cache.getIfPresent(key);
        boolean hit = (cached != null);
        metricsService.recordCacheAccess(hit);
        if (hit) {
            log.debug("AI Response Cache HIT for model {}", model);
        }
        return Optional.ofNullable(cached);
    }

    public void put(UUID tenantId, String model, String prompt, String response) {
        if (response == null || response.isBlank()) return;
        String key = generateCacheKey(tenantId, model, prompt);
        cache.put(key, response);
    }

    public long getCacheSize() {
        return cache.estimatedSize();
    }

    public double getHitRate() {
        return cache.stats().hitRate();
    }

    public void clear() {
        cache.invalidateAll();
    }

    private String generateCacheKey(UUID tenantId, String model, String prompt) {
        try {
            String combined = (tenantId != null ? tenantId.toString() : "global") + ":" + model + ":" + prompt;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
