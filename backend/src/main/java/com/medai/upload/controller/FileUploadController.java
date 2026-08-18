package com.medai.upload.controller;

import com.medai.common.dto.ApiResponse;
import com.medai.common.dto.PagedResponse;
import com.medai.upload.dto.FileUploadResponse;
import com.medai.upload.entity.MedicalFile;
import com.medai.upload.enums.FileType;
import com.medai.upload.service.FileUploadService;
import com.medai.upload.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients/{patientId}/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final StorageService storageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @PathVariable UUID patientId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") FileType fileType,
            @RequestParam(value = "description", required = false) String description) {
        FileUploadResponse response = fileUploadService.uploadFile(patientId, file, fileType, description);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File uploaded successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<ApiResponse<PagedResponse<FileUploadResponse>>> listFiles(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(fileUploadService.listFiles(patientId, page, size)));
    }

    @GetMapping("/{fileId}/download")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<InputStreamResource> downloadFile(
            @PathVariable UUID patientId,
            @PathVariable UUID fileId) {
        MedicalFile medicalFile = fileUploadService.getFile(patientId, fileId);
        InputStream inputStream = storageService.retrieve(medicalFile.getStoragePath());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + medicalFile.getOriginalFileName() + "\"")
                .contentType(MediaType.parseMediaType(medicalFile.getMimeType()))
                .contentLength(medicalFile.getFileSizeBytes())
                .body(new InputStreamResource(inputStream));
    }

    @GetMapping("/{fileId}/view")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<InputStreamResource> viewFile(
            @PathVariable UUID patientId,
            @PathVariable UUID fileId) {
        MedicalFile medicalFile = fileUploadService.getFile(patientId, fileId);
        InputStream inputStream = storageService.retrieve(medicalFile.getStoragePath());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + medicalFile.getOriginalFileName() + "\"")
                // Patient files are private; a shared cache must never retain them.
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600, no-store")
                .contentType(MediaType.parseMediaType(medicalFile.getMimeType()))
                .contentLength(medicalFile.getFileSizeBytes())
                .body(new InputStreamResource(inputStream));
    }

    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable UUID patientId,
            @PathVariable UUID fileId) {
        fileUploadService.deleteFile(patientId, fileId);
        return ResponseEntity.ok(ApiResponse.success("File deleted", null));
    }
}
