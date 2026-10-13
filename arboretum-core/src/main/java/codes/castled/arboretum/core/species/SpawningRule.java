package codes.castled.arboretum.core.species;

/**
 * How strictly a species' documented habitat is applied when choosing where it grows.
 *
 * <p>Separate from {@link GenerationMode} because "how many" and "where" are different questions.
 * Keeping them apart means every combination is meaningful: a Sparse + Mixed world is a
 * lightweight, creatively varied one, and Abundant + Ecological is strict but dense.
 *
 * <p>Neither setting relaxes placement safety. A mixed world still refuses to grow a tree into
 * bedrock, a cave ceiling, or a chunk that is not loaded.
 */
public enum SpawningRule {

    /**
     * Strict: biome, tag, climate and ground must all match the species' documented habitat.
     * The default, because it is what makes the trees read as real rather than as random greenery.
     */
    ECOLOGICAL("ecological"),

    /**
     * Wider: any biome the species has ever been recorded in, and the climate band widened by
     * {@link #CLIMATE_RELAXATION}. Ground and placement rules are untouched.
     */
    MIXED("mixed");

    /**
     * How far the climate band is widened in mixed mode, as a fraction of the species' own range.
     *
     * <p>A band stated as 0.8..1.2 becomes -0.08..2.08 in mixed mode, which reaches adjacent
     * climate zones without reaching the far side of the world.
     */
    public static final float CLIMATE_RELAXATION = 0.1F;

    private final String id;

    SpawningRule(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** @return the effective temperature band for a species under this rule. */
    public SpeciesDefinition.FloatRange effectiveTemperature(SpeciesDefinition.Ecology ecology) {
        return widen(ecology.temperature());
    }

    /** @return the effective moisture band for a species under this rule. */
    public SpeciesDefinition.FloatRange effectiveMoisture(SpeciesDefinition.Ecology ecology) {
        return widen(ecology.moisture());
    }

    private SpeciesDefinition.FloatRange widen(SpeciesDefinition.FloatRange range) {
        if (this == ECOLOGICAL) {
            return range;
        }
        float min = range.min();
        float max = range.max();
        if (Float.isInfinite(min) || Float.isInfinite(max)) {
            return range;
        }
        float padding = (max - min) * CLIMATE_RELAXATION;
        return new SpeciesDefinition.FloatRange(min - padding, max + padding);
    }

    public static SpawningRule parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ECOLOGICAL;
        }
        String text = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (SpawningRule rule : values()) {
            if (rule.id.equals(text)) {
                return rule;
            }
        }
        return ECOLOGICAL;
    }
}