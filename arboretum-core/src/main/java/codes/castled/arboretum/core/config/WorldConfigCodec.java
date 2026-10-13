package codes.castled.arboretum.core.config;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import codes.castled.arboretum.core.data.ValidationReport;
import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.GenerationMode;
import codes.castled.arboretum.core.species.SpawningRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and writes {@link WorldConfig}, and migrates older schemas forward.
 *
 * <p>This is the boundary that keeps a world openable. A config written by Arboretum 0.1 has to
 * load in 0.4 after two more fields were added, and a config from a version this build does not know
 * has to fail with a message that says which version wrote it rather than with a
 * {@code NullPointerException} three frames later.
 *
 * <p>Reading is defensive on purpose: a hand-edited or corrupted config must produce defaults and
 * warnings, not a failed world load. Generation settings are the one thing that must never be
 * silently wrong, so anything unreadable in {@code world_settings} is reported and the
 * corresponding default is used.
 */
public final class WorldConfigCodec {

    private WorldConfigCodec() {
    }

    /** Serialises a config to the JSON stored in the world folder. */
    public static String write(WorldConfig config) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", config.schema());
        if (config.collectionId() != null) {
            json.addProperty("collection", config.collectionId().toString());
        }

        JsonArray species = new JsonArray();
        config.species().forEach(id -> species.add(id.toString()));
        json.add("species", species);

        if (!config.weightOverrides().isEmpty()) {
            JsonObject weights = new JsonObject();
            config.weightOverrides().forEach((id, weight) -> weights.addProperty(id.toString(), weight));
            json.add("weight_overrides", weights);
        }

        json.addProperty("generation_mode", config.generationMode().id());
        json.addProperty("spawning_rule", config.spawningRule().id());

        WorldConfig.WorldSettings settings = config.worldSettings();
        JsonObject worldSettings = new JsonObject();
        worldSettings.addProperty("generation_enabled", settings.generationEnabled());
        worldSettings.addProperty("attempts_per_chunk", settings.attemptsPerChunk());
        worldSettings.addProperty("spacing", settings.spacing());
        worldSettings.addProperty("min_spacing", settings.minSpacing());
        worldSettings.addProperty("discovery", settings.discovery().id());
        worldSettings.addProperty("discovery_range", settings.discoveryRange());
        worldSettings.addProperty("announce_discovery", settings.announceDiscovery());
        json.add("world_settings", worldSettings);

