package codes.castled.arboretum.core.template;

import codes.castled.arboretum.core.nbt.NbtException;
import codes.castled.arboretum.core.nbt.NbtIO;
import codes.castled.arboretum.core.nbt.NbtTag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between Minecraft structure-template NBT and {@link StructureTemplate}.
 *
 * <p>The format is the one a structure block writes, which is the whole point: a contributor builds
 * a tree in-game, saves a structure block, drops the {@code .nbt} into the species folder, and it
 * works. See docs/authoring-trees.md.
 */
public final class StructureTemplateIO {

    /** Templates larger than this are rejected. A 128x128x128 cube is already 2M blocks. */
    public static final int MAX_TEMPLATE_DIMENSION = 256;
    public static final int MAX_TEMPLATE_BLOCKS = 1 << 21;

    private StructureTemplateIO() {
    }

    /** Parses a template document. Gzip and uncompressed NBT are both accepted. */
    public static StructureTemplate read(InputStream stream) {
        NbtTag.CompoundTag root;
        try {
            root = NbtIO.readAuto(stream);
        } catch (IOException exception) {
            throw new NbtException("Could not open the template stream: " + exception.getMessage(), exception);
        }
        return read(root);
    }

    /** Parses a template from an already-read NBT compound. */
    public static StructureTemplate read(NbtTag.CompoundTag root) {
        String what = "Structure template";
        // Collect rather than abort on the first problem, so a broken template reports everything
        // wrong with it at once. The final throw is what stops a corrupt template being used.
        List<String> problems = new ArrayList<>();
        NbtTag.CompoundTagAccess access = new NbtTag.CompoundTagAccess(root, problems::add);

        int[] size = access.readIntVector("size", 3, what);
        if (size == null) {
            throw new NbtException(String.join("; ", problems));
        }
        int sizeX = size[0];
        int sizeY = size[1];
        int sizeZ = size[2];
        requirePositiveDimension("x", sizeX, what);
        requirePositiveDimension("y", sizeY, what);
        requirePositiveDimension("z", sizeZ, what);

        List<NbtTag.CompoundTag> paletteTags;
        List<NbtTag.CompoundTag> blockTags;
        try {
            paletteTags = access.requireCompoundList("palette", what);
            blockTags = access.requireCompoundList("blocks", what);
        } catch (NbtException exception) {
            problems.add(exception.getMessage());
            throw new NbtException(String.join("; ", problems), exception);
        }

        List<BlockStateRef> palette = readPalette(paletteTags, what, problems);
        List<TemplateBlock> blocks = readBlocks(blockTags, palette.size(), what, problems);
        int dataVersion = access.has("DataVersion") ? access.requireInt("DataVersion", what) : 0;

        if (!problems.isEmpty()) {
            throw new NbtException(what + " is malformed: " + String.join("; ", problems));
        }
        return new StructureTemplate(sizeX, sizeY, sizeZ, palette, blocks, dataVersion);
    }

    private static void requirePositiveDimension(String axis, int value, String what) {
        if (value <= 0) {
            throw new NbtException(what + " has a non-positive '" + axis + "' size of " + value + ".");
        }
        if (value > MAX_TEMPLATE_DIMENSION) {
            throw new NbtException(what + " '" + axis + "' size " + value + " exceeds the "
                    + MAX_TEMPLATE_DIMENSION + " limit; the file is corrupt.");
        }
    }

