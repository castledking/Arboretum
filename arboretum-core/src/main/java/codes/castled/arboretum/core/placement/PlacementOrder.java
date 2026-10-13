package codes.castled.arboretum.core.placement;

import codes.castled.arboretum.core.template.BlockPos;

import java.util.Comparator;

/**
 * The order positions are written in.
 *
 * <p>Not cosmetic. Leaves carry a distance property that the engine recomputes from their
 * neighbours, and logs carry properties the leaves read. Writing leaves before logs means a leaf's
 * neighbour set was computed against air and then had a trunk appear next to it, which leaves the
 * tree one decay tick from disappearing. Writing roots and logs first and leaves last means every
 * distance calculation sees the final tree.
 */
public final class PlacementOrder {

    /** Roots, then logs, then everything else, then leaves last. */
    public enum Kind {
        ROOT(0),
        LOG(1),
        BRANCH(2),
        DECORATION(3),
        LEAF(4);

        private final int rank;

        Kind(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }

    /**
     * Sorts by kind, then bottom-to-top.
     *
     * <p>The secondary ordering is not required for correctness but makes placement deterministic,
     * which is what makes a failed chunk reproduce exactly and makes a placement diff readable.
     */
    public static final Comparator<BlockPos> COMPARATOR = (left, right) -> {
        int byY = Integer.compare(left.y(), right.y());
        if (byY != 0) {
            return byY;
        }
        int byX = Integer.compare(left.x(), right.x());
        return byX != 0 ? byX : Integer.compare(left.z(), right.z());
    };

    private PlacementOrder() {
    }

    /** @return the rank for a placement kind. */
    public static int rankOf(Kind kind) {
        return kind.rank();
    }
}