package com.medai.upload.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Rejects an unimplemented {@code app.storage.type} at startup, with a message that says what is
 * actually available.
 *
 * <p>The configuration has always advertised {@code local} or {@code s3}, and {@code .env.example}
 * documented both, but only local storage exists. Choosing {@code s3} used to produce an
 * unsatisfied-dependency stack trace with no hint that the backend was simply never written.
 */
@Configuration
@Slf4j
public class StorageTypeValidator {

    private static final List<String> IMPLEMENTED = List.of("local");

    @Value("${app.storage.type:local}")
    private String storageType;

    @PostConstruct
    void validate() {
        if (!IMPLEMENTED.contains(storageType.toLowerCase())) {
            throw new IllegalStateException(
                    "app.storage.type='" + storageType + "' is not implemented. Supported values: "
                    + IMPLEMENTED + ". Object storage (S3/R2) requires a StorageService implementation "
                    + "that does not exist yet — until then, use 'local' with a mounted volume and "
                    + "back that volume up.");
        }
        log.info("File storage backend: {}", storageType);
    }
}
