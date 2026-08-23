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
 * Filesystem-backed storage. Single-instance only.
 *
 * <p>Writes to the local disk of whichever process is serving the request, so it is correct for
 * exactly one instance and silently wrong for more than one: the upload lands on one pod and the
 * download is routed to another, which has never seen the file. {@link StorageTypeValidator} says
 * this loudly at startup, and {@link S3StorageService} is the answer for anything replicated.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalStorageService implements StorageService {

    private final Path rootPath;

    public LocalStorageService(@Value("${app.storage.local-path}") String localPath) {
        this.rootPath = Paths.get(localPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException e) {
            throw new StorageException("Could not create upload directory: " + rootPath, e);
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
            throw new StorageException("Failed to store file: " + fileName, e);
        }
    }

    @Override
    public InputStream retrieve(String storagePath) {
        try {
            Path filePath = resolveWithinRoot(storagePath);
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new StorageException("Failed to retrieve file: " + storagePath, e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path filePath = resolveWithinRoot(storagePath);
            Files.deleteIfExists(filePath);
            log.info("Deleted file: {}", storagePath);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file: " + storagePath, e);
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
            throw new StorageException("File not found: " + storagePath);
        }
        return new FileSystemResource(filePath);
    }
}
