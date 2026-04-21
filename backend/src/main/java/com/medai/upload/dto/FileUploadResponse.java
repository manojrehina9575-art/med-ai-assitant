package com.medai.upload.dto;

import com.medai.upload.enums.FileType;
import com.medai.upload.enums.UploadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    private UUID id;
    private UUID tenantId;
    private UUID patientId;
    private UUID uploadedBy;
    private String fileName;
    private String originalFileName;
    private FileType fileType;
    private String mimeType;
    private Long fileSizeBytes;
    private String description;
    private UploadStatus uploadStatus;
    private Map<String, Object> metadata;
    private Instant createdAt;
}
