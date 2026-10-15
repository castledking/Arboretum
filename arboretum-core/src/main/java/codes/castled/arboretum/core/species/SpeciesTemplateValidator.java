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

        if (structure.wholeTrees()) {
            // Each whole tree stands alone: it must hold the trunk's logs and the leaves itself. A
            // marker only some trees use is fine; one no tree uses is flagged.
            List<StructureTemplate> trees = new ArrayList<>();
            for (int i = 0; i < structure.templates().size(); i++) {
                String field = "structure.templates[" + i + "]";
                StructureTemplate tree = require(species, library, structure.templates().get(i).path(), field, report);
                if (tree != null) {
                    trees.add(tree);
                    requireSome(species, tree, field, report);
                    requireLogs(species, tree, field, report);
                    checkLeafReach(species, List.of(tree), report);
                }
            }
            if (trees.size() == structure.templates().size()) {
                checkMarkers(species, trees, report);
            }
            return report;
        }

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
            requireSome(species, trunk, "structure.trunk_template", report);
            // A canopy on its own is a floating blob: the trunk template has to hold the logs.
            requireLogs(species, trunk, "structure.trunk_template", report);
        }
        if (canopy != null) {
            requireSome(species, canopy, "structure.canopy_template", report);
        }
        // A marker belongs to one part - logs to the trunk, leaves to the canopy - so it is only
        // missing when none of the species' templates has it.
        List<StructureTemplate> loaded = new ArrayList<>();
        for (StructureTemplate template : new StructureTemplate[] {trunk, canopy, roots}) {
            if (template != null) {
                loaded.add(template);
            }
        }
        int expected = 1 + (structure.canopyTemplate() != null ? 1 : 0) + (structure.hasRoots() ? 1 : 0);
        if (loaded.size() == expected) {
            checkMarkers(species, loaded, report);
            checkLeafReach(species, loaded, report);
        }
        return report;
    }

    private static void requireLogs(SpeciesDefinition species, StructureTemplate template, String field,
                                    ValidationReport report) {
        if (countMarkers(species, template, species.structure().logMarkers()) == 0) {
            report.error(field, "contains none of the species' log_markers " + species.structure().logMarkers()
                    + ". The template must hold the tree's trunk.");
        }
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

    private static void checkMarkers(SpeciesDefinition species, List<StructureTemplate> templates,
                                     ValidationReport report) {
        List<String> absent = new ArrayList<>();
        for (String marker : markersOf(species)) {
            long found = 0;
            for (StructureTemplate template : templates) {
                found += countMarkers(species, template, List.of(marker));
            }
            if (found == 0) {
                absent.add(marker);
            }
        }
        if (absent.isEmpty()) {
            return;
        }
        report.warn("structure", "no template contains the declared markers " + absent
                + ", so they place nothing. If the templates were saved with a different block, "
                + "update the species' marker lists to match them.");
    }

    /**
     * Warns about leaves vanilla would let decay: farther than six blocks from a log, counted
     * through leaves, in the tree as placed unrotated (where a leaf and a log meet, the log wins).
     * They generate and then rot away within minutes.
     */
    private static void checkLeafReach(SpeciesDefinition species, List<StructureTemplate> templates,
                                       ValidationReport report) {
        java.util.Set<String> logMarkers = new java.util.HashSet<>(species.structure().logMarkers());
        java.util.Set<String> leafMarkers = new java.util.HashSet<>(species.structure().leafMarkers());
        java.util.Set<codes.castled.arboretum.core.template.BlockPos> logs = new java.util.HashSet<>();
        java.util.Set<codes.castled.arboretum.core.template.BlockPos> leaves = new java.util.HashSet<>();
        for (StructureTemplate template : templates) {
            for (codes.castled.arboretum.core.template.TemplateBlock block : template.blocks()) {
                String id = template.stateAt(block).blockId();
                if (logMarkers.contains(id)) {
                    logs.add(block.pos());
                } else if (leafMarkers.contains(id)) {
                    leaves.add(block.pos());
                }
            }
        }
        leaves.removeAll(logs);
        long decaying = codes.castled.arboretum.core.placement.LeafReach.decaying(logs, leaves);
        if (decaying > 0) {
            report.warn("structure", decaying + " of " + leaves.size() + " leaves are more than "
                    + (codes.castled.arboretum.core.placement.LeafReach.DECAY_DISTANCE - 1)
                    + " blocks from a log, counted through leaves, so vanilla decays them minutes after "
                    + "the tree grows. Add branches into the canopy's far edges, or trim those leaves.");
        }
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