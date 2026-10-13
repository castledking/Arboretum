package codes.castled.arboretum.core.config;

import codes.castled.arboretum.core.collection.CollectionDefinition;
import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.GenerationMode;
import codes.castled.arboretum.core.species.SpawningRule;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Arboretum settings for one world, in the one shape every layer consumes.
 *
 * <p>The world-creation screen writes this, the server reads it, and the generation engine takes it
 * as its only input. The screen never touches generation internals and the generator never asks the
 * screen, which is the whole reason this record exists: settings that cannot reach the thing they
 * configure cannot be tested.
 *
 * <p>Two halves, deliberately:
 * <ul>
 *   <li>{@code selection} - the world preset. Written once at creation. Changing it would change
 *       what the world is, so it is stored but the generator only ever sees the frozen copy.</li>
 *   <li>{@code worldSettings} - the server-authoritative live settings. Readable and writable at
 *       runtime by the server, never writable by a client.</li>
 * </ul>
 *
 * <p>{@link #schema} is what makes an Arboretum world openable after a mod update. See
 * {@link WorldConfigCodec} for the migration path.
 */
public record WorldConfig(
        int schema,
        ArboretumId collectionId,
        List<ArboretumId> species,
        Map<ArboretumId, Integer> weightOverrides,
        GenerationMode generationMode,
        SpawningRule spawningRule,
        WorldSettings worldSettings
) {

    /** The world-config schema this build writes. */
    public static final int CURRENT_SCHEMA = 1;

    /** The lowest schema this build can still read. */
    public static final int MINIMUM_SUPPORTED_SCHEMA = 1;

    public WorldConfig {
        species = List.copyOf(species);
        weightOverrides = Map.copyOf(weightOverrides);
    }

    /** The collection this world was created with. */
    public ArboretumId collectionId() {
        return collectionId;
    }

    /** @return the frozen species list, which is what generation actually iterates. */
    public List<ArboretumId> species() {
        return species;
    }

    /**
     * The settings that may change while a world is running.
     *
     * @param generationEnabled  master switch for Arboretum tree generation
     * @param attemptsPerChunk   base candidate attempts per chunk before the generation mode scales it
     * @param spacing           minimum horizontal distance in blocks between Arboretum trees
     * @param minSpacing        smallest value {@code spacing} may take, so a config edit cannot
     *                          set it so high that generation never succeeds
     * @param discovery          how much of the field guide is gated behind discovery
     * @param discoveryRange    how far a player must be from a tree to identify it, in blocks
     * @param announceDiscovery whether identifying a species prints a message
     */
    public record WorldSettings(
            boolean generationEnabled,
            int attemptsPerChunk,
            int spacing,
            int minSpacing,
            DiscoveryMode discovery,
            int discoveryRange,
            boolean announceDiscovery
    ) {
        public WorldSettings {
            if (attemptsPerChunk < 0) {
                throw new IllegalArgumentException("attemptsPerChunk must not be negative.");
            }
            if (minSpacing < 1) {
                throw new IllegalArgumentException("minSpacing must be at least 1.");
            }
            if (spacing < minSpacing) {
                throw new IllegalArgumentException(
                        "spacing " + spacing + " is below minSpacing " + minSpacing + ".");
            }
            if (discoveryRange < 0) {
                throw new IllegalArgumentException("discoveryRange must not be negative.");
            }
        }

        /**
         * The defaults for a freshly created world.
         *
         * <p>Tuned so that selecting Arboretum and pressing Create produces a world with trees in
         * it, with no further configuration.
         */
        public static WorldSettings defaults() {
            return new WorldSettings(true, 4, 3, 1, DiscoveryMode.RELAXED, 8, true);
        }
    }

    /** How much of the field guide is gated behind actually finding a tree. */
    public enum DiscoveryMode {
        /** Trees generate but nothing is gated. For creative builders. */
        DISABLED("disabled"),
        /** Identifying a tree is a single interaction, and everything unlocks at once. */
        RELAXED("relaxed"),
        /**
         * Identification and further observations are separate steps that reveal more each time.
         * Kept short deliberately: a field guide that takes hours to fill is a chore.
         */
        FIELD_RESEARCH("field_research");

        private final String id;

        DiscoveryMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** @return how many observation steps this mode needs to complete an entry. */
        public int observationsToComplete() {
            return switch (this) {
                case DISABLED -> 0;
                case RELAXED -> 1;
                case FIELD_RESEARCH -> 3;
            };
        }

        public static DiscoveryMode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return RELAXED;
            }
            String text = raw.trim().toLowerCase(java.util.Locale.ROOT);
            for (DiscoveryMode mode : values()) {
                if (mode.id.equals(text)) {
                    return mode;
                }
            }
            return RELAXED;
        }
    }

    /** The config written when a player creates an Arboretum world. */
    public static WorldConfig forCollection(CollectionDefinition collection,
                                            GenerationMode generationMode,
                                            SpawningRule spawningRule) {
        return new WorldConfig(
                CURRENT_SCHEMA,
                collection.id(),
                new java.util.ArrayList<>(collection.enabledSpecies()),
                weightOverridesFrom(collection),
                generationMode,
                spawningRule,
                WorldSettings.defaults());
    }

    private static Map<ArboretumId, Integer> weightOverridesFrom(CollectionDefinition collection) {
        Map<ArboretumId, Integer> overrides = new LinkedHashMap<>();
        collection.overrides().forEach((id, override) -> {
            if (override.overridesWeight()) {
                overrides.put(id, override.weight());
            }
        });
        return overrides;
    }

    /**
     * The species this world generates, resolved against the installed catalogue.
     *
     * <p>From the frozen list, not the collection: a species a later version adds to the
     * collection doesn't appear in an existing world, and a removed collection changes nothing.
     * A frozen species the catalogue no longer has is left out, with a warning in the report -
     * the world keeps generating the rest.
     */
    public codes.castled.arboretum.core.collection.CollectionResolver.Resolution resolve(
            codes.castled.arboretum.core.species.SpeciesCatalogue catalogue) {
        Map<ArboretumId, CollectionDefinition.Override> overrides = new LinkedHashMap<>();
        weightOverrides.forEach((id, weight) -> overrides.put(id, CollectionDefinition.Override.weight(weight)));
        CollectionDefinition frozen = new CollectionDefinition(
                collectionId == null ? ArboretumId.arboretum("world") : collectionId,
                "this world", "the species frozen when the world was created", false, species, overrides);
        return codes.castled.arboretum.core.collection.CollectionResolver.resolve(
                catalogue, frozen, generationMode, spawningRule, Set.of());
    }

    /**
     * The config for a fresh world with no collection chosen yet.
     *
     * <p>Species list is empty rather than defaulted here. Filling in a default list at this point
     * would make the world depend on whatever catalogue happened to be installed at creation time,
     * which is exactly the instability the frozen list exists to prevent.
     */
    public static WorldConfig empty() {
        return new WorldConfig(CURRENT_SCHEMA, null, List.of(), Map.of(),
                GenerationMode.NATURAL, SpawningRule.ECOLOGICAL, WorldSettings.defaults());
    }

    /** @return a copy with a different live setting. */
    public WorldConfig withWorldSettings(WorldSettings settings) {
        return new WorldConfig(schema, collectionId, species, weightOverrides,
                generationMode, spawningRule, settings);
    }

    /** @return a copy with a different generation mode. Selection is untouched. */
    public WorldConfig withGenerationMode(GenerationMode mode) {
        return new WorldConfig(schema, collectionId, species, weightOverrides,
                mode, spawningRule, worldSettings);
    }

    /** @return a copy with a different spawning rule. */
    public WorldConfig withSpawningRule(SpawningRule rule) {
        return new WorldConfig(schema, collectionId, species, weightOverrides,
                generationMode, rule, worldSettings);
    }

    /** A one-line summary for the world-load log. */
    public String summary() {
        return "schema " + schema
                + ", collection " + (collectionId == null ? "<none>" : collectionId)
                + ", " + species.size() + " species"
                + ", " + generationMode.id() + "/" + spawningRule.id()
                + ", discovery " + worldSettings.discovery().id()
                + (worldSettings.generationEnabled() ? "" : ", generation DISABLED");
    }
}