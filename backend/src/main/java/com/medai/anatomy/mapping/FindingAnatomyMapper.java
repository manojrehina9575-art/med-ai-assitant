package com.medai.anatomy.mapping;

import com.medai.anatomy.model.AnatomyTarget;
import com.medai.finding.model.StructuredFinding;

import java.util.Optional;

/**
 * Maps an extracted {@link StructuredFinding} onto a stable {@link AnatomyTarget}.
 *
 * <p>Implementations must be deterministic and must never infer laterality that the finding does
 * not state. An unsupported or absent anatomy concept returns an empty result rather than a
 * best guess.
 */
public interface FindingAnatomyMapper {

    Optional<AnatomyTarget> map(StructuredFinding finding);
}
