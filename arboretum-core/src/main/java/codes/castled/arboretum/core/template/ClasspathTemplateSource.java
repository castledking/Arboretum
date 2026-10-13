package codes.castled.arboretum.core.template;

import java.io.IOException;
import java.io.InputStream;

/**
 * Templates read from a class loader: the bundled ones, inside the jar on every platform, and
 * inside the build output in tests - the same lookup either way, so a test that passes here
 * passes in the shipped jar.
 */
public final class ClasspathTemplateSource implements TemplateSource {

    private final ClassLoader loader;

    public ClasspathTemplateSource(ClassLoader loader) {
        this.loader = loader;
    }

    @Override
    public byte[] open(String path) throws IOException {
        try (InputStream in = loader.getResourceAsStream(normalise(path))) {
            return in == null ? null : in.readAllBytes();
        }
    }

    /** Species name templates by resource path; a leading slash is tolerated. */
    static String normalise(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    @Override
    public String describe() {
        return "the bundled resources";
    }
}
