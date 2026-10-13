package codes.castled.arboretum.core.bundle;

import codes.castled.arboretum.core.collection.CollectionDefinition;
import codes.castled.arboretum.core.data.Loaded;
import codes.castled.arboretum.core.data.ValidationReport;
import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.SpeciesCatalogue;
import codes.castled.arboretum.core.species.SpeciesDefinition;
import codes.castled.arboretum.core.species.SpeciesLoader;
import codes.castled.arboretum.core.species.SpeciesTemplateValidator;
import codes.castled.arboretum.core.template.ClasspathTemplateSource;
import codes.castled.arboretum.core.template.TemplateLibrary;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything Arboretum ships: the species, the collections and the templates, loaded the way
 * every platform loads them - through data/arboretum/index.json on a class loader.
 *
 * <p>A species is in the catalogue only if its file parses <em>and</em> its templates load and
 * carry its markers. Anything rejected stays out, and its report says why, so startup can log it
 * and tests can assert on it. Collections are kept as parsed; resolving one against the catalogue
 * is {@link codes.castled.arboretum.core.collection.CollectionResolver}'s job.
 */
public final class BundledData {

    /** Where the build writes the index (see arboretum-core's build file). */
    public static final String INDEX = "data/arboretum/index.json";

    private final SpeciesCatalogue catalogue;
    private final Map<ArboretumId, CollectionDefinition> collections;
    private final TemplateLibrary templates;
    private final List<ValidationReport> reports;

    private BundledData(SpeciesCatalogue catalogue, Map<ArboretumId, CollectionDefinition> collections,
                        TemplateLibrary templates, List<ValidationReport> reports) {
        this.catalogue = catalogue;
        this.collections = Collections.unmodifiableMap(collections);
        this.templates = templates;
        this.reports = List.copyOf(reports);
    }

    /** Loads the bundle visible to {@code loader}. Throws only when the index itself is missing. */
    public static BundledData load(ClassLoader loader) throws IOException {
        JsonObject index = readJson(loader, INDEX);
        if (index == null) {
            throw new IOException(INDEX + " is missing: the build did not package Arboretum's data index");
        }
        TemplateLibrary templates = new TemplateLibrary(new ClasspathTemplateSource(loader));
        List<ValidationReport> reports = new ArrayList<>();

        List<SpeciesDefinition> accepted = new ArrayList<>();
        for (String path : paths(index, "species")) {
            JsonObject json = readJson(loader, path);
            if (json == null) {
                reports.add(new ValidationReport(path).error("listed in the index but not found"));
                continue;
            }
            Loaded<SpeciesDefinition> species = SpeciesLoader.read(path, json);
            ValidationReport report = species.report();
            if (species.ok()) {
                report.merge(SpeciesTemplateValidator.validate(species.value(), templates));
                if (!report.hasErrors()) {
                    accepted.add(species.value());
                }
            }
            reports.add(report);
        }
        SpeciesCatalogue catalogue = SpeciesCatalogue.of(accepted);
        reports.add(catalogue.report());

        Map<ArboretumId, CollectionDefinition> collections = new TreeMap<>();
        for (String path : paths(index, "collections")) {
            JsonObject json = readJson(loader, path);
            if (json == null) {
                reports.add(new ValidationReport(path).error("listed in the index but not found"));
                continue;
            }
            Loaded<CollectionDefinition> collection = CollectionDefinition.read(path, json);
            reports.add(collection.report());
            if (collection.ok() && collections.putIfAbsent(collection.value().id(), collection.value()) != null) {
                reports.add(new ValidationReport(path).error(
                        "collection " + collection.value().id() + " is declared more than once"));
            }
        }
        return new BundledData(catalogue, collections, templates, reports);
    }

    public SpeciesCatalogue catalogue() {
        return catalogue;
    }

    public Map<ArboretumId, CollectionDefinition> collections() {
        return collections;
    }

    public TemplateLibrary templates() {
        return templates;
    }

    /** One report per file read, plus the catalogue's own; empty reports are kept too. */
    public List<ValidationReport> reports() {
        return reports;
    }

    public boolean hasErrors() {
        return reports.stream().anyMatch(ValidationReport::hasErrors);
    }

    /** Every problem, for the startup log; "" when the bundle is clean. */
    public String describeProblems() {
        List<ValidationReport> withProblems = reports.stream().filter(r -> !r.isClean()).toList();
        return withProblems.isEmpty() ? "" : ValidationReport.describeAll("Arboretum data", withProblems);
    }

    private static List<String> paths(JsonObject index, String key) {
        List<String> out = new ArrayList<>();
        JsonElement element = index.get(key);
        if (element instanceof JsonArray array) {
            array.forEach(item -> out.add(item.getAsString()));
        }
        return out;
    }

    private static JsonObject readJson(ClassLoader loader, String path) throws IOException {
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
