package com.medai.upload.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Locale;

/**
 * Rejects an unimplemented {@code app.storage.type} at startup, and warns loudly about the one
 * supported value that cannot be replicated.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class StorageTypeValidator {

    private static final List<String> IMPLEMENTED = List.of("local", "s3");

    private final StorageProperties properties;

    @PostConstruct
    void validate() {
        String type = properties.getType() == null ? "" : properties.getType().toLowerCase(Locale.ROOT);

        if (!IMPLEMENTED.contains(type)) {
            throw new IllegalStateException(
                    "app.storage.type='" + properties.getType() + "' is not implemented. Supported "
                    + "values: " + IMPLEMENTED + ". Use 's3' for any S3-compatible endpoint "
                    + "(AWS, Cloudflare R2, MinIO) — set app.storage.s3.bucket and, for non-AWS, "
                    + "app.storage.s3.endpoint.");
        }

        if ("local".equals(type)) {
            log.warn("""

                    ============================================================================
                     File storage: local disk at {}

                     This backend is SINGLE-INSTANCE ONLY. Files are written to this process's own
                     filesystem, so with more than one replica an upload handled by one instance
                     is invisible to every other one, and downloads fail intermittently and
                     unpredictably.

                     Safe only if: exactly one instance is running, and the path is a durable,
                     backed-up volume rather than a container's ephemeral layer.

                     For anything replicated, set STORAGE_TYPE=s3.
                    ============================================================================
                    """, properties.getLocalPath());
        }
    }
}
