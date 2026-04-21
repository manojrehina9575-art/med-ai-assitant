package com.medai.upload.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

public interface StorageService {

    String store(UUID tenantId, UUID patientId, String fileName, MultipartFile file);

    InputStream retrieve(String storagePath);

    void delete(String storagePath);

    boolean exists(String storagePath);

    Resource retrieveAsResource(String storagePath);
}
