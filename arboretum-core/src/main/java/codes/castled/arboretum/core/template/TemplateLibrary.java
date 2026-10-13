package codes.castled.arboretum.core.template;

import codes.castled.arboretum.core.nbt.NbtException;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses templates once and keeps them for the life of the process.
 *
 * <p>Caching is not optional here. A template parse is a gzip inflate plus an object graph build;
 * a dense baobab canopy is ~900 blocks. Doing that per candidate tree per chunk would make
 * generation cost scale with template size rather than with trees placed.
 *
 * <p>Bounded on purpose: a server running many packs could otherwise accumulate every template any
 * pack ever referenced. The cache holds {@link #DEFAULT_CAPACITY} entries and evicts in insertion
 * order, which is fine because real usage touches a handful of templates constantly and a cold
 * template costs one parse.
 *
 * <p>A parse failure is cached as a failure too. A corrupt template that re-inflates on every chunk
 * would turn one bad pack file into a server-wide performance problem.
 */
public final class TemplateLibrary {

    /** How many parsed templates to keep before evicting the oldest. */
    public static final int DEFAULT_CAPACITY = 256;

    private final TemplateSource source;
    private final int capacity;
    private final Map<String, Object> cache = new LinkedHashMap<>(64, 0.75F, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
            return size() > TemplateLibrary.this.capacity;
        }
    };
    private final Object lock = new Object();

    private long hits;
    private long misses;
    private long failures;

    public TemplateLibrary(TemplateSource source) {
        this(source, DEFAULT_CAPACITY);
    }

    public TemplateLibrary(TemplateSource source, int capacity) {
        this.source = source;
        this.capacity = Math.max(1, capacity);
    }

    public TemplateSource source() {
        return source;
    }

    /**
     * Loads and parses a template, or returns the cached one.
     *
     * @return the template, or empty when the source has no such path
     * @throws NbtException when the path exists but cannot be parsed as a structure template
     */
    public Optional<StructureTemplate> load(String path) {
        Object cached;
        synchronized (lock) {
            cached = cache.get(path);
            if (cached == null) {
                misses++;
            } else {
                hits++;
            }
        }
        if (cached instanceof StructureTemplate template) {
            return Optional.of(template);
        }
        if (cached instanceof CachedFailure failure) {
            throw failure.exception();
        }

        StructureTemplate parsed;
        try {
            byte[] bytes = source.open(path);
            if (bytes == null) {
                // Not an error: an absent path is cached as null-by-absence via a marker so a species
                // with a missing template does not re-hit the source on every chunk.
                synchronized (lock) {
                    cache.put(path, Absent.INSTANCE);
                }
                return Optional.empty();
            }
            parsed = StructureTemplateIO.read(new ByteArrayInputStream(bytes));
        } catch (NbtException exception) {
            synchronized (lock) {
                failures++;
                cache.put(path, new CachedFailure(exception));
            }
            throw exception;
        } catch (java.io.IOException exception) {
            synchronized (lock) {
                failures++;
                cache.put(path, new CachedFailure(
                        new NbtException("Could not read template '" + path + "' from "
                                + source.describe() + ": " + exception.getMessage(), exception)));
            }
            throw new NbtException("Could not read template '" + path + "' from "
                    + source.describe() + ": " + exception.getMessage(), exception);
        }

        synchronized (lock) {
            cache.put(path, parsed);
        }
        return Optional.of(parsed);
    }

    /** @return true when the path resolves to a readable template. */
    public boolean exists(String path) {
        try {
            return load(path).isPresent();
        } catch (NbtException exception) {
            return false;
        }
    }

    /** Drops every cached template. Used when a pack reloads. */
    public void invalidate() {
        synchronized (lock) {
            cache.clear();
        }
    }

    /** Drops one cached template, so an edited template is picked up without a reload. */
    public void invalidate(String path) {
        synchronized (lock) {
            cache.remove(path);
        }
    }

    public int size() {
        synchronized (lock) {
            return cache.size();
        }
    }

    /** Cache counters for the diagnostics command. */
    public Stats stats() {
        synchronized (lock) {
            return new Stats(cache.size(), capacity, hits, misses, failures);
        }
    }

    /**
     * Cache counters.
     *
     * @param size     entries currently held
     * @param capacity the eviction threshold
     * @param hits     loads served from cache
     * @param misses   loads that had to read and parse
     * @param failures loads that failed to parse
     */
    public record Stats(int size, int capacity, long hits, long misses, long failures) {
        /** @return the cache hit rate as a fraction, or 0 when nothing has been loaded. */
        public double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }

        @Override
        public String toString() {
            return size + "/" + capacity + " templates, " + hits + " hits, " + misses + " misses, "
                    + failures + " failures";
        }
    }

    private enum Absent {
        INSTANCE
    }

    private record CachedFailure(NbtException exception) {
    }
}