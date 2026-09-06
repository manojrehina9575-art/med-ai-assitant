package com.medai.qa.service;

import com.medai.anatomy.service.AnatomyService;
import com.medai.finding.model.AnatomicalRegion;
import com.medai.finding.model.FindingSourceSection;
import com.medai.finding.model.FindingType;
import com.medai.finding.model.StructuredFinding;
import com.medai.qa.model.LateralitySide;
import com.medai.qa.model.QaEvidence;
import com.medai.qa.model.QaIssue;
import com.medai.qa.model.QaIssueType;
import com.medai.qa.model.QaResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class QaEvidenceEnricher {

    private final AnatomyService anatomyService;

    public QaResult enrich(QaResult result, List<StructuredFinding> structuredFindings) {
        if (result == null || result.issues().isEmpty()) {
            return result;
        }

        List<StructuredFinding> findings = structuredFindings == null ? List.of() : structuredFindings;
        List<QaIssue> enrichedIssues = result.issues().stream()
                .map(issue -> issue.withEvidence(evidenceFor(issue, findings)))
                .toList();
        return new QaResult(
                result.reportId(),
                result.status(),
                enrichedIssues,
                result.issueCount(),
                result.evaluatedAt());
    }

    private List<QaEvidence> evidenceFor(QaIssue issue, List<StructuredFinding> findings) {
        if (issue.type() != QaIssueType.LATERALITY_CONFLICT || findings.isEmpty()) {
            return List.of();
        }

        EvidenceTarget findingTarget = new EvidenceTarget(
                sectionOf(issue.sectionA()),
                issue.findingText(),
                issue.sideA(),
                issue.anatomyCode(),
                issue.region());
        EvidenceTarget impressionTarget = new EvidenceTarget(
                sectionOf(issue.sectionB()),
                issue.impressionText(),
                issue.sideB(),
                issue.anatomyCode(),
                issue.region());

        List<StructuredFinding> findingCandidates = candidatesFor(findingTarget, findings);
        List<StructuredFinding> impressionCandidates = candidatesFor(impressionTarget, findings);
        Optional<FindingType> sharedType = uniqueSharedType(findingCandidates, impressionCandidates);
        if (!findingCandidates.isEmpty() && !impressionCandidates.isEmpty() && sharedType.isEmpty()) {
            return List.of();
        }

        if (sharedType.isPresent()) {
            findingCandidates = filterByType(findingCandidates, sharedType.get());
            impressionCandidates = filterByType(impressionCandidates, sharedType.get());
        }

        List<QaEvidence> evidence = new ArrayList<>(2);
        uniqueMatch(findingCandidates, findingTarget).map(this::toEvidence).ifPresent(evidence::add);
        uniqueMatch(impressionCandidates, impressionTarget).map(this::toEvidence).ifPresent(evidence::add);
        return evidence;
    }

    private List<StructuredFinding> candidatesFor(EvidenceTarget target, List<StructuredFinding> findings) {
        if (target.sourceText() == null || target.sourceText().isBlank()
                || target.sourceSection() == FindingSourceSection.UNKNOWN) {
            return List.of();
        }

        String normalizedText = normalize(target.sourceText());
        String anatomyCode = normalizeCode(target.anatomyCode());
        return findings.stream()
                .filter(finding -> finding.sourceSection() == target.sourceSection())
                .filter(finding -> normalize(finding.sourceText()).equals(normalizedText))
                .filter(finding -> matchesSide(finding, target.side()))
                .filter(finding -> matchesAnatomy(finding, anatomyCode))
                .toList();
    }

    private Optional<StructuredFinding> uniqueMatch(List<StructuredFinding> candidates, EvidenceTarget target) {
        List<StructuredFinding> regionPreferred = preferRegion(candidates, target.region());
        return regionPreferred.size() == 1 ? Optional.of(regionPreferred.getFirst()) : Optional.empty();
    }

    private List<StructuredFinding> preferRegion(List<StructuredFinding> candidates, String region) {
        String normalizedRegion = normalizeCode(region);
        if (normalizedRegion == null) {
            return candidates;
        }

        List<StructuredFinding> exact = candidates.stream()
                .filter(finding -> finding.region() != AnatomicalRegion.UNSPECIFIED)
                .filter(finding -> finding.region().name().equals(normalizedRegion))
                .toList();
        return exact.isEmpty() ? candidates : exact;
    }

    private Optional<FindingType> uniqueSharedType(
            List<StructuredFinding> findingCandidates,
            List<StructuredFinding> impressionCandidates
    ) {
        if (findingCandidates.isEmpty() || impressionCandidates.isEmpty()) {
            return Optional.empty();
        }

        Set<FindingType> types = new LinkedHashSet<>();
        findingCandidates.stream().map(StructuredFinding::findingType).forEach(types::add);
        Set<FindingType> impressionTypes = new LinkedHashSet<>();
        impressionCandidates.stream().map(StructuredFinding::findingType).forEach(impressionTypes::add);
        types.retainAll(impressionTypes);
        return types.size() == 1 ? Optional.of(types.iterator().next()) : Optional.empty();
    }

    private List<StructuredFinding> filterByType(List<StructuredFinding> candidates, FindingType type) {
        return candidates.stream()
                .filter(finding -> finding.findingType() == type)
                .toList();
    }

    private boolean matchesSide(StructuredFinding finding, LateralitySide side) {
        return side == null || finding.side().name().equals(side.name());
    }

    private boolean matchesAnatomy(StructuredFinding finding, String anatomyCode) {
        return anatomyCode == null || (finding.anatomy() != null && finding.anatomy().name().equals(anatomyCode));
    }

    private QaEvidence toEvidence(StructuredFinding finding) {
        return new QaEvidence(
                finding.sourceSection(),
                finding.findingType(),
                finding.anatomy(),
                finding.anatomyText(),
                finding.side(),
                finding.region(),
                finding.status(),
                finding.certainty(),
                finding.sourceText(),
                anatomyService.targetFor(finding).orElse(null));
    }

    private FindingSourceSection sectionOf(String section) {
        String normalized = normalizeCode(section);
        if (normalized == null) {
            return FindingSourceSection.UNKNOWN;
        }
        try {
            return FindingSourceSection.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return FindingSourceSection.UNKNOWN;
        }
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip().toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private record EvidenceTarget(
            FindingSourceSection sourceSection,
            String sourceText,
            LateralitySide side,
            String anatomyCode,
            String region
    ) {
    }
}
