package com.medai.knowledge.controller;

import com.medai.auth.security.UserPrincipal;
import com.medai.common.dto.ApiResponse;
import com.medai.common.dto.PagedResponse;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.config.RateLimitService;
import com.medai.knowledge.dto.KnowledgeDocumentResponse;
import com.medai.knowledge.dto.RagQueryRequest;
import com.medai.knowledge.dto.RagResponse;
import com.medai.knowledge.entity.DocumentType;
import com.medai.knowledge.entity.KnowledgeDocument;
import com.medai.knowledge.repository.KnowledgeDocumentRepository;
import com.medai.knowledge.service.DocumentIngestionService;
import com.medai.knowledge.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final DocumentIngestionService ingestionService;
    private final RagService ragService;
    private final KnowledgeDocumentRepository documentRepository;
    private final RateLimitService rateLimitService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "documentType", defaultValue = "GUIDELINE") DocumentType documentType,
            @RequestParam(value = "source", required = false) String source,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        rateLimitService.checkRateLimit(principal.tenantId());
        KnowledgeDocument doc = ingestionService.ingestDocument(file, title, documentType, source, principal);
        return ResponseEntity.ok(ApiResponse.success("Document uploaded and indexed", toResponse(doc)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<ApiResponse<PagedResponse<KnowledgeDocumentResponse>>> listDocuments(
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<KnowledgeDocument> docPage;

        if (documentType != null) {
            docPage = documentRepository.findByTenantIdAndDocumentTypeOrderByCreatedAtDesc(
                    principal.tenantId(), documentType, pageable);
        } else {
            docPage = documentRepository.findByTenantIdOrderByCreatedAtDesc(principal.tenantId(), pageable);
        }

        Page<KnowledgeDocumentResponse> responsePage = docPage.map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(responsePage)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<ApiResponse<KnowledgeDocumentResponse>> getDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        KnowledgeDocument doc = documentRepository.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge Document not found"));
        return ResponseEntity.ok(ApiResponse.success(toResponse(doc)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOSPITAL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        KnowledgeDocument doc = documentRepository.findByIdAndTenantId(id, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge Document not found"));
        documentRepository.delete(doc);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }

    @PostMapping("/query")
    @PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'DOCTOR', 'LAB_TECH')")
    public ResponseEntity<ApiResponse<RagResponse>> queryKnowledgeBase(
            @Valid @RequestBody RagQueryRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        rateLimitService.checkRateLimit(principal.tenantId());
        RagResponse response = ragService.queryKnowledgeBase(request, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private KnowledgeDocumentResponse toResponse(KnowledgeDocument doc) {
        return KnowledgeDocumentResponse.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .documentType(doc.getDocumentType())
                .source(doc.getSource())
                .fileName(doc.getFileName())
                .fileSizeBytes(doc.getFileSizeBytes())
                .totalChunks(doc.getTotalChunks())
                .status(doc.getStatus())
                .errorMessage(doc.getErrorMessage())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
