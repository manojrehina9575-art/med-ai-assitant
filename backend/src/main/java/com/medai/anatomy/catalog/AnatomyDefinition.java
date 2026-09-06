package com.medai.anatomy.catalog;

import com.medai.anatomy.model.AnatomyStructure;
import com.medai.anatomy.model.AnatomySystem;
import com.medai.finding.model.AnatomicalSide;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Static catalog entry describing one anatomical structure the system can target.
 *
 * @param structureCode       stable machine code for the structure
 * @param system              body system the structure belongs to
 * @param displayLabel        lowercase noun used to compose display names ("humerus")
 * @param paired              true when the structure exists as a left/right pair
 * @param supportedSides      sides that resolve to a viewer key; empty for unpaired structures
 * @param viewerKeyPattern    stable key template, with {@code {side}} for paired structures
 * @param parentStructureCode broader structure this one sits within, or null
 */
public record AnatomyDefinition(
        AnatomyStructure structureCode,
        AnatomySystem system,
        String displayLabel,
        boolean paired,
        Set<AnatomicalSide> supportedSides,
        String viewerKeyPattern,
        AnatomyStructure parentStructureCode
) {
    private static final String SIDE_PLACEHOLDER = "{side}";

    public AnatomyDefinition {
        structureCode = Objects.requireNonNull(structureCode, "structureCode is required");
        system = Objects.requireNonNull(system, "system is required");
        displayLabel = Objects.requireNonNull(displayLabel, "displayLabel is required");
        viewerKeyPattern = Objects.requireNonNull(viewerKeyPattern, "viewerKeyPattern is required");
        supportedSides = supportedSides == null || supportedSides.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(supportedSides));
    }

    /** Paired structure with left/right viewer keys, e.g. {@code skeleton.humerus.{side}}. */
    static AnatomyDefinition paired(
            AnatomyStructure structureCode,
            AnatomySystem system,
            String displayLabel,
            String viewerKeyPattern
    ) {
        return new AnatomyDefinition(
                structureCode,
                system,
                displayLabel,
                true,
                EnumSet.of(AnatomicalSide.RIGHT, AnatomicalSide.LEFT),
                viewerKeyPattern,
                null);
    }

    /** Unpaired/midline structure with a single viewer key, e.g. {@code skeleton.sternum}. */
    static AnatomyDefinition unpaired(
            AnatomyStructure structureCode,
            AnatomySystem system,
            String displayLabel,
            String viewerKey
    ) {
        return new AnatomyDefinition(structureCode, system, displayLabel, false, Set.of(), viewerKey, null);
    }

    /**
     * Resolves the stable viewer key for a side, or empty when no single unambiguous key exists.
     *
     * <p>A paired structure with an unspecified or bilateral side has no single mesh, so no key is
     * invented — picking one side would be a clinical guess.
     */
    public java.util.Optional<String> viewerKey(AnatomicalSide side) {
        if (!paired) {
            return java.util.Optional.of(viewerKeyPattern);
        }
        if (side == null || !supportedSides.contains(side)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                viewerKeyPattern.replace(SIDE_PLACEHOLDER, side.name().toLowerCase(java.util.Locale.ROOT)));
    }
}
