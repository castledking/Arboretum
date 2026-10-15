package codes.castled.arboretum.fabric;

import codes.castled.arboretum.core.bundle.BundledData;
import codes.castled.arboretum.core.collection.CollectionDefinition;
import codes.castled.arboretum.core.collection.CollectionResolver;
import codes.castled.arboretum.core.config.WorldConfig;
import codes.castled.arboretum.core.config.WorldConfigCodec;
import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.GenerationMode;
import codes.castled.arboretum.core.species.SpawningRule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/**
 * One running world's Arboretum settings and what they resolve to.
 *
 * <p>The config lives in {@code <world>/arboretum.json}. A world that has one is read, never
 * rewritten; a world without one gets the default (the Medium collection, Natural, Ecological),
 * written once so the next start reads the same thing. The world-creation screen will write the
 * file instead; until then this is how a world gets its config.
 */
final class WorldState {

    static final String FILE = "arboretum.json";
    static final ArboretumId DEFAULT_COLLECTION = ArboretumId.arboretum("medium");

    private static volatile WorldState current;

    final WorldConfig config;
    final CollectionResolver.Resolution resolution;
    final PlacedTrees placed;
    final Map<String, AtomicInteger> refusals = new ConcurrentHashMap<>();
    /**
     * Every tree world generation tried to grow (species@x,y,z/rotation), grown or refused. The
     * same seed must make the same attempts whatever order chunks generate in; whether an attempt
     * at a chunk border grows can depend on that order - a neighbour's tree may already stand
     * there - as it does for vanilla trees. Bounded, for diagnostics.
     */
    final java.util.Set<String> attempts = ConcurrentHashMap.newKeySet();
    static final int ATTEMPT_CAPACITY = 1024;

    private WorldState(WorldConfig config, CollectionResolver.Resolution resolution, PlacedTrees placed) {
        this.config = config;
        this.resolution = resolution;
        this.placed = placed;
    }

    /** The running world's state, or null when no server is running. */
    static WorldState current() {
        return current;
    }

    static void clear() {
        current = null;
    }

    /** Reads (or creates) the world's config and resolves it against the bundle. */
    static WorldState load(MinecraftServer server, BundledData bundle, Logger log) throws IOException {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path file = root.resolve(FILE);
        WorldConfig config;
        if (Files.exists(file)) {
            WorldConfigCodec.Result read = WorldConfigCodec.read(file.toString(),
                    Files.readString(file, StandardCharsets.UTF_8));
            if (!read.isClean()) {
                log.warn("{}", read.report().describe());
            }
            config = read.config();
            log.info("Read the world's Arboretum config from {}: collection {}, {} species, {} / {}", FILE,
                    config.collectionId(), config.species().size(), config.generationMode().id(),
                    config.spawningRule().id());
        } else {
            CollectionDefinition collection = bundle.collections().get(DEFAULT_COLLECTION);
            if (collection == null) {
                throw new IOException("the default collection " + DEFAULT_COLLECTION + " is not bundled");
            }
            config = WorldConfig.forCollection(collection, GenerationMode.NATURAL, SpawningRule.ECOLOGICAL);
            Files.createDirectories(root);
            Files.writeString(file, WorldConfigCodec.write(config), StandardCharsets.UTF_8);
            log.info("Created the world's Arboretum config in {}: collection {}", FILE, config.collectionId());
        }
        CollectionResolver.Resolution resolution = config.resolve(bundle.catalogue());
        if (!resolution.report().isClean()) {
            log.warn("{}", resolution.report().describe());
        }
        log.info("This world grows: {}", resolution.selected().keySet());
        WorldState state = new WorldState(config, resolution, PlacedTrees.load(root));
        current = state;
        return state;
    }

    void recordAttempt(String attempt) {
        if (attempts.size() < ATTEMPT_CAPACITY) {
            attempts.add(attempt);
        }
    }

    void countRefusal(String reason) {
        refusals.computeIfAbsent(reason, key -> new AtomicInteger()).incrementAndGet();
    }
}
