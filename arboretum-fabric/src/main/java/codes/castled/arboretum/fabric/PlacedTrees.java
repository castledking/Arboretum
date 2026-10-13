package codes.castled.arboretum.fabric;

import codes.castled.arboretum.core.id.ArboretumId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * The last trees Arboretum grew in this world, each with the blocks it wrote - what
 * {@code /arboretum verify} checks the world against.
 *
 * <p>Diagnostics, bounded to {@link #CAPACITY} trees. Kept in memory; written to
 * {@code <world>/arboretum-placed.json} only with {@code -Darboretum.debug=true}, so the boot
 * test can verify trees across a restart without a normal server writing the file.
 */
final class PlacedTrees {

    static final int CAPACITY = 64;
    static final String FILE = "arboretum-placed.json";
    static final boolean DEBUG = Boolean.getBoolean("arboretum.debug");

    /** A grown tree: its species, origin, rotation, and every block as {x, y, z, state}. */
    record Tree(ArboretumId species, int x, int y, int z, String rotation, List<Block> blocks) {
    }

    record Block(int x, int y, int z, String state) {
    }

    private final Path file;
    private final Deque<Tree> trees = new ArrayDeque<>();
    private int total;

    private PlacedTrees(Path file) {
        this.file = file;
    }

    static PlacedTrees load(Path worldRoot) throws IOException {
        PlacedTrees placed = new PlacedTrees(worldRoot.resolve(FILE));
        if (DEBUG && Files.exists(placed.file)) {
            JsonObject json = JsonParser.parseString(Files.readString(placed.file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            placed.total = json.get("total").getAsInt();
            for (var element : json.getAsJsonArray("trees")) {
                JsonObject tree = element.getAsJsonObject();
                List<Block> blocks = new ArrayList<>();
                for (var block : tree.getAsJsonArray("blocks")) {
                    JsonArray b = block.getAsJsonArray();
                    blocks.add(new Block(b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt(),
                            b.get(3).getAsString()));
                }
                placed.trees.add(new Tree(ArboretumId.parse(tree.get("species").getAsString()),
                        tree.get("x").getAsInt(), tree.get("y").getAsInt(), tree.get("z").getAsInt(),
                        tree.get("rotation").getAsString(), blocks));
            }
        }
        return placed;
    }

    synchronized void add(Tree tree) {
        total++;
        trees.addLast(tree);
        while (trees.size() > CAPACITY) {
            trees.removeFirst();
        }
    }

    synchronized List<Tree> snapshot() {
        return List.copyOf(trees);
    }

    synchronized int total() {
        return total;
    }

    synchronized Map<ArboretumId, Integer> bySpecies() {
        Map<ArboretumId, Integer> counts = new java.util.TreeMap<>();
        for (Tree tree : trees) {
            counts.merge(tree.species(), 1, Integer::sum);
        }
        return counts;
    }

    synchronized void save() throws IOException {
        if (!DEBUG) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("total", total);
        JsonArray list = new JsonArray();
        for (Tree tree : trees) {
            JsonObject t = new JsonObject();
            t.addProperty("species", tree.species().toString());
            t.addProperty("x", tree.x());
            t.addProperty("y", tree.y());
            t.addProperty("z", tree.z());
            t.addProperty("rotation", tree.rotation());
            JsonArray blocks = new JsonArray();
            for (Block block : tree.blocks()) {
                JsonArray b = new JsonArray();
                b.add(block.x());
                b.add(block.y());
                b.add(block.z());
                b.add(block.state());
                blocks.add(b);
            }
            t.add("blocks", blocks);
            list.add(t);
        }
        json.add("trees", list);
        Gson gson = new GsonBuilder().create();
        Files.writeString(file, gson.toJson(json), StandardCharsets.UTF_8);
    }
}
