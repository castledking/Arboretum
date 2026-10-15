package codes.castled.arboretum.core.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.arboretum.core.bundle.BundledData;
import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.SpeciesDefinition;
import codes.castled.arboretum.core.template.BlockPos;
import codes.castled.arboretum.core.template.BlockStateRef;
import codes.castled.arboretum.core.template.Rotation;
import codes.castled.arboretum.core.testing.FlatWorld;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every tree of every shipped species, from the bundled JSON and NBT, planned into a flat world -
 * in all four rotations, and against each thing that must stop a tree.
 */
class ShippedSpeciesPlacementTest {

    static BundledData bundle;

    @BeforeAll
    static void load() throws Exception {
        bundle = BundledData.load(ShippedSpeciesPlacementTest.class.getClassLoader());
    }

    /** One case per tree: each whole-tree variant, or a parts species' one set. */
    static Stream<Tree> trees() throws Exception {
        load();
        List<Tree> out = new ArrayList<>();
        for (SpeciesDefinition species : bundle.catalogue().all()) {
            Set<String> ground = TreePlacementPlanner.groundFor(species);
            assertTrue(ground.contains("minecraft:grass_block"),
                    species.id() + " can't root in grass, which the test world is made of: " + new TreeSet<>(ground));
            for (TreeTemplates.Chosen chosen : TreeTemplates.all(species, bundle.templates())) {
                out.add(new Tree(species, chosen));
            }
        }
        return out.stream();
    }

    @ParameterizedTest
    @MethodSource("trees")
    void growsInEveryRotationWithTheSameBlocks(Tree tree) {
        PlacementPlan.Placed unrotated = tree.place(FlatWorld.grass(), Rotation.NONE);
        Map<String, Long> axes = axisCounts(unrotated);
        for (Rotation rotation : Rotation.values()) {
            PlacementPlan.Placed placed = tree.place(FlatWorld.grass(), rotation);
            assertEquals(unrotated.blockCount(), placed.blockCount(), rotation + " changed the block count");
            assertTrue(placed.placements().keySet().stream().allMatch(pos -> pos.y() >= 0),
                    rotation + " put a block below the ground");
            assertTrue(placed.placements().values().stream().anyMatch(s -> tree.logs().contains(s.blockId())), "no logs");
            assertTrue(placed.placements().values().stream().anyMatch(s -> tree.leaves().contains(s.blockId())), "no leaves");
            // A quarter turn swaps the x and z log axes; a half turn keeps them.
            Map<String, Long> turned = axisCounts(placed);
            boolean quarter = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.CLOCKWISE_270;
            assertEquals(axes.getOrDefault("y", 0L), turned.getOrDefault("y", 0L), rotation + " y logs");
            assertEquals(axes.getOrDefault(quarter ? "z" : "x", 0L), turned.getOrDefault("x", 0L), rotation + " x logs");
            assertEquals(axes.getOrDefault(quarter ? "x" : "z", 0L), turned.getOrDefault("z", 0L), rotation + " z logs");
        }
    }

    @ParameterizedTest
    @MethodSource("trees")
    void theTrunkBaseLandsOnTheOrigin(Tree tree) {
        PlacementPlan.Placed placed = tree.place(FlatWorld.grass(), Rotation.NONE);
        int lowest = placed.placements().entrySet().stream()
                .filter(e -> tree.logs().contains(e.getValue().blockId()))
                .mapToInt(e -> e.getKey().y()).min().orElseThrow();
        assertEquals(0, lowest, "the lowest logs must stand at the origin's height");
        assertTrue(placed.placements().entrySet().stream().anyMatch(e -> e.getKey().y() == 0
                && Math.abs(e.getKey().x()) <= 1 && Math.abs(e.getKey().z()) <= 1
                && tree.logs().contains(e.getValue().blockId())), "no trunk log at the origin");
    }

    @ParameterizedTest
    @MethodSource("trees")
    void grownLeavesAreNotPersistent(Tree tree) {
        PlacementPlan.Placed placed = tree.place(FlatWorld.grass(), Rotation.NONE);
        for (BlockStateRef state : placed.placements().values()) {
            assertFalse(state.hasProperty("persistent") || state.hasProperty("distance"),
                    state + " carries a placed block's leaf state");
        }
    }

    @ParameterizedTest
    @MethodSource("trees")
    void theSameInputsGiveTheSamePlan(Tree tree) {
        assertEquals(tree.place(FlatWorld.grass(), Rotation.CLOCKWISE_90).placements(),
                tree.place(FlatWorld.grass(), Rotation.CLOCKWISE_90).placements());
    }

    @ParameterizedTest
    @MethodSource("trees")
    void noGroundItCanRootInMeansNoTree(Tree tree) {
        // Sand: no shipped species roots in it (the Italian cypress does root in stone).
        assertRefused(tree.plan(new FlatWorld("minecraft:sand", 64), Rotation.NONE, 320), PlacementPlan.Reason.NO_GROUND);
    }

