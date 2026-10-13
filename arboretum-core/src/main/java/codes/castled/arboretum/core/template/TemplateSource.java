package codes.castled.arboretum.core.template;

import java.util.Map;

/**
 * Where a species' templates are read from.
 *
 * <p>An interface rather than a filesystem path because the three platforms resolve templates
 * differently - Fabric through the mod's resource manager, Paper through the plugin's jar, and the
 * tooling through a directory - while the loader, cache and validation above it should not care.
 */
public interface TemplateSource {

    /**
     * Opens a template by its declared path.
     *
     * @param path the value of a species' {@code trunk_template} field, e.g.
     *             {@code data/arboretum/templates/baobab/trunk.nbt}
     * @return the bytes, or null when no template exists at that path
     */
    byte[] open(String path) throws java.io.IOException;

    /** @return a human-readable name for this source, used in error messages. */
    String describe();

    /** A source backed by an in-memory map, used by tests and by the resource-pack exporter. */
    final class InMemory implements TemplateSource {
        private final String name;
        private final Map<String, byte[]> templates;

        public InMemory(String name, Map<String, byte[]> templates) {
            this.name = name;
            this.templates = Map.copyOf(templates);
        }

        @Override
        public byte[] open(String path) {
            return templates.get(path);
        }

        @Override
        public String describe() {
            return name;
        }
    }
}