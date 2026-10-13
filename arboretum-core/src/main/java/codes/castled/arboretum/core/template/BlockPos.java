package codes.castled.arboretum.core.template;

/**
 * A block position inside a template or a world.
 *
 * <p>Deliberately independent of Minecraft's {@code BlockPos}. The placement planner is pure
 * arithmetic over these coordinates and is unit tested without a game running.
 */
public record BlockPos(int x, int y, int z) {

    public BlockPos {
        // BlockPos has no meaningful normalisation; a position is what it is.
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public BlockPos offset(Direction direction) {
        return offset(direction.stepX(), direction.stepY(), direction.stepZ());
    }

    public BlockPos relative(BlockPos other) {
        return new BlockPos(x - other.x, y - other.y, z - other.z);
    }

    /** @return the smallest axis-aligned box containing both positions. */
    public static Bounds boundsOf(java.util.Collection<BlockPos> positions) {
        if (positions.isEmpty()) {
            return new Bounds(0, 0, 0, 0, 0, 0);
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.x());
            minY = Math.min(minY, pos.y());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxY = Math.max(maxY, pos.y());
            maxZ = Math.max(maxZ, pos.z());
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** An inclusive axis-aligned box. */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int sizeX() {
            return maxX - minX + 1;
        }

        public int sizeY() {
            return maxY - minY + 1;
        }

        public int sizeZ() {
            return maxZ - minZ + 1;
        }

        public boolean contains(BlockPos pos) {
            return pos.x() >= minX && pos.x() <= maxX
                    && pos.y() >= minY && pos.y() <= maxY
                    && pos.z() >= minZ && pos.z() <= maxZ;
        }
    }
}