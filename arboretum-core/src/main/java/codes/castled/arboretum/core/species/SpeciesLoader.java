package codes.castled.arboretum.core.species;

import com.google.gson.JsonObject;
import codes.castled.arboretum.core.data.DataReader;
import codes.castled.arboretum.core.data.ValidationReport;
import codes.castled.arboretum.core.id.ArboretumId;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a species definition from JSON and validates it.
 *
 * <p>Loading and validating are one operation on purpose. A definition that reaches the catalogue
 * has already been checked, so no downstream subsystem has to re-verify it and no subsystem can
 * forget to.
 *
 * <p>What counts as an error is a judgement call worth stating plainly:
 * <ul>
 *   <li><b>Error</b> - the definition cannot work: no id, no templates, a template path that is not
 *       a string, a climate range with min above max, a marker that is not a block id. A definition
 *       with an error is rejected and the catalogue carries on without it.</li>
 *   <li><b>Warning</b> - the definition works but something was ignored or defaulted: an unknown
 *       field, a leaf filter left empty, a missing reference URL. These load, because refusing to
 *       load a tree over a missing citation would be hostile.</li>
 * </ul>
 */
public final class SpeciesLoader {

    private static final Set<String> ROOT_FIELDS = Set.of(
            "id", "schema", "botany", "visuals", "structure", "ecology");

    private static final Set<String> BOTANY_FIELDS = Set.of(
            "common_name", "scientific_name", "genus", "family", "native_range", "habitat",
            "climate", "distinguishing_characteristics", "interesting_facts", "reference_urls",
            "attribution", "verified");

    private static final Set<String> VISUAL_FIELDS = Set.of(
            "carrier_block", "log_block", "log_top_block", "leaf_block", "leaf_cutout",
            "root_block", "assets");

    private static final Set<String> STRUCTURE_FIELDS = Set.of(
            "trunk_template", "canopy_template", "roots_template", "log_markers", "leaf_markers",
            "extra_markers", "growable_on", "leaf_filter", "log_filter", "replaceable_blocks",
            "max_log_depth", "random_rotation", "allow_mirroring");

    private static final Set<String> ECOLOGY_FIELDS = Set.of(
            "biomes", "biome_tags", "ground_blocks", "temperature", "moisture", "weight",
            "rarity", "mode_weight_overrides");

    /** The fields a species file may mark as verified. */
    private static final Set<String> VERIFIABLE_FIELDS = Set.of(
            "common_name", "scientific_name", "genus", "family", "native_range", "habitat",
            "climate", "distinguishing_characteristics", "interesting_facts");

    private SpeciesLoader() {
    }

    /**
     * Parses and validates one species file.
     *
     * @param source a human-readable path used in every reported problem
     * @return the definition, or null when the report contains errors
     */
    public static SpeciesDefinition load(String source, JsonObject json) {
        return read(source, json).value();
    }

    /** As {@link #load}, keeping the report that explains a rejection. */
    public static codes.castled.arboretum.core.data.Loaded<SpeciesDefinition> read(String source, JsonObject json) {
        ValidationReport report = new ValidationReport(source);
        DataReader reader = new DataReader(json, "$", report);
        reader.warnUnknown(ROOT_FIELDS);

        ArboretumId id = readId(source, json, report);
        int schema = reader.optionalInt("schema", SpeciesDefinition.CURRENT_SCHEMA);
        if (schema > SpeciesDefinition.CURRENT_SCHEMA) {
            report.error("$.schema", "file targets schema " + schema + " but this Arboretum reads up to "
                    + SpeciesDefinition.CURRENT_SCHEMA + ". Update Arboretum, or ask the pack author "
                    + "to lower the schema.");
        }
        if (schema < 1) {
            report.error("$.schema", "must be 1 or greater, was " + schema + ".");
        }

        SpeciesDefinition.Botany botany = readBotany(reader, report);
        SpeciesDefinition.Visuals visuals = readVisuals(reader, report);
        SpeciesDefinition.Structure structure = readStructure(reader, report);
        SpeciesDefinition.Ecology ecology = readEcology(reader, report);

        if (report.hasErrors()) {
            return new codes.castled.arboretum.core.data.Loaded<>(null, report);
        }
        return new codes.castled.arboretum.core.data.Loaded<>(
                new SpeciesDefinition(id, schema, botany, visuals, structure, ecology), report);
    }

