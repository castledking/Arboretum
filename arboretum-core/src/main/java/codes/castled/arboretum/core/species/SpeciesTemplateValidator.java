package codes.castled.arboretum.core.species;

import codes.castled.arboretum.core.data.DataReader;
import codes.castled.arboretum.core.data.ValidationReport;
import codes.castled.arboretum.core.template.StructureTemplate;
import codes.castled.arboretum.core.template.TemplateLibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks a species' templates against the markers its definition declares.
 *
 * <p>Run at load time, not at generation time. A species whose trunk template has no leaf marker in
 * its palette is a mistake in the species file, and finding it at startup means a clear log line
 * instead of a forest full of bare poles that only a player would notice.
 *
 * <p>Three template problems are checked here, and they are the three that produce visibly wrong
 * trees rather than no tree at all:
 * <ul>
 *   <li>a declared marker that appears in no palette, which would be silently skipped;</li>
 *   <li>a template with no placeable block at all, which would never generate;</li>
 *   <li>a trunk template with no log marker, which would place a floating canopy.</li>
 * </ul>
 */
public final class SpeciesTemplateValidator {

    private SpeciesTemplateValidator() {
    }

    /**
     * Validates a species against its templates.
     *
     * @return the report; the caller decides whether errors disqualify the species
     */
    public static ValidationReport validate(SpeciesDefinition species, TemplateLibrary library) {
        ValidationReport report = new ValidationReport(species.id().toString());
        SpeciesDefinition.Structure structure = species.structure();

        StructureTemplate trunk = require(species, library, structure.trunkTemplate(),
                "structure.trunk_template", report);
        StructureTemplate canopy = require(species, library, structure.canopyTemplate(),
                "structure.canopy_template", report);
        StructureTemplate roots = null;
        if (structure.hasRoots()) {
            roots = require(species, library, structure.rootsTemplate(),
                    "structure.roots_template", report);
        }

        if (trunk != null) {
            checkMarkers(species, trunk, "structure.trunk_template", report);
            requireSome(species, trunk, "structure.trunk_template", report);
        }
        if (canopy != null) {
            checkMarkers(species, canopy, "structure.canopy_template", report);
            requireSome(species, canopy, "structure.canopy_template", report);
        }
        if (roots != null) {
            checkMarkers(species, roots, "structure.roots_template", report);
        }

        // The trunk template has to contain log markers. A canopy on its own is a floating blob,
        // and max_log_depth would never find ground because there is nothing to stand on.
        if (trunk != null) {
            long logBlocks = countMarkers(species, trunk, structure.logMarkers());
            if (logBlocks == 0) {
                report.error("structure.log_markers",
                        "the trunk template contains none of them. The trunk template must hold the "
                                + "tree's logs; leave the leaves to the canopy template.");
            }
        }

        return report;
    }

    private static StructureTemplate require(SpeciesDefinition species, TemplateLibrary library,
                                             String path, String field, ValidationReport report) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            Optional<StructureTemplate> template = library.load(path);
            if (template.isEmpty()) {
                report.error(field, "template '" + path + "' was not found in "
                        + library.source().describe() + ".");
                return null;
            }
            return template.get();
        } catch (codes.castled.arboretum.core.nbt.NbtException exception) {
            report.error(field, "template '" + path + "' could not be read: "
                    + exception.getMessage());
            return null;
        }
    }

    private static void checkMarkers(SpeciesDefinition species, StructureTemplate template,
                                     String field, ValidationReport report) {
        List<String> absent = new ArrayList<>();
        for (String marker : markersOf(species)) {
            if (countMarkers(species, template, List.of(marker)) == 0) {
                absent.add(marker);
            }
        }
        if (absent.isEmpty()) {
            return;
        }
        report.warn(field, "template '" + template + "' contains none of the declared markers "
                + absent + ". Those positions will be skipped. If the template was saved with a "
                + "different block, update the species' marker lists to match it.");
    }

    private static void requireSome(SpeciesDefinition species, StructureTemplate template,
                                    String field, ValidationReport report) {
        long placeable = 0;
        for (String marker : markersOf(species)) {
            placeable += countMarkers(species, template, List.of(marker));
        }
        if (placeable == 0) {
            report.error(field, "no block in this template matches any marker the species declares, "
                    + "so the template contributes nothing. Check the marker lists against the "
                    + "blocks actually in the saved structure.");
        }
    }

    private static List<String> markersOf(SpeciesDefinition species) {
        List<String> markers = new ArrayList<>(species.structure().logMarkers());
        markers.addAll(species.structure().leafMarkers());
        markers.addAll(species.structure().extraMarkers().keySet());
        return markers;
    }

    private static long countMarkers(SpeciesDefinition species, StructureTemplate template,
                                     List<String> markers) {
        if (markers.isEmpty()) {
            return 0;
        }
        java.util.Set<String> lookup = new java.util.HashSet<>(markers);
        long count = 0;
        for (codes.castled.arboretum.core.template.TemplateBlock block : template.blocks()) {
            var state = template.stateAt(block);
            if (state != null && lookup.contains(state.blockId())) {
                count++;
            }
        }
        return count;
    }
}