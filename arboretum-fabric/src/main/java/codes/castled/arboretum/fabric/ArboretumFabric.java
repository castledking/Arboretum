package codes.castled.arboretum.fabric;

import codes.castled.arboretum.core.bundle.BundledData;
import codes.castled.arboretum.core.species.SpeciesDefinition;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arboretum on Fabric: loads the bundled catalogue, registers the tree feature and where each
 * species grows, and reads each world's config as its server starts.
 */
public final class ArboretumFabric implements ModInitializer {

    public static final String MOD_ID = "arboretum";
    static final Logger LOG = LoggerFactory.getLogger("Arboretum");

    private static BundledData bundle;

    /** The loaded catalogue; set before anything can generate. */
    static BundledData bundle() {
        return bundle;
    }

    @Override
    public void onInitialize() {
        try {
            bundle = BundledData.load(ArboretumFabric.class.getClassLoader());
        } catch (IOException exception) {
            throw new UncheckedIOException("Arboretum could not load its bundled data", exception);
        }
        LOG.info("{}; {} collections", bundle.catalogue().summary(), bundle.collections().size());
        String problems = bundle.describeProblems();
        if (!problems.isEmpty()) {
            LOG.warn("{}", problems);
        }

        Registry.register(BuiltInRegistries.FEATURE_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "species_tree"),
                SpeciesTreeFeature.CODEC);
        for (String name : List.of("baobab", "jacaranda")) {
            // Only species with structures and a placed feature generate; the rest stay catalogued.
            bundle.catalogue().get(codes.castled.arboretum.core.id.ArboretumId.arboretum(name))
                    .ifPresent(ArboretumFabric::addToItsBiomes);
        }

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                WorldState.load(server, bundle, LOG);
            } catch (IOException exception) {
                LOG.error("Arboretum could not read this world's config; no Arboretum trees will grow", exception);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            WorldState state = WorldState.current();
            if (state != null) {
                try {
                    state.placed.save();
                } catch (IOException exception) {
                    LOG.warn("Could not save the placed-tree log", exception);
                }
            }
            WorldState.clear();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                ArboretumCommand.register(dispatcher));
    }

    /** Offers the species' placed feature in every biome of its documented habitat. */
    private static void addToItsBiomes(SpeciesDefinition species) {
        List<ResourceKey<Biome>> biomes = new ArrayList<>();
        for (String id : species.ecology().biomes()) {
            biomes.add(ResourceKey.create(Registries.BIOME, Identifier.parse(id)));
        }
        Identifier feature = Identifier.fromNamespaceAndPath(species.id().namespace(), species.id().path());
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(biomes),
                GenerationStep.Decoration.VEGETAL_DECORATION, ResourceKey.create(Registries.PLACED_FEATURE, feature));
        LOG.info("{} grows in {}", species.id(), species.ecology().biomes());
    }
}
