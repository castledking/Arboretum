package codes.castled.arboretum.core.placement;

import codes.castled.arboretum.core.template.BlockPos;
import codes.castled.arboretum.core.template.Rotation;

/**
 * Everything the planner needs to know about one placement attempt, none of it Minecraft.
 *
 * @param origin       the world position the template's base centre maps to
 * @param rotation     quarter-turn rotation about the vertical axis through {@code origin}
 * @param randomSeed   seed for any deterministic-but-varying choice, so the same attempt replays
 * @param minY         lowest world Y a tree may occupy
 * @param maxY         highest world Y a tree may occupy
 * @param groundBlocks blocks the species may root in
 */
public record PlacementContext(
        BlockPos origin,
        Rotation rotation,
        long randomSeed,
        int minY,
        int maxY,
        java.util.Set<String> groundBlocks
) {
    public PlacementContext {
        groundBlocks = java.util.Set.copyOf(groundBlocks);
    }

    /** @return a copy positioned somewhere else, keeping every other setting. */
    public PlacementContext at(BlockPos newOrigin) {
        return new PlacementContext(newOrigin, rotation, randomSeed, minY, maxY, groundBlocks);
    }

    /** @return a copy with a different rotation. */
    public PlacementContext rotated(Rotation newRotation) {
        return new PlacementContext(origin, newRotation, randomSeed, minY, maxY, groundBlocks);
    }

    /** @return true when a Y value is inside the build limits. */
    public boolean withinHeight(int y) {
        return y >= minY && y <= maxY;
    }
}