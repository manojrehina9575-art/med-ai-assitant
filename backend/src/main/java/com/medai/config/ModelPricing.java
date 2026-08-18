package com.medai.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-model token pricing, in USD per million tokens.
 *
 * <p>Every analysis service previously computed spend at GPT-4o's list price ($2.50 in / $10.00
 * out) regardless of which model actually ran — currently a Groq-hosted Qwen at a small fraction of
 * that. The per-tenant daily cost cap was therefore enforced against a number with no relationship
 * to the bill.
 *
 * <p>An unlisted model costs zero rather than a guess, and is logged once so the omission is
 * visible instead of quietly under-reporting spend. Add prices under
 * {@code app.ai.pricing.<model-key>}; the key matches on the model id, case-insensitively, by
 * prefix, so {@code gpt-4o} covers {@code gpt-4o-2024-11-20}.
 */
@Configuration
@ConfigurationProperties(prefix = "app.ai.pricing")
@Getter
@Setter
@Slf4j
public class ModelPricing {

    /** model key → price. Populated from configuration; see application.yml for defaults. */
    private Map<String, Price> models = new HashMap<>();

    private final Set<String> warnedModels = ConcurrentHashMap.newKeySet();

    @Getter
    @Setter
    public static class Price {
        /** USD per million input (prompt) tokens. */
        private BigDecimal inputPerMillion = BigDecimal.ZERO;
        /** USD per million output (completion) tokens. */
        private BigDecimal outputPerMillion = BigDecimal.ZERO;
    }

    /**
     * Estimated cost of one call. Returns zero (never null) when either token count is unknown or
     * the model has no configured price.
     */
    public BigDecimal estimate(String modelId, Integer promptTokens, Integer completionTokens) {
        if (modelId == null || promptTokens == null || completionTokens == null) {
            return BigDecimal.ZERO;
        }

        Price price = lookup(modelId);
        if (price == null) {
            if (warnedModels.add(modelId.toLowerCase())) {
                log.warn("No price configured for model '{}' — its spend counts as $0 and will not "
                         + "contribute to the daily cost cap. Add app.ai.pricing entries for it.", modelId);
            }
            return BigDecimal.ZERO;
        }

        BigDecimal million = BigDecimal.valueOf(1_000_000);
        return price.getInputPerMillion().multiply(BigDecimal.valueOf(promptTokens))
                .add(price.getOutputPerMillion().multiply(BigDecimal.valueOf(completionTokens)))
                .divide(million, 6, RoundingMode.HALF_UP);
    }

    private Price lookup(String modelId) {
        String normalized = modelId.toLowerCase();
        Price exact = models.get(normalized);
        if (exact != null) {
            return exact;
        }
        // Longest matching prefix wins, so "gpt-4o-mini" is not served by the "gpt-4o" entry.
        return models.entrySet().stream()
                .filter(e -> normalized.startsWith(e.getKey().toLowerCase()))
                .max((a, b) -> Integer.compare(a.getKey().length(), b.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }
}
