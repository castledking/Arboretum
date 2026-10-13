package codes.castled.arboretum.core.placement;

import codes.castled.arboretum.core.template.BlockPos;

import java.util.Objects;

/**
 * Read-only view of the world the placement planner needs.
 *
 * <p>This one interface is what makes the placement engine testable without a running game. The
 * Fabric adapter implements it over a {@code WorldGenLevel}; the Paper adapter over a Bukkit
 * {@code World}; the unit tests over a small in-memory map. The planner itself contains no
 * Minecraft types at all, so the rules that decide whether a tree may be placed are the same rules
 * that ship.
 *
 * <p>Both the block <em>id</em> and its occupancy are exposed. Occupancy alone would be enough to
 * ask "is this air", but not to enforce the two rules that matter most: the species' ground filter
 * ("this species roots in sand") and the protected-block floor ("never overwrite bedrock"). Both
 * need to know which block is actually there, so the query returns it rather than leaving the core
 * to guess.
 *
 * <p>Every query is by absolute world position. The planner never loads a chunk: a position the
 * adapter cannot answer for comes back {@link Occupancy#UNKNOWN} and the whole tree is refused.
 */
public interface BlockQuery {

    /** How a position relates to the build. */
    enum Occupancy {
        AIR,
        /** A block the species' filter allows replacing, e.g. tall grass or snow. */
        REPLACEABLE,
        /** Water or another fluid. */
        FLUID,
        /** A block that cannot be replaced. */
        SOLID,
        /**
         * The position is outside what this query can answer for: an unloaded chunk, a height the
         * world does not have, or a platform that declined. Treated as "refuse the tree".
         */
        UNKNOWN;

        public boolean isUnknown() {
            return this == UNKNOWN;
        }

        /** @return true when this occupancy permits a write without a per-species override. */
        public boolean isFree() {
            return this == AIR || this == FLUID;
        }
    }

    /**
     * One block as the planner sees it.
     *
     * @param blockId   the block's namespaced id, e.g. {@code minecraft:dirt}. Never null; an
     *                  unknown position reports {@code "minecraft:air"} together with
     *                  {@link Occupancy#UNKNOWN}, so callers that only look at the id still get
     *                  something sane.
     * @param occupancy whether the position can be written
     * @param fluid     true when the block is a fluid source or flowing fluid
     */
    record WorldBlock(String blockId, Occupancy occupancy, boolean fluid) {
        public WorldBlock {
            Objects.requireNonNull(blockId, "blockId");
            Objects.requireNonNull(occupancy, "occupancy");
        }

        /** An empty position. */
        public static WorldBlock air() {
            return new WorldBlock("minecraft:air", Occupancy.AIR, false);
        }

        /** A position the query cannot answer. */
        public static WorldBlock unknown() {
            return new WorldBlock("minecraft:air", Occupancy.UNKNOWN, false);
        }

        public static WorldBlock of(String blockId, Occupancy occupancy) {
            return new WorldBlock(blockId, occupancy,
                    occupancy == Occupancy.FLUID || blockId.endsWith("_water"));
        }
    }

    /** @return the block at an absolute position; {@link WorldBlock#unknown()} when unanswerable. */
    WorldBlock blockAt(BlockPos pos);
}