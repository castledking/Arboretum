package codes.castled.arboretum.core.species;

/**
 * How densely Arboretum's species generate.
 *
 * <p>The modes scale a documented set of parameters rather than behaving arbitrarily. Each factor
 * multiplies a species' {@link SpeciesDefinition.Ecology#weightFor(GenerationMode)}, so changing
 * mode is a predictable rescale of the existing distribution rather than a different algorithm.
 *
 * <p>Density is deliberately independent of habitat: {@link #ABUNDANT} does not also relax which
 * biomes a species grows in. A player who chose ABUNDANT asked for more trees, not for baobabs in
 * a taiga. Relaxing habitat is the separate {@link SpawningRule} axis.
 */
public enum GenerationMode {

    /** Species weight x1, chunk attempt count x1. Plausible distribution. The default. */
    NATURAL("natural", 1.0F, 1.0F),

    /**
     * Species weight x4, chunk attempt count x2.
     *
     * <p>Not both multiplied together: doing so would give an 8x density spike in the forests that
     * already suit a species, which reads as "the mod broke the biome" rather than "there are more
     * trees here".
     */
    ABUNDANT("abundant", 4.0F, 2.0F),

    /**
     * Species weight /8, chunk attempt count /2, a 1/16 overall frequency.
     *
     * <p>Leaves the vanilla landscape mostly intact, for players who want Arboretum's trees to be
     * something you find rather than something you fell into.
     */
    SPARSE("sparse", 0.125F, 0.5F);

    private final String id;
    private final float weightFactor;
    private final float attemptFactor;

    GenerationMode(String id, float weightFactor, float attemptFactor) {
        this.id = id;
        this.weightFactor = weightFactor;
        this.attemptFactor = attemptFactor;
    }

    /** The stable string stored in world configuration files. */
    public String id() {
        return id;
    }

    /** Multiplies a species' declared weight. */
    public float weightFactor() {
        return weightFactor;
    }

    /** Multiplies the per-chunk candidate attempt count. */
    public float attemptFactor() {
        return attemptFactor;
    }

    /** @return the effective weight for a species in this mode, never less than 1. */
    public int effectiveWeight(SpeciesDefinition species) {
        return Math.max(1, Math.round(species.ecology().weightFor(this) * weightFactor));
    }

    /** @return the number of placement attempts per chunk for this mode. */
    public int effectiveAttempts(int baseAttempts) {
        return Math.max(1, Math.round(baseAttempts * attemptFactor));
    }

    /** Parses a stored mode id, defaulting to {@link #NATURAL} rather than failing a world load. */
    public static GenerationMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NATURAL;
        }
        String text = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (GenerationMode mode : values()) {
            if (mode.id.equals(text)) {
                return mode;
            }
        }
        return NATURAL;
    }
}