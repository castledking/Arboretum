package codes.castled.arboretum.fabric;

import codes.castled.arboretum.core.template.BlockStateRef;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** The core's platform-neutral block states, as Minecraft's. */
final class BlockStates {

    private BlockStates() {
    }

    /**
     * Minecraft's state for a core state. An unknown block or property is an error, never a silent
     * substitute: the species file named something this Minecraft doesn't have.
     */
    static BlockState toMinecraft(BlockStateRef ref) {
        Identifier id = Identifier.parse(ref.blockId());
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == Blocks.AIR && !ref.blockId().equals("minecraft:air")) {
            throw new IllegalArgumentException("no block " + ref.blockId() + " in this Minecraft");
        }
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> property : ref.properties().entrySet()) {
            Property<?> found = block.getStateDefinition().getProperty(property.getKey());
            if (found == null) {
                throw new IllegalArgumentException(ref.blockId() + " has no property " + property.getKey());
            }
            state = with(state, found, property.getValue(), ref);
        }
        return state;
    }

    static String idOf(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property,
                                                             String value, BlockStateRef ref) {
        T parsed = property.getValue(value).orElseThrow(() -> new IllegalArgumentException(
                ref.blockId() + " property " + property.getName() + " has no value " + value));
        return state.setValue(property, parsed);
    }
}
