package codes.castled.arboretum.core.species;

import codes.castled.arboretum.core.data.DataReader;
import codes.castled.arboretum.core.data.ValidationReport;
import codes.castled.arboretum.core.id.ArboretumId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The loaded, validated set of species Arboretum knows about.
 *
 * <p>Immutable and built once at load time. World generation reads it from several threads, so it
 * hands out copies and never a mutable view. A definition that failed validation is absent, and
 * the report explaining why is kept alongside so startup can log it.
 */
public final class SpeciesCatalogue {

    private final Map<ArboretumId, SpeciesDefinition> species;
    private final List<ArboretumId> rejected;
    private final ValidationReport report;

    private SpeciesCatalogue(Map<ArboretumId, SpeciesDefinition> species,
                             List<ArboretumId> rejected,
                             ValidationReport report) {
        // TreeMap so iteration order is stable and species listings do not reshuffle between runs.
        this.species = Map.copyOf(new TreeMap<>(species));
        this.rejected = List.copyOf(rejected);
        this.report = report;
    }

    /** Builds a catalogue from already-parsed definitions. */
    public static SpeciesCatalogue of(Collection<SpeciesDefinition> definitions) {
        Map<ArboretumId, SpeciesDefinition> byId = new TreeMap<>();
        ValidationReport report = new ValidationReport("species catalogue");
        List<ArboretumId> rejected = new java.util.ArrayList<>();

        for (SpeciesDefinition definition : definitions) {
            SpeciesDefinition existing = byId.putIfAbsent(definition.id(), definition);
            if (existing != null) {
                // Two files claiming one id is an authoring collision, not a merge: the player
                // would see whichever file happened to load first, which is not reproducible.
                rejected.add(definition.id());
                report.error(definition.id().toString(),
                        "declared more than once. Every species needs a unique id; rename one of the files.");
            }
        }
        return new SpeciesCatalogue(byId, rejected, report);
    }

    /** An empty catalogue, used by tests and by a server that found no species at all. */
    public static SpeciesCatalogue empty() {
        return new SpeciesCatalogue(Map.of(), List.of(), new ValidationReport("species catalogue"));
    }

    public Optional<SpeciesDefinition> get(ArboretumId id) {
        return Optional.ofNullable(species.get(id));
    }

    public boolean contains(ArboretumId id) {
        return species.containsKey(id);
    }

    public int size() {
        return species.size();
    }

    /** @return every species, in stable id order. */
    public Collection<SpeciesDefinition> all() {
        return species.values();
    }

    public Set<ArboretumId> ids() {
        return species.keySet();
    }

    /** @return the ids that were declared but rejected, so startup can report them. */
    public List<ArboretumId> rejectedIds() {
        return rejected;
    }

    /** @return the ids in {@code requested} that this catalogue does not have. */
    public List<ArboretumId> missingFrom(Collection<ArboretumId> requested) {
        return requested.stream().filter(id -> !species.containsKey(id)).sorted().toList();
    }

    public ValidationReport report() {
        return report;
    }

    /** A one-line summary for the startup log. */
    public String summary() {
        return "Loaded " + species.size() + " species"
                + (rejected.isEmpty() ? "" : " (" + rejected.size() + " rejected)");
    }

    @Override
    public String toString() {
        return "SpeciesCatalogue" + species.keySet();
    }

    /** Convenience for resolving ids declared inside a collection or world config. */
    public static ArboretumId requireId(String raw, String what) {
        try {
            return ArboretumId.parse(raw);
        } catch (ArboretumId.InvalidIdException exception) {
            throw new IllegalArgumentException(what + ": " + exception.getMessage(), exception);
        }
    }

    /** Reads a list of ids, reporting each unparseable one against the report. */
    public static List<ArboretumId> readIds(DataReader reader, String field) {
        List<ArboretumId> ids = new java.util.ArrayList<>();
        for (String raw : reader.optionalStringList(field)) {
            try {
                ids.add(ArboretumId.parse(raw));
            } catch (ArboretumId.InvalidIdException exception) {
                reader.report().error(field, exception.getMessage());
            }
        }
        return ids;
    }
}