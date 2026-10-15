package codes.castled.arboretum.fabric;

import codes.castled.arboretum.core.bundle.BundledData;
import codes.castled.arboretum.core.placement.BlockQuery;
import codes.castled.arboretum.core.placement.PlacementContext;
import codes.castled.arboretum.core.placement.PlacementPlan;
import codes.castled.arboretum.core.placement.TreePlacementPlanner;
import codes.castled.arboretum.core.species.SpeciesDefinition;
import codes.castled.arboretum.core.template.BlockStateRef;
import codes.castled.arboretum.core.template.Rotation;
import codes.castled.arboretum.core.placement.TreeTemplates;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelWriter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Grows one tree: plans it with the core, turns the plan into Minecraft blocks, and writes it.
 * World generation and {@code /arboretum place} both come through here, so the command tests the
 * same path natural trees take.
 */
final class Grower {

    private Grower() {
    }

    /** What happened: the tree grown, or why not. */
    record Result(PlacedTrees.Tree tree, String refusal) {
        boolean grown() {
            return tree != null;
        }
    }

    static <L extends LevelWriter & LevelHeightAccessor> Result grow(
            L level, BlockQuery query, BundledData bundle, SpeciesDefinition species, BlockPos origin,
            Rotation rotation, RandomSource random) {
        // One of the species' whole trees by weight, or its trunk/canopy/roots parts.
        TreeTemplates.Chosen chosen = TreeTemplates.choose(species, bundle.templates(), random.nextLong());
        if (chosen == null) {
            // Validated at startup, so this means the bundle changed underneath a running server.
            return new Result(null, "TEMPLATE_MISSING");
        }
        PlacementContext context = new PlacementContext(
                new codes.castled.arboretum.core.template.BlockPos(origin.getX(), origin.getY(), origin.getZ()),
                rotation, random.nextLong(), level.getMinY(), level.getMaxY(),
                TreePlacementPlanner.groundFor(species));
        PlacementPlan plan = new TreePlacementPlanner(species).plan(context, chosen.trunk(), chosen.canopy(),
                chosen.roots(), query);
        if (plan.refused().isPresent()) {
            return new Result(null, plan.refused().get().reason().name());
        }
        PlacementPlan.Placed placed = plan.placed().orElseThrow();

        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (Map.Entry<codes.castled.arboretum.core.template.BlockPos, BlockStateRef> entry
                : placed.placements().entrySet()) {
            var pos = entry.getKey();
            blocks.put(new BlockPos(pos.x(), pos.y(), pos.z()), BlockStates.toMinecraft(entry.getValue()));
        }
        setLeafDistances(blocks);

        List<PlacedTrees.Block> written = new ArrayList<>(blocks.size());
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
            BlockPos pos = entry.getKey();
            written.add(new PlacedTrees.Block(pos.getX(), pos.getY(), pos.getZ(), describe(entry.getValue())));
        }
        return new Result(new PlacedTrees.Tree(species.id(), origin.getX(), origin.getY(), origin.getZ(),
                rotation.name(), written), null);
    }

    /**
     * Gives every leaf its distance to the nearest log of the tree, as vanilla's tree feature
     * does. A leaf block starts at distance 7, which decays on the next random tick: without this
     * a freshly generated canopy would rot away within minutes.
     */
    static void setLeafDistances(Map<BlockPos, BlockState> blocks) {
        Map<BlockPos, Integer> distance = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockState state = entry.getValue();
            if (state.hasProperty(BlockStateProperties.AXIS) && !(state.getBlock() instanceof LeavesBlock)) {
                distance.put(entry.getKey(), 0);
                queue.add(entry.getKey());
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            int next = distance.get(pos) + 1;
            if (next > LeavesBlock.DECAY_DISTANCE) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos neighbour = pos.relative(direction);
                BlockState state = blocks.get(neighbour);
                if (state != null && state.hasProperty(LeavesBlock.DISTANCE) && !distance.containsKey(neighbour)) {
                    distance.put(neighbour, next);
                    queue.addLast(neighbour);
                }
            }
        }
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockState state = entry.getValue();
            if (state.hasProperty(LeavesBlock.DISTANCE)) {
                int d = Math.min(LeavesBlock.DECAY_DISTANCE, distance.getOrDefault(entry.getKey(), LeavesBlock.DECAY_DISTANCE));
                entry.setValue(state.setValue(LeavesBlock.DISTANCE, d));
            }
        }
    }

    /**
     * A block as {@code /arboretum verify} compares it: the block and its axis. A leaf's distance
     * is left out, since vanilla rightly recomputes it as neighbours change.
     */
    static String describe(BlockState state) {
        String id = BlockStates.idOf(state);
        return state.hasProperty(BlockStateProperties.AXIS)
                ? id + "[axis=" + state.getValue(BlockStateProperties.AXIS).getSerializedName() + "]"
                : id;
    }

    static Rotation randomRotation(SpeciesDefinition species, RandomSource random) {
        return species.structure().randomRotation()
                ? Rotation.values()[random.nextInt(Rotation.values().length)] : Rotation.NONE;
    }
}
