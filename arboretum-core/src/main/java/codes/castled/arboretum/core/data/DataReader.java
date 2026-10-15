package codes.castled.arboretum.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict readers over Gson's tree model.
 *
 * <p>Gson itself is permissive: it will happily hand back a string where an integer belongs and
 * leave the caller to discover the problem three layers down. Every accessor here reports the
 * <em>field path</em> instead, because "expected number but was string at ecology.weight" is the
 * difference between an author fixing a file in thirty seconds and an hour of guessing.
 *
 * <p>Missing optional fields return a documented default. Missing required fields are recorded as
 * errors and the caller decides whether the definition is unusable. Unknown fields are warnings,
 * not errors, so a file written for a future Arboretum still loads on an older one instead of
 * vanishing.
 */
public final class DataReader {

    private final JsonObject root;
    private final String path;
    private final ValidationReport report;

    public DataReader(JsonObject root, String path, ValidationReport report) {
        this.root = root;
        this.path = path;
        this.report = report;
    }

    public ValidationReport report() {
        return report;
    }

    public boolean has(String field) {
        return root.has(field) && !root.get(field).isJsonNull();
    }

    /** @return true when the object has fields the current schema does not know about. */
    public void warnUnknown(Set<String> known) {
        for (String field : root.keySet()) {
            if (!known.contains(field)) {
                report.warn(path + "." + field,
                        "unknown field; Arboretum will ignore it. Check for a typo, or that this "
                                + "file targets a newer Arboretum than this one.");
            }
        }
    }

