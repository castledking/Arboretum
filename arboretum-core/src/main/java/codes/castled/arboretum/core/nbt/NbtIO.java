package codes.castled.arboretum.core.nbt;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reads and writes gzipped NBT, the format Minecraft saves structure templates in.
 *
 * <p>Both directions are implemented so Arboretum can own its template format end to end: the
 * {@link codes.castled.arboretum.core.template.StructureTemplateWriter} that produces the shipped
 * templates and the reader used at world-generation time share one implementation, and a template
 * round-trip is covered by a test rather than by trusting two separate code paths to agree.
 *
 * <p>Reading is deliberately bounded. A template is loaded during chunk generation, so a corrupt or
 * hostile file must fail with a short message rather than allocate without limit.
 */
public final class NbtIO {

    /** Refuse to allocate a single list longer than this; real templates are far smaller. */
    private static final int MAX_COLLECTION_LENGTH = 1 << 22;
    /** Refuse to read a string longer than this, in UTF-8 bytes. */
    private static final int MAX_STRING_BYTES = 1 << 20;

    private NbtIO() {
    }

    /** Reads the root compound of a gzipped NBT document. */
    public static NbtTag.CompoundTag readGzipped(InputStream stream) {
        try (GZIPInputStream gzip = new GZIPInputStream(stream, 1 << 16)) {
            DataInputStream input = new DataInputStream(new java.io.BufferedInputStream(gzip, 1 << 16));
            return readRoot(input);
        } catch (IOException exception) {
            throw new NbtException("Could not read gzipped NBT: " + exception.getMessage(), exception);
        }
    }

    /** Reads the root compound of an uncompressed NBT document. */
    public static NbtTag.CompoundTag readUncompressed(InputStream stream) {
        try {
            return readRoot(new DataInputStream(stream));
        } catch (IOException exception) {
            throw new NbtException("Could not read NBT: " + exception.getMessage(), exception);
        }
    }

    /**
     * Reads a template document, accepting either gzip or raw NBT.
     *
     * <p>Minecraft has written both over the years and packs in the wild contain both, so the
     * format is detected rather than assumed.
     */
    public static NbtTag.CompoundTag readAuto(InputStream stream) throws IOException {
        byte[] head = new byte[2];
        int read = readFully(stream, head);
        if (read == 2 && (head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x8B) {
            java.io.SequenceInputStream sequence = new java.io.SequenceInputStream(
                    new java.io.ByteArrayInputStream(head, 0, read), stream);
            return readGzipped(sequence);
        }
        java.io.ByteArrayOutputStream head2 = new java.io.ByteArrayOutputStream();
        if (read > 0) {
            head2.write(head, 0, read);
        }
        head2.write(read == 2 ? stream.readAllBytes() : new byte[0]);
        return readUncompressed(new java.io.ByteArrayInputStream(head2.toByteArray()));
    }

    private static int readFully(InputStream stream, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int count = stream.read(target, offset, target.length - offset);
            if (count < 0) {
                break;
            }
            offset += count;
        }
        return offset;
    }

    private static NbtTag.CompoundTag readRoot(DataInputStream input) throws IOException {
        int typeId = input.readUnsignedByte();
        if (typeId == TagType.END.id()) {
            // An empty document: some tools write a bare TAG_End root. Treat as an empty compound.
            return NbtTag.CompoundTag.empty();
        }
        if (typeId != TagType.COMPOUND.id()) {
            throw new NbtException("NBT root must be a COMPOUND but is " + TagType.byId(typeId) + ".");
        }
        readString(input); // The root tag's name, conventionally empty.
        NbtTag payload = readPayload(input, TagType.COMPOUND);
        return (NbtTag.CompoundTag) payload;
    }

    private static NbtTag readPayload(DataInputStream input, TagType type) throws IOException {
        switch (type) {
            case BYTE -> {
                return new NbtTag.ByteTag(input.readByte());
            }
            case SHORT -> {
                return new NbtTag.ShortTag(input.readShort());
            }
            case INT -> {
                return new NbtTag.IntTag(input.readInt());
            }
            case LONG -> {
                return new NbtTag.LongTag(input.readLong());
            }
            case FLOAT -> {
                return new NbtTag.FloatTag(input.readFloat());
            }
            case DOUBLE -> {
                return new NbtTag.DoubleTag(input.readDouble());
            }
            case BYTE_ARRAY -> {
                return new NbtTag.ByteArrayTag(readByteArray(input));
            }
            case STRING -> {
                return new NbtTag.StringTag(readString(input));
            }
            case LIST -> {
                int elementId = input.readUnsignedByte();
                TagType elementType = TagType.byId(elementId);
                int length = readLength(input);
                if (elementType == TagType.END && length > 0) {
                    throw new NbtException("NBT list declares END elements but has " + length + " of them.");
                }
                List<NbtTag> elements = new ArrayList<>(Math.min(length, 1024));
                for (int i = 0; i < length; i++) {
                    elements.add(readPayload(input, elementType));
                }
                return new NbtTag.ListTag(elementType, elements);
            }
            case COMPOUND -> {
                return readCompound(input);
            }
            case INT_ARRAY -> {
                return new NbtTag.IntArrayTag(readIntArray(input));
            }
            case LONG_ARRAY -> {
                return new NbtTag.LongArrayTag(readLongArray(input));
            }
            case END -> {
                throw new NbtException("Unexpected NBT TAG_End.");
            }
            default -> throw new NbtException("Unsupported NBT tag " + type + ".");
        }
    }

