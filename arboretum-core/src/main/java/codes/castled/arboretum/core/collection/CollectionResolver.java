package codes.castled.arboretum.core.collection;

import codes.castled.arboretum.core.data.ValidationReport;
import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.GenerationMode;
import codes.castled.arboretum.core.species.SpeciesCatalogue;
import codes.castled.arboretum.core.species.SpeciesDefinition;
import codes.castled.arboretum.core.species.SpawningRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Turns a collection into the exact set of species a world will generate, with weights applied.
 *
 * <p>Resolution happens once, at world load, and the result is written into the world. That is the
 * decision that makes an existing world stable: adding a species to the catalogue in a mod update
 * cannot silently change a world that was created against an older catalogue, because the world
 * stores the resolved list rather than the collection name alone.
 */
public final class CollectionResolver {

    private CollectionResolver() {
    }

    /**
     * Resolves a collection against a catalogue.
     *
     * @param excluded ids the user has switched off for this world
     * @return the normalised selection, plus every problem found on the way
     */
    public static Resolution resolve(SpeciesCatalogue catalogue,
                                     CollectionDefinition collection,
                                     GenerationMode mode,
                                     SpawningRule rule,
                                     Set<ArboretumId> excluded) {
        ValidationReport report = new ValidationReport("collection " + collection.id());

        // A missing species is a warning, not an error. A pack that adds species referenced by a
        // collection the player does not have must still work; the collection simply has fewer
        // members than it asks for.
        List<ArboretumId> missing = catalogue.missingFrom(collection.species());
        for (ArboretumId id : missing) {
            report.warn("species." + id, "referenced by the collection but not present in the "
                    + "installed catalogue. It will not generate in this world.");
        }

        Set<ArboretumId> excludedHere = excluded == null ? Set.of() : excluded;

        Map<ArboretumId, ResolvedSpecies> selected = new LinkedHashMap<>();
        Set<ArboretumId> skipped = new LinkedHashSet<>();

        for (ArboretumId id : collection.enabledSpecies()) {
            Optional<SpeciesDefinition> found = catalogue.get(id);
            if (found.isEmpty()) {
                skipped.add(id);
                continue;
            }
            if (excludedHere.contains(id)) {
                skipped.add(id);
                report.warn("species." + id, "excluded by world configuration.");
                continue;
            }
            SpeciesDefinition species = found.get();
            CollectionDefinition.Override override = collection.overrideFor(id);
            if (override != null && override.isDisabled()) {
                skipped.add(id);
                continue;
            }
            int weight = collection.weightFor(species, mode);
            if (weight <= 0) {
                skipped.add(id);
                report.warn("species." + id, "resolved to a weight of 0, so it will not generate.");
                continue;
            }
            selected.put(id, new ResolvedSpecies(species, weight,
                    override == null ? null : override.note()));
        }

        int totalWeight = selected.values().stream().mapToInt(ResolvedSpecies::weight).sum();
        Map<ArboretumId, Double> shares = new LinkedHashMap<>();
        for (var entry : selected.entrySet()) {
            shares.put(entry.getKey(),
                    totalWeight == 0 ? 0.0 : (double) entry.getValue().weight() / totalWeight);
        }

        if (selected.isEmpty()) {
            report.warn("selection", "resolved to no species. Arboretum will generate nothing in this "
                    + "world. Check the collection name and any excluded species.");
        }

        return new Resolution(collection.id(), selected, skipped, shares, totalWeight, report);
    }

    /**
     * Builds the COMPLETE collection: every species the installed catalogue has, minus the ones the
     * user disabled.
     *
     * <p>Deliberately computed rather than stored as a file. A stored "all species" list would go
     * stale the moment a pack adds a species, and would then be wrong in a way nothing reports.
     */
    public static CollectionDefinition completeProfile(SpeciesCatalogue catalogue, Set<ArboretumId> excluded) {
        List<ArboretumId> ids = new ArrayList<>(catalogue.ids());
        if (excluded != null) {
            ids.removeAll(excluded);
        }
        return new CollectionDefinition(
                ArboretumId.arboretum("complete"),
                "Complete",
                "Every species the installed Arboretum catalogue provides.",
                true,
                ids,
                Map.of());
    }

    /** A species chosen for a world, with the weight it resolved to. */
    public record ResolvedSpecies(SpeciesDefinition definition, int weight, String note) {
        public ArboretumId id() {
            return definition.id();
        }
    }

    /**
     * The result of resolution.
     *
     * @param collectionId which collection produced this, for diagnostics
     * @param selected     species that will generate, with their resolved weights
     * @param skipped      species the collection listed that will not generate, and why
     * @param shares       each selected species' fraction of the total weight, for diagnostics
     */
    public record Resolution(ArboretumId collectionId,
                             Map<ArboretumId, ResolvedSpecies> selected,
                             Set<ArboretumId> skipped,
                             Map<ArboretumId, Double> shares,
                             int totalWeight,
                             ValidationReport report) {

        public Resolution {
            selected = Map.copyOf(new LinkedHashMap<>(selected));
            skipped = Set.copyOf(skipped);
            shares = Map.copyOf(new TreeMap<>(shares));
        }

        public boolean isEmpty() {
            return selected.isEmpty();
        }

        public int size() {
            return selected.size();
        }

        public Collection<ArboretumId> ids() {
            return selected.keySet();
        }

        public Optional<ResolvedSpecies> get(ArboretumId id) {
            return Optional.ofNullable(selected.get(id));
        }

        /** Picks one species at random from the weighted set. */
        public ResolvedSpecies select(java.util.random.RandomGenerator random) {
            if (selected.isEmpty()) {
                throw new IllegalStateException("Cannot select from an empty resolution.");
            }
            int roll = random.nextInt(totalWeight);
            var iterator = selected.values().iterator();
            ResolvedSpecies current = iterator.next();
            for (int i = 0; i < roll && iterator.hasNext(); i++) {
                current = iterator.next();
            }
            return current;
        }

        /** A one-line summary for the startup log. */
        public String summary() {
            return collectionId + ": " + selected.size() + " species, total weight " + totalWeight
                    + (skipped.isEmpty() ? "" : ", " + skipped.size() + " skipped");
        }
    }
}