package codes.castled.arboretum.core.data;

/**
 * A parsed data file and the report written while parsing it.
 *
 * <p>The value is null when the report has errors. Callers that only want the value can ignore the
 * report; callers that must explain a rejection - startup logging, validation tests - keep it.
 */
public record Loaded<T>(T value, ValidationReport report) {

    public boolean ok() {
        return value != null;
    }
}