    private static NbtTag.CompoundTag readCompound(DataInputStream input) throws IOException {
        java.util.Map<String, NbtTag> entries = new java.util.LinkedHashMap<>();
        while (true) {
            int childTypeId = input.readUnsignedByte();
            if (childTypeId == TagType.END.id()) {
                return new NbtTag.CompoundTag(entries);
            }
            String name = readString(input);
            entries.put(name, readPayload(input, TagType.byId(childTypeId)));
        }
    }

    private static int readLength(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            throw new NbtException("NBT collection length is negative (" + length + ").");
        }
        if (length > MAX_COLLECTION_LENGTH) {
            throw new NbtException("NBT collection length " + length + " exceeds the "
                    + MAX_COLLECTION_LENGTH + " element limit; the file is corrupt.");
        }
        return length;
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readUnsignedShort();
        if (length > MAX_STRING_BYTES) {
            throw new NbtException("NBT string length " + length + " exceeds the "
                    + MAX_STRING_BYTES + " byte limit; the file is corrupt.");
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readByteArray(DataInputStream input) throws IOException {
        int length = readLength(input);
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return bytes;
    }

    private static int[] readIntArray(DataInputStream input) throws IOException {
        int length = readLength(input);
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = input.readInt();
        }
        return values;
    }

    private static long[] readLongArray(DataInputStream input) throws IOException {
        int length = readLength(input);
        long[] values = new long[length];
        for (int i = 0; i < length; i++) {
            values[i] = input.readLong();
        }
        return values;
    }

    /** Writes a compound as gzipped NBT, the format Minecraft expects for a {@code .nbt} file. */
    public static void writeGzipped(NbtTag.CompoundTag root, OutputStream stream) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(1 << 12);
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeByte(TagType.COMPOUND.id());
                output.writeShort(0); // Empty root name.
                writePayload(output, root);
            }
            try (GZIPOutputStream gzip = new GZIPOutputStream(stream, 1 << 16)) {
                gzip.write(buffer.toByteArray());
            }
        } catch (IOException exception) {
            throw new NbtException("Could not write gzipped NBT: " + exception.getMessage(), exception);
        }
    }

    /** Writes a compound as uncompressed NBT, which makes template diffs reviewable in git. */
    public static void writeUncompressed(NbtTag.CompoundTag root, OutputStream stream) {
        try {
            DataOutputStream output = new DataOutputStream(stream);
            output.writeByte(TagType.COMPOUND.id());
            output.writeShort(0);
            writePayload(output, root);
            output.flush();
        } catch (IOException exception) {
            throw new NbtException("Could not write NBT: " + exception.getMessage(), exception);
        }
    }

    private static void writePayload(DataOutputStream output, NbtTag tag) throws IOException {
        switch (tag.type()) {
            case BYTE -> output.writeByte(((NbtTag.ByteTag) tag).value());
            case SHORT -> output.writeShort(((NbtTag.ShortTag) tag).value());
            case INT -> output.writeInt(((NbtTag.IntTag) tag).value());
            case LONG -> output.writeLong(((NbtTag.LongTag) tag).value());
            case FLOAT -> output.writeFloat(((NbtTag.FloatTag) tag).value());
            case DOUBLE -> output.writeDouble(((NbtTag.DoubleTag) tag).value());
            case BYTE_ARRAY -> {
                byte[] values = ((NbtTag.ByteArrayTag) tag).raw();
                output.writeInt(values.length);
                output.write(values);
            }
            case STRING -> writeString(output, ((NbtTag.StringTag) tag).value());
            case LIST -> {
                NbtTag.ListTag list = (NbtTag.ListTag) tag;
                output.writeByte(list.elementType().id());
                output.writeInt(list.elements().size());
                for (NbtTag element : list.elements()) {
                    writePayload(output, element);
                }
            }
            case COMPOUND -> {
                for (var entry : ((NbtTag.CompoundTag) tag).entries().entrySet()) {
                    output.writeByte(entry.getValue().type().id());
                    writeString(output, entry.getKey());
                    writePayload(output, entry.getValue());
                }
                output.writeByte(TagType.END.id());
            }
            case INT_ARRAY -> {
                int[] values = ((NbtTag.IntArrayTag) tag).raw();
                output.writeInt(values.length);
                for (int value : values) {
                    output.writeInt(value);
                }
            }
            case LONG_ARRAY -> {
                long[] values = ((NbtTag.LongArrayTag) tag).raw();
                output.writeInt(values.length);
                for (long value : values) {
                    output.writeLong(value);
                }
            }
            case END -> throw new NbtException("Cannot write a bare TAG_End payload.");
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeShort(bytes.length);
        output.write(bytes);
    }
}