    private static List<BlockStateRef> readPalette(List<NbtTag.CompoundTag> entries, String what,
                                                List<String> problems) {
        if (entries.isEmpty()) {
            problems.add(what + " has an empty palette.");
            return List.of();
        }
        List<BlockStateRef> palette = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            final int index = i;
            NbtTag.CompoundTagAccess entry =
                    new NbtTag.CompoundTagAccess(entries.get(index), message -> problems.add(
                            what + " palette entry " + index + ": " + message));
            // Structure blocks write Name/Properties; some schematic converters write id/properties.
            NbtTag nameTag = entry.get("Name") != null ? entry.get("Name") : entry.get("id");
            if (nameTag == null) {
                problems.add(what + " palette entry " + index + " is missing required tag 'Name'.");
                continue;
            }
            if (!(nameTag instanceof NbtTag.StringTag name)) {
                problems.add(what + " palette entry " + index + " has 'Name' of type "
                        + nameTag.type() + ", expected STRING.");
                continue;
            }
            Map<String, String> properties = new LinkedHashMap<>();
            NbtTag propertiesTag = entry.get("Properties") != null ? entry.get("Properties") : entry.get("properties");
            if (propertiesTag instanceof NbtTag.CompoundTag propertiesCompound) {
                for (var property : propertiesCompound.entries().entrySet()) {
                    if (property.getValue() instanceof NbtTag.StringTag value) {
                        properties.put(property.getKey(), value.value());
                    } else {
                        problems.add(what + " palette entry " + index + " property '" + property.getKey()
                                + "' should be a string but is " + property.getValue().type() + ".");
                    }
                }
            } else if (propertiesTag != null) {
                problems.add(what + " palette entry " + index + " has 'Properties' of type "
                        + propertiesTag.type() + ", expected COMPOUND.");
                continue;
            }
            palette.add(new BlockStateRef(name.value(), properties));
        }
        return palette;
    }

    private static List<TemplateBlock> readBlocks(List<NbtTag.CompoundTag> entries, int paletteSize,
                                                  String what, List<String> problems) {
        if (entries.size() > MAX_TEMPLATE_BLOCKS) {
            problems.add(what + " has " + entries.size() + " blocks, over the "
                    + MAX_TEMPLATE_BLOCKS + " limit; the file is corrupt.");
            return List.of();
        }
        List<TemplateBlock> blocks = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            final int index = i;
            NbtTag.CompoundTagAccess entry =
                    new NbtTag.CompoundTagAccess(entries.get(index), message -> problems.add(
                            what + " block " + index + ": " + message));
            int[] pos = entry.readIntVector("pos", 3, what + " block " + index);
            if (pos == null) {
                continue;
            }
            NbtTag stateTag = entry.get("state");
            if (!(stateTag instanceof NbtTag.IntTag stateTagValue)) {
                problems.add(what + " block " + index + " tag 'state' should be Int but is "
                        + (stateTag == null ? "missing" : stateTag.type()) + ".");
                continue;
            }
            int state = stateTagValue.value();
            if (state < 0 || state >= paletteSize) {
                problems.add(what + " block " + index + " references palette index " + state
                        + " but the palette holds " + paletteSize + " entries.");
                continue;
            }
            blocks.add(new TemplateBlock(new BlockPos(pos[0], pos[1], pos[2]), state));
        }
        return blocks;
    }

    /** Serialises a template as gzipped NBT, ready to be written to {@code .nbt}. */
    public static byte[] writeGzipped(StructureTemplate template) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 14);
        NbtIO.writeGzipped(toNbt(template), out);
        return out.toByteArray();
    }

    /** Serialises a template as plain NBT, which keeps generated templates diffable in git. */
    public static byte[] writeUncompressed(StructureTemplate template) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        NbtIO.writeUncompressed(toNbt(template), out);
        return out.toByteArray();
    }

    /**
 * Builds the NBT compound for a template.
 *
 * <p>Writes {@code size} and {@code pos} as int arrays, matching what Minecraft's own structure
 * block produces, so the shipped templates load in every tool that reads vanilla structures and not
 * only in Arboretum.
 */
public static NbtTag.CompoundTag toNbt(StructureTemplate template) {
        List<NbtTag> paletteTags = new ArrayList<>(template.palette().size());
        for (BlockStateRef state : template.palette()) {
            NbtTag.CompoundTagBuilder entry = NbtTag.CompoundTag.empty().toBuilder()
                    .putString("Name", state.blockId());
            if (!state.properties().isEmpty()) {
                Map<String, NbtTag> properties = new LinkedHashMap<>();
                state.properties().forEach((key, value) -> properties.put(key, new NbtTag.StringTag(value)));
                entry.putCompound("Properties", new NbtTag.CompoundTag(properties));
            }
            paletteTags.add(entry.build());
        }

        List<NbtTag> blockTags = new ArrayList<>(template.blocks().size());
        for (TemplateBlock block : template.blocks()) {
            blockTags.add(NbtTag.CompoundTag.empty().toBuilder()
                    .putIntArray("pos", new int[]{block.pos().x(), block.pos().y(), block.pos().z()})
                    .putInt("state", block.paletteIndex())
                    .build());
        }

        return NbtTag.CompoundTag.empty().toBuilder()
                .putIntArray("size", new int[]{template.sizeX(), template.sizeY(), template.sizeZ()})
                .putList("entities", NbtTag.ListTag.empty())
                .putList("blocks", new NbtTag.ListTag(TagTypes.COMPOUND, blockTags))
                .putList("palette", new NbtTag.ListTag(TagTypes.COMPOUND, paletteTags))
                .putInt("DataVersion", template.dataVersion())
                .build();
    }

    /** Local alias so the list tag types read clearly at the call sites above. */
    private static final class TagTypes {
        static final codes.castled.arboretum.core.nbt.TagType COMPOUND =
                codes.castled.arboretum.core.nbt.TagType.COMPOUND;

        private TagTypes() {
        }
    }
}