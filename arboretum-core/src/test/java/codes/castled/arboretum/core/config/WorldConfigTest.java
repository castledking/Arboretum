package codes.castled.arboretum.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.arboretum.core.bundle.BundledData;
import codes.castled.arboretum.core.collection.CollectionDefinition;
import codes.castled.arboretum.core.collection.CollectionResolver;
import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.GenerationMode;
import codes.castled.arboretum.core.species.SpawningRule;
import codes.castled.arboretum.core.species.SpeciesCatalogue;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * A world's Arboretum settings: written at creation, read back on every start, and resolved
 * against whatever catalogue is installed then.
 */
class WorldConfigTest {

    static BundledData bundle;

    @BeforeAll
    static void load() throws Exception {
        bundle = BundledData.load(WorldConfigTest.class.getClassLoader());
    }

    @Test
    void aWorldSurvivesBeingSavedAndReadBack() {
        WorldConfig created = WorldConfig.forCollection(collection("savanna"), GenerationMode.ABUNDANT,
                SpawningRule.MIXED);
        WorldConfigCodec.Result read = WorldConfigCodec.read("world", WorldConfigCodec.write(created));
        assertTrue(read.isClean(), read.report().describe());
        assertEquals(created, read.config());
    }

    @Test
    void eachBuiltInCollectionMakesAWorldOfItsSpecies() {
        assertEquals(Set.of("arboretum:baobab", "arboretum:jacaranda"), generated(world("small")));
        assertEquals(Set.of("arboretum:baobab", "arboretum:jacaranda"), generated(world("medium")));
        assertEquals(Set.of("arboretum:baobab"), generated(world("savanna")));
    }

    @Test
    void theCollectionsWeightsAreFrozenIntoTheWorld() {
        WorldConfig savanna = world("savanna");
        assertEquals(4, savanna.weightOverrides().get(ArboretumId.arboretum("baobab")));
        assertEquals(4, savanna.resolve(bundle.catalogue()).selected()
                .get(ArboretumId.arboretum("baobab")).weight());
    }

    @Test
    void aSpeciesAddedLaterDoesNotJoinAnExistingWorld() {
        // Created when Small held only the baobab; the catalogue has more species now.
        WorldConfig old = new WorldConfig(WorldConfig.CURRENT_SCHEMA, ArboretumId.arboretum("small"),
                List.of(ArboretumId.arboretum("baobab")), java.util.Map.of(), GenerationMode.NATURAL,
                SpawningRule.ECOLOGICAL, WorldConfig.WorldSettings.defaults());
        assertEquals(Set.of("arboretum:baobab"), generated(old));
    }

    @Test
    void aSpeciesRemovedSinceCreationIsSkippedWithAWarning() {
        List<ArboretumId> species = new ArrayList<>(world("small").species());
        species.add(ArboretumId.arboretum("since_removed"));
        WorldConfig world = new WorldConfig(WorldConfig.CURRENT_SCHEMA, ArboretumId.arboretum("small"),
                species, java.util.Map.of(), GenerationMode.NATURAL, SpawningRule.ECOLOGICAL,
                WorldConfig.WorldSettings.defaults());
        CollectionResolver.Resolution resolution = world.resolve(bundle.catalogue());
        assertEquals(Set.of("arboretum:baobab", "arboretum:jacaranda"), ids(resolution.selected().keySet()));
        assertTrue(resolution.report().describe().contains("since_removed"), resolution.report().describe());
        assertTrue(!resolution.report().hasErrors(), "a removed species must not stop the world");
    }

    @Test
    void anEmptyCatalogueGeneratesNothingRatherThanFailing() {
        assertEquals(Set.of(), ids(world("small").resolve(SpeciesCatalogue.empty()).selected().keySet()));
    }

    @Test
    void aDamagedFileFallsBackToNoTreesAndSaysSo() {
        WorldConfigCodec.Result read = WorldConfigCodec.read("world", "{not json");
        assertTrue(read.report().hasErrors());
        assertEquals(List.of(), read.config().species());
    }

    @Test
    void aFileFromANewerArboretumIsReported() {
        String newer = WorldConfigCodec.write(world("small")).replace("\"schema\": 1", "\"schema\": 99");
        assertTrue(WorldConfigCodec.read("world", newer).report().hasErrors());
    }

    private static WorldConfig world(String collection) {
        return WorldConfig.forCollection(collection(collection), GenerationMode.NATURAL, SpawningRule.ECOLOGICAL);
    }

    private static CollectionDefinition collection(String name) {
        return bundle.collections().get(ArboretumId.arboretum(name));
    }

    private static Set<String> generated(WorldConfig world) {
        return ids(world.resolve(bundle.catalogue()).selected().keySet());
    }

    private static Set<String> ids(java.util.Collection<ArboretumId> ids) {
        return ids.stream().map(ArboretumId::toString).collect(Collectors.toSet());
    }
}
