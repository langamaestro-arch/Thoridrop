package com.langamaestro.thoridrop;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * First playable milestone: admin-spawned supply crate and manual activation.
 * Enemy spawning, persistence and rewards are NOT implemented yet.
 */
@Mod(Thoridrop.MOD_ID)
public final class Thoridrop {
    public static final String MOD_ID = "thoridrop";
    private static final Logger LOGGER = LogUtils.getLogger();

    private ServerLevel dropLevel;
    private BlockPos dropPosition;
    private DropSession session;
    private int locatorTicks;
    private final java.util.Set<UUID> waveMobs = new java.util.HashSet<>();
    private int waveDelay;

    public Thoridrop(IEventBus modBus) {
        DropBlocks.register(modBus);
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Thoridrop initialized");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("thoridrop")
            .then(Commands.literal("status")
                .executes(context -> {
                    String message = session == null
                        ? "Thoridrop: no active supply crate."
                        : "Thoridrop: " + session.phase() + ", wave " + session.wave()
                            + "/" + session.waveCount() + " at " + dropPosition.toShortString();
                    context.getSource().sendSuccess(() -> Component.literal(message), false);
                    return 1;
                }))
            .then(Commands.literal("start")
                .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    return createDrop(player, DropSession.Rarity.COMMON);
                })
                .then(Commands.literal("rare")
                    .executes(context -> createDrop(context.getSource().getPlayerOrException(), DropSession.Rarity.RARE)))
                .then(Commands.literal("legendary")
                    .executes(context -> createDrop(context.getSource().getPlayerOrException(), DropSession.Rarity.LEGENDARY))))
            .then(Commands.literal("stop")
                .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN))
                .executes(context -> {
                    if (session == null) return 0;
                    if (dropLevel != null && dropPosition != null
                            && isSupplyCrate(dropLevel.getBlockState(dropPosition).getBlock())) {
                        dropLevel.removeBlock(dropPosition, false);
                    }
                    for (UUID id : waveMobs) {
                        net.minecraft.world.entity.Entity mob = dropLevel.getEntity(id);
                        if (mob != null) mob.discard();
                    }
                    waveMobs.clear();
                    session.finish();
                    session = null;
                    dropLevel = null;
                    dropPosition = null;
                    context.getSource().sendSuccess(() -> Component.literal("Thoridrop event stopped."), true);
                    return 1;
                }))
        );
    }

    private int createDrop(ServerPlayer player, DropSession.Rarity rarity) {
        if (session != null) {
            player.sendSystemMessage(Component.literal("Thoridrop: stop the existing event first."));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition().offset(3, 0, 0);
        for (int dy = 0; dy <= 5; dy++) {
            BlockPos candidate = pos.above(dy);
            if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()) {
                pos = candidate;
                break;
            }
        }
        if (!level.getBlockState(pos).isAir()) {
            player.sendSystemMessage(Component.literal("Thoridrop: no clear position for the supply crate."));
            return 0;
        }
        Block crate = switch (rarity) {
            case COMMON -> DropBlocks.COMMON.get();
            case RARE -> DropBlocks.RARE.get();
            case LEGENDARY -> DropBlocks.LEGENDARY.get();
        };
        level.setBlockAndUpdate(pos, crate.defaultBlockState());
        dropLevel = level;
        dropPosition = pos.immutable();
        session = new DropSession(UUID.randomUUID(), rarity, 5);
        player.sendSystemMessage(Component.literal("Thoridrop " + rarity
                + " supply crate at " + pos.toShortString() + ". Right-click to activate."));
        return 1;
    }

    private boolean isSupplyCrate(Block block) {
        return block == DropBlocks.COMMON.get() || block == DropBlocks.RARE.get()
                || block == DropBlocks.LEGENDARY.get();
    }

    @SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (session == null || dropLevel == null || dropPosition == null) return;
        if (session.phase() == DropSession.Phase.COMBAT) {
            if (waveDelay > 0) {
                waveDelay--;
                if (waveDelay == 0) spawnWave();
            } else if (!waveMobs.isEmpty()) {
                java.util.Iterator<UUID> it = waveMobs.iterator();
                while (it.hasNext()) {
                    net.minecraft.world.entity.Entity entity = dropLevel.getEntity(it.next());
                    if (entity instanceof net.minecraft.world.entity.LivingEntity living && !living.isAlive()) {
                        it.remove();
                        if (session.enemyDefeated()) waveDelay = 60;
                    }
                }
                if (session.phase() == DropSession.Phase.REWARDS) {
                    broadcast("All 5 waves defeated! Thoridrop event complete.");
                }
            }
        }
        if (++locatorTicks % 20 != 0) return;
        for (ServerPlayer player : dropLevel.players()) {
            double dx = dropPosition.getX() + 0.5 - player.getX();
            double dz = dropPosition.getZ() + 0.5 - player.getZ();
            int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            double bearing = Math.toDegrees(Math.atan2(-dx, dz));
            double relative = net.minecraft.util.Mth.wrapDegrees(bearing - player.getYRot());
            String arrow = Math.abs(relative) < 22.5 ? "↑"
                : Math.abs(relative) > 157.5 ? "↓"
                : relative > 0 ? (relative < 67.5 ? "↖" : relative < 112.5 ? "←" : "↙")
                : (relative > -67.5 ? "↗" : relative > -112.5 ? "→" : "↘");
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(Component.literal(
                "✦ THORIDROP " + session.rarity() + "  " + arrow + "  " + distance + "m  •  " + session.phase())));
        }
    }

    private void broadcast(String message) {
        for (ServerPlayer p : dropLevel.players()) p.sendSystemMessage(Component.literal(message));
    }

    private void spawnWave() {
        if (session == null || session.phase() != DropSession.Phase.COMBAT) return;
        waveMobs.clear();
        int wave = session.wave();
        int extra = session.rarity() == DropSession.Rarity.LEGENDARY ? 3
                  : session.rarity() == DropSession.Rarity.RARE ? 1 : 0;
        int count = wave == 5 ? 1 : 2 + wave + extra;
        net.minecraft.world.entity.EntityType<?>[] types = switch (wave) {
            case 1 -> new net.minecraft.world.entity.EntityType<?>[]{net.minecraft.world.entity.EntityType.ZOMBIE};
            case 2 -> new net.minecraft.world.entity.EntityType<?>[]{net.minecraft.world.entity.EntityType.ZOMBIE, net.minecraft.world.entity.EntityType.SKELETON};
            case 3 -> new net.minecraft.world.entity.EntityType<?>[]{net.minecraft.world.entity.EntityType.SKELETON, net.minecraft.world.entity.EntityType.SPIDER};
            case 4 -> new net.minecraft.world.entity.EntityType<?>[]{net.minecraft.world.entity.EntityType.HUSK, net.minecraft.world.entity.EntityType.STRAY};
            default -> new net.minecraft.world.entity.EntityType<?>[]{net.minecraft.world.entity.EntityType.RAVAGER};
        };
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            int x = dropPosition.getX() + (int)Math.round(Math.cos(angle) * 9);
            int z = dropPosition.getZ() + (int)Math.round(Math.sin(angle) * 9);
            BlockPos spawn = dropLevel.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, dropPosition.getY(), z));
            if (!dropLevel.getBlockState(spawn).isAir() || !dropLevel.getBlockState(spawn.above()).isAir()) continue;
            net.minecraft.world.entity.Entity entity = types[i % types.length].create(dropLevel, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (entity == null) continue;
            entity.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
            if (wave == 5) entity.setCustomName(Component.literal("THORIDROP BOSS"));
            if (dropLevel.addFreshEntity(entity)) waveMobs.add(entity.getUUID());
        }
        session.setWaveEnemyCount(waveMobs.size());
        broadcast("THORIDROP - Wave " + wave + "/5: " + waveMobs.size() + " enemies!");
        if (waveMobs.isEmpty()) broadcast("No safe enemy spawn locations. Use /thoridrop stop and try open terrain.");
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (session == null || dropPosition == null || dropLevel == null) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level() != dropLevel || !event.getPos().equals(dropPosition)) return;
        if (session.activate()) {
            waveDelay = 40;
            broadcast("Thoridrop activated! Wave 1 begins in 2 seconds.");
        }
        // Custom supply crates never expose a vanilla chest inventory.
        event.setCanceled(true);
    }
}
