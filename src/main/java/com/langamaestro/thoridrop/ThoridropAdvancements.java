package com.langamaestro.thoridrop;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/** Native advancement milestones, completed by server-side event results. */
public final class ThoridropAdvancements {
    private ThoridropAdvancements() {}
    public static void grant(ServerPlayer player,String name,int points) {
        var advancement=player.level().getServer().getAdvancements().get(
            Identifier.fromNamespaceAndPath("thoridrop",name));
        if(advancement == null) return;
        // Vanilla award is persisted with player advancement data.
        if(player.getAdvancements().award(advancement,"completed") && points>0) {
            DropPoints.earn(player.getUUID(),points);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§6✦ Logro desbloqueado: "+name+" (+"+points+" puntos)"));
        }
    }
    public static void grantVictory(ServerPlayer player, DropSession.Rarity rarity, int wins,
                                    boolean flawless,boolean cooperative) {
        grant(player,"root",0);
        grant(player,"first_win",10);
        if(rarity==DropSession.Rarity.RARE)grant(player,"mythic",20);
        if(rarity==DropSession.Rarity.LEGENDARY) {
            grant(player,"mythic",20);
            grant(player,"legendary",55);
        }
        if(flawless)grant(player,"flawless",20);
        if(cooperative)grant(player,"cooperative",25);
        if(wins>=10)grant(player,"veteran_10",30);
        if(wins>=25)grant(player,"veteran_25",60);
        if(wins>=50)grant(player,"veteran_50",110);
        if(wins>=100)grant(player,"veteran_100",250);
    }
}
