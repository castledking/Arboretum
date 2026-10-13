package codes.castled.arboretum.core.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.arboretum.core.collection.CollectionDefinition;
import codes.castled.arboretum.core.collection.CollectionResolver;
import codes.castled.arboretum.core.data.ValidationReport;
import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.GenerationMode;
import codes.castled.arboretum.core.species.SpawningRule;
import codes.castled.arboretum.core.species.SpeciesDefinition;
import codes.castled.arboretum.core.species.SpeciesTemplateValidator;
import codes.castled.arboretum.core.template.StructureTemplate;
import codes.castled.arboretum.core.template.TemplateBlock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The data Arboretum ships, loaded the way every platform loads it: data/arboretum/index.json
 * on the class path. No inline JSON, no file paths - if this passes, the jar's data loads.
 */
class BundledDataTest {

    static BundledData bundle;

    @BeforeAll
    static void load() throws Exception {
        bundle = BundledData.load(BundledDataTest.class.getClassLoader());
    }

    @Test
    void theShippedDataLoadsWithoutErrors() {
        assertFalse(bundle.hasErrors(), bundle.describeProblems());
    }

    @Test
    void allFourSpeciesAreInTheCatalogue() {
        assertEquals(Set.of("arboretum:baobab", "arboretum:jacaranda", "arboretum:bald_cypress",
                "arboretum:italian_cypress"), ids(bundle.catalogue().ids()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"baobab", "jacaranda", "bald_cypress", "italian_cypress"})
    void everySpeciesPassesTemplateValidationCleanly(String name) {
        SpeciesDefinition species = species(name);
        ValidationReport report = SpeciesTemplateValidator.validate(species, bundle.templates());
        assertTrue(report.isClean(), report.describe());
    }

    @ParameterizedTest
    @ValueSource(strings = {"baobab", "jacaranda", "bald_cypress", "italian_cypress"})
    void trunksHoldTheLogsAndCanopiesTheLeaves(String name) {
        SpeciesDefinition species = species(name);
        StructureTemplate trunk = template(species.structure().trunkTemplate());
        StructureTemplate canopy = template(species.structure().canopyTemplate());
        assertTrue(count(trunk, species.structure().logMarkers()) > 0, "trunk has no log markers");
        assertTrue(count(canopy, species.structure().leafMarkers()) > 0, "canopy has no leaf markers");
        for (StructureTemplate template : List.of(trunk, canopy)) {
            assertTrue(template.sizeX() <= 48 && template.sizeY() <= 64 && template.sizeZ() <= 48,
                    template + " is " + template.sizeX() + "x" + template.sizeY() + "x" + template.sizeZ()
                            + ", too large to be one tree");
        }
    }

    @Test
    void theBaldCypressRootsTemplateLoads() {
        SpeciesDefinition species = species("bald_cypress");
        assertTrue(species.structure().hasRoots());
        assertTrue(template(species.structure().rootsTemplate()).blockCount() > 0);
    }

    @Test
    void theBuiltInCollectionsLoad() {
        assertEquals(Set.of("arboretum:small", "arboretum:medium", "arboretum:savanna"),
                ids(bundle.collections().keySet()));
    }

    @Test
    void smallAndMediumResolveToTheFinishedSpecies() {
        for (String name : List.of("small", "medium")) {
            assertEquals(Set.of("arboretum:baobab", "arboretum:jacaranda"), resolved(name), name);
        }
    }

    @Test
    void savannaResolvesToTheBaobabWithItsWeight() {
        CollectionResolver.Resolution resolution = resolution("savanna");
        assertEquals(Set.of("arboretum:baobab"), ids(resolution.selected().keySet()));
        assertEquals(4, resolution.selected().get(ArboretumId.arboretum("baobab")).weight());
    }

    @Test
    void anExcludedSpeciesIsLeftOut() {
        CollectionDefinition medium = collection("medium");
        CollectionResolver.Resolution resolution = CollectionResolver.resolve(bundle.catalogue(), medium,
                GenerationMode.NATURAL, SpawningRule.ECOLOGICAL, Set.of(ArboretumId.arboretum("jacaranda")));
        assertEquals(Set.of("arboretum:baobab"), ids(resolution.selected().keySet()));
    }

    static SpeciesDefinition species(String name) {
        return bundle.catalogue().get(ArboretumId.arboretum(name))
                .orElseThrow(() -> new AssertionError(name + " is not in the catalogue:\n" + bundle.describeProblems()));
    }

    static StructureTemplate template(String path) {
        return bundle.templates().load(path).orElseThrow(() -> new AssertionError(path + " did not load"));
    }

    private static CollectionDefinition collection(String name) {
        CollectionDefinition collection = bundle.collections().get(ArboretumId.arboretum(name));
        assertTrue(collection != null, name + " is not bundled");
        return collection;
    }

    private static CollectionResolver.Resolution resolution(String name) {
        return CollectionResolver.resolve(bundle.catalogue(), collection(name),
                GenerationMode.NATURAL, SpawningRule.ECOLOGICAL, Set.of());
    }

    private static Set<String> resolved(String name) {
        return ids(resolution(name).selected().keySet());
    }

    private static long count(StructureTemplate template, List<String> markers) {
        long n = 0;
        for (TemplateBlock block : template.blocks()) {
            if (markers.contains(template.stateAt(block).blockId())) {
                n++;
            }
        }
        return n;
    }

    private static Set<String> ids(java.util.Collection<ArboretumId> ids) {
        return ids.stream().map(ArboretumId::toString).collect(Collectors.toSet());
    }
}
