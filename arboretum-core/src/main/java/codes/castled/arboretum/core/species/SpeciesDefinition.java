package codes.castled.arboretum.core.species;

import codes.castled.arboretum.core.id.ArboretumId;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything Arboretum knows about one tree species.
 *
 * <p>Defined once and consumed by every subsystem: world generation reads {@link #structure()} and
 * {@link #ecology()}, the field guide reads {@link #botany()}, the resource-pack exporter reads
 * {@link #visuals()}, and the platform adapters read nothing but this record. That is the point of
 * the record existing: no subsystem gets its own private copy of a species.
 *
 * @param id       stable identifier, {@code arboretum:baobab}
 * @param schema   version of the species file layout that produced this definition
 * @param botany   scientific identity and player-facing lore
 * @param visuals  block and asset choices for this species
 * @param structure how the species is built from structure templates
 * @param ecology  where and how densely it spawns
 */
public record SpeciesDefinition(
        ArboretumId id,
        int schema,
        Botany botany,
        Visuals visuals,
        Structure structure,
        Ecology ecology
) {

    /** The species-file schema version Arboretum currently writes and requires. */
    public static final int CURRENT_SCHEMA = 1;

    /**
     * Scientific identity.
     *
     * <p>{@code verified} marks which facts were checked against a reference and which are flavour.
     * The field guide shows the distinction rather than presenting invented detail as fact; see
     * docs/species-schema.md.
     */
    public record Botany(
            String commonName,
            String scientificName,
            String genus,
            String family,
            String nativeRange,
            String habitat,
            String climate,
            List<String> distinguishingCharacteristics,
            List<String> interestingFacts,
            List<String> referenceUrls,
            String attribution,
            Set<String> verifiedFields
    ) {
        public Botany {
            distinguishingCharacteristics = List.copyOf(distinguishingCharacteristics);
            interestingFacts = List.copyOf(interestingFacts);
            referenceUrls = List.copyOf(referenceUrls);
            verifiedFields = Set.copyOf(verifiedFields);
        }

        /** @return true when the named botanical field was checked against a reference. */
        public boolean isVerified(String field) {
            return verifiedFields.contains(field);
        }
    }

    /**
     * Block and asset choices.
     *
     * @param carrierBlockId  the vanilla block this species retextures in resource-pack-only mode
     * @param logBlockId      the block placed for log markers in mod and Paper modes
     * @param logTopBlockId  the block placed for the top/bottom of logs, when it differs
     * @param leafBlockId     the block placed for leaf markers
     * @param leafCutout      whether leaves need transparency, which the pack must declare
     * @param rootBlockId     optional block for root markers
     */
    public record Visuals(
            String carrierBlockId,
            String logBlockId,
            String logTopBlockId,
            String leafBlockId,
            boolean leafCutout,
            String rootBlockId,
            Map<String, String> assets
    ) {
        public Visuals {
            assets = Map.copyOf(assets);
        }
    }

    /**
     * How the species is assembled at placement time.
     *
     * @param trunkTemplate     resource path of the trunk template
     * @param canopyTemplate    resource path of the canopy template
     * @param rootsTemplate     optional resource path of a roots template
     * @param trunkTemplate     the trunk part, or null for a species of whole trees
     * @param canopyTemplate    the canopy part, or null (a trunk template may hold its own leaves)
     * @param logMarkers        template blocks treated as logs
     * @param leafMarkers       template blocks treated as leaves
     * @param extraMarkers      template block -> placed block, for roots, flowers, hanging strands
     * @param groundFilter      blocks the tree may grow on
     * @param leafFilter        blocks leaves may replace
     * @param logFilter         blocks logs may replace
     * @param replaceableBlocks the filter behaviour applied when a position is occupied
     * @param templates         whole-tree templates, one picked per tree; empty for trunk/canopy parts
     * @param markerBlocks      marker -> the block it becomes; a marker not listed stays itself
     */
    public record Structure(
            String trunkTemplate,
            String canopyTemplate,
            String rootsTemplate,
            List<String> logMarkers,
            List<String> leafMarkers,
            Map<String, String> extraMarkers,
            Set<String> groundFilter,
            Set<String> leafFilter,
            Set<String> logFilter,
            ReplaceableBlocks replaceableBlocks,
            int maxLogDepth,
            boolean randomRotation,
            boolean allowMirroring,
            List<Variant> templates,
            Map<String, String> markerBlocks
    ) {
        public Structure {
            templates = List.copyOf(templates);
            markerBlocks = Map.copyOf(markerBlocks);
            logMarkers = List.copyOf(logMarkers);
            leafMarkers = List.copyOf(leafMarkers);
            extraMarkers = Map.copyOf(extraMarkers);
            groundFilter = Set.copyOf(groundFilter);
            leafFilter = Set.copyOf(leafFilter);
            logFilter = Set.copyOf(logFilter);
        }

        /** @return true when the species has a roots template to place as well as trunk and canopy. */
        public boolean hasRoots() {
            return rootsTemplate != null && !rootsTemplate.isBlank();
        }

        /** @return true when the species is a set of whole trees rather than trunk and canopy parts. */
        public boolean wholeTrees() {
            return !templates.isEmpty();
        }

        /** @return the block a marker becomes: its mapped block, or the marker itself. */
        public String blockFor(String marker) {
            return markerBlocks.getOrDefault(marker, marker);
        }
    }

    /**
     * One whole-tree template, and how often it is chosen against the species' others.
     *
     * @param path   the template's resource path, e.g. {@code data/arboretum/templates/jacaranda/jacaranda_01.nbt}
     * @param weight relative chance of being chosen, at least 1
     */
    public record Variant(String path, int weight) {
    }

    /**
     * Where the species spawns and how often.
     *
     * @param biomes           biome ids the species may spawn in directly
     * @param biomeTags        biome tags the species may spawn in
     * @param groundBlocks     surface blocks the species prefers
     * @param temperature      acceptable temperature band
     * @param moisture         acceptable moisture band, absent on versions without the climate sampler
     * @param weight           relative spawn weight against other eligible species
     * @param rarity           the abundance category shown in the field guide
     */
    public record Ecology(
            List<String> biomes,
            List<String> biomeTags,
            Set<String> groundBlocks,
            FloatRange temperature,
            FloatRange moisture,
            int weight,
            Rarity rarity,
            Map<String, Integer> modeWeightOverrides
    ) {
        public Ecology {
            biomes = List.copyOf(biomes);
            biomeTags = List.copyOf(biomeTags);
            groundBlocks = Set.copyOf(groundBlocks);
            modeWeightOverrides = Map.copyOf(modeWeightOverrides);
        }

        /** @return the weight for a generation mode, falling back to the species weight. */
        public int weightFor(GenerationMode mode) {
            return modeWeightOverrides.getOrDefault(mode.id(), weight);
        }
    }

    /** An inclusive floating-point range, used for temperature and moisture. */
    public record FloatRange(float min, float max) {
        public FloatRange {
            if (min > max) {
                throw new IllegalArgumentException("Range min " + min + " is above max " + max + ".");
            }
        }

        public boolean contains(float value) {
            return value >= min && value <= max;
        }

        public static FloatRange any() {
            return new FloatRange(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
        }
    }

    /** How abundant a species is, in the terms the field guide uses. */
    public enum Rarity {
        COMMON,
        UNCOMMON,
        RARE,
        VERY_RARE
    }

    /**
     * How the engine treats an occupied position.
     *
     * <p>{@link #BLOCK} refuses to grow, {@link #PASSTHROUGH} grows around it, {@link #PIERCE}
     * removes it. The default is {@link #BLOCK} for logs and {@link #PASSTHROUGH} for leaves so a
     * tree never silently deletes a player's build.
     */
    public enum ReplaceableBlocks {
        BLOCK,
        PASSTHROUGH,
        PIERCE
    }
}