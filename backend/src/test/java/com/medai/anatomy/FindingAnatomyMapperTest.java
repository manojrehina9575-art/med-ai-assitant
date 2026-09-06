package com.medai.anatomy;

import com.medai.anatomy.catalog.AnatomyCatalog;
import com.medai.anatomy.catalog.AnatomyDefinition;
import com.medai.anatomy.mapping.DeterministicFindingAnatomyMapper;
import com.medai.anatomy.model.AnatomyStructure;
import com.medai.anatomy.model.AnatomySystem;
import com.medai.anatomy.model.AnatomyTarget;
import com.medai.anatomy.service.AnatomyService;
import com.medai.finding.model.AnatomicalRegion;
import com.medai.finding.model.AnatomicalSide;
import com.medai.finding.model.AnatomicalStructure;
import com.medai.finding.model.FindingCertainty;
import com.medai.finding.model.FindingSourceSection;
import com.medai.finding.model.FindingStatus;
import com.medai.finding.model.FindingType;
import com.medai.finding.model.StructuredFinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FindingAnatomyMapperTest {

    private final AnatomyCatalog catalog = new AnatomyCatalog();
    private final DeterministicFindingAnatomyMapper mapper = new DeterministicFindingAnatomyMapper(catalog);
    private final AnatomyService service = new AnatomyService(mapper, catalog);

    @Test
    @DisplayName("maps a right proximal humerus fracture to the right humerus skeletal target")
    void mapsRightProximalHumerus() {
        AnatomyTarget target = require(finding(
                FindingType.FRACTURE,
                AnatomicalStructure.HUMERUS,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.PROXIMAL));

        assertThat(target.system()).isEqualTo(AnatomySystem.SKELETAL);
        assertThat(target.structureCode()).isEqualTo(AnatomyStructure.HUMERUS);
        assertThat(target.side()).isEqualTo(AnatomicalSide.RIGHT);
        assertThat(target.region()).isEqualTo(AnatomicalRegion.PROXIMAL);
        assertThat(target.viewerKey()).isEqualTo("skeleton.humerus.right");
        assertThat(target.displayName()).isEqualTo("Right proximal humerus");
        assertThat(target.sourceAnatomy()).isEqualTo(AnatomicalStructure.HUMERUS);
    }

    @Test
    @DisplayName("maps a left distal humerus finding, keeping region out of the viewer key")
    void mapsLeftDistalHumerus() {
        AnatomyTarget target = require(finding(
                FindingType.FRACTURE,
                AnatomicalStructure.HUMERUS,
                AnatomicalSide.LEFT,
                AnatomicalRegion.DISTAL));

        assertThat(target.viewerKey()).isEqualTo("skeleton.humerus.left");
        assertThat(target.region()).isEqualTo(AnatomicalRegion.DISTAL);
        assertThat(target.displayName()).isEqualTo("Left distal humerus");
        assertThat(target.viewerKey()).doesNotContain("distal");
    }

    @Test
    @DisplayName("maps a right femur finding")
    void mapsRightFemur() {
        AnatomyTarget target = require(finding(
                FindingType.FRACTURE,
                AnatomicalStructure.FEMUR,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.UNSPECIFIED));

        assertThat(target.system()).isEqualTo(AnatomySystem.SKELETAL);
        assertThat(target.structureCode()).isEqualTo(AnatomyStructure.FEMUR);
        assertThat(target.viewerKey()).isEqualTo("skeleton.femur.right");
        assertThat(target.displayName()).isEqualTo("Right femur");
    }

    @Test
    @DisplayName("keeps a left shoulder finding as SHOULDER instead of narrowing it to a bone")
    void mapsLeftShoulderWithoutNarrowing() {
        AnatomyTarget target = require(finding(
                FindingType.DISLOCATION,
                AnatomicalStructure.SHOULDER,
                AnatomicalSide.LEFT,
                AnatomicalRegion.UNSPECIFIED));

        assertThat(target.structureCode()).isEqualTo(AnatomyStructure.SHOULDER);
        assertThat(target.structureCode()).isNotEqualTo(AnatomyStructure.HUMERUS);
        assertThat(target.viewerKey()).isEqualTo("skeleton.shoulder.left");
    }

    @Test
    @DisplayName("keeps a right knee finding as KNEE instead of narrowing it to the femur")
    void mapsRightKneeWithoutNarrowing() {
        AnatomyTarget target = require(finding(
                FindingType.EFFUSION,
                AnatomicalStructure.KNEE,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.UNSPECIFIED));

        assertThat(target.structureCode()).isEqualTo(AnatomyStructure.KNEE);
        assertThat(target.structureCode()).isNotEqualTo(AnatomyStructure.FEMUR);
        assertThat(target.viewerKey()).isEqualTo("skeleton.knee.right");
    }

    @Test
    @DisplayName("maps a left ankle finding as ANKLE, not a leg bone")
    void mapsLeftAnkle() {
        AnatomyTarget target = require(finding(
                FindingType.FRACTURE,
                AnatomicalStructure.ANKLE,
                AnatomicalSide.LEFT,
                AnatomicalRegion.UNSPECIFIED));

        assertThat(target.structureCode()).isEqualTo(AnatomyStructure.ANKLE);
        assertThat(target.viewerKey()).isEqualTo("skeleton.ankle.left");
    }

    @Test
    @DisplayName("never invents a side or a side-specific viewer key when laterality is unspecified")
    void doesNotGuessSide() {
        AnatomyTarget target = require(finding(
                FindingType.FRACTURE,
                AnatomicalStructure.HUMERUS,
                AnatomicalSide.UNSPECIFIED,
                AnatomicalRegion.UNSPECIFIED));

        assertThat(target.structureCode()).isEqualTo(AnatomyStructure.HUMERUS);
        assertThat(target.side()).isEqualTo(AnatomicalSide.UNSPECIFIED);
        assertThat(target.viewerKey()).isNull();
        assertThat(target.hasViewerKey()).isFalse();
        assertThat(target.displayName()).isEqualTo("Humerus");
    }

    @Test
    @DisplayName("returns no target when the finding carries no resolvable anatomy")
    void failsSafeForUnsupportedAnatomy() {
        assertThat(mapper.map(finding(
                FindingType.LESION,
                null,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.UNSPECIFIED))).isEmpty();
        assertThat(mapper.map(null)).isEmpty();
        assertThat(service.targetsFor(List.of(finding(
                FindingType.LESION,
                null,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.UNSPECIFIED)))).isEmpty();
    }

    @Test
    @DisplayName("maps a brain finding to a nervous-system target")
    void mapsBrain() {
        AnatomyTarget target = require(finding(
                FindingType.ANEURYSM,
                AnatomicalStructure.BRAIN,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.UNSPECIFIED));

        assertThat(target.system()).isEqualTo(AnatomySystem.NERVOUS);
        assertThat(target.structureCode()).isEqualTo(AnatomyStructure.BRAIN);
        assertThat(target.side()).isEqualTo(AnatomicalSide.RIGHT);
        assertThat(target.displayName()).isEqualTo("Right brain");
        assertThat(target.viewerKey()).isEqualTo("nervous.brain");
    }

    @Test
    @DisplayName("maps a left upper lung nodule to a respiratory target with no skeletal viewer key")
    void mapsNonSkeletalLung() {
        AnatomyTarget target = require(finding(
                FindingType.NODULE,
                AnatomicalStructure.LUNG,
                AnatomicalSide.LEFT,
                AnatomicalRegion.UPPER));

        assertThat(target.system()).isEqualTo(AnatomySystem.RESPIRATORY);
        assertThat(target.structureCode()).isEqualTo(AnatomyStructure.LUNG);
        assertThat(target.side()).isEqualTo(AnatomicalSide.LEFT);
        assertThat(target.region()).isEqualTo(AnatomicalRegion.UPPER);
        assertThat(target.displayName()).isEqualTo("Left upper lung");
        assertThat(target.viewerKey()).isEqualTo("respiratory.lung.left");
        assertThat(target.viewerKey()).doesNotStartWith("skeleton.");
    }

    @Test
    @DisplayName("keeps a bilateral finding bilateral and refuses to pick one side's viewer key")
    void handlesBilateralDeterministically() {
        AnatomyTarget target = require(finding(
                FindingType.EFFUSION,
                AnatomicalStructure.PLEURA,
                AnatomicalSide.BILATERAL,
                AnatomicalRegion.UNSPECIFIED));

        assertThat(target.system()).isEqualTo(AnatomySystem.RESPIRATORY);
        assertThat(target.structureCode()).isEqualTo(AnatomyStructure.PLEURA);
        assertThat(target.side()).isEqualTo(AnatomicalSide.BILATERAL);
        assertThat(target.viewerKey()).isNull();
        assertThat(target.displayName()).isEqualTo("Bilateral pleura");

        // A caller that needs meshes resolves both sides explicitly from the catalog.
        assertThat(service.viewerKey(AnatomyStructure.PLEURA, AnatomicalSide.RIGHT))
                .contains("respiratory.pleura.right");
        assertThat(service.viewerKey(AnatomyStructure.PLEURA, AnatomicalSide.LEFT))
                .contains("respiratory.pleura.left");
        assertThat(service.viewerKey(AnatomyStructure.PLEURA, AnatomicalSide.BILATERAL)).isEmpty();
    }

    @Test
    @DisplayName("maps a right kidney lesion to a urinary target")
    void mapsNonSkeletalKidney() {
        AnatomyTarget target = require(finding(
                FindingType.LESION,
                AnatomicalStructure.KIDNEY,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.UNSPECIFIED));

        assertThat(target.system()).isEqualTo(AnatomySystem.URINARY);
        assertThat(target.viewerKey()).isEqualTo("urinary.kidney.right");
    }

    @Test
    @DisplayName("mapping is deterministic and repeatable for the same finding")
    void mappingIsDeterministic() {
        StructuredFinding finding = finding(
                FindingType.FRACTURE,
                AnatomicalStructure.HUMERUS,
                AnatomicalSide.RIGHT,
                AnatomicalRegion.PROXIMAL);

        assertThat(mapper.map(finding)).isEqualTo(mapper.map(finding));
        assertThat(service.targetFor(finding)).isEqualTo(mapper.map(finding));
    }

    @Test
    @DisplayName("catalog exposes every structure with a unique, system-namespaced viewer key")
    void catalogKeysAreStableAndNamespaced() {
        List<AnatomyDefinition> definitions = service.catalogDefinitions();

        assertThat(definitions).hasSize(AnatomyStructure.values().length);
        assertThat(definitions).extracting(AnatomyDefinition::viewerKeyPattern).doesNotHaveDuplicates();
        assertThat(definitions)
                .filteredOn(definition -> definition.system() == AnatomySystem.SKELETAL)
                .extracting(AnatomyDefinition::viewerKeyPattern)
                .allSatisfy(pattern -> assertThat(pattern).startsWith("skeleton."));
        assertThat(definitions)
                .filteredOn(definition -> definition.system() != AnatomySystem.SKELETAL)
                .extracting(AnatomyDefinition::viewerKeyPattern)
                .allSatisfy(pattern -> assertThat(pattern).doesNotStartWith("skeleton."));
        assertThat(catalog.find(null)).isEmpty();
    }

    private AnatomyTarget require(StructuredFinding finding) {
        Optional<AnatomyTarget> target = mapper.map(finding);
        assertThat(target).isPresent();
        return target.orElseThrow();
    }

    private StructuredFinding finding(
            FindingType findingType,
            AnatomicalStructure anatomy,
            AnatomicalSide side,
            AnatomicalRegion region
    ) {
        return new StructuredFinding(
                "test-finding",
                findingType,
                anatomy,
                anatomy == null ? null : anatomy.name().toLowerCase(java.util.Locale.ROOT),
                side,
                region,
                FindingStatus.PRESENT,
                FindingCertainty.ASSERTED,
                null,
                null,
                FindingSourceSection.FINDINGS,
                "test source text",
                0,
                16,
                null);
    }
}
