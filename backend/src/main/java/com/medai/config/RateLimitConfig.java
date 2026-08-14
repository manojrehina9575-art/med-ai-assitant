package com.medai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for per-tenant AI rate limiting.
 * Prevents a single tenant from exhausting the shared OpenAI budget.
 */
@Configuration
@ConfigurationProperties(prefix = "app.ai.rate-limit")
@Getter
@Setter
public class RateLimitConfig {

    /**
     * Maximum number of AI analysis requests per tenant per minute.
     */
    private int maxRequestsPerMinute = 10;

    /**
     * Maximum estimated cost in USD per tenant per day.
     */
    private double maxCostPerDayUsd = 50.0;
}
