package codes.castled.arboretum.core.id;

import java.util.Locale;
import java.util.Objects;

/**
 * A namespaced identifier such as {@code arboretum:baobab}.
 *
 * <p>Ids are validated once, at construction, so every downstream subsystem can assume a
 * well-formed id without re-checking. Validation failures carry the offending text, because a
 * species file with a typo in its id is the single most likely authoring mistake and the message
 * is the only thing that tells the author where to look.
 */
public final class ArboretumId implements Comparable<ArboretumId> {

    /** The namespace Arboretum's own content lives in. */
    public static final String ARBORETUM_NAMESPACE = "arboretum";

    private final String namespace;
    private final String path;

    private ArboretumId(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    /**
     * Parses {@code namespace:path} or a bare {@code path}, which defaults to the
     * {@code arboretum} namespace.
     *
     * @throws InvalidIdException if either half is empty or contains characters Minecraft's
     *                            resource id grammar does not allow
     */
    public static ArboretumId parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String text = raw.trim();
        if (text.isEmpty()) {
            throw new InvalidIdException("An identifier cannot be empty.");
        }
        int colon = text.indexOf(':');
        String namespace;
        String path;
        if (colon < 0) {
            namespace = ARBORETUM_NAMESPACE;
            path = text;
        } else {
            namespace = text.substring(0, colon);
            path = text.substring(colon + 1);
            if (text.indexOf(':', colon + 1) >= 0) {
                throw new InvalidIdException("Identifier '" + raw + "' contains more than one ':'.");
            }
        }
        validateNamespace(namespace, raw);
        validatePath(path, raw);
        return new ArboretumId(namespace, path);
    }

    public static ArboretumId of(String namespace, String path) {
        validateNamespace(namespace, namespace + ":" + path);
        validatePath(path, namespace + ":" + path);
        return new ArboretumId(namespace, path);
    }

    /** Creates an id in the {@code arboretum} namespace, rejecting anything already namespaced. */
    public static ArboretumId arboretum(String path) {
        return of(ARBORETUM_NAMESPACE, path);
    }

    private static void validateNamespace(String namespace, String context) {
        if (namespace.isEmpty()) {
            throw new InvalidIdException("Identifier '" + context + "' has an empty namespace.");
        }
        if (!isAllowed(namespace, true)) {
            throw new InvalidIdException(
                    "Namespace '" + namespace + "' in '" + context + "' may only use [a-z0-9._-].");
        }
    }

    private static void validatePath(String path, String context) {
        if (path.isEmpty()) {
            throw new InvalidIdException("Identifier '" + context + "' has an empty path.");
        }
        if (!isAllowed(path, false)) {
            throw new InvalidIdException(
                    "Path '" + path + "' in '" + context + "' may only use [a-z0-9/._-].");
        }
    }

    private static boolean isAllowed(String text, boolean namespace) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-'
                    || (!namespace && c == '/');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    public String namespace() {
        return namespace;
    }

    public String path() {
        return path;
    }

    /** @return true when this id belongs to Arboretum's own namespace. */
    public boolean isArboretum() {
        return ARBORETUM_NAMESPACE.equals(namespace);
    }

    /** @return the {@code namespace:path} form, omitting the namespace when it is Arboretum's. */
    public String toShortString() {
        return isArboretum() ? path : namespace + ":" + path;
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ArboretumId id)) {
            return false;
        }
        return namespace.equals(id.namespace) && path.equals(id.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }

    @Override
    public int compareTo(ArboretumId other) {
        int byNamespace = namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : path.compareTo(other.path);
    }

    /** Normalises free-form player or pack text for comparison, e.g. "Baobab" -> {@code baobab}. */
    public static String normaliseSearchTerm(String term) {
        return term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
    }

    /** Thrown when an identifier cannot be parsed. Callers turn this into a validation error. */
    public static final class InvalidIdException extends IllegalArgumentException {
        InvalidIdException(String message) {
            super(message);
        }
    }
}