package com.langamaestro.thoridrop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** All menu data is read on the authoritative server. Client actions are fixed numeric IDs. */
public final class ThoridropMenuNetwork {
    private ThoridropMenuNetwork() {}

    public record MenuRequest(int action) implements CustomPacketPayload {
        public static final Type<MenuRequest> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Thoridrop.MOD_ID,"menu_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,MenuRequest> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, MenuRequest::action, MenuRequest::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record MenuSnapshot(String data) implements CustomPacketPayload {
        public static final Type<MenuSnapshot> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Thoridrop.MOD_ID,"menu_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,MenuSnapshot> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, MenuSnapshot::data, MenuSnapshot::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(MenuRequest.TYPE, MenuRequest.CODEC, (packet, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            int action=packet.action();
            if(action >= 1 && action <= 12) {
                Thoridrop mod=Thoridrop.instance();
                if(mod != null) mod.menuPurchase(player, action);
            } else if(action != 0) return;
            sendSnapshot(player);
        });
        registrar.playToClient(MenuSnapshot.TYPE, MenuSnapshot.CODEC);
    }

    public static void sendSnapshot(ServerPlayer player) {
        var id = player.getUUID();
        StringBuilder s = new StringBuilder();
        // Stable row-format protocol: no names or other arbitrary client input.
        s.append(DropPoints.balance(id)).append("\n");
        s.append(DropStatistics.value(id,"wins")).append(",")
         .append(DropStatistics.value(id,"wins_common")).append(",")
         .append(DropStatistics.value(id,"wins_rare")).append(",")
         .append(DropStatistics.value(id,"wins_legendary")).append(",")
         .append(DropStatistics.value(id,"claims")).append(",")
         .append(DropStatistics.value(id,"deaths")).append(",")
         .append(DropStatistics.value(id,"flawless")).append(",")
         .append(DropStatistics.value(id,"kills")).append(",")
         .append(DropStatistics.value(id,"hits")).append(",")
         .append(DropStatistics.value(id,"streak")).append(",")
         .append(DropStatistics.value(id,"best_streak")).append(",")
         .append(DropStatistics.value(id,"best_ticks")).append(",")
         .append(DropStatistics.value(id,"team_wins")).append(",")
         .append(DropStatistics.value(id,"timed_wins")).append(",")
         .append(DropStatistics.value(id,"special_wins")).append(",")
         .append(DropStatistics.value(id,"events_started")).append("\n");
        // Four pages per ranking, six entries per page, plus personal history.
        for(String field : new String[]{"wins","wins_legendary","flawless"})
            for(int page=0;page<4;page++)
                s.append(DropStatistics.rankingPage(field,page,6)).append("\n");
        s.append(DropStatistics.history(id)).append("\n");
        PacketDistributor.sendToPlayer(player, new MenuSnapshot(s.toString()));
    }
}
