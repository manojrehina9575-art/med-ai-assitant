package com.medai.report.service;

import com.medai.analysis.entity.AnalysisRequest;
import com.medai.analysis.enums.AnalysisStatus;
import com.medai.analysis.repository.AnalysisRequestRepository;
import com.medai.auth.security.UserPrincipal;
import com.medai.common.dto.PagedResponse;
import com.medai.common.exception.BadRequestException;
import com.medai.common.exception.ResourceNotFoundException;
import com.medai.patient.entity.Patient;
import com.medai.patient.repository.PatientRepository;
import com.medai.report.dto.ReportDtos.*;
import com.medai.report.entity.ReportReview;
import com.medai.report.repository.ReportReviewRepository;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Draft → review → sign.
 *
 * <p>The single most valuable thing that was missing. An analysis result was terminal: generated,
 * and then it sat there. Three consequences, all fixed by the same state machine:
 *
 * <ul>
 *   <li>Clinically, nobody owned the output. A report nobody signed is a report nobody acts on.</li>
 *   <li>Legally, "the product drafts, a clinician decides" was a claim in a document. Here it is
 *       enforced by the software: nothing reaches a signed state without a named practitioner.</li>
 *   <li>Commercially, the accept/edit/reject verdict is the training signal. The fine-tuning
 *       pipeline has existed since MVP 8 with nothing feeding it; this is what feeds it.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportSignOffService {

    private static final List<String> OPEN_STATUSES = List.of("DRAFT", "IN_REVIEW");

    private final ReportReviewRepository reviewRepository;
    private final AnalysisRequestRepository analysisRepository;
    private final PatientRepository patientRepository;

    /**
     * Opens a review for a completed analysis, freezing what the model produced.
     *
     * <p>Idempotent: called from the analysis-completion path, so a retry must not create a second
     * review. An abstention gets no review — there is nothing to sign, and putting "the model
     * declined" on a worklist wastes a radiologist's attention.
     */
    @Transactional
    public Optional<ReportReview> openReview(UUID tenantId, UUID analysisId) {
        AnalysisRequest analysis = analysisRepository.findByIdAndTenantId(analysisId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis", "id", analysisId.toString()));

        if (analysis.getStatus() != AnalysisStatus.COMPLETED) {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(analysis.getAbstained())) {
            log.debug("Analysis {} abstained; no review opened.", analysisId);
            return Optional.empty();
        }

        Optional<ReportReview> existing = reviewRepository.findOpenByAnalysis(tenantId, analysisId);
        if (existing.isPresent()) {
            return existing;
        }

        ReportReview review = reviewRepository.save(ReportReview.builder()
                .tenantId(tenantId)
                .analysisId(analysisId)
                .patientId(analysis.getPatientId())
                .status("DRAFT")
                .draftContent(analysis.getResult())
                .build());

        log.info("Review {} opened for analysis {}", review.getId(), analysisId);
        return Optional.of(review);
    }

    /**
     * The reading worklist, oldest first.
     *
     * <p>Oldest rather than newest because the one that has waited longest is the one at risk, and
     * a newest-first list quietly starves the bottom of the queue.
     */
    @Transactional(readOnly = true)
    public PagedResponse<ReviewView> worklist(int page, int size) {
        UUID tenantId = TenantContext.requireTenantId();
        Page<ReportReview> reviews = reviewRepository.findByTenantIdAndStatusInOrderByCreatedAtAsc(
                tenantId, OPEN_STATUSES, PageRequest.of(page, size));
        return toPagedResponse(tenantId, reviews);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReviewView> forPatient(UUID patientId, int page, int size) {
        UUID tenantId = TenantContext.requireTenantId();
        Page<ReportReview> reviews = reviewRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(
                tenantId, patientId, PageRequest.of(page, size));
        return toPagedResponse(tenantId, reviews);
    }

    @Transactional(readOnly = true)
    public ReviewView get(UUID reviewId) {
        UUID tenantId = TenantContext.requireTenantId();
        ReportReview review = reviewRepository.findByIdAndTenantId(reviewId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportReview", "id", reviewId.toString()));
        return toView(review, patientNameOf(tenantId, review.getPatientId()), analysisTypeOf(tenantId, review));
    }

    /**
     * Takes ownership of a draft.
     *
     * <p>Advisory rather than a lock. Two radiologists opening the same study is a workflow problem
     * worth surfacing, not a transaction to serialise — refusing the second one outright would
     * strand a study whenever somebody claimed it and went to lunch.
     */
    @Transactional
    public ReviewView claim(UUID reviewId, UserPrincipal principal) {
        ReportReview review = openReviewOrFail(reviewId, principal.tenantId());

        if (review.getClaimedBy() != null && !review.getClaimedBy().equals(principal.userId())) {
            log.info("Review {} reassigned from {} to {}",
                    reviewId, review.getClaimedBy(), principal.userId());
        }

        review.setClaimedBy(principal.userId());
        review.setClaimedAt(Instant.now());
        review.setStatus("IN_REVIEW");

        return toViewOf(reviewRepository.save(review), principal.tenantId());
    }

    /**
     * Signs, corrects or rejects the draft.
     *
     * <p>This is the moment the product's regulatory position becomes true rather than asserted: a
     * named, licensed practitioner takes responsibility for the content. It is also where the
     * training label is produced, which is why the three actions are distinguished rather than
     * collapsed into "done".
     */
    @Transactional
    public ReviewView sign(UUID reviewId, SignRequest request, UserPrincipal principal) {
        ReportReview review = openReviewOrFail(reviewId, principal.tenantId());

        String action = request.action() == null ? "" : request.action().trim().toUpperCase(Locale.ROOT);

        switch (action) {
            case "ACCEPTED" -> {
                // The draft was right as generated. The strongest positive example there is.
                review.setFinalContent(review.getDraftContent());
                review.setStatus("SIGNED");
            }
            case "EDITED" -> {
                if (request.finalContent() == null || request.finalContent().isBlank()) {
                    throw new BadRequestException(
                            "An edited report needs its corrected content — that correction is the "
                            + "record of what was signed.");
                }
                review.setFinalContent(request.finalContent());
                review.setStatus("SIGNED");
            }
            case "REJECTED" -> {
                if (request.rejectionReason() == null || request.rejectionReason().isBlank()) {
                    throw new BadRequestException(
                            "A rejection needs a reason. Without one the model cannot be improved "
                            + "and the rejection cannot be reviewed.");
                }
                review.setRejectionReason(request.rejectionReason());
                review.setFinalContent(null);
                review.setStatus("REJECTED");
            }
            default -> throw new BadRequestException(
                    "action must be ACCEPTED, EDITED or REJECTED. Received: " + request.action());
        }

        review.setReviewAction(action);
        review.setSignedBy(principal.userId());
        review.setSignedAt(Instant.now());

        ReportReview saved = reviewRepository.save(review);
        log.info("Review {} {} by user {} (analysis {})",
                reviewId, action, principal.userId(), review.getAnalysisId());

        return toViewOf(saved, principal.tenantId());
    }

    /**
     * Supersedes a signed report with a corrected one.
     *
     * <p>A signed report is never edited in place. An amendment is a new review linked to the one
     * it replaces, and both stay readable — which is what a records request or a case review needs:
     * not just the final text but what was signed first and when it changed.
     */
    @Transactional
    public ReviewView amend(UUID signedReviewId, String correctedContent, UserPrincipal principal) {
        if (correctedContent == null || correctedContent.isBlank()) {
            throw new BadRequestException("An amendment needs the corrected report content.");
        }

        ReportReview original = reviewRepository.findByIdAndTenantId(signedReviewId, principal.tenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ReportReview", "id", signedReviewId.toString()));

        if (!"SIGNED".equals(original.getStatus())) {
            throw new BadRequestException(
                    "Only a signed report can be amended; this one is " + original.getStatus()
                    + ". Use the sign endpoint instead.");
        }

        original.setStatus("AMENDED");
        reviewRepository.save(original);

        ReportReview amendment = reviewRepository.save(ReportReview.builder()
                .tenantId(principal.tenantId())
                .analysisId(original.getAnalysisId())
                .patientId(original.getPatientId())
                .status("SIGNED")
                .reviewAction("EDITED")
                .draftContent(original.getFinalContent())
                .finalContent(correctedContent)
                .amendsReviewId(original.getId())
                .signedBy(principal.userId())
                .signedAt(Instant.now())
                .build());

        log.warn("Review {} amended by {}; supersedes {}",
                amendment.getId(), principal.userId(), original.getId());

        return toViewOf(amendment, principal.tenantId());
    }

    /** True once a practitioner has signed for this analysis — what the FHIR facade asks. */
    @Transactional(readOnly = true)
    public Optional<ReportReview> signedReviewFor(UUID tenantId, UUID analysisId) {
        return reviewRepository
                .findByTenantIdAndAnalysisIdAndStatusOrderBySignedAtDesc(tenantId, analysisId, "SIGNED")
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public WorklistSummary summary(long openEscalations) {
        UUID tenantId = TenantContext.requireTenantId();

        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        long signedToday = reviewRepository
                .findSignedForTraining(tenantId, null, PageRequest.of(0, 500)).stream()
                .filter(r -> r.getSignedAt() != null && r.getSignedAt().isAfter(startOfToday))
                .count();

        return new WorklistSummary(
                reviewRepository.countByTenantIdAndStatus(tenantId, "DRAFT"),
                reviewRepository.countByTenantIdAndStatus(tenantId, "IN_REVIEW"),
                signedToday,
                openEscalations,
                reviewRepository.countByTenantIdAndReviewAction(tenantId, "ACCEPTED"),
                reviewRepository.countByTenantIdAndReviewAction(tenantId, "EDITED"),
                reviewRepository.countByTenantIdAndReviewAction(tenantId, "REJECTED"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ReportReview openReviewOrFail(UUID reviewId, UUID tenantId) {
        ReportReview review = reviewRepository.findByIdAndTenantId(reviewId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportReview", "id", reviewId.toString()));

        if (!OPEN_STATUSES.contains(review.getStatus())) {
            throw new BadRequestException(
                    "This report is already " + review.getStatus()
                    + " and cannot be signed again. Amend it instead.");
        }
        return review;
    }

    private PagedResponse<ReviewView> toPagedResponse(UUID tenantId, Page<ReportReview> reviews) {
        // Batch the patient lookup: a worklist page is exactly where an N+1 hides.
        Map<UUID, String> names = patientNames(tenantId, reviews.getContent());
        Map<UUID, String> types = analysisTypes(tenantId, reviews.getContent());

        return PagedResponse.<ReviewView>builder()
                .content(reviews.getContent().stream()
                        .map(r -> toView(r, names.get(r.getPatientId()), types.get(r.getAnalysisId())))
                        .toList())
                .page(reviews.getNumber())
                .size(reviews.getSize())
                .totalElements(reviews.getTotalElements())
                .totalPages(reviews.getTotalPages())
                .last(reviews.isLast())
                .build();
    }

    private ReviewView toViewOf(ReportReview review, UUID tenantId) {
        return toView(review, patientNameOf(tenantId, review.getPatientId()), analysisTypeOf(tenantId, review));
    }

    private Map<UUID, String> patientNames(UUID tenantId, List<ReportReview> reviews) {
        Set<UUID> ids = reviews.stream().map(ReportReview::getPatientId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return patientRepository.findByTenantIdAndIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(Patient::getId, Patient::getFullName));
    }

    private Map<UUID, String> analysisTypes(UUID tenantId, List<ReportReview> reviews) {
        return reviews.stream()
                .map(ReportReview::getAnalysisId)
                .distinct()
                .map(id -> analysisRepository.findByIdAndTenantId(id, tenantId).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(AnalysisRequest::getId,
                        a -> a.getAnalysisType().name(), (a, b) -> a));
    }

    private String patientNameOf(UUID tenantId, UUID patientId) {
        return patientRepository.findByIdAndTenantId(patientId, tenantId)
                .map(Patient::getFullName).orElse(null);
    }

    private String analysisTypeOf(UUID tenantId, ReportReview review) {
        return analysisRepository.findByIdAndTenantId(review.getAnalysisId(), tenantId)
                .map(a -> a.getAnalysisType().name()).orElse(null);
    }

    private ReviewView toView(ReportReview r, String patientName, String analysisType) {
        return new ReviewView(r.getId(), r.getAnalysisId(), r.getPatientId(), patientName, analysisType,
                r.getStatus(), r.getClaimedBy(), r.getClaimedAt(), r.getSignedBy(), r.getSignedAt(),
                r.getReviewAction(), r.getRejectionReason(), r.getDraftContent(), r.getFinalContent(),
                r.getAmendsReviewId(), r.getCreatedAt());
    }
}