    public String requireString(String field) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            report.error(path + "." + field, "required field is missing.");
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            report.error(path + "." + field, "expected a string but found " + describe(element) + ".");
            return null;
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            report.error(path + "." + field, "must not be empty.");
            return null;
        }
        return value;
    }

    public String optionalString(String field, String fallback) {
        if (!has(field)) {
            return fallback;
        }
        return requireString(field);
    }

    public int requireInt(String field) {
        Integer value = readInt(field);
        if (value == null && !has(field)) {
            report.error(path + "." + field, "required field is missing.");
        }
        return value == null ? 0 : value;
    }

    public int optionalInt(String field, int fallback) {
        return has(field) ? requireInt(field) : fallback;
    }

    /** Reads an integer, also accepting a whole-valued float so {@code 5.0} is not an error. */
    public Integer readInt(String field) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive()) {
            report.error(path + "." + field, "expected a number but found " + describe(element) + ".");
            return null;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            double value = primitive.getAsDouble();
            if (value != Math.rint(value)) {
                report.error(path + "." + field, "expected a whole number but found " + value + ".");
                return null;
            }
            return (int) value;
        }
        report.error(path + "." + field, "expected a number but found " + describe(element) + ".");
        return null;
    }

    public float requireFloat(String field) {
        Float value = readFloat(field);
        if (value == null && !has(field)) {
            report.error(path + "." + field, "required field is missing.");
        }
        return value == null ? 0F : value;
    }

    public float optionalFloat(String field, float fallback) {
        return has(field) ? requireFloat(field) : fallback;
    }

    public Float readFloat(String field) {
        JsonElement element = root.get(field);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            report.error(path + "." + field, "expected a number but found " + describe(element) + ".");
            return null;
        }
        return element.getAsFloat();
    }

    public boolean optionalBoolean(String field, boolean fallback) {
        if (!has(field)) {
            return fallback;
        }
        JsonElement element = root.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            report.error(path + "." + field, "expected true or false but found " + describe(element) + ".");
            return fallback;
        }
        return element.getAsBoolean();
    }

    /** Reads a required string enum, listing the accepted spellings when it does not match. */
    public <E extends Enum<E>> E requireEnum(String field, Class<E> type, String... accepted) {
        String raw = requireString(field);
        if (raw == null) {
            return null;
        }
        return parseEnum(field, raw, type, accepted);
    }

    public <E extends Enum<E>> E optionalEnum(String field, Class<E> type, E fallback, String... accepted) {
        if (!has(field)) {
            return fallback;
        }
        String raw = requireString(field);
        if (raw == null) {
            return fallback;
        }
        E parsed = parseEnum(field, raw, type, accepted);
        return parsed == null ? fallback : parsed;
    }

    private <E extends Enum<E>> E parseEnum(String field, String raw, Class<E> type, String... accepted) {
        String text = raw.trim().toUpperCase(java.util.Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(text)) {
                return constant;
            }
        }
        // Ids are authored lower-case with underscores, enum constants are upper-case with
        // underscores, so the only other thing to try is a case-insensitive match.
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(text.replace('-', '_'))) {
                return constant;
            }
        }
        report.error(path + "." + field, "'" + raw + "' is not a valid value. Expected one of: "
                + String.join(", ", accepted) + ".");
        return null;
    }

    /** Reads an array of strings. Returns an empty list when the field is absent. */
    public List<String> optionalStringList(String field) {
        if (!has(field)) {
            return List.of();
        }
        JsonElement element = root.get(field);
        if (!element.isJsonArray()) {
            report.error(path + "." + field, "expected an array of strings but found "
                    + describe(element) + ".");
            return List.of();
        }
        List<String> values = new ArrayList<>();
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                report.error(path + "." + field + "[" + i + "]",
                        "expected a string but found " + describe(item) + ".");
                continue;
            }
            values.add(item.getAsString());
        }
        return values;
    }

    /**
     * Reads markers given either as a list ({@code ["minecraft:acacia_log"]}, each kept as itself)
     * or as a map from marker to the block it becomes ({@code {"minecraft:acacia_wood":
     * "arboretum:jacaranda_wood"}}). Insertion order is kept.
     */
    public java.util.Map<String, String> optionalMarkerMap(String field) {
        if (has(field) && root.get(field).isJsonObject()) {
            return optionalStringMap(field);
        }
        java.util.Map<String, String> identity = new java.util.LinkedHashMap<>();
        for (String marker : optionalStringList(field)) {
            identity.put(marker, marker);
        }
        return identity;
    }

    /** The raw array, or null when absent; an error when present but not an array. */
    public JsonArray optionalArray(String field) {
        if (!has(field)) {
            return null;
        }
        JsonElement element = root.get(field);
        if (!element.isJsonArray()) {
            report.error(path + "." + field, "expected an array but found " + describe(element) + ".");
            return null;
        }
        return element.getAsJsonArray();
    }

    public String path() {
        return path;
    }

    public Set<String> optionalStringSet(String field) {
        return new LinkedHashSet<>(optionalStringList(field));
    }

    /** Reads a nested object, or null when absent. */
    public DataReader optionalObject(String field) {
        if (!has(field)) {
            return null;
        }
        JsonElement element = root.get(field);
        if (!element.isJsonObject()) {
            report.error(path + "." + field, "expected an object but found " + describe(element) + ".");
            return null;
        }
        return new DataReader(element.getAsJsonObject(), path + "." + field, report);
    }

    /**
     * Reads an object whose values are all strings, e.g. the extra-marker map.
     *
     * @return an empty map when the field is absent
     */
    public java.util.Map<String, String> optionalStringMap(String field) {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        if (!has(field)) {
            return values;
        }
        JsonElement element = root.get(field);
        if (!element.isJsonObject()) {
            report.error(path + "." + field, "expected an object but found " + describe(element) + ".");
            return values;
        }
        for (var entry : element.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                report.error(path + "." + field + "." + entry.getKey(),
                        "expected a string but found " + describe(value) + ".");
                continue;
            }
            values.put(entry.getKey(), value.getAsString());
        }
        return values;
    }

    /** Reads an object whose values are all integers, e.g. per-mode weight overrides. */
    public java.util.Map<String, Integer> optionalIntMap(String field) {
        java.util.Map<String, Integer> values = new java.util.LinkedHashMap<>();
        if (!has(field)) {
            return values;
        }
        JsonElement element = root.get(field);
        if (!element.isJsonObject()) {
            report.error(path + "." + field, "expected an object but found " + describe(element) + ".");
            return values;
        }
        DataReader nested = new DataReader(element.getAsJsonObject(), path + "." + field, report);
        for (String key : element.getAsJsonObject().keySet()) {
            Integer value = nested.readInt(key);
            if (value != null) {
                values.put(key, value);
            }
        }
        return values;
    }

    /** Reads a two-number array as a range. */
    public float[] optionalFloatRange(String field) {
        if (!has(field)) {
            return null;
        }
        JsonElement element = root.get(field);
        if (!element.isJsonArray() || element.getAsJsonArray().size() != 2) {
            report.error(path + "." + field,
                    "expected exactly two numbers as [min, max] but found " + describe(element) + ".");
            return null;
        }
        JsonArray array = element.getAsJsonArray();
        List<JsonElement> items = new ArrayList<>();
        array.forEach(items::add);
        // Reuse the numeric checks so the reported field path matches the surrounding object.
        JsonObject wrapper = new JsonObject();
        wrapper.add("min", items.get(0));
        wrapper.add("max", items.get(1));
        DataReader wrapped = new DataReader(wrapper, path + "." + field, report);
        Float min = wrapped.readFloat("min");
        Float max = wrapped.readFloat("max");
        if (min == null || max == null) {
            return null;
        }
        if (min > max) {
            report.error(path + "." + field, "min " + min + " is above max " + max + ".");
            return null;
        }
        return new float[]{min, max};
    }

    private static String describe(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "null";
        }
        if (element.isJsonArray()) {
            return "an array";
        }
        if (element.isJsonObject()) {
            return "an object";
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isString()) {
            return "the string \"" + primitive.getAsString() + "\"";
        }
        if (primitive.isBoolean()) {
            return "the boolean " + primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return "the number " + primitive.getAsDouble();
        }
        return "an unsupported value";
    }

    /** Parses a JSON document, converting Gson's exception into Arboretum's. */
    public static JsonObject parseObject(String source, String text) {
        try {
            JsonElement element = com.google.gson.JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                throw new DataSyntaxException(source + " must contain a JSON object at the top level.");
            }
            return element.getAsJsonObject();
        } catch (JsonSyntaxException exception) {
            throw new DataSyntaxException(source + " is not valid JSON: " + exception.getMessage(), exception);
        }
    }

    /** Thrown when a data file cannot be parsed at all. */
    public static final class DataSyntaxException extends RuntimeException {
        public DataSyntaxException(String message) {
            super(message);
        }

        public DataSyntaxException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}