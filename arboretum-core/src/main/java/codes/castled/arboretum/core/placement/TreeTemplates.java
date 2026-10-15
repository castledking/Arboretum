package codes.castled.arboretum.core.placement;

import codes.castled.arboretum.core.species.SpeciesDefinition;
import codes.castled.arboretum.core.template.StructureTemplate;
import codes.castled.arboretum.core.template.TemplateLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The templates one tree is grown from: a whole tree picked from the species' variants by weight,
 * or the species' trunk, canopy and roots parts.
 */
public final class TreeTemplates {

    /** What {@link TreePlacementPlanner#plan} takes; canopy and roots may be null. */
    public record Chosen(String name, StructureTemplate trunk, StructureTemplate canopy, StructureTemplate roots) {
    }

    private TreeTemplates() {
    }

    /** Picks one tree's templates; the same seed picks the same variant. Null if one is missing. */
    public static Chosen choose(SpeciesDefinition species, TemplateLibrary library, long seed) {
        SpeciesDefinition.Structure structure = species.structure();
        if (structure.wholeTrees()) {
            SpeciesDefinition.Variant variant = pick(structure.templates(), seed);
            StructureTemplate tree = library.load(variant.path()).orElse(null);
            return tree == null ? null : new Chosen(variant.path(), tree, null, null);
        }
        StructureTemplate trunk = library.load(structure.trunkTemplate()).orElse(null);
        StructureTemplate canopy = structure.canopyTemplate() == null ? null
                : library.load(structure.canopyTemplate()).orElse(null);
        StructureTemplate roots = structure.hasRoots() ? library.load(structure.rootsTemplate()).orElse(null) : null;
        if (trunk == null || (structure.canopyTemplate() != null && canopy == null)
                || (structure.hasRoots() && roots == null)) {
            return null;
        }
        return new Chosen(structure.trunkTemplate(), trunk, canopy, roots);
    }

    /** Every way the species can be grown: each variant, or its one set of parts. */
    public static List<Chosen> all(SpeciesDefinition species, TemplateLibrary library) {
        List<Chosen> out = new ArrayList<>();
        if (species.structure().wholeTrees()) {
            for (SpeciesDefinition.Variant variant : species.structure().templates()) {
                library.load(variant.path()).ifPresent(tree -> out.add(new Chosen(variant.path(), tree, null, null)));
            }
        } else {
            Chosen parts = choose(species, library, 0L);
            if (parts != null) {
                out.add(parts);
            }
        }
        return out;
    }

    private static SpeciesDefinition.Variant pick(List<SpeciesDefinition.Variant> variants, long seed) {
        int total = variants.stream().mapToInt(SpeciesDefinition.Variant::weight).sum();
        int roll = new SplittableRandom(seed).nextInt(total);
        for (SpeciesDefinition.Variant variant : variants) {
            roll -= variant.weight();
            if (roll < 0) {
                return variant;
            }
        }
        return variants.getLast();
    }
}
