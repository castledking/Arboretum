package codes.castled.arboretum.fabric;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Arboretum's wood blocks: a log, a wood (bark on all sides) and leaves per species, behaving as
 * the vanilla wood they were textured over. The assets and data for each are written by
 * tools/generate_wood_assets.py, which must list the same woods.
 */
final class ArboretumBlocks {

    /**
     * One species' wood.
     *
     * @param like          the vanilla wood whose strength, sounds and map colour it copies
     * @param particleChance how often a leaf block drops a falling leaf each display tick
     * @param particleColor  the falling leaf's colour (the leaves are painted, not biome-tinted)
     */
    record Wood(String name, Block likeLog, Block likeWood, Block likeLeaves, float particleChance, int particleColor) {
    }

    static final List<Wood> WOODS = List.of(
            // Sparse, dry olive foliage: the odd leaf, as most vanilla trees.
            new Wood("baobab", Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, Blocks.DARK_OAK_LEAVES, 0.01F, 0x707051),
            // The jacaranda's signature: violet petals drift down as cherry blossom does.
            new Wood("jacaranda", Blocks.ACACIA_LOG, Blocks.ACACIA_WOOD, Blocks.ACACIA_LEAVES, 0.1F, 0x9B5FC8));

    /** Every registered block, for the creative tab. */
    static final List<Block> ALL = new ArrayList<>();

    private ArboretumBlocks() {
    }

    static void register() {
        FlammableBlockRegistry flammable = FlammableBlockRegistry.getDefaultInstance();
        for (Wood wood : WOODS) {
            Block log = block(wood.name() + "_log", RotatedPillarBlock::new, wood.likeLog());
            Block bark = block(wood.name() + "_wood", RotatedPillarBlock::new, wood.likeWood());
            Block leaves = block(wood.name() + "_leaves", properties -> new UntintedParticleLeavesBlock(
                    wood.particleChance(),
                    ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF000000 | wood.particleColor()),
                    AmbientLeavesBlockSoundPlayer.noAmbientSound(), properties), wood.likeLeaves());
            // Vanilla's own log and leaves fire settings.
            flammable.add(log, 5, 5);
            flammable.add(bark, 5, 5);
            flammable.add(leaves, 30, 60);
        }
        ResourceKey<CreativeModeTab> natural = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("natural_blocks"));
        CreativeModeTabEvents.modifyOutputEvent(natural).register(output -> ALL.forEach(block -> output.accept(
                new net.minecraft.world.item.ItemStack(block), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS)));
    }

    private static Block block(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory, Block like) {
        Identifier id = Identifier.fromNamespaceAndPath(ArboretumFabric.MOD_ID, name);
        Block block = factory.apply(BlockBehaviour.Properties.ofFullCopy(like)
                .setId(ResourceKey.create(Registries.BLOCK, id)));
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
        ALL.add(block);
        return block;
    }
}
