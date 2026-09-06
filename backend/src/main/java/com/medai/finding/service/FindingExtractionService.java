package com.medai.finding.service;

import com.medai.finding.extraction.FindingExtractor;
import com.medai.finding.extraction.ReportSectionParser;
import com.medai.finding.model.StructuredFinding;
import com.medai.report.entity.ReportReview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FindingExtractionService {

    private final ReportSectionParser sectionParser;
    private final FindingExtractor findingExtractor;

    public List<StructuredFinding> extract(String reportText) {
        return sectionParser.parse(reportText).stream()
                .flatMap(section -> findingExtractor.extract(section.sourceSection(), section.text()).stream())
                .toList();
    }

    public List<StructuredFinding> extract(ReportReview review) {
        if (review == null) {
            return List.of();
        }
        return extract(sourceText(review));
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
