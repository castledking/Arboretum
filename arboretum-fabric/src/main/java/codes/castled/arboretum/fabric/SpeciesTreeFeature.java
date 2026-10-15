package codes.castled.arboretum.fabric;

import codes.castled.arboretum.core.collection.CollectionResolver;
import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.GenerationDensity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * {@code arboretum:species_tree}: grows one species, if the world's config includes it.
 *
 * <p>Offered once per chunk of the species' biomes by its placed feature; rolls the species'
 * chance there ({@link GenerationDensity}), so the world's collection and generation mode decide
 * how many grow - none at all for a species the world didn't choose.
 */
record SpeciesTreeFeature(String species) implements Feature {

    static final MapCodec<SpeciesTreeFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("species").forGetter(SpeciesTreeFeature::species)
    ).apply(instance, SpeciesTreeFeature::new));

    /** A feature writes in its own chunk and the ones around it. */
    static final int WRITE_REACH = 1;

    @Override
    public MapCodec<SpeciesTreeFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        WorldState state = WorldState.current();
        if (state == null || !state.config.worldSettings().generationEnabled()) {
            return false;
        }
        CollectionResolver.ResolvedSpecies resolved = state.resolution.selected().get(ArboretumId.parse(species));
        if (resolved == null || random.nextFloat() >= GenerationDensity.chancePerChunk(resolved.weight())) {
            return false;
        }
        codes.castled.arboretum.core.template.Rotation rotation = Grower.randomRotation(resolved.definition(), random);
        state.recordAttempt(resolved.definition().id().path() + "@" + origin.getX() + "," + origin.getY() + ","
                + origin.getZ() + "/" + rotation.name());
        Grower.Result result = Grower.grow(level, new RegionBlockQuery(level, origin, WRITE_REACH),
                ArboretumFabric.bundle(), resolved.definition(), origin, rotation, random);
        if (result.grown()) {
            state.placed.add(result.tree());
            return true;
        }
        state.countRefusal(result.refusal());
        return false;
    }
}