    @ParameterizedTest
    @MethodSource("trees")
    void aTreeTallerThanTheWorldIsRefused(Tree tree) {
        assertRefused(tree.plan(FlatWorld.grass(), Rotation.NONE, 4), PlacementPlan.Reason.WORLD_HEIGHT);
    }

    @ParameterizedTest
    @MethodSource("trees")
    void bedrockInTheWayIsNeverReplaced(Tree tree) {
        assertRefused(tree.plan(FlatWorld.grass().set(tree.aLog(), "minecraft:bedrock", BlockQuery.Occupancy.SOLID),
                Rotation.NONE, 320), PlacementPlan.Reason.PROTECTED_BLOCK);
    }

    @ParameterizedTest
    @MethodSource("trees")
    void aSolidBlockInTheTrunkObstructsIt(Tree tree) {
        assertRefused(tree.plan(FlatWorld.grass().set(tree.aLog(), "minecraft:stone", BlockQuery.Occupancy.SOLID),
                Rotation.NONE, 320), PlacementPlan.Reason.OBSTRUCTED);
    }

    @ParameterizedTest
    @MethodSource("trees")
    void aTreeReachingIntoUngeneratedChunksWaits(Tree tree) {
        // Loaded only in the origin's column: every tree is wider than one block.
        assertRefused(tree.plan(new FlatWorld("minecraft:grass_block", 0), Rotation.NONE, 320),
                PlacementPlan.Reason.OUTSIDE_LOADED_REGION);
    }

    /** The hand-built jacarandas: acacia in the templates, jacaranda blocks in the world. */
    @Test
    void theJacarandasAcaciaBecomesJacarandaBlocksWithTheirBranchesIntact() throws Exception {
        List<Tree> jacarandas = trees().filter(t -> t.species().id().equals(ArboretumId.arboretum("jacaranda"))).toList();
        assertEquals(4, jacarandas.size());
        for (Tree tree : jacarandas) {
            Map<BlockPos, BlockStateRef> placed = tree.place(FlatWorld.grass(), Rotation.NONE).placements();
            assertFalse(placed.values().stream().anyMatch(s -> s.blockId().startsWith("minecraft:acacia")),
                    tree.chosen().name() + " placed acacia");
            assertTrue(placed.values().stream().anyMatch(s -> s.blockId().equals("arboretum:jacaranda_wood")
                    && !"y".equals(s.property("axis"))), tree.chosen().name() + " lost its sideways branches");
            assertTrue(placed.values().stream().anyMatch(s -> s.blockId().equals("arboretum:jacaranda_leaves")));
        }
    }

    // --- helpers ------------------------------------------------------------------------------

    record Tree(SpeciesDefinition species, TreeTemplates.Chosen chosen) {

        Set<String> logs() {
            return new TreePlacementPlanner(species).logBlocks();
        }

        Set<String> leaves() {
            return new TreePlacementPlanner(species).leafBlocks();
        }

        PlacementPlan plan(BlockQuery world, Rotation rotation, int maxY) {
            PlacementContext context = new PlacementContext(new BlockPos(0, 0, 0), rotation, 12345L,
                    -64, maxY, TreePlacementPlanner.groundFor(species));
            return new TreePlacementPlanner(species).plan(context, chosen.trunk(), chosen.canopy(), chosen.roots(), world);
        }

        PlacementPlan.Placed place(FlatWorld world, Rotation rotation) {
            PlacementPlan plan = plan(world, rotation, 320);
            return plan.placed().orElseThrow(() -> new AssertionError(this + " " + rotation
                    + " was refused: " + plan.refused().map(r -> r.reason() + ": " + r.detail()).orElse("")));
        }

        /** A log position of the unrotated tree, to put something in the way. */
        BlockPos aLog() {
            return place(FlatWorld.grass(), Rotation.NONE).placements().entrySet().stream()
                    .filter(e -> logs().contains(e.getValue().blockId()))
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
        }

        @Override
        public String toString() {
            String name = chosen.name();
            return species.id() + " (" + name.substring(name.lastIndexOf('/') + 1) + ")";
        }
    }

    static void assertRefused(PlacementPlan plan, PlacementPlan.Reason reason) {
        assertTrue(plan.refused().isPresent(), "expected " + reason + " but the tree was placed");
        assertEquals(reason, plan.refused().get().reason(), plan.refused().get().detail());
    }

    static Map<String, Long> axisCounts(PlacementPlan.Placed placed) {
        Map<String, Long> counts = new HashMap<>();
        for (BlockStateRef state : placed.placements().values()) {
            String axis = state.property("axis");
            if (axis != null) {
                counts.merge(axis, 1L, Long::sum);
            }
        }
        return counts;
    }
}
