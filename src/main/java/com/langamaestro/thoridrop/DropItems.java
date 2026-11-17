package com.langamaestro.thoridrop;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DropItems {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(Thoridrop.MOD_ID);
    public static final DeferredItem<Item> SUPPLY_FRAGMENT=ITEMS.registerSimpleItem("supply_fragment");
    public static final DeferredItem<Item> VICTORY_TROPHY=ITEMS.registerSimpleItem("victory_trophy");
    private DropItems(){}
    public static void register(IEventBus bus){ITEMS.register(bus);}
}
