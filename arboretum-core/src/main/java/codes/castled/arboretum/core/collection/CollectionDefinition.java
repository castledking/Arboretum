package codes.castled.arboretum.core.collection;

import codes.castled.arboretum.core.data.DataReader;
import codes.castled.arboretum.core.data.ValidationReport;
import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.GenerationMode;
import codes.castled.arboretum.core.species.SpeciesDefinition;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A named selection of species, with optional per-species weight overrides.
 *
 * <p>A collection never duplicates a species definition. It names ids and may adjust how heavily a
 * species is drawn, which is the difference between "the medium collection" and "the medium
 * collection, but with more baobabs" without a second copy of the botanical data.
 */
public record CollectionDefinition(
        ArboretumId id,
        String displayName,
        String description,
        boolean builtIn,
        List<ArboretumId> species,
        Map<ArboretumId, Override> overrides
) {

    /**
     * A per-species adjustment inside a collection.
     *
     * @param weight       relative spawn weight override, or null to keep the species' own
     * @param disabled     when true the species is kept in the collection but never generates
     * @param note         author-facing explanation, surfaced in diagnostics
     */
    public record Override(Integer weight, boolean disabled, String note) {
        public Override {
        }

        public static Override weight(int weight) {
            return new Override(weight, false, null);
        }

        public static Override disabled(String reason) {
            return new Override(null, true, reason);
        }

        public boolean overridesWeight() {
            return weight != null;
        }

        public boolean isDisabled() {
            return disabled;
        }
    }

    public CollectionDefinition {
        species = List.copyOf(species);
        overrides = Map.copyOf(overrides);
    }

    /** The schemas a collection file may declare. */
    public static final int CURRENT_SCHEMA = 1;

    /** @return the ids this collection actually generates, with disabled species removed. */
    public Set<ArboretumId> enabledSpecies() {
        return species.stream()
                .filter(id -> {
                    Override override = overrides.get(id);
                    return override == null || !override.isDisabled();
                })
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public Override overrideFor(ArboretumId speciesId) {
        return overrides.get(speciesId);
    }

    /** @return the weight a species contributes in this collection under a generation mode. */
    public int weightFor(SpeciesDefinition species, GenerationMode mode) {
        Override override = overrides.get(species.id());
        if (override != null && override.overridesWeight()) {
            return Math.max(1, Math.round(override.weight() * mode.weightFactor()));
        }
        return mode.effectiveWeight(species);
    }

    /**
     * Parses a collection file.
     *
     * @return the definition, or null when the report has errors
     */
    public static CollectionDefinition load(String source, com.google.gson.JsonObject json) {
        return read(source, json).value();
    }

    /** As {@link #load}, keeping the report that explains a rejection. */
    public static codes.castled.arboretum.core.data.Loaded<CollectionDefinition> read(
            String source, com.google.gson.JsonObject json) {
        ValidationReport report = new ValidationReport(source);
        DataReader reader = new DataReader(json, "$", report);
        reader.warnUnknown(Set.of("id", "schema", "display_name", "description", "built_in",
                "species", "overrides"));

        int schema = reader.optionalInt("schema", CURRENT_SCHEMA);
        if (schema > CURRENT_SCHEMA) {
            report.error("$.schema", "file targets schema " + schema + " but this Arboretum reads up to "
                    + CURRENT_SCHEMA + ".");
        }

        ArboretumId id = null;
        String rawId = reader.requireString("id");
        if (rawId != null) {
            try {
                id = ArboretumId.parse(rawId);
            } catch (ArboretumId.InvalidIdException exception) {
                report.error("$.id", exception.getMessage());
            }
        }

        List<ArboretumId> species = codes.castled.arboretum.core.species.SpeciesCatalogue
                .readIds(reader, "species");

        Map<ArboretumId, Override> overrides = new java.util.LinkedHashMap<>();
        if (reader.has("overrides")) {
            DataReader nested = reader.optionalObject("overrides");
            if (nested != null) {
                com.google.gson.JsonObject raw = json.getAsJsonObject("overrides");
                for (String key : raw.keySet()) {
                    ArboretumId overrideId;
                    try {
                        overrideId = ArboretumId.parse(key);
                    } catch (ArboretumId.InvalidIdException exception) {
                        report.error("$.overrides." + key, exception.getMessage());
                        continue;
                    }
                    DataReader entry = new DataReader(raw.getAsJsonObject(key),
                            "$.overrides." + key, report);
                    entry.warnUnknown(Set.of("weight", "disabled", "note"));
                    Integer weight = entry.readInt("weight");
                    if (weight != null && weight < 0) {
                        report.error("$.overrides." + key + ".weight",
                                "must not be negative, was " + weight + ".");
                        weight = null;
                    }
                    boolean disabled = entry.optionalBoolean("disabled", false);
                    overrides.put(overrideId,
                            new Override(weight, disabled, entry.optionalString("note", null)));
                }
            }
        }

        // An override for a species the collection does not list is almost always a mistake, and a
        // silent one: the collection looks configured but the setting does nothing.
        for (ArboretumId overrideId : overrides.keySet()) {
            if (!species.contains(overrideId)) {
                report.warn("$.overrides." + overrideId,
                        "overrides a species the collection does not list, so the override has no "
                                + "effect. Add the species to 'species', or remove the override.");
            }
        }

        // Read before the error check: a missing name is an error like any other.
        String displayName = reader.requireString("display_name");
        if (report.hasErrors()) {
            return new codes.castled.arboretum.core.data.Loaded<>(null, report);
        }
        return new codes.castled.arboretum.core.data.Loaded<>(new CollectionDefinition(
                id,
                displayName,
                reader.optionalString("description", ""),
                reader.optionalBoolean("built_in", false),
                species,
                overrides), report);
    }
}