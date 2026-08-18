package com.medai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Background execution pools.
 *
 * <p>{@code @EnableScheduling} is what makes {@link com.medai.analysis.job.AnalysisJobReaper} run.
 * Note that {@code @Retryable} is deliberately not used anywhere: it requires {@code @EnableRetry}
 * to create a proxy, and relying on an annotation whose absence is silent is how analysis retries
 * appeared to be implemented while never once executing. Retries live in the reaper, where their
 * state is visible in the database.
 */
@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AsyncConfig {

    /**
     * Bounded pool for AI analysis.
     *
     * <p>Without this, {@code @Async} combined with {@code spring.threads.virtual.enabled=true}
     * gives every request its own virtual thread and no ceiling at all: fifty uploads become fifty
     * simultaneous model calls, which trips the provider's per-minute token limit and, on a paid
     * provider, bills for all fifty. Each in-flight call also holds a base64 image in heap, so
     * unbounded fan-out is an out-of-memory kill waiting for a busy afternoon.
     *
     * <p>{@code CallerRunsPolicy} is the deliberate choice for saturation: when the queue is full
     * the submitting thread performs the work itself, which slows intake instead of discarding a
     * clinician's request.
     */
    @Bean("analysisExecutor")
    public ThreadPoolTaskExecutor analysisExecutor(
            @org.springframework.beans.factory.annotation.Value("${app.analysis.executor.core-size:4}") int coreSize,
            @org.springframework.beans.factory.annotation.Value("${app.analysis.executor.max-size:8}") int maxSize,
            @org.springframework.beans.factory.annotation.Value("${app.analysis.executor.queue-capacity:50}") int queueCapacity) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ai-analysis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("AI analysis executor: core={}, max={}, queue={}", coreSize, maxSize, queueCapacity);
        return executor;
    }

    /**
     * Separate small pool for audit writes, so a burst of analysis work can never starve the audit
     * trail — and a slow audit write can never occupy an analysis thread.
     */
    @Bean("auditExecutor")
    public ThreadPoolTaskExecutor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-");
        // Audit entries must not be dropped under load.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
