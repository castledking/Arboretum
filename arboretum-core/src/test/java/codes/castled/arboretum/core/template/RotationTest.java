package codes.castled.arboretum.core.template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rotation, the operation most likely to silently corrupt a tree.
 *
 * <p>Two things have to move together: the block positions, and the directional block-state
 * properties. A template whose positions rotate but whose {@code axis} does not produces a tree
 * whose trunk lies on its side, which looks obviously wrong but is easy to miss in a test that only
 * checks coordinates.
 */
class RotationTest {

    @Test
    void noneLeavesPositionsUnchanged() {
        BlockPos pos = new BlockPos(3, 5, -7);
        assertEquals(pos, Rotation.NONE.rotate(pos));
    }

    @Test
    void clockwiseQuarterTurnMapsNorthToEast() {
        // Minecraft's coordinate system: +X is east, +Z is south. A clockwise turn viewed from
        // above sends north (-Z) to east (+X).
        assertEquals(new BlockPos(1, 0, 0), Rotation.CLOCKWISE_90.rotate(new BlockPos(0, 0, -1)));
        assertEquals(new BlockPos(0, 0, 1), Rotation.CLOCKWISE_90.rotate(new BlockPos(1, 0, 0)));
        assertEquals(new BlockPos(-1, 0, 0), Rotation.CLOCKWISE_90.rotate(new BlockPos(0, 0, 1)));
        assertEquals(new BlockPos(0, 0, -1), Rotation.CLOCKWISE_90.rotate(new BlockPos(-1, 0, 0)));
    }

    @Test
    void halfTurnIsItsOwnInverse() {
        BlockPos pos = new BlockPos(4, 9, -2);
        assertEquals(pos, Rotation.CLOCKWISE_180.rotate(Rotation.CLOCKWISE_180.rotate(pos)));
    }

    @Test
    void fourQuarterTurnsReturnToTheStart() {
        BlockPos pos = new BlockPos(4, 9, -2);
        BlockPos rotated = pos;
        for (int i = 0; i < 4; i++) {
            rotated = Rotation.CLOCKWISE_90.rotate(rotated);
        }
        assertEquals(pos, rotated);
    }

    @Test
    void everyRotationIsInvertible() {
        for (Rotation rotation : Rotation.values()) {
            BlockPos pos = new BlockPos(6, 1, -3);
            assertEquals(pos, rotation.rotate(rotation.inverse().rotate(pos)),
                    rotation + " must be invertible");
        }
    }

    @Test
    void compositionIsAssociativeAroundTheOrigin() {
        for (Rotation first : Rotation.values()) {
            for (Rotation second : Rotation.values()) {
                BlockPos pos = new BlockPos(5, 2, -8);
                BlockPos viaComposition = first.rotate(second.rotate(pos));
                BlockPos viaCombined = first.rotate(second).rotate(pos);
                assertEquals(viaCombined, viaComposition,
                        first + " then " + second + " must equal their combination");
            }
        }
    }

    @Test
    void neverChangesTheVerticalComponent() {
        // A tree rotated about the Y axis must not gain or lose height. If it did, a rotated baobab
        // would sink into the ground.
        for (Rotation rotation : Rotation.values()) {
            for (int y : new int[]{-64, 0, 1, 319}) {
                assertEquals(y, rotation.rotate(new BlockPos(3, y, -7)).y(),
                        rotation + " must not change y");
            }
        }
    }

