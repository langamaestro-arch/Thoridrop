package com.langamaestro.thoridrop.client;

import com.langamaestro.thoridrop.Thoridrop;
import com.langamaestro.thoridrop.ThoridropMenuNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import org.lwjgl.glfw.GLFW;

/** Physical-client-only menu registration; no GUI classes load on a dedicated server. */
@EventBusSubscriber(modid=Thoridrop.MOD_ID,value=Dist.CLIENT)
public final class ThoridropMenuClient {
    private static final KeyMapping.Category CATEGORY =
        new KeyMapping.Category(Identifier.fromNamespaceAndPath(Thoridrop.MOD_ID,"menu"));
    public static final KeyMapping OPEN = new KeyMapping("key.thoridrop.open_menu",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    private ThoridropMenuClient() {}

    @SubscribeEvent
    public static void registerBindings(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(OPEN);
    }

    @SubscribeEvent
    public static void registerClientPackets(RegisterClientPayloadHandlersEvent event) {
        event.register(ThoridropMenuNetwork.MenuSnapshot.TYPE,
            (packet, context) -> ThoridropMenuScreen.receive(packet.data()));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        while(OPEN.consumeClick()) {
            if(mc.player == null || mc.level == null) continue;
            if(mc.screen instanceof ThoridropMenuScreen) {
                mc.setScreen(null);
            } else if(mc.screen == null) {
                mc.setScreen(new ThoridropMenuScreen());
                ClientPacketDistributor.sendToServer(new ThoridropMenuNetwork.MenuRequest(0));
            }
        }
    }
}
