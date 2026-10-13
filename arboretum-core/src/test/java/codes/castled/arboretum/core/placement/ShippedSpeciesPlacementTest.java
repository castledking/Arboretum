package codes.castled.arboretum.core.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import codes.castled.arboretum.core.bundle.BundledData;
import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.SpeciesDefinition;
import codes.castled.arboretum.core.template.BlockPos;
import codes.castled.arboretum.core.template.BlockStateRef;
import codes.castled.arboretum.core.template.Rotation;
import codes.castled.arboretum.core.template.StructureTemplate;
import codes.castled.arboretum.core.testing.FlatWorld;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Every shipped species, from the bundled JSON and NBT, planned into a flat world - in all four
 * rotations, and against each thing that must stop a tree.
 */
class ShippedSpeciesPlacementTest {

    static BundledData bundle;

    @BeforeAll
    static void load() throws Exception {
        bundle = BundledData.load(ShippedSpeciesPlacementTest.class.getClassLoader());
    }

    @ParameterizedTest
    @ValueSource(strings = {"baobab", "jacaranda", "bald_cypress", "italian_cypress"})
    void growsInEveryRotationWithTheSameBlocks(String name) {
        Tree tree = tree(name);
        PlacementPlan.Placed unrotated = tree.place(FlatWorld.grass(), Rotation.NONE);
        Map<String, Long> axes = axisCounts(unrotated);
        for (Rotation rotation : Rotation.values()) {
            PlacementPlan.Placed placed = tree.place(FlatWorld.grass(), rotation);
            assertEquals(unrotated.blockCount(), placed.blockCount(), rotation + " changed the block count");
            assertTrue(placed.placements().keySet().stream().allMatch(pos -> pos.y() >= 0),
                    rotation + " put a block below the ground");
            assertTrue(placed.placements().values().stream()
                    .anyMatch(state -> state.blockId().equals(tree.species().visuals().logBlockId())), "no logs");
            assertTrue(placed.placements().values().stream()
                    .anyMatch(state -> state.blockId().equals(tree.species().visuals().leafBlockId())), "no leaves");
            // A quarter turn swaps the x and z log axes; a half turn keeps them.
            Map<String, Long> turned = axisCounts(placed);
            boolean quarter = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.CLOCKWISE_270;
            assertEquals(axes.getOrDefault("y", 0L), turned.getOrDefault("y", 0L), rotation + " y logs");
            assertEquals(axes.getOrDefault(quarter ? "z" : "x", 0L), turned.getOrDefault("x", 0L), rotation + " x logs");
            assertEquals(axes.getOrDefault(quarter ? "x" : "z", 0L), turned.getOrDefault("z", 0L), rotation + " z logs");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"baobab", "jacaranda", "bald_cypress", "italian_cypress"})
    void theSameInputsGiveTheSamePlan(String name) {
        Tree tree = tree(name);
        assertEquals(tree.place(FlatWorld.grass(), Rotation.CLOCKWISE_90).placements(),
                tree.place(FlatWorld.grass(), Rotation.CLOCKWISE_90).placements());
    }

    @ParameterizedTest
    @ValueSource(strings = {"baobab", "jacaranda", "bald_cypress", "italian_cypress"})
    void noGroundItCanRootInMeansNoTree(String name) {
        // Sand: no shipped species roots in it (the Italian cypress does root in stone).
        assertRefused(tree(name).plan(new FlatWorld("minecraft:sand", 64), Rotation.NONE, 320),
                PlacementPlan.Reason.NO_GROUND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"baobab", "jacaranda", "bald_cypress", "italian_cypress"})
    void aTreeTallerThanTheWorldIsRefused(String name) {
        assertRefused(tree(name).plan(FlatWorld.grass(), Rotation.NONE, 4), PlacementPlan.Reason.WORLD_HEIGHT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"baobab", "jacaranda", "bald_cypress", "italian_cypress"})
    void bedrockInTheWayIsNeverReplaced(String name) {
        Tree tree = tree(name);
        assertRefused(tree.plan(FlatWorld.grass().set(tree.aLog(), "minecraft:bedrock", BlockQuery.Occupancy.SOLID),
                Rotation.NONE, 320), PlacementPlan.Reason.PROTECTED_BLOCK);
    }

    @ParameterizedTest
    @ValueSource(strings = {"baobab", "jacaranda", "bald_cypress", "italian_cypress"})
    void aSolidBlockInTheTrunkObstructsIt(String name) {
        Tree tree = tree(name);
        assertRefused(tree.plan(FlatWorld.grass().set(tree.aLog(), "minecraft:stone", BlockQuery.Occupancy.SOLID),
                Rotation.NONE, 320), PlacementPlan.Reason.OBSTRUCTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"baobab", "jacaranda", "bald_cypress", "italian_cypress"})
    void aTreeReachingIntoUngeneratedChunksWaits(String name) {
        // Loaded only in the origin's column: every tree is wider than one block.
        assertRefused(tree(name).plan(new FlatWorld("minecraft:grass_block", 0), Rotation.NONE, 320),
                PlacementPlan.Reason.OUTSIDE_LOADED_REGION);
    }

    // --- helpers ------------------------------------------------------------------------------

    record Tree(SpeciesDefinition species, StructureTemplate trunk, StructureTemplate canopy,
                StructureTemplate roots) {

        PlacementPlan plan(BlockQuery world, Rotation rotation, int maxY) {
            PlacementContext context = new PlacementContext(new BlockPos(0, 0, 0), rotation, 12345L,
                    -64, maxY, TreePlacementPlanner.groundFor(species));
            return new TreePlacementPlanner(species).plan(context, trunk, canopy, roots, world);
        }

        PlacementPlan.Placed place(FlatWorld world, Rotation rotation) {
            PlacementPlan plan = plan(world, rotation, 320);
            return plan.placed().orElseThrow(() -> new AssertionError(species.id() + " " + rotation
                    + " was refused: " + plan.refused().map(r -> r.reason() + ": " + r.detail()).orElse("")));
        }

        /** A log position of the unrotated tree, to put something in the way. */
        BlockPos aLog() {
            return place(FlatWorld.grass(), Rotation.NONE).placements().entrySet().stream()
                    .filter(e -> e.getValue().blockId().equals(species.visuals().logBlockId()))
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
        }
    }

    static Tree tree(String name) {
        SpeciesDefinition species = bundle.catalogue().get(ArboretumId.arboretum(name))
                .orElseThrow(() -> new AssertionError(name + " not in catalogue:\n" + bundle.describeProblems()));
        SpeciesDefinition.Structure structure = species.structure();
        Set<String> ground = TreePlacementPlanner.groundFor(species);
        assertTrue(ground.contains("minecraft:grass_block"),
                name + " can't root in grass, which the test world is made of: " + new TreeSet<>(ground));
        return new Tree(species,
                bundle.templates().load(structure.trunkTemplate()).orElseThrow(),
                bundle.templates().load(structure.canopyTemplate()).orElseThrow(),
                structure.hasRoots() ? bundle.templates().load(structure.rootsTemplate()).orElseThrow() : null);
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
