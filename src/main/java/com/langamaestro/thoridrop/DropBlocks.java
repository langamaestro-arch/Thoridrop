package com.langamaestro.thoridrop;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DropBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Thoridrop.MOD_ID);

    public static final DeferredBlock<Block> COMMON = BLOCKS.registerSimpleBlock("common_crate",
            properties -> properties.mapColor(MapColor.WOOD).strength(3.0f));
    public static final DeferredBlock<Block> RARE = BLOCKS.registerSimpleBlock("rare_crate",
            properties -> properties.mapColor(MapColor.COLOR_BLUE).strength(3.5f));
    public static final DeferredBlock<Block> LEGENDARY = BLOCKS.registerSimpleBlock("legendary_crate",
            properties -> properties.mapColor(MapColor.COLOR_PURPLE).strength(4.0f));

    private DropBlocks() {}
    public static void register(IEventBus bus) { BLOCKS.register(bus); }
}
