package codes.castled.arboretum.core.testing;

import codes.castled.arboretum.core.placement.BlockQuery;
import codes.castled.arboretum.core.template.BlockPos;
import java.util.HashMap;
import java.util.Map;

/**
 * A flat test world: one ground block filling everything below y=0, air above, and only a
 * square region "loaded" - outside it every position is UNKNOWN, as an ungenerated chunk is.
 * Single blocks can be set on top of that, to obstruct or protect.
 */
public final class FlatWorld implements BlockQuery {

    private final String ground;
    private final int radius;
    private final Map<BlockPos, WorldBlock> placed = new HashMap<>();

    public FlatWorld(String ground, int radius) {
        this.ground = ground;
        this.radius = radius;
    }

    /** Grass to y=-1, air above, loaded for 64 blocks around the origin. */
    public static FlatWorld grass() {
        return new FlatWorld("minecraft:grass_block", 64);
    }

    public FlatWorld set(BlockPos pos, String blockId, Occupancy occupancy) {
        placed.put(pos, WorldBlock.of(blockId, occupancy));
        return this;
    }

    @Override
    public WorldBlock blockAt(BlockPos pos) {
        if (Math.abs(pos.x()) > radius || Math.abs(pos.z()) > radius) {
            return WorldBlock.unknown();
        }
        WorldBlock set = placed.get(pos);
        if (set != null) {
            return set;
        }
        return pos.y() < 0 ? WorldBlock.of(ground, Occupancy.SOLID) : WorldBlock.air();
    }
}
