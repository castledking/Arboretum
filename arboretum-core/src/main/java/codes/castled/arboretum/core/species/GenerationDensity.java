package codes.castled.arboretum.core.species;

/**
 * How often a species' tree feature places a tree in a chunk of its habitat.
 *
 * <p>The feature is offered once per eligible chunk. It grows a tree with probability
 * {@code effectiveWeight / WEIGHT_PER_CERTAINTY}, where the effective weight is the species'
 * weight after the collection's override and the generation mode
 * ({@link GenerationMode#effectiveWeight}). So on Natural a weight-2 baobab tries in about one
 * savanna chunk in 24, and Abundant's fourfold weight makes that one in 6.
 *
 * <p>This is the vertical-slice rule. The design's landmark grid and ecotone groves replace it
 * per species; until then every species shares it.
 */
public final class GenerationDensity {

    /** The effective weight at which a tree is attempted in every eligible chunk. */
    public static final float WEIGHT_PER_CERTAINTY = 48F;

    private GenerationDensity() {
    }

    public static float chancePerChunk(int effectiveWeight) {
        if (effectiveWeight <= 0) {
            return 0F;
        }
        return Math.min(1F, effectiveWeight / WEIGHT_PER_CERTAINTY);
    }
}
