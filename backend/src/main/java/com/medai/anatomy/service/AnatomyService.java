package com.medai.anatomy.service;

import com.medai.anatomy.catalog.AnatomyCatalog;
import com.medai.anatomy.catalog.AnatomyDefinition;
import com.medai.anatomy.mapping.FindingAnatomyMapper;
import com.medai.anatomy.model.AnatomyStructure;
import com.medai.anatomy.model.AnatomyTarget;
import com.medai.finding.model.AnatomicalSide;
import com.medai.finding.model.StructuredFinding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Single entry point for anatomy resolution.
 *
 * <p>QA enrichment, longitudinal comparison and (later) the anatomy viewer all resolve targets
 * through here so they share one deterministic mapping.
 */
@Service
@RequiredArgsConstructor
public class AnatomyService {

    private final FindingAnatomyMapper findingAnatomyMapper;
    private final AnatomyCatalog catalog;

    /** Resolves the anatomical target for a finding, or empty when none can be resolved safely. */
    public Optional<AnatomyTarget> targetFor(StructuredFinding finding) {
        return findingAnatomyMapper.map(finding);
    }

    /** Targets for a list of findings, skipping findings with no resolvable anatomy. */
    public List<AnatomyTarget> targetsFor(List<StructuredFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return List.of();
        }
        return findings.stream()
                .map(this::targetFor)
                .flatMap(Optional::stream)
                .toList();
    }

    /** The supported structures, for callers that need to enumerate what can be targeted. */
    public List<AnatomyDefinition> catalogDefinitions() {
        return catalog.definitions();
    }

    /**
     * Stable viewer key for a structure and side. Lets a caller holding a bilateral target resolve
     * the two side-specific keys without re-deriving the naming scheme.
     */
    public Optional<String> viewerKey(AnatomyStructure structureCode, AnatomicalSide side) {
        return catalog.viewerKey(structureCode, side);
    }
}