    private static ArboretumId readId(String source, JsonObject json, ValidationReport report) {
        DataReader reader = new DataReader(json, "$", report);
        String raw = reader.optionalString("id", null);
        if (raw == null) {
            report.error("$.id", "required field is missing. Every species needs a stable id such as "
                    + "\"arboretum:baobab\".");
            return null;
        }
        try {
            return ArboretumId.parse(raw);
        } catch (ArboretumId.InvalidIdException exception) {
            report.error("$.id", exception.getMessage());
            return null;
        }
    }

    private static SpeciesDefinition.Botany readBotany(DataReader reader, ValidationReport report) {
        DataReader botany = reader.optionalObject("botany");
        if (botany == null) {
            report.error("$.botany", "required object is missing. A species without botanical "
                    + "identity cannot appear in the field guide.");
            return null;
        }
        botany.warnUnknown(BOTANY_FIELDS);

        String commonName = botany.requireString("common_name");
        String scientificName = botany.requireString("scientific_name");
        validateBinomial(scientificName, report);

        List<String> references = botany.optionalStringList("reference_urls");
        if (references.isEmpty()) {
            report.warn("$.botany.reference_urls",
                    "no reference urls. Arboretum asks that every botanical claim be checkable; an "
                            + "empty list is allowed but makes review harder.");
        }
        for (String url : references) {
            if (!url.startsWith("https://")) {
                report.warn("$.botany.reference_urls", "'" + url + "' is not an https url.");
            }
        }

        Set<String> verified = new LinkedHashSet<>(botany.optionalStringSet("verified"));
        for (String field : verified) {
            if (!VERIFIABLE_FIELDS.contains(field)) {
                report.error("$.botany.verified", "'" + field + "' is not a verifiable field. "
                        + "Valid fields: " + String.join(", ", sorted(VERIFIABLE_FIELDS)) + ".");
            }
        }
        if (verified.isEmpty()) {
            report.warn("$.botany.verified",
                    "no fields are marked verified. The field guide labels such facts as unconfirmed.");
        }

        return new SpeciesDefinition.Botany(
                commonName,
                scientificName,
                botany.optionalString("genus", null),
                botany.optionalString("family", null),
                botany.optionalString("native_range", null),
                botany.optionalString("habitat", null),
                botany.optionalString("climate", null),
                botany.optionalStringList("distinguishing_characteristics"),
                botany.optionalStringList("interesting_facts"),
                references,
                botany.optionalString("attribution", null),
                verified);
    }

    /**
     * Checks the shape of a binomial: {@code Genus species}. Rejects a full sentence, which is the
     * mistake that would otherwise reach the field guide looking authoritative.
     */
    private static void validateBinomial(String binomial, ValidationReport report) {
        if (binomial == null) {
            return;
        }
        String text = binomial.trim();
        if (text.split("\\s+").length != 2) {
            report.error("$.botany.scientific_name",
                    "'" + binomial + "' is not a binomial. It should be exactly two words: the genus "
                            + "capitalised and the specific epithet lower-case, e.g. \"Adansonia digitata\".");
            return;
        }
        String[] parts = text.split("\\s+");
        if (!Character.isUpperCase(parts[0].charAt(0))) {
            report.warn("$.botany.scientific_name",
                    "the genus '" + parts[0] + "' should start with a capital letter.");
        }
        if (Character.isUpperCase(parts[1].charAt(0))) {
            report.warn("$.botany.scientific_name",
                    "the specific epithet '" + parts[1] + "' should start with a lower-case letter.");
        }
    }

