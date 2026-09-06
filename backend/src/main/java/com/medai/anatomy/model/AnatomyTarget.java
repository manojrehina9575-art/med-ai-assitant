package com.medai.anatomy.model;

import com.medai.finding.model.AnatomicalRegion;
import com.medai.finding.model.AnatomicalSide;
import com.medai.finding.model.AnatomicalStructure;

/**
 * A resolved anatomical target for a finding: what structure the finding refers to, expressed with
 * stable machine-readable identifiers rather than user-facing text.
 *
 * <p>{@code displayName} is the only field intended for display. {@code viewerKey} is the stable
 * identifier a future viewer resolves to a mesh; it is {@code null} whenever no single unambiguous
 * mesh can be named (unknown structure, or a paired structure whose side is unspecified or
 * bilateral). {@code region} deliberately does not participate in the viewer key: the viewer
 * selects the structure mesh and then uses the region as focus metadata.
 */
public record AnatomyTarget(
        AnatomySystem system,
        AnatomyStructure structureCode,
        String displayName,
        AnatomicalSide side,
        AnatomicalRegion region,
        String viewerKey,
        AnatomyStructure parentStructureCode,
        AnatomicalStructure sourceAnatomy
) {
    public boolean hasViewerKey() {
        return viewerKey != null && !viewerKey.isBlank();
    }
}
