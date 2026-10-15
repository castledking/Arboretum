package codes.castled.arboretum.fabric;

import codes.castled.arboretum.core.id.ArboretumId;
import codes.castled.arboretum.core.species.SpeciesDefinition;
import codes.castled.arboretum.core.template.Rotation;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code /arboretum}: what this world grows, and checks on it.
 *
 * <ul>
 *   <li>{@code stats} - the config, the species, trees grown and why others were refused;</li>
 *   <li>{@code verify} - checks every recorded tree's blocks against the world;</li>
 *   <li>{@code list} - the recorded trees, sorted;</li>
 *   <li>{@code attempts} - every tree world generation tried to grow, sorted, so two runs of one
 *       seed can be compared;</li>
 *   <li>{@code place <species> <rotation>} - grows one tree where the command runs, through the
 *       same path natural generation takes (no chance roll).</li>
 * </ul>
 */
final class ArboretumCommand {

    /** How many chunks around the command's position {@code place} may read and write. */
    static final int PLACE_REACH = 4;

    private ArboretumCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arboretum")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("stats").executes(ArboretumCommand::stats))
                .then(Commands.literal("verify").executes(ArboretumCommand::verify))
                .then(Commands.literal("list").executes(ArboretumCommand::list))
                .then(Commands.literal("attempts").executes(ArboretumCommand::attempts))
                .then(Commands.literal("place")
                        .then(Commands.argument("species", StringArgumentType.word())
                                .then(Commands.argument("rotation", IntegerArgumentType.integer(0, 3))
                                        .executes(ArboretumCommand::place)))));
    }

    private static int stats(CommandContext<CommandSourceStack> context) {
        WorldState state = WorldState.current();
        if (state == null) {
            return fail(context, "no Arboretum world is loaded");
        }
        Map<String, Integer> refused = new TreeMap<>();
        state.refusals.forEach((reason, count) -> refused.put(reason, count.get()));
        reply(context, "Arboretum: collection " + state.config.collectionId() + ", "
                + state.config.generationMode().id() + "/" + state.config.spawningRule().id()
                + ", grows " + state.resolution.selected().keySet());
        reply(context, "Arboretum: grown " + state.placed.total() + " " + state.placed.bySpecies()
                + "; refused " + refused);
        return state.placed.total();
    }

    private static int verify(CommandContext<CommandSourceStack> context) {
        WorldState state = WorldState.current();
        if (state == null) {
            return fail(context, "no Arboretum world is loaded");
        }
        ServerLevel level = context.getSource().getServer().overworld();
        int trees = 0;
        int blocks = 0;
        int wrong = 0;
        int unloaded = 0;
        String firstWrong = null;
        for (PlacedTrees.Tree tree : state.placed.snapshot()) {
            trees++;
            for (PlacedTrees.Block block : tree.blocks()) {
                BlockPos pos = new BlockPos(block.x(), block.y(), block.z());
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                    unloaded++;
                    continue;
                }
                blocks++;
                String actual = Grower.describe(level.getBlockState(pos));
                if (!actual.equals(block.state())) {
                    wrong++;
                    if (firstWrong == null) {
                        firstWrong = tree.species() + " at " + pos.toShortString() + ": expected "
                                + block.state() + ", found " + actual;
                    }
                }
            }
        }
        reply(context, "Arboretum verify: " + trees + " trees, " + blocks + " blocks checked, " + wrong
                + " wrong, " + unloaded + " in unloaded chunks" + (firstWrong == null ? "" : "; first: " + firstWrong));
        return wrong == 0 ? Math.max(1, trees) : 0;
    }

    /** Every recorded tree as species@x,y,z/rotation, sorted - comparable between runs. */
    private static int list(CommandContext<CommandSourceStack> context) {
        WorldState state = WorldState.current();
        if (state == null) {
            return fail(context, "no Arboretum world is loaded");
        }
        java.util.List<String> trees = new java.util.ArrayList<>();
        for (PlacedTrees.Tree tree : state.placed.snapshot()) {
            trees.add(tree.species().path() + "@" + tree.x() + "," + tree.y() + "," + tree.z() + "/" + tree.rotation());
        }
        java.util.Collections.sort(trees);
        reply(context, "Arboretum trees: " + String.join(" ", trees));
        return trees.size();
    }

    /** Every tree world generation tried to grow, grown or refused, sorted. */
    private static int attempts(CommandContext<CommandSourceStack> context) {
        WorldState state = WorldState.current();
        if (state == null) {
            return fail(context, "no Arboretum world is loaded");
        }
        java.util.List<String> attempts = new java.util.ArrayList<>(state.attempts);
        java.util.Collections.sort(attempts);
        reply(context, "Arboretum attempts: " + String.join(" ", attempts));
        return attempts.size();
    }

    private static int place(CommandContext<CommandSourceStack> context) {
        WorldState state = WorldState.current();
        if (state == null) {
            return fail(context, "no Arboretum world is loaded");
        }
        String name = StringArgumentType.getString(context, "species");
        Optional<SpeciesDefinition> species = ArboretumFabric.bundle().catalogue()
                .get(name.contains(":") ? ArboretumId.parse(name) : ArboretumId.arboretum(name));
        if (species.isEmpty()) {
            return fail(context, "no species " + name + " in the catalogue");
        }
        Rotation rotation = Rotation.values()[IntegerArgumentType.getInteger(context, "rotation")];
        ServerLevel level = context.getSource().getLevel();
        BlockPos origin = BlockPos.containing(context.getSource().getPosition());
        Grower.Result result = Grower.grow(level, new RegionBlockQuery(level, origin, PLACE_REACH),
                ArboretumFabric.bundle(), species.get(), origin, rotation, level.getRandom());
        if (!result.grown()) {
            state.countRefusal(result.refusal());
            return fail(context, species.get().id() + " refused at " + origin.toShortString() + ": " + result.refusal());
        }
        state.placed.add(result.tree());
        reply(context, "Arboretum placed " + species.get().id() + " " + rotation + " at " + origin.toShortString()
                + " (" + result.tree().blocks().size() + " blocks)");
        return 1;
    }

    private static void reply(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(() -> Component.literal(text), true);
    }

    private static int fail(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendFailure(Component.literal(text));
        return 0;
    }

    @SuppressWarnings("unused")
    private static String describe(BlockState state) {
        return Grower.describe(state);
    }
}