    private static SpeciesDefinition.Visuals readVisuals(DataReader reader, ValidationReport report) {
        DataReader visuals = reader.optionalObject("visuals");
        if (visuals == null) {
            report.error("$.visuals", "required object is missing.");
            return null;
        }
        visuals.warnUnknown(VISUAL_FIELDS);

        String logBlock = visuals.requireString("log_block");
        String leafBlock = visuals.requireString("leaf_block");
        requireBlockId(logBlock, "$.visuals.log_block", report);
        requireBlockId(leafBlock, "$.visuals.leaf_block", report);

        String logTopBlock = visuals.optionalString("log_top_block", null);
        if (logTopBlock != null) {
            requireBlockId(logTopBlock, "$.visuals.log_top_block", report);
        }
        String rootBlock = visuals.optionalString("root_block", null);
        if (rootBlock != null) {
            requireBlockId(rootBlock, "$.visuals.root_block", report);
        }
        String carrierBlock = visuals.optionalString("carrier_block", null);
        if (carrierBlock != null) {
            requireBlockId(carrierBlock, "$.visuals.carrier_block", report);
        }

        return new SpeciesDefinition.Visuals(
                carrierBlock,
                logBlock,
                logTopBlock,
                leafBlock,
                visuals.optionalBoolean("leaf_cutout", false),
                rootBlock,
                visuals.optionalStringMap("assets"));
    }

    private static SpeciesDefinition.Structure readStructure(DataReader reader, ValidationReport report) {
        DataReader structure = reader.optionalObject("structure");
        if (structure == null) {
            report.error("$.structure", "required object is missing.");
            return null;
        }
        structure.warnUnknown(STRUCTURE_FIELDS);

        String trunk = structure.requireString("trunk_template");
        String canopy = structure.requireString("canopy_template");
        requireTemplatePath(trunk, "$.structure.trunk_template", report);
        requireTemplatePath(canopy, "$.structure.canopy_template", report);

        String roots = structure.optionalString("roots_template", null);
        if (roots != null) {
            requireTemplatePath(roots, "$.structure.roots_template", report);
        }

        List<String> logMarkers = structure.optionalStringList("log_markers");
        List<String> leafMarkers = structure.optionalStringList("leaf_markers");
        if (logMarkers.isEmpty()) {
            report.error("$.structure.log_markers", "at least one marker is required, otherwise the "
                    + "trunk template has no blocks to replace.");
        }
        if (leafMarkers.isEmpty()) {
            report.warn("$.structure.leaf_markers",
                    "no leaf markers. The species will place a trunk with no foliage.");
        }
        for (String marker : logMarkers) {
            requireBlockId(marker, "$.structure.log_markers", report);
        }
        for (String marker : leafMarkers) {
            requireBlockId(marker, "$.structure.leaf_markers", report);
        }

        Map<String, String> extraMarkers = structure.optionalStringMap("extra_markers");
        for (var entry : extraMarkers.entrySet()) {
            requireBlockId(entry.getKey(), "$.structure.extra_markers", report);
            requireBlockId(entry.getValue(), "$.structure.extra_markers", report);
        }
        for (String marker : logMarkers) {
            if (extraMarkers.containsKey(marker)) {
                report.error("$.structure.extra_markers",
                        "'" + marker + "' is already a log marker; a marker cannot be both.");
            }
        }
        for (String marker : leafMarkers) {
            if (extraMarkers.containsKey(marker)) {
                report.error("$.structure.extra_markers",
                        "'" + marker + "' is already a leaf marker; a marker cannot be both.");
            }
        }

        Set<String> groundFilter = structure.optionalStringSet("growable_on");
        if (groundFilter.isEmpty()) {
            report.error("$.structure.growable_on", "at least one block is required. An empty filter "
                    + "would let the species grow on anything, including air and water.");
        }
        for (String block : groundFilter) {
            requireBlockId(block, "$.structure.growable_on", report);
        }

        Set<String> leafFilter = structure.optionalStringSet("leaf_filter");
        Set<String> logFilter = structure.optionalStringSet("log_filter");
        for (String block : leafFilter) {
            requireBlockId(block, "$.structure.leaf_filter", report);
        }
        for (String block : logFilter) {
            requireBlockId(block, "$.structure.log_filter", report);
        }

        int maxLogDepth = structure.optionalInt("max_log_depth", 3);
        if (maxLogDepth < 0 || maxLogDepth > 16) {
            report.error("$.structure.max_log_depth",
                    "must be between 0 and 16, was " + maxLogDepth + ". The value is how far below "
                            + "the trunk base Arboretum looks for ground.");
        }

        return new SpeciesDefinition.Structure(
                trunk,
                canopy,
                roots,
                logMarkers,
                leafMarkers,
                extraMarkers,
                groundFilter,
                leafFilter,
                logFilter,
                structure.optionalEnum("replaceable_blocks", SpeciesDefinition.ReplaceableBlocks.class,
                        SpeciesDefinition.ReplaceableBlocks.BLOCK, "BLOCK", "PASSTHROUGH", "PIERCE"),
                maxLogDepth,
                structure.optionalBoolean("random_rotation", true),
                structure.optionalBoolean("allow_mirroring", false));
    }

