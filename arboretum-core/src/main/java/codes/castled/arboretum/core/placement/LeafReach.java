package codes.castled.arboretum.core.placement;

import codes.castled.arboretum.core.template.BlockPos;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which of a tree's leaves vanilla would let decay.
 *
 * <p>A leaf block survives while it is within {@link #DECAY_DISTANCE} blocks of a log, counted
 * through leaves only. A canopy built wider than its branches reach rots away within minutes of
 * generating, so a template with such leaves is caught at load time, not after a world is made.
 */
public final class LeafReach {

    /** Vanilla's LeavesBlock decay distance: a leaf at 7 or more decays. */
    public static final int DECAY_DISTANCE = 7;

    private LeafReach() {
    }

    /**
     * @param logs   positions of log blocks
     * @param leaves positions of leaf blocks
     * @return each leaf's distance to the nearest log through leaves, capped at DECAY_DISTANCE
     */
    public static Map<BlockPos, Integer> distances(Set<BlockPos> logs, Set<BlockPos> leaves) {
        Map<BlockPos, Integer> distance = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos log : logs) {
            distance.put(log, 0);
            queue.add(log);
        }
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            int next = distance.get(pos) + 1;
            if (next >= DECAY_DISTANCE) {
                continue;
            }
            for (int[] step : steps) {
                BlockPos neighbour = pos.offset(step[0], step[1], step[2]);
                if (leaves.contains(neighbour) && !distance.containsKey(neighbour)) {
                    distance.put(neighbour, next);
                    queue.addLast(neighbour);
                }
            }
        }
        Map<BlockPos, Integer> out = new HashMap<>();
        for (BlockPos leaf : leaves) {
            out.put(leaf, distance.getOrDefault(leaf, DECAY_DISTANCE));
        }
        return out;
    }

    /** @return how many of the leaves would decay. */
    public static long decaying(Set<BlockPos> logs, Set<BlockPos> leaves) {
        return distances(logs, leaves).values().stream().filter(d -> d >= DECAY_DISTANCE).count();
    }
}