        return new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(json) + "\n";
    }

    /**
     * Parses a stored config, migrating older schemas forward.
     *
     * @param source path used in reported problems
     * @return the config and any problems; the config always has usable values
     */
    public static Result read(String source, String text) {
        ValidationReport report = new ValidationReport(source);
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(text == null ? "{}" : text);
        } catch (RuntimeException exception) {
            report.error("$", "could not be parsed as JSON (" + exception.getMessage()
                    + "). Falling back to defaults; this world will generate no Arboretum trees "
                    + "until the file is repaired.");
            return new Result(WorldConfig.empty(), report);
        }
        if (!parsed.isJsonObject()) {
            report.error("$", "must be a JSON object. Falling back to defaults.");
            return new Result(WorldConfig.empty(), report);
        }
        return read(source, parsed.getAsJsonObject(), report);
    }

    private static Result read(String source, JsonObject json, ValidationReport report) {
        int schema = readInt(json, "schema", 0, report);
        if (schema == 0) {
            report.error("$.schema", "missing or unreadable. A world config must declare its schema "
                    + "version. Falling back to schema " + WorldConfig.CURRENT_SCHEMA + " with "
                    + "defaults, which means this world will generate no Arboretum trees.");
            schema = WorldConfig.CURRENT_SCHEMA;
        } else if (schema < WorldConfig.MINIMUM_SUPPORTED_SCHEMA) {
            report.error("$.schema", "was written with schema " + schema + ", which this Arboretum "
                    + "no longer supports. Arboretum supports "
                    + WorldConfig.MINIMUM_SUPPORTED_SCHEMA + " and above.");
        } else if (schema > WorldConfig.CURRENT_SCHEMA) {
            report.error("$.schema", "was written by a newer Arboretum (schema " + schema
                    + "; this build reads up to " + WorldConfig.CURRENT_SCHEMA + "). Arboretum is "
                    + "keeping the world playable and generating nothing, rather than guessing. "
                    + "Update Arboretum to restore its trees.");
            return new Result(disableGeneration(WorldConfig.empty()), report);
        }

        ArboretumId collectionId = readId(json, "collection", report);
        List<ArboretumId> species = readIdArray(json, "species", report);
        Map<ArboretumId, Integer> weights = readWeightOverrides(json, report);

        GenerationMode generationMode = GenerationMode.parse(readString(json, "generation_mode"));
        SpawningRule spawningRule = SpawningRule.parse(readString(json, "spawning_rule"));
        WorldConfig.WorldSettings worldSettings = readWorldSettings(json, report);

        WorldConfig config = new WorldConfig(schema, collectionId, species, weights,
                generationMode, spawningRule, worldSettings);
        return new Result(migrate(schema, config, report), report);
    }

    /**
     * Brings a schema-1 config up to the current schema.
     *
     * <p>There is only one schema so far, so this is currently a no-op that exists so the next
     * migration has an obvious home and so the call site does not have to change. Each step is
     * applied in order from the config's schema up to {@link WorldConfig#CURRENT_SCHEMA}.
     */
    private static WorldConfig migrate(int fromSchema, WorldConfig config, ValidationReport report) {
        if (fromSchema >= WorldConfig.CURRENT_SCHEMA) {
            return config;
        }
        // Step 1: no earlier schema exists yet. When one does, add it here, from -> from + 1.
        report.warn("$.schema", "migrated from schema " + fromSchema + " to "
                + WorldConfig.CURRENT_SCHEMA + ".");
        return config;
    }

    private static WorldConfig.WorldSettings readWorldSettings(JsonObject json, ValidationReport report) {
        WorldConfig.WorldSettings defaults = WorldConfig.WorldSettings.defaults();
        if (!json.has("world_settings") || !json.get("world_settings").isJsonObject()) {
            if (json.has("world_settings")) {
                report.error("$.world_settings", "must be an object; using defaults.");
            }
            return defaults;
        }
        JsonObject settings = json.getAsJsonObject("world_settings");

        boolean enabled = readBoolean(settings, "generation_enabled", defaults.generationEnabled(), report);
        int attempts = readInt(settings, "attempts_per_chunk", defaults.attemptsPerChunk(), report);
        int minSpacing = readInt(settings, "min_spacing", defaults.minSpacing(), report);
        int spacing = readInt(settings, "spacing", defaults.spacing(), report);
        int range = readInt(settings, "discovery_range", defaults.discoveryRange(), report);
        boolean announce = readBoolean(settings, "announce_discovery", defaults.announceDiscovery(), report);
        WorldConfig.DiscoveryMode discovery =
                WorldConfig.DiscoveryMode.parse(readString(settings, "discovery"));

        if (attempts < 0) {
            report.error("$.world_settings.attempts_per_chunk",
                    "must not be negative; using " + defaults.attemptsPerChunk() + ".");
            attempts = defaults.attemptsPerChunk();
        }
        if (minSpacing < 1) {
            report.error("$.world_settings.min_spacing", "must be at least 1; using "
                    + defaults.minSpacing() + ".");
            minSpacing = defaults.minSpacing();
        }
        if (spacing < minSpacing) {
            report.error("$.world_settings.spacing",
                    "must be at least min_spacing (" + minSpacing + "); using " + minSpacing + ".");
            spacing = minSpacing;
        }
        if (range < 0) {
            report.error("$.world_settings.discovery_range", "must not be negative; using "
                    + defaults.discoveryRange() + ".");
            range = defaults.discoveryRange();
        }

        return new WorldConfig.WorldSettings(enabled, attempts, spacing, minSpacing, discovery, range, announce);
    }

    private static WorldConfig disableGeneration(WorldConfig config) {
        WorldConfig.WorldSettings settings = config.worldSettings();
        return config.withWorldSettings(new WorldConfig.WorldSettings(false,
                settings.attemptsPerChunk(), settings.spacing(), settings.minSpacing(),
                settings.discovery(), settings.discoveryRange(), settings.announceDiscovery()));
    }

    private static List<ArboretumId> readIdArray(JsonObject json, String field, ValidationReport report) {
        List<ArboretumId> ids = new ArrayList<>();
        if (!json.has(field)) {
            return ids;
        }
        if (!json.get(field).isJsonArray()) {
            report.error("$." + field, "must be an array of species ids.");
            return ids;
        }
        Set<ArboretumId> seen = new LinkedHashSet<>();
        JsonArray array = json.getAsJsonArray(field);
        for (int i = 0; i < array.size(); i++) {
            JsonElement element = array.get(i);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                report.error("$." + field + "[" + i + "]", "must be a string id.");
                continue;
            }
            try {
                ArboretumId id = ArboretumId.parse(element.getAsString());
                if (!seen.add(id)) {
                    report.warn("$." + field + "[" + i + "]", "'" + id + "' is listed twice; ignored.");
                    continue;
                }
                ids.add(id);
            } catch (ArboretumId.InvalidIdException exception) {
                report.error("$." + field + "[" + i + "]", exception.getMessage());
            }
        }
        return ids;
    }

    private static Map<ArboretumId, Integer> readWeightOverrides(JsonObject json, ValidationReport report) {
        Map<ArboretumId, Integer> weights = new LinkedHashMap<>();
        if (!json.has("weight_overrides") || !json.get("weight_overrides").isJsonObject()) {
            return weights;
        }
        JsonObject overrides = json.getAsJsonObject("weight_overrides");
        for (String key : overrides.keySet()) {
            try {
                ArboretumId id = ArboretumId.parse(key);
                JsonElement value = overrides.get(key);
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    report.error("$.weight_overrides." + key, "must be a number.");
                    continue;
                }
                int weight = value.getAsInt();
                if (weight < 0) {
                    report.error("$.weight_overrides." + key, "must not be negative, was " + weight + ".");
                    continue;
                }
                weights.put(id, weight);
            } catch (ArboretumId.InvalidIdException exception) {
                report.error("$.weight_overrides." + key, exception.getMessage());
            }
        }
        return weights;
    }

    private static ArboretumId readId(JsonObject json, String field, ValidationReport report) {
        String raw = readString(json, field);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ArboretumId.parse(raw);
        } catch (ArboretumId.InvalidIdException exception) {
            report.error("$." + field, exception.getMessage());
            return null;
        }
    }

    private static String readString(JsonObject json, String field) {
        if (!json.has(field)) {
            return null;
        }
        JsonElement element = json.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private static int readInt(JsonObject json, String field, int fallback, ValidationReport report) {
        if (!json.has(field)) {
            return fallback;
        }
        JsonElement element = json.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            report.error("$." + field, "must be a number; using " + fallback + ".");
            return fallback;
        }
        return element.getAsInt();
    }

    private static boolean readBoolean(JsonObject json, String field, boolean fallback, ValidationReport report) {
        if (!json.has(field)) {
            return fallback;
        }
        JsonElement element = json.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            report.error("$." + field, "must be true or false; using " + fallback + ".");
            return fallback;
        }
        return element.getAsBoolean();
    }

    /** The parsed config plus everything the loader had to say about it. */
    public record Result(WorldConfig config, ValidationReport report) {
        public boolean isClean() {
            return report.isClean();
        }
    }
}