    private static SpeciesDefinition.Ecology readEcology(DataReader reader, ValidationReport report) {
        DataReader ecology = reader.optionalObject("ecology");
        if (ecology == null) {
            report.error("$.ecology", "required object is missing.");
            return null;
        }
        ecology.warnUnknown(ECOLOGY_FIELDS);

        List<String> biomes = ecology.optionalStringList("biomes");
        List<String> biomeTags = ecology.optionalStringList("biome_tags");
        if (biomes.isEmpty() && biomeTags.isEmpty()) {
            report.error("$.ecology", "at least one of 'biomes' or 'biome_tags' is required. A species "
                    + "with no eligible biome would never generate.");
        }

        float[] temperature = ecology.optionalFloatRange("temperature");
        float[] moisture = ecology.optionalFloatRange("moisture");
        if (temperature == null) {
            temperature = new float[]{
                    SpeciesDefinition.FloatRange.any().min(), SpeciesDefinition.FloatRange.any().max()};
        }
        if (moisture == null) {
            moisture = new float[]{
                    SpeciesDefinition.FloatRange.any().min(), SpeciesDefinition.FloatRange.any().max()};
        }

        int weight = ecology.optionalInt("weight", 1);
        if (weight < 0) {
            report.error("$.ecology.weight", "must not be negative, was " + weight + ".");
        }
        if (weight == 0) {
            report.warn("$.ecology.weight",
                    "a weight of 0 means the species never generates. That is legal - it is how a "
                            + "species is kept in the catalogue but out of a world - but is rarely "
                            + "what an author means.");
        }

        Set<String> groundBlocks = ecology.optionalStringSet("ground_blocks");
        for (String block : groundBlocks) {
            requireBlockId(block, "$.ecology.ground_blocks", report);
        }

        return new SpeciesDefinition.Ecology(
                biomes,
                biomeTags,
                groundBlocks,
                new SpeciesDefinition.FloatRange(temperature[0], temperature[1]),
                new SpeciesDefinition.FloatRange(moisture[0], moisture[1]),
                weight,
                ecology.optionalEnum("rarity", SpeciesDefinition.Rarity.class,
                        SpeciesDefinition.Rarity.COMMON,
                        "COMMON", "UNCOMMON", "RARE", "VERY_RARE"),
                ecology.optionalIntMap("mode_weight_overrides"));
    }

    private static void requireBlockId(String blockId, String field, ValidationReport report) {
        try {
            ArboretumId.parse(blockId);
        } catch (ArboretumId.InvalidIdException exception) {
            report.error(field, exception.getMessage());
        }
    }

    private static void requireTemplatePath(String path, String field, ValidationReport report) {
        if (path.endsWith(".nbt")) {
            return;
        }
        if (path.contains(".")) {
            report.error(field, "template paths should end in .nbt, found '" + path + "'.");
            return;
        }
        report.warn(field, "template path '" + path + "' has no .nbt extension.");
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }
}