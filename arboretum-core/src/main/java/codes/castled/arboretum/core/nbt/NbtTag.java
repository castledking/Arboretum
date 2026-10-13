package codes.castled.arboretum.core.nbt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An NBT value.
 *
 * <p>Arboretum reads structure templates written by Minecraft's own structure blocks, so this is a
 * complete NBT model rather than a subset: an unknown tag must fail loudly rather than be skipped,
 * because a silently dropped tag in a template usually means a misplaced block.
 */
public sealed interface NbtTag permits NbtTag.ByteTag, NbtTag.ShortTag, NbtTag.IntTag,
        NbtTag.LongTag, NbtTag.FloatTag, NbtTag.DoubleTag, NbtTag.ByteArrayTag, NbtTag.StringTag,
        NbtTag.IntArrayTag, NbtTag.LongArrayTag, NbtTag.ListTag, NbtTag.CompoundTag {

    TagType type();

    record ByteTag(byte value) implements NbtTag {
        @Override
        public TagType type() {
            return TagType.BYTE;
        }
    }

    record ShortTag(short value) implements NbtTag {
        @Override
        public TagType type() {
            return TagType.SHORT;
        }
    }

    record IntTag(int value) implements NbtTag {
        @Override
        public TagType type() {
            return TagType.INT;
        }
    }

    record LongTag(long value) implements NbtTag {
        @Override
        public TagType type() {
            return TagType.LONG;
        }
    }

    record FloatTag(float value) implements NbtTag {
        @Override
        public TagType type() {
            return TagType.FLOAT;
        }
    }

    record DoubleTag(double value) implements NbtTag {
        @Override
        public TagType type() {
            return TagType.DOUBLE;
        }
    }

    /**
     * A byte array.
     *
     * <p>Not a record: a record holding an array would use reference equality, so two tags with
     * identical contents would compare unequal and every template round trip would look like a
     * change. The array is also copied in and out, so a caller cannot mutate a parsed template
     * after the fact.
     */
    final class ByteArrayTag implements NbtTag {
        private final byte[] value;
        private final int hash;

        public ByteArrayTag(byte[] value) {
            this.value = value.clone();
            this.hash = Arrays.hashCode(this.value);
        }

        public byte[] value() {
            return value.clone();
        }

        /** Direct access for the writer, which only reads. */
        byte[] raw() {
            return value;
        }

        @Override
        public TagType type() {
            return TagType.BYTE_ARRAY;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ByteArrayTag tag && Arrays.equals(value, tag.value);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return "ByteArrayTag[length=" + value.length + "]";
        }
    }

    record StringTag(String value) implements NbtTag {
        @Override
        public TagType type() {
            return TagType.STRING;
        }
    }

    /** An int array. Not a record, for the array-equality reason given on {@link ByteArrayTag}. */
    final class IntArrayTag implements NbtTag {
        private final int[] value;
        private final int hash;

        public IntArrayTag(int[] value) {
            this.value = value.clone();
            this.hash = Arrays.hashCode(this.value);
        }

        public int[] value() {
            return value.clone();
        }

        int[] raw() {
            return value;
        }

        @Override
        public TagType type() {
            return TagType.INT_ARRAY;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IntArrayTag tag && Arrays.equals(value, tag.value);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return "IntArrayTag" + Arrays.toString(value);
        }
    }

    /** A long array. Not a record, for the array-equality reason given on {@link ByteArrayTag}. */
    final class LongArrayTag implements NbtTag {
        private final long[] value;
        private final int hash;

        public LongArrayTag(long[] value) {
            this.value = value.clone();
            this.hash = Arrays.hashCode(this.value);
        }

        public long[] value() {
            return value.clone();
        }

        long[] raw() {
            return value;
        }

        @Override
        public TagType type() {
            return TagType.LONG_ARRAY;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof LongArrayTag tag && Arrays.equals(value, tag.value);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return "LongArrayTag[length=" + value.length + "]";
        }
    }

    /** A homogeneous list. An empty list has no element type, exactly as in the NBT spec. */
    record ListTag(TagType elementType, List<NbtTag> elements) implements NbtTag {
        public ListTag {
            elements = List.copyOf(elements);
        }

        @Override
        public TagType type() {
            return TagType.LIST;
        }

        public static ListTag empty() {
            return new ListTag(TagType.END, List.of());
        }
    }

    /** A tag map. Insertion order is preserved so templates round-trip byte-for-byte stably. */
    record CompoundTag(Map<String, NbtTag> entries) implements NbtTag {
        public CompoundTag {
            entries = Map.copyOf(new LinkedHashMap<>(entries));
        }

        @Override
        public TagType type() {
            return TagType.COMPOUND;
        }

        public static CompoundTag empty() {
            return new CompoundTag(Map.of());
        }

        public CompoundTagBuilder toBuilder() {
            return new CompoundTagBuilder(this);
        }
    }

    /** Mutable builder used by the template writer and by tests that assemble templates. */
    final class CompoundTagBuilder {
        private final Map<String, NbtTag> entries = new LinkedHashMap<>();

        CompoundTagBuilder(CompoundTag source) {
            this.entries.putAll(source.entries());
        }

        public CompoundTagBuilder put(String name, NbtTag value) {
            entries.put(name, value);
            return this;
        }

        public CompoundTagBuilder putString(String name, String value) {
            return put(name, new StringTag(value));
        }

        public CompoundTagBuilder putInt(String name, int value) {
            return put(name, new IntTag(value));
        }

        public CompoundTagBuilder putIntArray(String name, int[] value) {
            return put(name, new IntArrayTag(value));
        }

        public CompoundTagBuilder putCompound(String name, CompoundTag value) {
            return put(name, value);
        }

        public CompoundTagBuilder putList(String name, ListTag value) {
            return put(name, value);
        }

        public CompoundTag build() {
            return new CompoundTag(entries);
        }
    }

    /**
     * Typed accessors for a compound. A present tag of the wrong type is an error, not a null:
     * a template whose {@code pos} is a string instead of an int array is corrupt and must be
     * reported as such.
     */
    final class CompoundTagAccess {
        private final CompoundTag compound;
        private final Consumer<String> reporter;

        /** Reports every problem found through this view as an {@link NbtException}. */
        public CompoundTagAccess(CompoundTag compound) {
            this(compound, message -> {
                throw new NbtException(message);
            });
        }

        /**
         * @param reporter receives each problem message. Template loading uses this to collect
         *                 problems rather than abort on the first, so an author sees everything
         *                 wrong with a template in one pass.
         */
        public CompoundTagAccess(CompoundTag compound, Consumer<String> reporter) {
            this.compound = compound;
            this.reporter = reporter;
        }

        private void report(String message) {
            reporter.accept(message);
        }

        public boolean has(String name) {
            return compound.entries().containsKey(name);
        }

        public NbtTag get(String name) {
            return compound.entries().get(name);
        }

        public <T extends NbtTag> T require(String name, Class<T> type, String what) {
            NbtTag tag = compound.entries().get(name);
            if (tag == null) {
                reportMissing(what, name);
                throw new NbtException(what + " is missing required tag '" + name + "'.");
            }
            if (!type.isInstance(tag)) {
                reportWrongType(what, name, type, tag);
                throw new NbtException(what + " tag '" + name + "' should be "
                        + type.getSimpleName() + " but is " + tag.type() + ".");
            }
            return type.cast(tag);
        }

        public String requireString(String name, String what) {
            return require(name, StringTag.class, what).value();
        }

        public int requireInt(String name, String what) {
            return require(name, IntTag.class, what).value();
        }

        public int[] requireIntArray(String name, String what) {
            return require(name, IntArrayTag.class, what).value();
        }

        /**
         * Reads a fixed-length integer vector written either as an int array or as a list of ints.
         *
         * <p>Both encodings occur in real structure templates. Minecraft's own structure block
         * writes {@code size} and {@code pos} as {@code TAG_Int_Array}, while other tree-template
         * tools - Oh The Trees You'll Grow among them - write them as {@code TAG_List} of
         * {@code TAG_Int}. The values are identical and the format is the same template, so the
         * reader accepts either rather than declaring half the ecosystem's templates unreadable.
         *
         * @param length the required number of components
         * @return the values, or null when the tag is missing or the wrong length
         */
        public int[] readIntVector(String name, int length, String what) {
            NbtTag tag = compound.entries().get(name);
            if (tag == null) {
                reportMissing(what, name);
                return null;
            }
            int[] values = switch (tag) {
                case IntArrayTag array -> array.value();
                case ListTag list -> toIntArray(list, name, what);
                default -> {
                    reportWrongType(what, name, IntArrayTag.class, tag);
                    yield null;
                }
            };
            if (values == null) {
                return null;
            }
            if (values.length != length) {
                report(what + " '" + name + "' must have " + length + " components but had "
                        + values.length + ".");
                return null;
            }
            return values;
        }

        private int[] toIntArray(ListTag list, String name, String what) {
            if (list.elementType() != TagType.INT) {
                report(what + " '" + name + "' is a list of " + list.elementType()
                        + ", expected a list of INT or an INT_ARRAY.");
                return null;
            }
            int[] values = new int[list.elements().size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = ((IntTag) list.elements().get(i)).value();
            }
            return values;
        }

        private void reportMissing(String what, String name) {
            report(what + " is missing required tag '" + name + "'.");
        }

        private void reportWrongType(String what, String name, Class<?> expected, NbtTag actual) {
            report(what + " tag '" + name + "' should be " + expected.getSimpleName()
                    + " but is " + actual.type() + ".");
        }

        public CompoundTag requireCompound(String name, String what) {
            return require(name, CompoundTag.class, what);
        }

        public ListTag requireList(String name, String what) {
            return require(name, ListTag.class, what);
        }

        public List<CompoundTag> requireCompoundList(String name, String what) {
            List<NbtTag> elements = requireList(name, what).elements();
            List<CompoundTag> compounds = new ArrayList<>(elements.size());
            for (int i = 0; i < elements.size(); i++) {
                NbtTag element = elements.get(i);
                if (!(element instanceof CompoundTag value)) {
                    throw new NbtException(what + " list '" + name + "' element " + i
                            + " should be COMPOUND but is " + element.type() + ".");
                }
                compounds.add(value);
            }
            return compounds;
        }

        public String optionalString(String name, String fallback) {
            NbtTag tag = compound.entries().get(name);
            return tag instanceof StringTag value ? value.value() : fallback;
        }
    }
}