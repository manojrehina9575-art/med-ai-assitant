package com.medai.anatomy.model;

/**
 * Stable machine-readable structure codes used by the anatomy layer.
 *
 * <p>Kept separate from {@link com.medai.finding.model.AnatomicalStructure} on purpose: that enum
 * is the vocabulary the report extractor can recognise in text, while this one is the vocabulary
 * the anatomy catalog (and later the 3D viewer) addresses. They overlap today but are free to
 * diverge — the catalog can describe a structure no extractor recognises yet, and the extractor can
 * recognise a concept the catalog has no target for.
 */
public enum AnatomyStructure {
    HUMERUS,
    FEMUR,
    BRAIN,
    SHOULDER,
    KNEE,
    ANKLE,
    KIDNEY,
    LUNG,
    PLEURA
}
