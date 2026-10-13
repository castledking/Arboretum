package codes.castled.arboretum.core.template;

import codes.castled.arboretum.core.nbt.NbtException;
import codes.castled.arboretum.core.nbt.NbtIO;
import codes.castled.arboretum.core.nbt.NbtTag;
import codes.castled.arboretum.core.nbt.TagType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NBT reading and writing, including reading templates produced by other tools.
 *
 * <p>{@code fixtures/ottyg_trunk.nbt} is a trunk template written by Oh The Trees You'll Grow,
 * MPL-2.0, (c) Corgi Taco, used unmodified as third-party input. It is here to prove Arboretum's
 * reader handles the format a real tool emits rather than only its own writer's output.
 */
class NbtIOTest {

    @Test
    void roundTripsEveryTagTypeUncompressed() {
        NbtTag.CompoundTag root = sampleRoot();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIO.writeUncompressed(root, out);

        NbtTag.CompoundTag read = NbtIO.readUncompressed(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(root, read, "a full round trip must preserve every tag");
    }

    @Test
    void roundTripsGzipped() {
        NbtTag.CompoundTag root = sampleRoot();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIO.writeGzipped(root, out);

        byte[] compressed = out.toByteArray();
        assertEquals(0x1F, compressed[0] & 0xFF, "output must start with the gzip magic byte");
        assertEquals(0x8B, compressed[1] & 0xFF);

        assertEquals(root, NbtIO.readGzipped(new ByteArrayInputStream(compressed)));
    }

    @Test
    void autoDetectHandlesBothEncodings() throws IOException {
        NbtTag.CompoundTag root = sampleRoot();

        ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
        NbtIO.writeGzipped(root, gzipped);
        assertEquals(root, NbtIO.readAuto(new ByteArrayInputStream(gzipped.toByteArray())));

        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        NbtIO.writeUncompressed(root, plain);
        assertEquals(root, NbtIO.readAuto(new ByteArrayInputStream(plain.toByteArray())));
    }

    @Test
    void autoDetectDoesNotLoseTheBytesItPeekedAt() throws IOException {
        // readAuto peeks two bytes to sniff the format. If it dropped them, a gzipped template
        // would fail to inflate - a bug this test exists to catch.
        NbtTag.CompoundTag root = sampleRoot();
        ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
        NbtIO.writeGzipped(root, gzipped);
        assertEquals(root, NbtIO.readAuto(new ByteArrayInputStream(gzipped.toByteArray())));
    }

    @Test
    void readsATrunkTemplateWrittenByOhTheTreesYoullGrow() throws IOException {
        StructureTemplate template = readFixture("ottyg_trunk.nbt");

        assertEquals(3, template.sizeX());
        assertEquals(5, template.sizeY());
        assertEquals(3, template.sizeZ());
        assertEquals(14, template.blocks().size());
        assertEquals(3, template.palette().size());
        assertTrue(template.dataVersion() > 0, "a Minecraft-saved template records a DataVersion");

        // The palette holds red wool, white wool and an axis-y oak log. Arboretum does not assign
        // meaning to wool; it only has to read it without complaint.
        List<String> ids = template.palette().stream().map(BlockStateRef::blockId).toList();
        assertEquals(List.of("minecraft:red_wool", "minecraft:white_wool", "minecraft:oak_log"), ids);

        BlockStateRef log = template.palette().stream()
                .filter(state -> state.blockId().equals("minecraft:oak_log")).findFirst().orElseThrow();
        assertEquals("y", log.property("axis"), "state properties must survive the read");
    }

    @Test
    void roundTripsATemplateProducedByTheGeneratorTool() throws IOException {
        StructureTemplate original = readFixture("baobab_trunk.nbt");
        byte[] written = StructureTemplateIO.writeGzipped(original);
        StructureTemplate reread = StructureTemplateIO.read(new ByteArrayInputStream(written));

        assertEquals(original.sizeX(), reread.sizeX());
        assertEquals(original.sizeY(), reread.sizeY());
        assertEquals(original.sizeZ(), reread.sizeZ());
        assertEquals(original.palette(), reread.palette());
        assertEquals(original.blocks(), reread.blocks(),
                "every block and its palette index must survive a round trip");
    }

    @Test
    void deduplicatesThePaletteOnWrite() {
        StructureTemplate template = StructureTemplate.builder(3, 3, 3)
                .put(0, 0, 0, BlockStateRef.of("minecraft:dark_oak_log"))
                .put(1, 0, 0, BlockStateRef.of("minecraft:dark_oak_log"))
                .put(2, 0, 0, BlockStateRef.of("minecraft:dark_oak_log"))
                .put(0, 1, 0, BlockStateRef.of("minecraft:dark_oak_leaves"))
                .build();

        assertEquals(4, template.blocks().size());
        assertEquals(2, template.palette().size(),
                "identical states must share one palette entry, or a 900-block canopy costs 900 entries");
    }

    @Test
    void rejectsANonCompoundRoot() {
        byte[] notACompound = {0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        NbtException exception = assertThrows(NbtException.class,
                () -> NbtIO.readUncompressed(new ByteArrayInputStream(notACompound)));
        assertTrue(exception.getMessage().contains("COMPOUND"), exception.getMessage());
    }

    @Test
    void rejectsAnAbsurdCollectionLengthRatherThanAllocating() {
        // A corrupt length header must fail immediately rather than trying to allocate two
        // billion elements, which is how a bad file turns into an OutOfMemoryError.
        byte[] body = {
                0x0A, 0x00, 0x00,
                0x09, 0x00, 0x02, 'b', 'd',
                0x0A, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };
        NbtException exception = assertThrows(NbtException.class,
                () -> NbtIO.readUncompressed(new ByteArrayInputStream(body)));
        assertTrue(exception.getMessage().contains("limit"), exception.getMessage());
    }

    @Test
    void rejectsANegativeCollectionLength() {
        byte[] body = {0x0A, 0x00, 0x00, 0x09, 0x00, 0x02, 'b', 'd',
                0x0A, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        NbtException exception = assertThrows(NbtException.class,
                () -> NbtIO.readUncompressed(new ByteArrayInputStream(body)));
        assertTrue(exception.getMessage().contains("negative"), exception.getMessage());
    }

    @Test
    void rejectsTruncatedInput() {
        NbtTag.CompoundTag root = sampleRoot();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIO.writeUncompressed(root, out);
        byte[] full = out.toByteArray();

        assertThrows(RuntimeException.class, () -> NbtIO.readUncompressed(
                new ByteArrayInputStream(java.util.Arrays.copyOf(full, full.length / 2))));
    }

    @Test
    void gzipOutputIsActuallyCompressed() {
        NbtTag.CompoundTag root = NbtTag.CompoundTag.empty().toBuilder()
                .putList("blocks", new NbtTag.ListTag(TagType.COMPOUND,
                        Collections.nCopies(200, NbtTag.CompoundTag.empty()
                                .toBuilder().putInt("state", 1).build())))
                .build();

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        NbtIO.writeGzipped(root, compressed);
        ByteArrayOutputStream plain = new ByteArrayOutputStream();
        NbtIO.writeUncompressed(root, plain);

        assertTrue(compressed.size() < plain.size() / 4,
                "gzipped " + compressed.size() + " should be far smaller than plain " + plain.size());
    }

    @Test
    void preservesUnicodeInStrings() {
        NbtTag.CompoundTag root = NbtTag.CompoundTag.empty().toBuilder()
                .putString("name", "Adansonia digitata \u2014 baobab \u00e9\u00e8")
                .build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIO.writeUncompressed(root, out);
        NbtTag.CompoundTag read = NbtIO.readUncompressed(new ByteArrayInputStream(out.toByteArray()));
        assertEquals("Adansonia digitata \u2014 baobab \u00e9\u00e8",
                new NbtTag.CompoundTagAccess(read).requireString("name", "test"));
    }

    @Test
    void byteArrayAndLongArrayTagsRoundTrip() {
        NbtTag.CompoundTag root = NbtTag.CompoundTag.empty().toBuilder()
                .put("blob", new NbtTag.ByteArrayTag(new byte[]{1, -2, 3, 0}))
                .put("longs", new NbtTag.LongArrayTag(new long[]{Long.MIN_VALUE, 0L, Long.MAX_VALUE}))
                .build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIO.writeUncompressed(root, out);
        NbtTag.CompoundTagAccess read =
                new NbtTag.CompoundTagAccess(NbtIO.readUncompressed(new ByteArrayInputStream(out.toByteArray())));

        assertArrayEquals(new byte[]{1, -2, 3, 0},
                ((NbtTag.ByteArrayTag) read.get("blob")).value());
        assertArrayEquals(new long[]{Long.MIN_VALUE, 0L, Long.MAX_VALUE},
                ((NbtTag.LongArrayTag) read.get("longs")).value());
    }

    @Test
    void reportsAPaletteIndexThatPointsPastThePalette() {
        NbtTag.CompoundTag root = NbtTag.CompoundTag.empty().toBuilder()
                .putIntArray("size", new int[]{1, 1, 1})
                .putList("entities", NbtTag.ListTag.empty())
                .putList("blocks", new NbtTag.ListTag(TagType.COMPOUND, List.of(
                        NbtTag.CompoundTag.empty().toBuilder()
                                .putIntArray("pos", new int[]{0, 0, 0})
                                .putInt("state", 7)
                                .build())))
                .putList("palette", new NbtTag.ListTag(TagType.COMPOUND, List.of(
                        NbtTag.CompoundTag.empty().toBuilder()
                                .putString("Name", "minecraft:stone").build())))
                .build();

        NbtException exception = assertThrows(NbtException.class,
                () -> StructureTemplateIO.read(root));
        assertTrue(exception.getMessage().contains("palette index 7"), exception.getMessage());
    }

    @Test
    void reportsAMissingRequiredTemplateTag() {
        NbtTag.CompoundTag root = NbtTag.CompoundTag.empty().toBuilder()
                .putIntArray("size", new int[]{1, 1, 1})
                .putList("entities", NbtTag.ListTag.empty())
                .putList("blocks", NbtTag.ListTag.empty())
                .build();

        NbtException exception = assertThrows(NbtException.class, () -> StructureTemplateIO.read(root));
        assertTrue(exception.getMessage().contains("palette"), exception.getMessage());
    }

    @Test
    void rejectsATemplateDimensionBeyondTheLimit() {
        NbtTag.CompoundTag root = NbtTag.CompoundTag.empty().toBuilder()
                .putIntArray("size", new int[]{100000, 1, 1})
                .putList("entities", NbtTag.ListTag.empty())
                .putList("blocks", NbtTag.ListTag.empty())
                .putList("palette", new NbtTag.ListTag(TagType.COMPOUND, List.of(
                        NbtTag.CompoundTag.empty().toBuilder()
                                .putString("Name", "minecraft:stone").build())))
                .build();

        NbtException exception = assertThrows(NbtException.class, () -> StructureTemplateIO.read(root));
        assertTrue(exception.getMessage().contains("corrupt"), exception.getMessage());
    }

    @Test
    void acceptsSizeAndPosAsEitherIntArraysOrIntLists() {
        // Minecraft's structure block writes int arrays; other tree tools write lists of ints.
        // Both describe the same template, so both must load to the same model.
        StructureTemplate fromArrays = StructureTemplateIO.read(templateWith(VectorEncoding.ARRAY));
        StructureTemplate fromList = StructureTemplateIO.read(templateWith(VectorEncoding.LIST));

        assertEquals(3, fromArrays.sizeX());
        assertEquals(3, fromList.sizeX());
        assertEquals(fromArrays.blocks(), fromList.blocks(),
                "the two encodings must produce identical templates");
        assertEquals(fromArrays.palette(), fromList.palette());
    }

    @Test
    void rejectsAVectorOfTheWrongLength() {
        NbtTag.CompoundTag root = templateWith(VectorEncoding.ARRAY);
        NbtTag.CompoundTag broken = root.toBuilder()
                .put("size", new NbtTag.IntArrayTag(new int[]{3, 5}))
                .build();
        NbtException exception = assertThrows(NbtException.class, () -> StructureTemplateIO.read(broken));
        assertTrue(exception.getMessage().contains("3 components"), exception.getMessage());
    }

    @Test
    void rejectsAnIntListWhoseElementsAreNotInts() {
        NbtTag.CompoundTag root = templateWith(VectorEncoding.ARRAY);
        NbtTag.CompoundTag broken = root.toBuilder()
                .put("size", new NbtTag.ListTag(TagType.STRING, List.of(
                        new NbtTag.StringTag("3"), new NbtTag.StringTag("5"), new NbtTag.StringTag("3"))))
                .build();
        NbtException exception = assertThrows(NbtException.class, () -> StructureTemplateIO.read(broken));
        assertTrue(exception.getMessage().contains("expected a list of INT"), exception.getMessage());
    }

    private enum VectorEncoding { ARRAY, LIST }

    private static NbtTag.CompoundTag templateWith(VectorEncoding encoding) {
        NbtTag size = switch (encoding) {
            case ARRAY -> new NbtTag.IntArrayTag(new int[]{3, 5, 3});
            case LIST -> new NbtTag.ListTag(TagType.INT, List.of(
                    new NbtTag.IntTag(3), new NbtTag.IntTag(5), new NbtTag.IntTag(3)));
        };
        NbtTag pos = switch (encoding) {
            case ARRAY -> new NbtTag.IntArrayTag(new int[]{1, 2, 3});
            case LIST -> new NbtTag.ListTag(TagType.INT, List.of(
                    new NbtTag.IntTag(1), new NbtTag.IntTag(2), new NbtTag.IntTag(3)));
        };

        NbtTag.CompoundTag paletteEntry = NbtTag.CompoundTag.empty().toBuilder()
                .putString("Name", "minecraft:oak_log").build();
        NbtTag.CompoundTag block = NbtTag.CompoundTag.empty().toBuilder()
                .put("pos", pos)
                .putInt("state", 0)
                .build();

        return NbtTag.CompoundTag.empty().toBuilder()
                .put("size", size)
                .putList("entities", NbtTag.ListTag.empty())
                .putList("blocks", new NbtTag.ListTag(TagType.COMPOUND, List.of(block)))
                .putList("palette", new NbtTag.ListTag(TagType.COMPOUND, List.of(paletteEntry)))
                .build();
    }

    private static StructureTemplate readFixture(String name) throws IOException {
        try (InputStream stream = NbtIOTest.class.getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(stream, "missing test fixture " + name);
            return StructureTemplateIO.read(stream);
        }
    }

    private static NbtTag.CompoundTag sampleRoot() {
        Map<String, NbtTag> properties = Map.of("axis", new NbtTag.StringTag("y"));
        NbtTag.CompoundTag paletteEntry = NbtTag.CompoundTag.empty().toBuilder()
                .putString("Name", "minecraft:oak_log")
                .putCompound("Properties", new NbtTag.CompoundTag(properties))
                .build();
        NbtTag.CompoundTag block = NbtTag.CompoundTag.empty().toBuilder()
                .putIntArray("pos", new int[]{1, -2, 3})
                .putInt("state", 0)
                .build();

        List<NbtTag> entries = new ArrayList<>();
        entries.add(block);
        return NbtTag.CompoundTag.empty().toBuilder()
                .putIntArray("size", new int[]{3, 5, 3})
                .putList("entities", NbtTag.ListTag.empty())
                .putList("blocks", new NbtTag.ListTag(TagType.COMPOUND, entries))
                .putList("palette", new NbtTag.ListTag(TagType.COMPOUND, List.of(paletteEntry)))
                .putInt("DataVersion", 4325)
                .build();
    }
}