package codes.castled.arboretum.core.bundle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.arboretum.core.id.ArboretumId;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a broken bundle does: the bad species is left out, and the report names the file, the field
 * and the fix. Each test builds a small bundle in a temporary directory and loads it through its own
 * class loader, so the shipped data can't leak in.
 */
class BundledDataRejectionTest {

    @TempDir
    Path dir;

    @Test
    void aMissingIndexIsAnError() throws IOException {
        assertThrows(IOException.class, () -> BundledData.load(loader()));
    }

    @Test
    void aSpeciesWhoseTemplateIsMissingIsRejectedAndSaysWhich() throws IOException {
        writeSpecies("lost", "data/arboretum/templates/lost/trunk.nbt", "minecraft:dark_oak_log");
        writeIndex(List.of("data/arboretum/species/lost.json"));
        BundledData bundle = BundledData.load(loader());
        assertFalse(bundle.catalogue().contains(ArboretumId.arboretum("lost")));
        assertTrue(bundle.describeProblems().contains("data/arboretum/templates/lost/trunk.nbt"),
                bundle.describeProblems());
    }

    @Test
    void aSpeciesWhoseMarkersAreNotInItsTemplatesIsRejected() throws IOException {
        // The real baobab templates, with a species that says its logs are oak.
        copyShipped("data/arboretum/templates/baobab/trunk.nbt");
        copyShipped("data/arboretum/templates/baobab/canopy.nbt");
        writeSpecies("oaken", "data/arboretum/templates/baobab/trunk.nbt", "minecraft:oak_log");
        writeIndex(List.of("data/arboretum/species/oaken.json"));
        BundledData bundle = BundledData.load(loader());
        assertFalse(bundle.catalogue().contains(ArboretumId.arboretum("oaken")));
        assertTrue(bundle.describeProblems().contains("log_markers"), bundle.describeProblems());
    }

    @Test
    void aSpeciesWithoutAnIdIsRejectedWithItsFileNamed() throws IOException {
        write("data/arboretum/species/anon.json", "{\"schema\": 1}");
        writeIndex(List.of("data/arboretum/species/anon.json"));
        BundledData bundle = BundledData.load(loader());
        assertTrue(bundle.hasErrors());
        assertTrue(bundle.describeProblems().contains("anon.json"), bundle.describeProblems());
    }

    @Test
    void aSpeciesListedButAbsentIsReported() throws IOException {
        writeIndex(List.of("data/arboretum/species/ghost.json"));
        BundledData bundle = BundledData.load(loader());
        assertTrue(bundle.describeProblems().contains("ghost.json"), bundle.describeProblems());
    }

    private ClassLoader loader() throws IOException {
        // No parent: only this directory is visible, never the shipped bundle.
        return new URLClassLoader(new URL[] {dir.toUri().toURL()}, null);
    }

    private void writeSpecies(String name, String trunk, String logMarker) throws IOException {
        write("data/arboretum/species/" + name + ".json", """
                {
                  "id": "arboretum:%s",
                  "schema": 1,
                  "botany": {"common_name": "%s", "scientific_name": "Testus %s", "genus": "Testus", "family": "Testaceae"},
                  "visuals": {"log_block": "minecraft:dark_oak_log", "leaf_block": "minecraft:dark_oak_leaves"},
                  "structure": {
                    "trunk_template": "%s",
                    "canopy_template": "data/arboretum/templates/baobab/canopy.nbt",
                    "log_markers": ["%s"],
                    "leaf_markers": ["minecraft:dark_oak_leaves"],
                    "growable_on": ["minecraft:grass_block"]
                  },
                  "ecology": {"biomes": ["minecraft:savanna"], "weight": 1}
                }
                """.formatted(name, name, name, trunk, logMarker));
    }

    private void writeIndex(List<String> species) throws IOException {
        StringBuilder list = new StringBuilder();
        for (String path : species) {
            list.append(list.isEmpty() ? "" : ",").append('"').append(path).append('"');
        }
        write(BundledData.INDEX, "{\"species\": [" + list + "], \"collections\": [], \"templates\": []}");
    }

    private void copyShipped(String path) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            Path out = dir.resolve(path);
            Files.createDirectories(out.getParent());
            Files.write(out, in.readAllBytes());
        }
    }

    private void write(String path, String text) throws IOException {
        Path out = dir.resolve(path);
        Files.createDirectories(out.getParent());
        Files.writeString(out, text, StandardCharsets.UTF_8);
    }
}
