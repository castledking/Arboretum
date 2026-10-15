package codes.castled.arboretum.core.placement;

import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.SpeciesDefinition;
import codes.castled.arboretum.core.template.BlockPos;
import codes.castled.arboretum.core.template.BlockStateRef;
import codes.castled.arboretum.core.template.Direction;
import codes.castled.arboretum.core.template.Rotation;
import codes.castled.arboretum.core.template.StructureTemplate;
import codes.castled.arboretum.core.template.TemplateBlock;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides whether a tree may be placed, and produces the exact block writes if so.
 *
 * <p>Three properties make this safe to run during chunk generation:
 * <ol>
 *   <li><b>It never mutates the world.</b> It reads through {@link BlockQuery} and returns a
 *       {@link PlacementPlan}. A crash mid-placement leaves nothing half-written.</li>
 *   <li><b>It refuses rather than guesses.</b> A position the query cannot answer is
 *       {@link BlockQuery.Occupancy#UNKNOWN} and the whole tree is refused. It never loads a chunk
 *       and never assumes the world around it.</li>
 *   <li><b>It never overwrites protected blocks.</b> {@link #PROTECTED_BLOCKS} is a floor applied
 *       before the species' own rule, so a pack cannot be authored into deleting bedrock or a
 *       command block, whatever its filters say.</li>
 * </ol>
 *
 * <p>All three are covered by tests in this package that run with no Minecraft on the classpath.
 */
public final class TreePlacementPlanner {

    /**
     * Blocks no template may ever overwrite, whatever a species' filters say.
     *
     * <p>A species file is pack content. A pack should be able to add a tree, not to reach bedrock
     * or a structure block. Deliberately block-id based rather than occupancy based, so it works
     * even when a platform reports an unusual block as replaceable.
     */
    public static final Set<String> PROTECTED_BLOCKS = Set.of(
            "minecraft:bedrock",
            "minecraft:barrier",
            "minecraft:command_block",
            "minecraft:chain_command_block",
            "minecraft:repeating_command_block",
            "minecraft:structure_block",
            "minecraft:structure_void",
            "minecraft:jigsaw",
            "minecraft:end_portal",
            "minecraft:end_portal_frame",
            "minecraft:nether_portal",
            "minecraft:light",
            "minecraft:debug_stick",
            "minecraft:spawner",
            "minecraft:reinforced_deepslate");

    private final SpeciesDefinition species;
    private final Map<String, PlacementOrder.Kind> markerKinds;
    private final Map<String, String> markerReplacements;

    public TreePlacementPlanner(SpeciesDefinition species) {
        this.species = species;
        this.markerKinds = buildMarkerKinds(species);
        this.markerReplacements = buildMarkerReplacements(species);
    }

    /**
     * Classifies each marker block id by what it becomes.
     *
     * <p>Extra markers are classified from their replacement block: a marker replaced with the
     * species' root block is a root, one replaced with its leaf block is a leaf, anything else is a
     * decoration. That keeps a species file from having to declare a category for every block.
     */
    private static Map<String, PlacementOrder.Kind> buildMarkerKinds(SpeciesDefinition species) {
        Map<String, PlacementOrder.Kind> kinds = new LinkedHashMap<>();
        for (String marker : species.structure().logMarkers()) {
            kinds.put(marker, PlacementOrder.Kind.LOG);
        }
        for (String marker : species.structure().leafMarkers()) {
            kinds.put(marker, PlacementOrder.Kind.LEAF);
        }
        String leafBlock = species.visuals().leafBlockId();
        String rootBlock = species.visuals().rootBlockId();
        species.structure().extraMarkers().forEach((marker, replacement) -> kinds.put(marker, kindOf(
                species, marker, replacement, leafBlock, rootBlock)));
        return Map.copyOf(kinds);
    }

    private static PlacementOrder.Kind kindOf(SpeciesDefinition species, String marker, String replacement,
                                             String leafBlock, String rootBlock) {
        if (replacement.equals(leafBlock)) {
            return PlacementOrder.Kind.LEAF;
        }
        if (rootBlock != null && replacement.equals(rootBlock)) {
            return PlacementOrder.Kind.ROOT;
        }
        if (species.structure().logMarkers().contains(replacement)
                || replacement.equals(species.visuals().logBlockId())) {
            return PlacementOrder.Kind.LOG;
        }
        return PlacementOrder.Kind.DECORATION;
    }

    private static Map<String, String> buildMarkerReplacements(SpeciesDefinition species) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put(species.visuals().logBlockId(), species.visuals().logBlockId());
        replacements.put(species.visuals().leafBlockId(), species.visuals().leafBlockId());
        if (species.visuals().logTopBlockId() != null) {
            replacements.put(species.visuals().logTopBlockId(), species.visuals().logTopBlockId());
        }
        if (species.visuals().rootBlockId() != null) {
            replacements.put(species.visuals().rootBlockId(), species.visuals().rootBlockId());
        }
        species.structure().extraMarkers().forEach(replacements::put);
        species.structure().markerBlocks().forEach(replacements::put);
        return Map.copyOf(replacements);
    }

    /** @return the blocks this species' log markers become, e.g. its log and its wood. */
    public Set<String> logBlocks() {
        Set<String> blocks = new java.util.TreeSet<>();
        species.structure().logMarkers().forEach(marker -> blocks.add(species.structure().blockFor(marker)));
        return blocks;
    }

    /** @return the blocks this species' leaf markers become. */
    public Set<String> leafBlocks() {
        Set<String> blocks = new java.util.TreeSet<>();
        species.structure().leafMarkers().forEach(marker -> blocks.add(species.structure().blockFor(marker)));
        return blocks;
    }

    public SpeciesDefinition species() {
        return species;
    }

    /**
     * The blocks a trunk of this species may root in, for {@link PlacementContext#groundBlocks()}.
     *
     * <p>{@code structure.growable_on} is the placement rule; {@code ecology.ground_blocks} is what
     * the spawn surface must be. A species that states only the latter roots in the same blocks.
     */
    public static Set<String> groundFor(SpeciesDefinition species) {
        Set<String> growable = species.structure().groundFilter();
        return growable.isEmpty() ? species.ecology().groundBlocks() : growable;
    }

    /** @return the marker block ids this species declares, for template validation. */
    public Set<String> markers() {
        return markerKinds.keySet();
    }

    /**
     * Plans a placement.
     *
     * @param context where and how the tree goes
     * @param trunk   the trunk template, already loaded and parsed
     * @param canopy  the canopy template, already loaded and parsed
     * @param roots   the roots template, or null
     * @param query   read-only view of the world
     * @return a {@link PlacementPlan.Placed} or a {@link PlacementPlan.Refused}
     */
    public PlacementPlan plan(PlacementContext context,
                              StructureTemplate trunk,
                              StructureTemplate canopy,
                              StructureTemplate roots,
                              BlockQuery query) {
        ArboretumId id = species.id();

        // --- 1. Collect template blocks, refusing anything that cannot produce a tree ---------
        // The trunk's base - the centre of its lowest layer of logs - lands on the origin, so a
        // template copied from a world with its corner anywhere still grows where it was asked to.
        BlockPos base = basePoint(trunk);
        List<PlacedBlock> candidates = new ArrayList<>();
        collect(trunk, context, base, PlacementOrder.Kind.BRANCH, candidates);
        if (canopy != null) {
            collect(canopy, context, base, PlacementOrder.Kind.BRANCH, candidates);
        }
        if (roots != null) {
            collect(roots, context, base, PlacementOrder.Kind.BRANCH, candidates);
        }

        if (candidates.isEmpty()) {
            return new PlacementPlan.Refused(id, PlacementPlan.Reason.EMPTY_TEMPLATE,
                    "no template block matched any declared marker");
        }

        // --- 2. Marker coverage ----------------------------------------------------------------
        // A species whose declared leaf marker appears in no palette would place a bare trunk that
        // reads as a bug. Report it against the species file rather than generating the artefact.
        Set<String> seenMarkers = new LinkedHashSet<>();
        for (PlacedBlock block : candidates) {
            if (block.marker) {
                seenMarkers.add(block.sourceId);
            }
        }
        for (String marker : species.structure().leafMarkers()) {
            if (!seenMarkers.contains(marker)) {
                return new PlacementPlan.Refused(id, PlacementPlan.Reason.MISSING_MARKER,
                        "leaf marker '" + marker + "' appears in no template palette. The species "
                                + "file lists it, so the templates must contain it.");
            }
        }

        // --- 3. Height limits ------------------------------------------------------------------
        for (PlacedBlock block : candidates) {
            if (!context.withinHeight(block.pos.y())) {
                return new PlacementPlan.Refused(id, PlacementPlan.Reason.WORLD_HEIGHT,
                        "a block would land at y=" + block.pos.y() + ", outside ["
                                + context.minY() + ", " + context.maxY() + "]");
            }
        }

        // --- 4. Overlap: one block per position --------------------------------------------------
        // Trunk, canopy and roots are authored separately and meet: the canopy is built around the
        // top of the trunk, the roots around its base. Where they meet the stronger part keeps the
        // position, as in vanilla trees - a trunk runs up through its own leaves. Two blocks of the
        // same kind that disagree are still an authoring error.
        Map<BlockPos, PlacedBlock> chosen = new LinkedHashMap<>();
        for (PlacedBlock block : candidates) {
            PlacedBlock previous = chosen.get(block.pos);
            if (previous == null) {
                chosen.put(block.pos, block);
            } else if (!previous.state.equals(block.state)) {
                int order = Integer.compare(precedence(block.kind), precedence(previous.kind));
                if (order == 0) {
                    return new PlacementPlan.Refused(id, PlacementPlan.Reason.OBSTRUCTED,
                            "trunk, canopy and roots templates overlap at " + block.pos + " ("
                                    + previous.state + " vs " + block.state + ")");
                }
                if (order > 0) {
                    chosen.put(block.pos, block);
                }
            }
        }

        // --- 5. Obstruction and protection -------------------------------------------------------
        Map<BlockPos, BlockStateRef> placements = new LinkedHashMap<>();
        Map<PlacementOrder.Kind, List<BlockPos>> byKind = new EnumMap<>(PlacementOrder.Kind.class);
        for (PlacedBlock block : chosen.values()) {
            BlockQuery.WorldBlock existing = query.blockAt(block.pos);
            if (existing.occupancy().isUnknown()) {
                // A neighbouring chunk is not generated. Growing across the border here produces a
                // tree clipped by the chunk edge, so refuse; the next attempt retries it.
                return new PlacementPlan.Refused(id, PlacementPlan.Reason.OUTSIDE_LOADED_REGION,
                        "position " + block.pos + " is not in the loaded region");
            }
            if (PROTECTED_BLOCKS.contains(existing.blockId())) {
                return new PlacementPlan.Refused(id, PlacementPlan.Reason.PROTECTED_BLOCK,
                        "position " + block.pos + " holds protected block " + existing.blockId());
            }
            if (!mayReplace(block, existing)) {
                return new PlacementPlan.Refused(id, PlacementPlan.Reason.OBSTRUCTED,
                        "cannot place " + block.state + " at " + block.pos + " over "
                                + existing.blockId());
            }
            placements.put(block.pos, block.state);
            byKind.computeIfAbsent(block.kind, key -> new ArrayList<>()).add(block.pos);
        }

        // --- 6. Ground ------------------------------------------------------------------------
        List<BlockPos> feet = new ArrayList<>(byKind.getOrDefault(PlacementOrder.Kind.ROOT, List.of()));
        feet.addAll(byKind.getOrDefault(PlacementOrder.Kind.LOG, List.of()));
        List<BlockPos> supportedFeet = new ArrayList<>();
        int maxDepth = species.structure().maxLogDepth();
        for (BlockPos foot : feet) {
            BlockPos ground = groundBelow(context, query, foot, maxDepth);
            if (ground != null) {
                supportedFeet.add(foot);
            }
        }
        if (supportedFeet.isEmpty()) {
            return new PlacementPlan.Refused(id, PlacementPlan.Reason.NO_GROUND,
                    "no trunk base stands on one of " + new java.util.TreeSet<>(context.groundBlocks())
                            + " within " + maxDepth + " block(s) below it");
        }

        // --- 7. Order and describe ---------------------------------------------------------------
        List<BlockPos> ordered = orderPlacements(byKind);
        BlockPos.Bounds footprint = BlockPos.boundsOf(
                ordered.stream().map(pos -> new BlockPos(pos.x(), 0, pos.z())).toList());
        int minY = ordered.stream().mapToInt(BlockPos::y).min().orElse(0);
        int maxY = ordered.stream().mapToInt(BlockPos::y).max().orElse(0);

        Map<BlockPos, BlockStateRef> orderedPlacements = new LinkedHashMap<>();
        for (BlockPos pos : ordered) {
            orderedPlacements.put(pos, placements.get(pos));
        }

        return new PlacementPlan.Placed(id, orderedPlacements, supportedFeet, footprint, maxY - minY + 1);
    }

    /** Which part keeps a position two templates both fill: log, branch, root, decoration, leaf. */
    private static int precedence(PlacementOrder.Kind kind) {
        return switch (kind) {
            case LOG -> 4;
            case BRANCH -> 3;
            case ROOT -> 2;
            case DECORATION -> 1;
            case LEAF -> 0;
        };
    }

    /**
     * Walks down from a trunk base looking for ground the species can root in.
     *
     * <p>{@code maxLogDepth} is what makes a tree on a slope work: the trunk base may be one or two
     * blocks above the soil, and the species declares how far to look.
     *
     * @return the ground position, or null when there is none within reach
     */
    private BlockPos groundBelow(PlacementContext context, BlockQuery query, BlockPos foot, int maxDepth) {
        Set<String> accepted = context.groundBlocks();
        BlockPos candidate = foot;
        for (int depth = 0; depth <= maxDepth; depth++) {
            candidate = candidate.offset(0, -1, 0);
            if (!context.withinHeight(candidate.y())) {
                return null;
            }
            BlockQuery.WorldBlock below = query.blockAt(candidate);
            if (below.occupancy().isUnknown()) {
                return null;
            }
            if (below.occupancy() == BlockQuery.Occupancy.SOLID || below.occupancy() == BlockQuery.Occupancy.FLUID) {
                return accepted.contains(below.blockId()) ? candidate : null;
            }
        }
        return null;
    }

    /**
     * Applies a placement kind's replaceable rule to one occupied position.
     *
     * <p>Leaves pass through solid neighbours by default so a canopy can interleave with an existing
     * trunk; logs never do, because a log inside somebody's wall is a tree that grew through a
     * build.
     */
    private boolean mayReplace(PlacedBlock block, BlockQuery.WorldBlock existing) {
        if (existing.occupancy().isFree()) {
            return true;
        }
        if (existing.occupancy() == BlockQuery.Occupancy.REPLACEABLE) {
            return true;
        }
        return switch (species.structure().replaceableBlocks()) {
            case PASSTHROUGH -> block.kind == PlacementOrder.Kind.LEAF;
            case PIERCE -> true;
            case BLOCK -> false;
        };
    }

    /** Gathers the placements a template contributes, in placement-kind order. */
    /**
     * The trunk's base in template coordinates: the centre of the bounding box of its lowest layer
     * of log markers. (0, 0, 0) for a template without logs.
     */
    public BlockPos basePoint(StructureTemplate template) {
        int minY = Integer.MAX_VALUE;
        for (TemplateBlock block : template.blocks()) {
            BlockStateRef state = template.stateAt(block);
            if (state != null && markerKinds.get(state.blockId()) == PlacementOrder.Kind.LOG) {
                minY = Math.min(minY, block.pos().y());
            }
        }
        if (minY == Integer.MAX_VALUE) {
            return new BlockPos(0, 0, 0);
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (TemplateBlock block : template.blocks()) {
            BlockStateRef state = template.stateAt(block);
            if (state != null && block.pos().y() == minY
                    && markerKinds.get(state.blockId()) == PlacementOrder.Kind.LOG) {
                minX = Math.min(minX, block.pos().x());
                maxX = Math.max(maxX, block.pos().x());
                minZ = Math.min(minZ, block.pos().z());
                maxZ = Math.max(maxZ, block.pos().z());
            }
        }
        return new BlockPos(Math.floorDiv(minX + maxX, 2), minY, Math.floorDiv(minZ + maxZ, 2));
    }

    private void collect(StructureTemplate template,
                         PlacementContext context,
                         BlockPos base,
                         PlacementOrder.Kind defaultKind,
                         List<PlacedBlock> out) {
        Rotation rotation = context.rotation();
        BlockPos origin = context.origin();
        for (TemplateBlock block : template.blocks()) {
            BlockStateRef source = template.stateAt(block);
            if (source == null) {
                continue;
            }
            PlacementOrder.Kind kind = markerKinds.get(source.blockId());
            boolean isMarker = kind != null;
            if (!isMarker && !isDirectlyPlaceable(source)) {
                // Structure templates also hold air, wool helpers and whatever the author left
                // lying around. Only blocks the species explicitly declares are written.
                continue;
            }
            BlockStateRef state = isMarker ? replacementFor(source) : source;
            BlockPos rotated = rotation.rotate(block.pos().offset(-base.x(), -base.y(), -base.z()));
            out.add(new PlacedBlock(
                    origin.offset(rotated.x(), rotated.y(), rotated.z()),
                    rotateState(state, rotation),
                    isMarker ? kind : defaultKind,
                    source.blockId(),
                    isMarker));
        }
    }

    /**
     * @return true when a template block is one of the species' own output blocks, which some
     *         templates place directly for fine detail, e.g. a specific log axis variant
     */
    private boolean isDirectlyPlaceable(BlockStateRef source) {
        return markerReplacements.containsKey(source.blockId());
    }

    /** @return the block a marker becomes. */
    private BlockStateRef replacementFor(BlockStateRef marker) {
        String replacementId = markerReplacements.get(marker.blockId());
        String id = replacementId == null ? marker.blockId() : replacementId;
        // The block keeps the shape it was built with - a branch's axis - and nothing that belongs
        // to the placed block's life: a hand-placed leaf is persistent and has a distance, and a
        // grown one must decay like any other and gets its distance where it lands.
        Map<String, String> kept = new LinkedHashMap<>();
        marker.properties().forEach((name, value) -> {
            if (!NOT_CARRIED.contains(name)) {
                kept.put(name, value);
            }
        });
        return new BlockStateRef(id, kept);
    }

    /** Properties a marker never hands on to the block it becomes. */
    static final Set<String> NOT_CARRIED = Set.of("persistent", "distance", "waterlogged");

    /**
     * Rotates the directional properties of a state.
     *
     * <p>Only {@code axis} and the four horizontal facings are remapped. Anything else is left alone,
     * because guessing at an unfamiliar property is how a rotated tree ends up with doors facing
     * into the trunk.
     */
    static BlockStateRef rotateState(BlockStateRef state, Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return state;
        }
        BlockStateRef result = state;
        if (result.hasProperty("axis")) {
            result = result.with("axis", rotation.rotateAxis(result.property("axis")));
        }
        for (String facing : new String[]{"facing", "north", "south", "east", "west"}) {
            if (result.hasProperty(facing)) {
                String value = result.property(facing);
                if (value != null) {
                    Direction mapped = mapFacing(value, rotation);
                    if (mapped != null) {
                        result = result.with(facing, facingState(mapped));
                    }
                }
            }
        }
        return result;
    }

    private static String facingState(Direction direction) {
        return switch (direction) {
            case NORTH -> "north";
            case EAST -> "east";
            case SOUTH -> "south";
            case WEST -> "west";
            default -> direction.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    private static Direction mapFacing(String value, Rotation rotation) {
        Direction direction = switch (value) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            // "up"/"down" belong to axis properties, not facings.
            default -> null;
        };
        return direction == null ? null : rotation.rotateDirection(direction);
    }

    /** Flattens the per-kind lists into one write order: roots, logs, branches, decorations, leaves. */
    private List<BlockPos> orderPlacements(Map<PlacementOrder.Kind, List<BlockPos>> byKind) {
        List<BlockPos> ordered = new ArrayList<>();
        for (PlacementOrder.Kind kind : PlacementOrder.Kind.values()) {
            List<BlockPos> positions = byKind.get(kind);
            if (positions == null) {
                continue;
            }
            List<BlockPos> sorted = new ArrayList<>(positions);
            sorted.sort(PlacementOrder.COMPARATOR);
            ordered.addAll(sorted);
        }
        return ordered;
    }

    /** A single template contribution, already rotated and translated into world space. */
    private record PlacedBlock(BlockPos pos, BlockStateRef state, PlacementOrder.Kind kind,
                               String sourceId, boolean marker) {
    }
}