package codes.castled.arboretum.core.template;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A block identity plus its state properties, kept as strings.
 *
 * <p>Strings rather than Minecraft objects, because the core is platform-neutral: the Fabric
 * adapter resolves {@code "minecraft:oak_log"} against the game's registry, and the Paper adapter
 * resolves it against Bukkit's. Core only ever needs to compare, copy and rotate these.
 */
public final class BlockStateRef implements Comparable<BlockStateRef> {

    private final String blockId;
    private final Map<String, String> properties;

    public BlockStateRef(String blockId, Map<String, String> properties) {
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        this.properties = properties == null || properties.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static BlockStateRef of(String blockId) {
        return new BlockStateRef(blockId, Map.of());
    }

    public String blockId() {
        return blockId;
    }

    public Map<String, String> properties() {
        return properties;
    }

    public String property(String name) {
        return properties.get(name);
    }

    public String property(String name, String fallback) {
        return properties.getOrDefault(name, fallback);
    }

    public boolean hasProperty(String name) {
        return properties.containsKey(name);
    }

    /** @return a copy with one property replaced. Passing {@code null} removes it. */
    public BlockStateRef with(String name, String value) {
        Map<String, String> copy = new LinkedHashMap<>(properties);
        if (value == null) {
            copy.remove(name);
        } else {
            copy.put(name, value);
        }
        return new BlockStateRef(blockId, copy);
    }

    /** @return a copy with every property removed. Used when a species replaces a template marker. */
    public BlockStateRef withoutProperties() {
        return properties.isEmpty() ? this : new BlockStateRef(blockId, Map.of());
    }

    public BlockStateRef withBlockId(String newBlockId) {
        return new BlockStateRef(newBlockId, properties);
    }

    /** @return the Minecraft blockstate string, e.g. {@code minecraft:oak_log[axis=y]}. */
    public String toBlockStateString() {
        if (properties.isEmpty()) {
            return blockId;
        }
        StringBuilder builder = new StringBuilder(blockId).append('[');
        boolean first = true;
        for (var entry : properties.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.append(']').toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockStateRef ref)) {
            return false;
        }
        return blockId.equals(ref.blockId) && properties.equals(ref.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockId, properties);
    }

    @Override
    public int compareTo(BlockStateRef other) {
        int byId = blockId.compareTo(other.blockId);
        return byId != 0 ? byId : toString().compareTo(other.toString());
    }

    @Override
    public String toString() {
        return toBlockStateString();
    }
}