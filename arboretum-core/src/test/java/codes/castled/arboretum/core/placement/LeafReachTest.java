package codes.castled.arboretum.core.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import codes.castled.arboretum.core.template.BlockPos;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Vanilla's leaf decay rule: a leaf lives within six blocks of a log, counted through leaves. */
class LeafReachTest {

    @Test
    void aLeafSixAwayLivesAndSevenAwayDecays() {
        Set<BlockPos> logs = Set.of(new BlockPos(0, 0, 0));
        Set<BlockPos> leaves = new HashSet<>();
        for (int x = 1; x <= 8; x++) {
            leaves.add(new BlockPos(x, 0, 0));
        }
        var distances = LeafReach.distances(logs, leaves);
        assertEquals(6, distances.get(new BlockPos(6, 0, 0)));
        assertEquals(LeafReach.DECAY_DISTANCE, distances.get(new BlockPos(7, 0, 0)));
        assertEquals(2, LeafReach.decaying(logs, leaves));
    }

    @Test
    void distanceRunsThroughLeavesNotThroughAir() {
        // Two leaves a block apart diagonally-free: the far one has no leaf path to the log.
        Set<BlockPos> logs = Set.of(new BlockPos(0, 0, 0));
        Set<BlockPos> leaves = Set.of(new BlockPos(1, 0, 0), new BlockPos(3, 0, 0));
        assertEquals(1, LeafReach.decaying(logs, leaves));
    }
}
