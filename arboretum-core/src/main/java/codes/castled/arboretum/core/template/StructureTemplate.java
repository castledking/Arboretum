package codes.castled.arboretum.core.template;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A parsed structure template: a palette of block states plus positioned references into it.
 *
 * <p>This is Minecraft's structure-template shape, which is what a structure block saves and what
 * Arboretum therefore has to read. Arboretum's own additions live in the species definition rather
 * than in the file, so a template authored in-game needs no Arboretum-specific tooling.
 *
 * <p>Immutable and shared between threads: templates are parsed once, cached, and read
 * concurrently during chunk generation.
 */
public final class StructureTemplate {

    /** The DataVersion Arboretum writes into templates it generates itself. */
    public static final int WRITER_DATA_VERSION = 4325;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final List<BlockStateRef> palette;
    private final List<TemplateBlock> blocks;
    private final int dataVersion;

    public StructureTemplate(int sizeX, int sizeY, int sizeZ,
                             List<BlockStateRef> palette, List<TemplateBlock> blocks, int dataVersion) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = List.copyOf(palette);
        this.blocks = List.copyOf(blocks);
        this.dataVersion = dataVersion;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public List<BlockStateRef> palette() {
        return palette;
    }

    public List<TemplateBlock> blocks() {
        return blocks;
    }

    /** The Minecraft DataVersion the template was saved with. Zero for Arboretum-written templates. */
    public int dataVersion() {
        return dataVersion;
    }

    public int blockCount() {
        return blocks.size();
    }

    /**
     * @return an index from block id to the template positions that use it. Built once at load so
     *         the placement planner never rescans the block list per marker lookup.
     */
    public Map<String, List<TemplateBlock>> indexByBlockId() {
        Map<String, List<TemplateBlock>> index = new LinkedHashMap<>();
        for (TemplateBlock block : blocks) {
            BlockStateRef state = stateAt(block);
            if (state != null) {
                index.computeIfAbsent(state.blockId(), key -> new java.util.ArrayList<>()).add(block);
            }
        }
        index.replaceAll((key, value) -> Collections.unmodifiableList(value));
        return Collections.unmodifiableMap(index);
    }

    /** @return the block state a template block refers to, or null when its palette index is bad. */
    public BlockStateRef stateAt(TemplateBlock block) {
        int index = block.paletteIndex();
        return index >= 0 && index < palette.size() ? palette.get(index) : null;
    }

    /** An inclusive box covering every placed block. */
    public BlockPos.Bounds bounds() {
        return BlockPos.boundsOf(blocks.stream().map(TemplateBlock::pos).toList());
    }

    /** @return a builder for constructing templates programmatically. */
    public static Builder builder(int sizeX, int sizeY, int sizeZ) {
        return new Builder(sizeX, sizeY, sizeZ);
    }

    /**
     * Assembles a template from positioned block states.
     *
     * <p>Palettes are built automatically and deduplicated, because a template that lists 900 oak
     * leaves as 900 separate palette entries is valid NBT but a needless memory cost when it is
     * cached and held for the life of the process.
     */
    public static final class Builder {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final Map<BlockStateRef, Integer> paletteIndex = new LinkedHashMap<>();
        private final List<BlockStateRef> palette = new java.util.ArrayList<>();
        private final List<TemplateBlock> blocks = new java.util.ArrayList<>();
        private int dataVersion = WRITER_DATA_VERSION;

        Builder(int sizeX, int sizeY, int sizeZ) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
        }

        public Builder dataVersion(int value) {
            this.dataVersion = value;
            return this;
        }

        public Builder put(BlockPos pos, BlockStateRef state) {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(state, "state");
            blocks.add(new TemplateBlock(pos, intern(state)));
            return this;
        }

        public Builder put(int x, int y, int z, BlockStateRef state) {
            return put(new BlockPos(x, y, z), state);
        }

        private int intern(BlockStateRef state) {
            Integer existing = paletteIndex.get(state);
            if (existing != null) {
                return existing;
            }
            int index = palette.size();
            palette.add(state);
            paletteIndex.put(state, index);
            return index;
        }

        public StructureTemplate build() {
            return new StructureTemplate(sizeX, sizeY, sizeZ, palette, blocks, dataVersion);
        }
    }

    @Override
    public String toString() {
        return "StructureTemplate[" + sizeX + "x" + sizeY + "x" + sizeZ
                + ", " + blocks.size() + " blocks, " + palette.size() + " palette entries]";
    }
}