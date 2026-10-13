package codes.castled.arboretum.fabric;

import codes.castled.arboretum.core.placement.BlockQuery;
import codes.castled.arboretum.core.template.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The core's read-only world view over a Minecraft level.
 *
 * <p>Only chunks within {@code reach} of the centre chunk are answered; everything further is
 * UNKNOWN, and the planner refuses a tree that touches an UNKNOWN position rather than clip it.
 * During world generation the reach is the one chunk a feature may write around its own, so a
 * tree never writes where Minecraft would reject it.
 */
final class RegionBlockQuery implements BlockQuery {

    private final LevelReader level;
    private final int centreX;
    private final int centreZ;
    private final int reach;

    RegionBlockQuery(LevelReader level, net.minecraft.core.BlockPos centre, int reach) {
        this.level = level;
        this.centreX = centre.getX() >> 4;
        this.centreZ = centre.getZ() >> 4;
        this.reach = reach;
    }

    @Override
    public WorldBlock blockAt(BlockPos pos) {
        int chunkX = pos.x() >> 4;
        int chunkZ = pos.z() >> 4;
        if (Math.abs(chunkX - centreX) > reach || Math.abs(chunkZ - centreZ) > reach
                || !level.hasChunk(chunkX, chunkZ)
                || pos.y() < level.getMinY() || pos.y() > level.getMaxY()) {
            return WorldBlock.unknown();
        }
        BlockState state = level.getBlockState(new net.minecraft.core.BlockPos(pos.x(), pos.y(), pos.z()));
        boolean fluid = !state.getFluidState().isEmpty();
        Occupancy occupancy;
        if (state.isAir()) {
            occupancy = Occupancy.AIR;
        } else if (state.getBlock() instanceof LiquidBlock) {
            occupancy = Occupancy.FLUID;
        } else if (state.canBeReplaced()) {
            occupancy = Occupancy.REPLACEABLE;
        } else {
            occupancy = Occupancy.SOLID;
        }
        return new WorldBlock(BlockStates.idOf(state), occupancy, fluid);
    }
}
