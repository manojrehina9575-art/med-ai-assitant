package com.medai.upload.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Filesystem-backed storage.
 *
 * <p>Registered for any {@code app.storage.type} other than an explicitly supported remote backend.
 * Previously this bean was conditional on {@code local} and there was no other implementation, so
 * setting {@code STORAGE_TYPE=s3} — as the README suggested for production — left the context with
 * no {@code StorageService} at all and the application failed to start with a missing-bean error.
 * {@link StorageTypeValidator} now rejects unsupported values at startup with an explanation.
 */
@Service
@Slf4j
public class LocalStorageService implements StorageService {

    private final Path rootPath;

    public LocalStorageService(@Value("${app.storage.local-path}") String localPath) {
        this.rootPath = Paths.get(localPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + rootPath, e);
        }
    }

    /**
     * Resolves a storage path and asserts the result is still inside the storage root.
     *
     * <p>Paths come from the database rather than user input today, so this is not currently
     * exploitable — but {@code resolve().normalize()} on its own will happily hand back
     * {@code /etc/passwd} for a stored path of {@code ../../etc/passwd}, and one bad migration or
     * one future feature that accepts a client-supplied path is all it would take.
     */
    private Path resolveWithinRoot(String storagePath) {
        Path resolved = rootPath.resolve(storagePath).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new SecurityException("Storage path escapes the storage root: " + storagePath);
        }
        return resolved;
    }

    @Override
    public String store(UUID tenantId, UUID patientId, String fileName, MultipartFile file) {
        String relativePath = tenantId.toString() + "/patients/" + patientId.toString() + "/" + fileName;
        Path targetPath = resolveWithinRoot(relativePath);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored file: {}", relativePath);
            return relativePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + fileName, e);
        }
    }

    @Override
    public InputStream retrieve(String storagePath) {
        try {
            Path filePath = resolveWithinRoot(storagePath);
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve file: " + storagePath, e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path filePath = resolveWithinRoot(storagePath);
            Files.deleteIfExists(filePath);
            log.info("Deleted file: {}", storagePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + storagePath, e);
        }
    }

    @Override
    public boolean exists(String storagePath) {
        Path filePath = resolveWithinRoot(storagePath);
        return Files.exists(filePath);
    }

    @Override
    public Resource retrieveAsResource(String storagePath) {
        Path filePath = resolveWithinRoot(storagePath);
        if (!Files.exists(filePath)) {
            throw new RuntimeException("File not found: " + storagePath);
        }
        return new FileSystemResource(filePath);
    }
}
