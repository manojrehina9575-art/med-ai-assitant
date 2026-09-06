package com.medai.qa.service;

import com.medai.common.exception.ResourceNotFoundException;
import com.medai.finding.extraction.ReportSectionParser;
import com.medai.finding.extraction.ReportSectionText;
import com.medai.finding.model.FindingSourceSection;
import com.medai.finding.service.FindingExtractionService;
import com.medai.qa.engine.QaEngine;
import com.medai.qa.model.QaReportText;
import com.medai.qa.model.QaResult;
import com.medai.report.entity.ReportReview;
import com.medai.report.repository.ReportReviewRepository;
import com.medai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QaService {

    private final ReportReviewRepository reviewRepository;
    private final QaEngine qaEngine;
    private final ReportSectionParser sectionParser;
    private final FindingExtractionService findingExtractionService;
    private final QaEvidenceEnricher evidenceEnricher;

    @Transactional(readOnly = true)
    public QaResult evaluateReport(UUID reviewId) {
        UUID tenantId = TenantContext.requireTenantId();
        ReportReview review = reviewRepository.findByIdAndTenantId(reviewId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportReview", "id", reviewId.toString()));

        QaResult result = qaEngine.evaluate(review.getId(), extractReportText(review));
        return evidenceEnricher.enrich(result, findingExtractionService.extract(review));
    }

    private QaReportText extractReportText(ReportReview review) {
        String source = sourceText(review);
        if (source.isBlank()) {
            return new QaReportText(List.of(), "");
        }

        List<ReportSectionText> sections = sectionParser.parse(source);
        List<String> findings = sections.stream()
                .filter(section -> section.sourceSection() == FindingSourceSection.FINDINGS)
                .map(ReportSectionText::text)
                .filter(this::hasText)
                .toList();
        String impression = sections.stream()
                .filter(section -> section.sourceSection() == FindingSourceSection.IMPRESSION)
                .map(ReportSectionText::text)
                .filter(this::hasText)
                .collect(Collectors.joining("\n"));
        return new QaReportText(findings, impression);
    }

    private String sourceText(ReportReview review) {
        if ("SIGNED".equals(review.getStatus()) && hasText(review.getFinalContent())) {
            return review.getFinalContent();
        }
        if (hasText(review.getDraftContent())) {
            return review.getDraftContent();
        }
        return hasText(review.getFinalContent()) ? review.getFinalContent() : "";
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
