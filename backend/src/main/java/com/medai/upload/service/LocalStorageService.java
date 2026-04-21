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
            throw new RuntimeException("Could not create upload directory: " + rootPath, e);
        }
    }

    @Override
    public String store(UUID tenantId, UUID patientId, String fileName, MultipartFile file) {
        String relativePath = tenantId.toString() + "/patients/" + patientId.toString() + "/" + fileName;
        Path targetPath = rootPath.resolve(relativePath).normalize();

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
            Path filePath = rootPath.resolve(storagePath).normalize();
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve file: " + storagePath, e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path filePath = rootPath.resolve(storagePath).normalize();
            Files.deleteIfExists(filePath);
            log.info("Deleted file: {}", storagePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + storagePath, e);
        }
    }

    @Override
    public boolean exists(String storagePath) {
        Path filePath = rootPath.resolve(storagePath).normalize();
        return Files.exists(filePath);
    }

    @Override
    public Resource retrieveAsResource(String storagePath) {
        Path filePath = rootPath.resolve(storagePath).normalize();
        if (!Files.exists(filePath)) {
            throw new RuntimeException("File not found: " + storagePath);
        }
        return new FileSystemResource(filePath);
    }
}
