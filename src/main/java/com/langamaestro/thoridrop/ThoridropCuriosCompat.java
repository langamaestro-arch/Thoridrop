package com.langamaestro.thoridrop;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Optional Curios 17 / MC 26.3 integration.
 *
 * Curios handles its slots during LivingDropsEvent at HIGHEST priority.
 * Cancelling that event later from Thoridrop does not put already-cleared Curios
 * back. Override Curios' own drop rules instead, retaining its original slot
 * state and attachment copy-on-death behaviour. No copied backpack, no dupe.
 *
 * The API is loaded reflectively so Thoridrop stays functional without Curios.
 */
public final class ThoridropCuriosCompat {
    private ThoridropCuriosCompat() {}

    @SuppressWarnings({"unchecked","rawtypes"})
    public static void register(Predicate<ServerPlayer> shouldKeep) {
        if(!ModList.get().isLoaded("curios"))return;
        try {
            Class<?> rulesClass=Class.forName("top.theillusivec4.curios.api.event.DropRulesEvent");
            Class<?> dropRuleClass=Class.forName("top.theillusivec4.curios.api.common.DropRule");
            Class<? extends Event> typedEvent=rulesClass.asSubclass(Event.class);
            Method getEntity=rulesClass.getMethod("getEntity");
            Method addOverride=rulesClass.getMethod("addOverride",Predicate.class,dropRuleClass);
            Object alwaysKeep=Enum.valueOf((Class<? extends Enum>)dropRuleClass.asSubclass(Enum.class),"ALWAYS_KEEP");
            // Registration receives actual Curios events, not all LivingDrops events.
            Consumer<Event> listener=event -> {
                try {
                    Object entity=getEntity.invoke(event);
                    if(entity instanceof ServerPlayer player && shouldKeep.test(player)) {
                        addOverride.invoke(event,(Predicate<ItemStack>)stack->true,alwaysKeep);
                    }
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException("Thoridrop could not preserve Curios inventory",ex);
                }
            };
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL,(Class)typedEvent,(Consumer)listener);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Curios installed but Thoridrop cannot register death protection",ex);
        }
    }
}
