package com.medai.anatomy.mapping;

import com.medai.anatomy.catalog.AnatomyCatalog;
import com.medai.anatomy.catalog.AnatomyDefinition;
import com.medai.anatomy.model.AnatomyStructure;
import com.medai.anatomy.model.AnatomyTarget;
import com.medai.finding.model.AnatomicalRegion;
import com.medai.finding.model.AnatomicalSide;
import com.medai.finding.model.AnatomicalStructure;
import com.medai.finding.model.StructuredFinding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic finding to anatomy mapping.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>The extracted anatomy concept is carried across as-is. A broader concept is never narrowed
 *       to a bone it merely contains (SHOULDER stays SHOULDER, KNEE never becomes FEMUR).</li>
 *   <li>Laterality is copied from the finding. It is never inferred, defaulted or guessed.</li>
 *   <li>Region is carried as metadata and never enters the viewer key.</li>
 *   <li>An absent or uncatalogued anatomy concept yields no target.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class DeterministicFindingAnatomyMapper implements FindingAnatomyMapper {

    private final AnatomyCatalog catalog;

    @Override
    public Optional<AnatomyTarget> map(StructuredFinding finding) {
        if (finding == null || finding.anatomy() == null) {
            return Optional.empty();
        }

        return structureCodeOf(finding.anatomy())
                .flatMap(catalog::find)
                .map(definition -> toTarget(definition, finding));
    }

    private AnatomyTarget toTarget(AnatomyDefinition definition, StructuredFinding finding) {
        AnatomicalSide side = finding.side() == null ? AnatomicalSide.UNSPECIFIED : finding.side();
        AnatomicalRegion region = finding.region() == null ? AnatomicalRegion.UNSPECIFIED : finding.region();

        return new AnatomyTarget(
                definition.system(),
                definition.structureCode(),
                displayName(definition, side, region),
                side,
                region,
                definition.viewerKey(side).orElse(null),
                definition.parentStructureCode(),
                finding.anatomy());
    }

    /**
     * Composes the human-readable label. Unspecified side or region is omitted rather than spelled
     * out, so the label never asserts more than the finding did.
     */
    private String displayName(AnatomyDefinition definition, AnatomicalSide side, AnatomicalRegion region) {
        List<String> parts = new ArrayList<>(3);
        if (side != AnatomicalSide.UNSPECIFIED) {
            parts.add(side.name().toLowerCase(Locale.ROOT));
        }
        if (region != AnatomicalRegion.UNSPECIFIED) {
            parts.add(region.name().toLowerCase(Locale.ROOT));
        }
        parts.add(definition.displayLabel());

        String label = String.join(" ", parts);
        return label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1);
    }

    /**
     * Translates the extraction vocabulary into the anatomy vocabulary by code name. A concept the
     * anatomy layer does not know about fails safe instead of throwing.
     */
    private Optional<AnatomyStructure> structureCodeOf(AnatomicalStructure anatomy) {
        try {
            return Optional.of(AnatomyStructure.valueOf(anatomy.name()));
        } catch (IllegalArgumentException unsupported) {
            return Optional.empty();
        }
    }
}
