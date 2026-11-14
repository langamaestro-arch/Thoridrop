package com.langamaestro.thoridrop.client;

import com.langamaestro.thoridrop.Thoridrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/** Ornate pixel-art HUD rendered in place of Thoridrop's synchronized boss bar. */
@EventBusSubscriber(modid = Thoridrop.MOD_ID, value = Dist.CLIENT)
public final class ThoridropHud {
    private static final Identifier FRAME = Identifier.fromNamespaceAndPath("thoridrop", "textures/gui/ornate_hud.png");
    private ThoridropHud() {}

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        String raw = event.getBossEvent().getName().getString();
        if (!raw.contains("THORIDROP")) return;
        event.setCanceled(true);
        event.setIncrement(0);
        GuiGraphicsExtractor gui = event.getGuiGraphics();
        int x = gui.guiWidth() / 2 - 128;
        int y = 3;
        gui.blit(RenderPipelines.GUI_TEXTURED, FRAME, x, y, 0, 0, 256, 56, 256, 64);

        String tier = raw.contains("LEGENDARIO") ? "LEGENDARIO"
                    : raw.contains("MÍTICO") ? "MÍTICO" : "NORMAL";
        int accent = tier.equals("MÍTICO") ? 0xFFE8B1FF : tier.equals("LEGENDARIO") ? 0xFFFFC96C : 0xFF8BD7FF;
        int fill = tier.equals("MÍTICO") ? 0xFFB34AFF : tier.equals("LEGENDARIO") ? 0xFFFF6A36 : 0xFF45B6FA;
        var font = Minecraft.getInstance().font;
        gui.centeredText(font, Component.literal("✦ THORIDROP " + tier + " ✦"), gui.guiWidth() / 2, y + 10, accent);

        int progress = Math.max(0, Math.min(218, Math.round(218 * event.getBossEvent().getProgress())));
        if (progress > 0) {
            gui.fill(x + 19, y + 29, x + 19 + progress, y + 35, fill);
            gui.fill(x + 19, y + 29, x + 19 + progress, y + 30, 0xAAFFFFFF);
        }

        String detail;
        if (raw.contains("OLEADA")) {
            detail = raw.substring(raw.indexOf("OLEADA")).replace("  •  ", "  ✦  ");
        } else if (raw.contains("RECOMPENSAS")) {
            detail = "RECOMPENSAS DESBLOQUEADAS  ✦  ABRE EL COFRE";
        } else if (raw.contains("OBJETIVO") || raw.contains("GIRA")) {
            String distance = raw.replaceAll(".*?(\\d+)m.*", "$1");
            String direction = raw.contains("OBJETIVO ENFRENTE") ? "◆ OBJETIVO ENFRENTE ◆"
                    : raw.contains("DERECHA") ? "▶ DERECHA" : "IZQUIERDA ◀";
            detail = distance + "m  ✦  " + direction;
        } else {
            detail = "SUMINISTROS  ✦  PREPARATE";
        }
        if (font.width(detail) > 238) detail = font.plainSubstrByWidth(detail, 238);
        gui.centeredText(font, Component.literal(detail), gui.guiWidth() / 2, y + 42, 0xFFFFE6A4);
    }
}
