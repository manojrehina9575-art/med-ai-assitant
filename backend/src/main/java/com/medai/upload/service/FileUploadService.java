package com.medai.upload.service;

import com.medai.auth.security.UserPrincipal;
import com.medai.common.dto.PagedResponse;
import com.medai.common.exception.BadRequestException;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.patient.repository.PatientRepository;
import com.medai.tenant.TenantContext;
import com.medai.upload.dto.FileUploadResponse;
import com.medai.upload.entity.MedicalFile;
import com.medai.upload.enums.FileType;
import com.medai.upload.enums.UploadStatus;
import com.medai.upload.repository.MedicalFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final MedicalFileRepository medicalFileRepository;
    private final PatientRepository patientRepository;
    private final StorageService storageService;

    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    @Transactional
    public FileUploadResponse uploadFile(UUID patientId, MultipartFile file,
                                          FileType fileType, String description) {
        UUID tenantId = TenantContext.requireTenantId();
        UserPrincipal principal = getCurrentUser();

        if (!patientRepository.existsById(patientId)) {
            throw new ResourceNotFoundException("Patient", "id", patientId);
        }

        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("File size exceeds 100MB limit");
        }

        String originalFileName = file.getOriginalFilename();
        String extension = getExtension(originalFileName);
        String storedFileName = UUID.randomUUID() + extension;

        String storagePath = storageService.store(tenantId, patientId, storedFileName, file);

        MedicalFile medicalFile = MedicalFile.builder()
                .patientId(patientId)
                .uploadedBy(principal.userId())
                .fileName(storedFileName)
                .originalFileName(originalFileName)
                .fileType(fileType)
                .mimeType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .storagePath(storagePath)
                .description(description)
                .uploadStatus(UploadStatus.UPLOADED)
                .metadata(new HashMap<>())
                .build();
        medicalFile.setTenantId(tenantId);
        medicalFile = medicalFileRepository.save(medicalFile);

        log.info("File uploaded: {} for patient {} (tenant: {})", originalFileName, patientId, tenantId);

        return toResponse(medicalFile);
    }

    @Transactional(readOnly = true)
    public PagedResponse<FileUploadResponse> listFiles(UUID patientId, int page, int size) {
        UUID tenantId = TenantContext.requireTenantId();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<MedicalFile> files = medicalFileRepository.findByTenantIdAndPatientId(tenantId, patientId, pageRequest);

        return PagedResponse.<FileUploadResponse>builder()
                .content(files.getContent().stream().map(this::toResponse).toList())
                .page(files.getNumber())
                .size(files.getSize())
                .totalElements(files.getTotalElements())
                .totalPages(files.getTotalPages())
                .last(files.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public MedicalFile getFile(UUID fileId) {
        UUID tenantId = TenantContext.requireTenantId();
        return medicalFileRepository.findByIdAndTenantId(fileId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("File", "id", fileId));
    }

    @Transactional
    public void deleteFile(UUID fileId) {
        UUID tenantId = TenantContext.requireTenantId();
        MedicalFile file = medicalFileRepository.findByIdAndTenantId(fileId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("File", "id", fileId));
        storageService.delete(file.getStoragePath());
        medicalFileRepository.delete(file);
        log.info("File deleted: {} (tenant: {})", fileId, tenantId);
    }

    private UserPrincipal getCurrentUser() {
        return (UserPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private String getExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        }
        return "";
    }

    private FileUploadResponse toResponse(MedicalFile f) {
        return FileUploadResponse.builder()
                .id(f.getId())
                .tenantId(f.getTenantId())
                .patientId(f.getPatientId())
                .uploadedBy(f.getUploadedBy())
                .fileName(f.getFileName())
                .originalFileName(f.getOriginalFileName())
                .fileType(f.getFileType())
                .mimeType(f.getMimeType())
                .fileSizeBytes(f.getFileSizeBytes())
                .description(f.getDescription())
                .uploadStatus(f.getUploadStatus())
                .metadata(f.getMetadata())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
