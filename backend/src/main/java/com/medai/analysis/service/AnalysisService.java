package com.medai.analysis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medai.analysis.dto.AnalysisResponse;
import com.medai.analysis.dto.AnalysisResultDto;
import com.medai.analysis.dto.CreateAnalysisRequest;
import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.enums.AnalysisType;
import com.medai.analysis.event.AnalysisRequestEvent;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.auth.security.UserPrincipal;
import com.medai.common.dto.PagedResponse;
import com.medai.common.exception.BadRequestException;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.patient.repository.PatientRepository;
import com.medai.tenant.TenantContext;
import com.medai.upload.repository.MedicalFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final AnalysisRequestRepository analysisRequestRepository;
    private final PatientRepository patientRepository;
    private final MedicalFileRepository medicalFileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public AnalysisResponse requestAnalysis(CreateAnalysisRequest request, UserPrincipal principal) {
        UUID tenantId = principal.tenantId();

        // Validate patient exists and belongs to tenant
        patientRepository.findByIdAndTenantId(request.getPatientId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", request.getPatientId().toString()));

        // Validate medical file exists and belongs to tenant
        medicalFileRepository.findById(request.getMedicalFileId())
                .filter(f -> f.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("MedicalFile", "id", request.getMedicalFileId().toString()));

        // Check if analysis already exists for this file and is pending/processing
        var existing = analysisRequestRepository.findByTenantIdAndMedicalFileId(tenantId, request.getMedicalFileId());
        boolean hasPendingOrProcessing = existing.stream()
                .anyMatch(a -> a.getStatus() == AnalysisStatus.PENDING || a.getStatus() == AnalysisStatus.PROCESSING);
        if (hasPendingOrProcessing) {
            throw new BadRequestException("An analysis is already in progress for this file");
        }

        // Create analysis request
        AnalysisRequest analysisRequest = AnalysisRequest.builder()
                .patientId(request.getPatientId())
                .medicalFileId(request.getMedicalFileId())
                .requestedBy(principal.userId())
                .analysisType(AnalysisType.IMAGE_ANALYSIS)
                .clinicalNotes(request.getClinicalNotes())
                .status(AnalysisStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .build();

        analysisRequest = analysisRequestRepository.save(analysisRequest);

        log.info("Created analysis request {} for patient {} by user {}",
                analysisRequest.getId(), request.getPatientId(), principal.userId());

        // Publish async event
        eventPublisher.publishEvent(new AnalysisRequestEvent(this, analysisRequest.getId(), tenantId));

        return toResponse(analysisRequest);
    }

    public AnalysisResponse getAnalysis(UUID analysisId, UserPrincipal principal) {
        UUID tenantId = principal.tenantId();
        AnalysisRequest request = analysisRequestRepository.findByIdAndTenantId(analysisId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("AnalysisRequest", "id", analysisId.toString()));
        return toResponse(request);
    }

    public PagedResponse<AnalysisResponse> getPatientAnalyses(UUID patientId, int page, int size, UserPrincipal principal) {
        UUID tenantId = principal.tenantId();
        patientRepository.findByIdAndTenantId(patientId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", "id", patientId.toString()));

        Page<AnalysisRequest> analyses = analysisRequestRepository
                .findByTenantIdAndPatientIdOrderByCreatedAtDesc(tenantId, patientId, PageRequest.of(page, size));

        return PagedResponse.<AnalysisResponse>builder()
                .content(analyses.getContent().stream().map(this::toResponse).toList())
                .page(analyses.getNumber())
                .size(analyses.getSize())
                .totalElements(analyses.getTotalElements())
                .totalPages(analyses.getTotalPages())
                .last(analyses.isLast())
                .build();
    }

    public PagedResponse<AnalysisResponse> getAllAnalyses(int page, int size, UserPrincipal principal) {
        UUID tenantId = principal.tenantId();
        Page<AnalysisRequest> analyses = analysisRequestRepository
                .findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(page, size));

        return PagedResponse.<AnalysisResponse>builder()
                .content(analyses.getContent().stream().map(this::toResponse).toList())
                .page(analyses.getNumber())
                .size(analyses.getSize())
                .totalElements(analyses.getTotalElements())
                .totalPages(analyses.getTotalPages())
                .last(analyses.isLast())
                .build();
    }

    @Transactional
    public AnalysisResponse retryAnalysis(UUID analysisId, UserPrincipal principal) {
        UUID tenantId = principal.tenantId();
        AnalysisRequest request = analysisRequestRepository.findByIdAndTenantId(analysisId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("AnalysisRequest", "id", analysisId.toString()));

        if (request.getStatus() != AnalysisStatus.FAILED) {
            throw new BadRequestException("Only failed analyses can be retried");
        }

        request.setStatus(AnalysisStatus.PENDING);
        request.setRetryCount(0);
        request.setErrorMessage(null);
        analysisRequestRepository.save(request);

        eventPublisher.publishEvent(new AnalysisRequestEvent(this, request.getId(), tenantId));

        return toResponse(request);
    }

    private AnalysisResponse toResponse(AnalysisRequest entity) {
        AnalysisResultDto resultDto = null;
        if (entity.getResult() != null) {
            try {
                resultDto = objectMapper.readValue(entity.getResult(), AnalysisResultDto.class);
            } catch (Exception e) {
                log.warn("Failed to parse analysis result JSON for request {}", entity.getId());
            }
        }

        return AnalysisResponse.builder()
                .id(entity.getId())
                .patientId(entity.getPatientId())
                .medicalFileId(entity.getMedicalFileId())
                .requestedBy(entity.getRequestedBy())
                .analysisType(entity.getAnalysisType().name())
                .clinicalNotes(entity.getClinicalNotes())
                .status(entity.getStatus().name())
                .urgency(entity.getUrgency())
                .result(resultDto)
                .errorMessage(entity.getErrorMessage())
                .modelUsed(entity.getModelUsed())
                .promptTokens(entity.getPromptTokens())
                .completionTokens(entity.getCompletionTokens())
                .totalTokens(entity.getTotalTokens())
                .estimatedCost(entity.getEstimatedCost())
                .processingStartedAt(entity.getProcessingStartedAt())
                .processingCompletedAt(entity.getProcessingCompletedAt())
                .retryCount(entity.getRetryCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
