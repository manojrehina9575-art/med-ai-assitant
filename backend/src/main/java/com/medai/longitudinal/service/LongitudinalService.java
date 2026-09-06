package com.medai.longitudinal.service;

import com.medai.common.exception.BadRequestException;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.finding.model.StructuredFinding;
import com.medai.finding.service.FindingExtractionService;
import com.medai.longitudinal.comparison.FindingComparator;
import com.medai.longitudinal.model.LongitudinalResult;
import com.medai.report.entity.ReportReview;
import com.medai.report.repository.ReportReviewRepository;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LongitudinalService {

    private final ReportReviewRepository reviewRepository;
    private final FindingExtractionService findingExtractionService;
    private final FindingComparator findingComparator;
    private final LongitudinalAnatomyEnricher anatomyEnricher;

    @Transactional(readOnly = true)
    public LongitudinalResult compare(UUID currentReviewId, UUID priorReviewId) {
        UUID tenantId = TenantContext.requireTenantId();
        if (currentReviewId.equals(priorReviewId)) {
            throw new BadRequestException("Current and prior reports must be different reviews.");
        }

        ReportReview current = reviewRepository.findByIdAndTenantId(currentReviewId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportReview", "id", currentReviewId.toString()));
        ReportReview prior = reviewRepository.findByIdAndTenantId(priorReviewId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportReview", "id", priorReviewId.toString()));

        validatePair(current, prior);

        List<StructuredFinding> priorFindings = findingExtractionService.extract(prior);
        List<StructuredFinding> currentFindings = findingExtractionService.extract(current);
        LongitudinalResult result = findingComparator.compare(
                current.getId(), prior.getId(), priorFindings, currentFindings);
        return anatomyEnricher.enrich(result);
    }

    private void validatePair(ReportReview current, ReportReview prior) {
        if (!current.getPatientId().equals(prior.getPatientId())) {
            throw new BadRequestException("Current and prior reports must belong to the same patient.");
        }
        if (!"SIGNED".equals(prior.getStatus()) || !hasText(prior.getFinalContent())) {
            throw new BadRequestException("Prior report must be SIGNED with final content.");
        }

        Instant currentTime = reportTime(current);
        Instant priorTime = reportTime(prior);
        if (currentTime == null || priorTime == null || !priorTime.isBefore(currentTime)) {
            throw new BadRequestException("Prior report must be earlier than the current report.");
        }
    }

    private Instant reportTime(ReportReview review) {
        if ("SIGNED".equals(review.getStatus()) && review.getSignedAt() != null) {
            return review.getSignedAt();
        }
        return review.getCreatedAt();
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
