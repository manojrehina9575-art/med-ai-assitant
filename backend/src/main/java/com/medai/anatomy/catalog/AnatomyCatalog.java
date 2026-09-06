package com.medai.anatomy.catalog;

import com.medai.anatomy.model.AnatomyStructure;
import com.medai.anatomy.model.AnatomySystem;
import com.medai.finding.model.AnatomicalSide;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic, code-defined catalog of the anatomical structures this phase supports.
 *
 * <p>No database table and no terminology import: the catalog is static so that the same finding
 * always resolves to the same target and viewer key across environments. Skeletal structures are
 * the V1 focus; the non-skeletal entries carry system metadata only and are namespaced under their
 * own system so they can never be mistaken for skeleton meshes.
 */
@Component
public class AnatomyCatalog {

    private static final Map<AnatomyStructure, AnatomyDefinition> DEFINITIONS = buildDefinitions();

    private static Map<AnatomyStructure, AnatomyDefinition> buildDefinitions() {
        Map<AnatomyStructure, AnatomyDefinition> definitions = new EnumMap<>(AnatomyStructure.class);

        // Skeletal — the V1 priority.
        put(definitions, AnatomyDefinition.paired(
                AnatomyStructure.HUMERUS, AnatomySystem.SKELETAL, "humerus", "skeleton.humerus.{side}"));
        put(definitions, AnatomyDefinition.paired(
                AnatomyStructure.FEMUR, AnatomySystem.SKELETAL, "femur", "skeleton.femur.{side}"));
        put(definitions, AnatomyDefinition.paired(
                AnatomyStructure.SHOULDER, AnatomySystem.SKELETAL, "shoulder", "skeleton.shoulder.{side}"));
        put(definitions, AnatomyDefinition.paired(
                AnatomyStructure.KNEE, AnatomySystem.SKELETAL, "knee", "skeleton.knee.{side}"));
        put(definitions, AnatomyDefinition.paired(
                AnatomyStructure.ANKLE, AnatomySystem.SKELETAL, "ankle", "skeleton.ankle.{side}"));

        // Non-skeletal structures. Keys stay in their own system namespace.
        put(definitions, AnatomyDefinition.unpaired(
                AnatomyStructure.BRAIN, AnatomySystem.NERVOUS, "brain", "nervous.brain"));
        put(definitions, AnatomyDefinition.paired(
                AnatomyStructure.LUNG, AnatomySystem.RESPIRATORY, "lung", "respiratory.lung.{side}"));
        put(definitions, AnatomyDefinition.paired(
                AnatomyStructure.PLEURA, AnatomySystem.RESPIRATORY, "pleura", "respiratory.pleura.{side}"));
        put(definitions, AnatomyDefinition.paired(
                AnatomyStructure.KIDNEY, AnatomySystem.URINARY, "kidney", "urinary.kidney.{side}"));

        return Map.copyOf(definitions);
    }

    private static void put(Map<AnatomyStructure, AnatomyDefinition> target, AnatomyDefinition definition) {
        target.put(definition.structureCode(), definition);
    }

    public Optional<AnatomyDefinition> find(AnatomyStructure structureCode) {
        return structureCode == null ? Optional.empty() : Optional.ofNullable(DEFINITIONS.get(structureCode));
    }

    /** All catalog entries in stable declaration (enum) order. */
    public List<AnatomyDefinition> definitions() {
        return java.util.Arrays.stream(AnatomyStructure.values())
                .map(DEFINITIONS::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Resolves the stable viewer key for a structure and side without going through a finding.
     * Useful for a future viewer that needs to expand a bilateral target into its two meshes.
     */
    public Optional<String> viewerKey(AnatomyStructure structureCode, AnatomicalSide side) {
        return find(structureCode).flatMap(definition -> definition.viewerKey(side));
    }
}
