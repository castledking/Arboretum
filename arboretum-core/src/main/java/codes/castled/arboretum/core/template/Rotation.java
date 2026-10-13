package codes.castled.arboretum.core.template;

/**
 * A quarter-turn rotation about the Y axis.
 *
 * <p>Templates are authored facing north and rotated at placement time. Rotation is the operation
 * most likely to corrupt a tree visually, so it lives in its own tiny class with its own tests:
 * a rotated template must map positions correctly <em>and</em> remap directional block-state
 * properties consistently, or branches end up pointing sideways.
 */
public enum Rotation {
    NONE(0),
    CLOCKWISE_90(1),
    CLOCKWISE_180(2),
    CLOCKWISE_270(3);

    private final int quarterTurns;

    Rotation(int quarterTurns) {
        this.quarterTurns = quarterTurns;
    }

    public int quarterTurns() {
        return quarterTurns;
    }

    public Rotation rotate(Rotation other) {
        return byQuarterTurns((this.quarterTurns + other.quarterTurns) % 4);
    }

    public Rotation inverse() {
        return byQuarterTurns((4 - this.quarterTurns) % 4);
    }

    public static Rotation byQuarterTurns(int quarterTurns) {
        return switch (((quarterTurns % 4) + 4) % 4) {
            case 0 -> NONE;
            case 1 -> CLOCKWISE_90;
            case 2 -> CLOCKWISE_180;
            default -> CLOCKWISE_270;
        };
    }

    /** Parses the spellings accepted in species JSON: {@code none}, {@code 90}, {@code 180}, ... */
    public static Rotation parse(String raw) {
        if (raw == null) {
            return NONE;
        }
        String text = raw.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "").replace("-", "");
        return switch (text) {
            case "none", "0", "identity" -> NONE;
            case "90", "90cw", "clockwise90" -> CLOCKWISE_90;
            case "180", "180cw", "half" -> CLOCKWISE_180;
            case "270", "270cw", "counterclockwise90" -> CLOCKWISE_270;
            default -> throw new IllegalArgumentException(
                    "Unknown rotation '" + raw + "'. Expected none, 90, 180 or 270.");
        };
    }

    /**
     * Rotates a template-local position about the vertical axis through the origin.
     *
     * <p>The origin is the template's base centre, which is why {@link #x} flips sign under a 180
     * turn rather than being translated: templates are authored around (0, 0).
     */
    public BlockPos rotate(BlockPos pos) {
        int x = pos.x();
        int z = pos.z();
        return switch (this) {
            case NONE -> new BlockPos(x, pos.y(), z);
            case CLOCKWISE_90 -> new BlockPos(-z, pos.y(), x);
            case CLOCKWISE_180 -> new BlockPos(-x, pos.y(), -z);
            case CLOCKWISE_270 -> new BlockPos(z, pos.y(), -x);
        };
    }

    /**
     * Rotates a relative offset between two template blocks.
     *
     * @return the offset as a block position with {@code y} set to zero
     */
    public BlockPos rotateOffset(int dx, int dz) {
        return rotate(new BlockPos(dx, 0, dz));
    }

    /**
     * Maps a horizontal facing after this rotation.
     *
     * <p>North becomes east under a clockwise quarter turn, matching the world coordinate rotation
     * in {@link #rotate}. Vertical directions are unaffected.
     */
    public Direction rotateDirection(Direction direction) {
        Direction current = direction;
        for (int i = 0; i < quarterTurns; i++) {
            current = Direction.rotateClockwise(current);
        }
        return current;
    }

    /**
     * Maps a Minecraft axis value after this rotation. Vertical axes never change; horizontal axes
     * are permuted. Log pillars carry an {@code axis} property, so getting this wrong makes a
     * rotated tree's trunk lie down.
     */
    public String rotateAxis(String axis) {
        if (axis == null) {
            return null;
        }
        if (axis.equalsIgnoreCase("y")) {
            return "y";
        }
        String result = axis.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < quarterTurns; i++) {
            result = switch (result) {
                case "x" -> "z";
                case "z" -> "x";
                default -> result;
            };
        }
        return result;
    }
}