    @Test
    void rotatesHorizontalDirectionsToMatchThePositionRotation() {
        // A block at north of the trunk must face north after rotation too. These two have to agree
        // or a rotated branch points the wrong way.
        for (Rotation rotation : Rotation.values()) {
            for (Direction direction : new Direction[]{
                    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                BlockPos offset = rotation.rotateOffset(direction.stepX(), direction.stepZ());
                Direction rotatedDirection = rotation.rotateDirection(direction);
                assertEquals(offset.x(), rotatedDirection.stepX(),
                        rotation + " " + direction + ": position and facing must agree");
                assertEquals(offset.z(), rotatedDirection.stepZ(),
                        rotation + " " + direction + ": position and facing must agree");
            }
        }
    }

    @Test
    void verticalDirectionsSurviveRotation() {
        for (Rotation rotation : Rotation.values()) {
            assertEquals(Direction.UP, rotation.rotateDirection(Direction.UP));
            assertEquals(Direction.DOWN, rotation.rotateDirection(Direction.DOWN));
        }
    }

    @Test
    void neverRotatesAVerticalLogAxis() {
        // The whole failure mode this guards: a pillar log whose axis was permuted horizontally,
        // which lays the trunk on its side.
        for (Rotation rotation : Rotation.values()) {
            assertEquals("y", rotation.rotateAxis("y"), rotation + " must keep a vertical log vertical");
        }
    }

    @Test
    void permutesHorizontalAxesInStepWithPositionRotation() {
        assertEquals("z", Rotation.CLOCKWISE_90.rotateAxis("x"));
        assertEquals("x", Rotation.CLOCKWISE_90.rotateAxis("z"));
        assertEquals("x", Rotation.CLOCKWISE_180.rotateAxis("x"));
        assertEquals("z", Rotation.CLOCKWISE_180.rotateAxis("z"));
        for (Rotation rotation : Rotation.values()) {
            for (String axis : new String[]{"x", "z"}) {
                String once = rotation.rotateAxis(axis);
                String twice = rotation.rotate(rotation.inverse()).rotateAxis(axis);
                assertEquals(axis, twice, rotation + " composed with its inverse must return the original axis");
            }
        }
    }

    @Test
    void rotateAxisToleratesCaseAndNull() {
        assertEquals("y", Rotation.CLOCKWISE_90.rotateAxis("Y"));
        assertEquals(null, Rotation.CLOCKWISE_90.rotateAxis(null));
    }

    @Test
    void parsesTheSpellingsSpeciesFilesUse() {
        assertEquals(Rotation.NONE, Rotation.parse("none"));
        assertEquals(Rotation.NONE, Rotation.parse("0"));
        assertEquals(Rotation.NONE, Rotation.parse(null));
        assertEquals(Rotation.CLOCKWISE_90, Rotation.parse("90"));
        assertEquals(Rotation.CLOCKWISE_90, Rotation.parse("CLOCKWISE_90"));
        assertEquals(Rotation.CLOCKWISE_180, Rotation.parse("180"));
        assertEquals(Rotation.CLOCKWISE_270, Rotation.parse("270"));
    }

    @Test
    void rejectsAnUnknownRotationWithAUsefulMessage() {
        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> Rotation.parse("ninety-ish"));
        assertTrue(exception.getMessage().contains("none, 90, 180 or 270"), exception.getMessage());
    }

    @Test
    void parsesQuarterTurnsModuloFourIncludingNegatives() {
        assertEquals(Rotation.NONE, Rotation.byQuarterTurns(4));
        assertEquals(Rotation.CLOCKWISE_90, Rotation.byQuarterTurns(-3));
        assertEquals(Rotation.CLOCKWISE_270, Rotation.byQuarterTurns(-1));
    }

    @Test
    void preservesTheFullExtentOfARotatedTemplate() {
        // A template rotated four times must occupy exactly the cells it started in. If any
        // rotation widened or narrowed the footprint, a tree would clip at the chunk border.
        java.util.Set<BlockPos> original = new java.util.HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                original.add(new BlockPos(x, 0, z));
            }
        }
        java.util.Set<BlockPos> rotated = new java.util.HashSet<>(original);
        for (int i = 0; i < 4; i++) {
            java.util.Set<BlockPos> next = new java.util.HashSet<>();
            for (BlockPos pos : rotated) {
                next.add(Rotation.CLOCKWISE_90.rotate(pos));
            }
            rotated = next;
        }
        assertEquals(original.size(), rotated.size(), "rotation must not merge distinct positions");
        assertEquals(original, rotated, "four quarter turns must return the exact same set");
    }
}