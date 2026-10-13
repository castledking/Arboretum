package codes.castled.arboretum.core.placement;

import codes.castled.arboretum.core.template.BlockPos;
import codes.castled.arboretum.core.template.BlockStateRef;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The result of planning a tree placement: either a complete plan, or a refusal with a reason.
 *
 * <p>Separating planning from mutation is the point. The planner produces this and nothing else;
 * the platform adapter applies it. That means every safety rule - ground, obstruction, world height,
 * protected blocks, marker replacement, rotation - is unit tested with no game running, and the
 * adapter's only job is to turn planned positions into block writes.
 */
public sealed interface PlacementPlan {

    /** @return the plan when placement succeeded. */
    Optional<Placed> placed();

    /** @return the refusal when it did not. */
    Optional<Refused> refused();

    boolean isPlaced();

    /**
     * A tree that may be placed.
     *
     * @param speciesId   which species this is, for diagnostics
     * @param placements  absolute world position -> block to write, insertion-ordered so logs are
     *                    written before leaves and leaves before roots
     * @param footPositions absolute positions that must stand on valid ground
     * @param footprint   horizontal extent, for spacing checks
     * @param height      number of blocks from the lowest to the highest position
     */
    record Placed(
            codes.castled.arboretum.core.id.ArboretumId speciesId,
            Map<BlockPos, BlockStateRef> placements,
            List<BlockPos> footPositions,
            BlockPos.Bounds footprint,
            int height
    ) implements PlacementPlan {
        public Placed {
            placements = Map.copyOf(placements);
            footPositions = List.copyOf(footPositions);
        }

        public int blockCount() {
            return placements.size();
        }

        @Override
        public Optional<Placed> placed() {
            return Optional.of(this);
        }

        @Override
        public Optional<Refused> refused() {
            return Optional.empty();
        }

        @Override
        public boolean isPlaced() {
            return true;
        }

        /**
         * The positions to write, ordered so that dependents come last.
         *
         * <p>Logs go down before leaves go up, which matters: if a canopy template's leaves sit at
         * the same position as a trunk log, writing logs last would leave the trunk's distance
         * calculation wrong.
         */
        public List<BlockPos> orderedPositions() {
            return placements.keySet().stream().sorted(PlacementOrder.COMPARATOR).toList();
        }
    }

    /**
     * A tree that may not be placed, and why.
     *
     * <p>Every refusal carries a {@link Reason} rather than only prose, so the diagnostics counter
     * can tell an operator "3,400 trees refused for NO_GROUND" without log-scraping.
     */
    record Refused(
            codes.castled.arboretum.core.id.ArboretumId speciesId,
            Reason reason,
            String detail
    ) implements PlacementPlan {
        @Override
        public Optional<Placed> placed() {
            return Optional.empty();
        }

        @Override
        public Optional<Refused> refused() {
            return Optional.of(this);
        }

        @Override
        public boolean isPlaced() {
            return false;
        }

        @Override
        public String toString() {
            return "Refused[" + speciesId + ", " + reason + (detail.isEmpty() ? "" : ": " + detail) + "]";
        }
    }

    /** Why a placement was refused. */
    enum Reason {
        /** No template carries the declared markers, so there is nothing to build. */
        EMPTY_TEMPLATE,
        /** A marker block id appears in no palette entry of any of the species' templates. */
        MISSING_MARKER,
        /** A required template file could not be read. */
        TEMPLATE_UNREADABLE,
        /** The tree would extend above or below the build limits. */
        WORLD_HEIGHT,
        /** The query could not answer for some position, meaning a chunk is not loaded. */
        OUTSIDE_LOADED_REGION,
        /** No template foot stood on an acceptable ground block within maxLogDepth. */
        NO_GROUND,
        /** An occupied position blocked the tree under the species' replaceable rule. */
        OBSTRUCTED,
        /** The template wants to overwrite a block that Arboretum never overwrites. */
        PROTECTED_BLOCK,
        /** The same tree was already placed nearby. */
        TOO_CLOSE
    }
}