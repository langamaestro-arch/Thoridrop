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
    private int waveTotal;
    private boolean chestOpen;
    private int chestOpeningTicks;
    private final java.util.Set<UUID> participants = new java.util.HashSet<>();
    private final java.util.Set<UUID> claimedRewards = new java.util.HashSet<>();

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
                        : "Thoridrop " + rarityName(session.rarity()) + ": " + session.phase() + ", wave " + session.wave()
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
                .then(Commands.literal("normal")
                    .executes(context -> createDrop(context.getSource().getPlayerOrException(), DropSession.Rarity.COMMON)))
                .then(Commands.literal("mitico")
                    .executes(context -> createDrop(context.getSource().getPlayerOrException(), DropSession.Rarity.RARE)))
                .then(Commands.literal("legendario")
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

    private static String rarityName(DropSession.Rarity rarity) {
        return switch (rarity) {
            case COMMON -> "NORMAL";
            case RARE -> "MÍTICO";
            case LEGENDARY -> "LEGENDARIO";
        };
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
        claimedRewards.clear();
        participants.clear();
        waveTotal = 0;
        chestOpen = false;
        chestOpeningTicks = -1;
        player.sendSystemMessage(Component.literal("Thoridrop " + rarityName(rarity)
                + " supply crate at " + pos.toShortString() + ". Right-click to activate."));
        return 1;
    }

    private boolean isSupplyCrate(Block block) {
        return block == DropBlocks.COMMON.get() || block == DropBlocks.RARE.get()
                || block == DropBlocks.LEGENDARY.get()
                || block == DropBlocks.COMMON_OPEN.get() || block == DropBlocks.RARE_OPEN.get()
                || block == DropBlocks.LEGENDARY_OPEN.get()
                || block == DropBlocks.COMMON_OPENING1.get() || block == DropBlocks.COMMON_OPENING2.get()
                || block == DropBlocks.RARE_OPENING1.get() || block == DropBlocks.RARE_OPENING2.get()
                || block == DropBlocks.LEGENDARY_OPENING1.get() || block == DropBlocks.LEGENDARY_OPENING2.get();
    }

    @SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (session == null || dropLevel == null || dropPosition == null) return;
        if (session.phase() == DropSession.Phase.COMBAT) {
            for (ServerPlayer player : dropLevel.players()) {
                if (player.distanceToSqr(dropPosition.getX() + 0.5, dropPosition.getY() + 0.5, dropPosition.getZ() + 0.5) <= 24 * 24) {
                    participants.add(player.getUUID());
                }
            }
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
                    broadcast("¡Cinco oleadas completadas! Los participantes pueden recoger su recompensa.");
                }
            }
        }
        if (session.phase() == DropSession.Phase.REWARDS && !chestOpen) {
            if (chestOpeningTicks < 0) chestOpeningTicks = 0;
            chestOpeningTicks++;
            if (chestOpeningTicks == 5) setCrateOpeningStage(1);
            if (chestOpeningTicks == 11) setCrateOpeningStage(2);
            if (chestOpeningTicks == 17) {
                setCrateOpeningStage(3);
                chestOpen = true;
                broadcast("¡Cofre abierto! Haz clic para recoger tu recompensa.");
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
            String progress = session.phase() == DropSession.Phase.COMBAT
                ? "  |  Oleada " + session.wave() + "/5  |  Eliminados " + (waveTotal - waveMobs.size()) + "/" + waveTotal + "  |  Restantes " + waveMobs.size()
                : session.phase() == DropSession.Phase.REWARDS ? "  |  RECOMPENSAS" : "  |  DISPONIBLE";
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(Component.literal(
                "✦ THORIDROP " + rarityName(session.rarity()) + "  " + arrow + "  " + distance + "m" + progress)));
        }
    }

    private void grantRewards(ServerPlayer player) {
        {
            java.util.Random random = new java.util.Random();
            net.minecraft.world.item.Item[] ironArmor = {
                net.minecraft.world.item.Items.IRON_HELMET, net.minecraft.world.item.Items.IRON_CHESTPLATE,
                net.minecraft.world.item.Items.IRON_LEGGINGS, net.minecraft.world.item.Items.IRON_BOOTS};
            net.minecraft.world.item.Item[] diamondArmor = {
                net.minecraft.world.item.Items.DIAMOND_HELMET, net.minecraft.world.item.Items.DIAMOND_CHESTPLATE,
                net.minecraft.world.item.Items.DIAMOND_LEGGINGS, net.minecraft.world.item.Items.DIAMOND_BOOTS};
            net.minecraft.world.item.Item[] ironGear = {
                net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.IRON_PICKAXE,
                net.minecraft.world.item.Items.IRON_AXE, net.minecraft.world.item.Items.IRON_SHOVEL};
            net.minecraft.world.item.Item[] diamondGear = {
                net.minecraft.world.item.Items.DIAMOND_SWORD, net.minecraft.world.item.Items.DIAMOND_PICKAXE,
                net.minecraft.world.item.Items.DIAMOND_AXE, net.minecraft.world.item.Items.DIAMOND_SHOVEL};
            int firstArmorIndex = random.nextInt(diamondArmor.length);
            net.minecraft.world.item.ItemStack[] loot = switch (session.rarity()) {
                case COMMON -> new net.minecraft.world.item.ItemStack[] {
                    new net.minecraft.world.item.ItemStack(ironArmor[random.nextInt(ironArmor.length)]),
                    new net.minecraft.world.item.ItemStack(ironGear[random.nextInt(ironGear.length)]),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4 + random.nextInt(5)),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 12)};
                case RARE -> new net.minecraft.world.item.ItemStack[] {
                    new net.minecraft.world.item.ItemStack(ironArmor[random.nextInt(ironArmor.length)]),
                    new net.minecraft.world.item.ItemStack(diamondGear[random.nextInt(diamondGear.length)]),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 2 + random.nextInt(3)),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 12),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 2)};
                case LEGENDARY -> new net.minecraft.world.item.ItemStack[] {
                    new net.minecraft.world.item.ItemStack(diamondArmor[firstArmorIndex]),
                    new net.minecraft.world.item.ItemStack(diamondArmor[(firstArmorIndex + 1 + random.nextInt(diamondArmor.length - 1)) % diamondArmor.length]),
                    new net.minecraft.world.item.ItemStack(diamondGear[random.nextInt(diamondGear.length)]),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 5 + random.nextInt(4)),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 16),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 4)};
            };
            for (net.minecraft.world.item.ItemStack item : loot) {
                if (!player.getInventory().add(item)) {
                    net.minecraft.world.entity.item.ItemEntity dropped = new net.minecraft.world.entity.item.ItemEntity(dropLevel, player.getX(), player.getY(), player.getZ(), item);
                    dropLevel.addFreshEntity(dropped);
                }
            }
            player.sendSystemMessage(Component.literal("Thoridrop: personal rewards delivered!"));
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
        String[] mobIds = switch (session.rarity()) {
            case COMMON -> switch (wave) {
                case 1 -> new String[]{"zombie"};
                case 2 -> new String[]{"zombie", "skeleton"};
                case 3 -> new String[]{"skeleton", "spider"};
                case 4 -> new String[]{"husk", "stray"};
                default -> new String[]{"ravager"};
            };
            case RARE -> switch (wave) {
                case 1 -> new String[]{"husk", "skeleton"};
                case 2 -> new String[]{"pillager", "stray"};
                case 3 -> new String[]{"vindicator", "witch"};
                case 4 -> new String[]{"vindicator", "pillager"};
                default -> new String[]{"ravager", "vindicator"};
            };
            case LEGENDARY -> switch (wave) {
                case 1 -> new String[]{"vindicator", "pillager"};
                case 2 -> new String[]{"witch", "vindicator"};
                case 3 -> new String[]{"witch", "vindicator"};
                case 4 -> new String[]{"piglin_brute", "evoker"};
                default -> new String[]{"ravager", "vindicator"};
            };
        };
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            int x = dropPosition.getX() + (int)Math.round(Math.cos(angle) * 9);
            int z = dropPosition.getZ() + (int)Math.round(Math.sin(angle) * 9);
            BlockPos spawn = dropLevel.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, dropPosition.getY(), z));
            if (!dropLevel.getBlockState(spawn).isAir() || !dropLevel.getBlockState(spawn.above()).isAir()) continue;
            String mobId = mobIds[i % mobIds.length];
            net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse("minecraft:" + mobId));
            if (type == null) continue;
            net.minecraft.world.entity.Entity entity = type.create(dropLevel, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (entity == null) continue;
            entity.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            if (wave == 5) entity.setCustomName(Component.literal("THORIDROP BOSS"));
            if (dropLevel.addFreshEntity(entity)) waveMobs.add(entity.getUUID());
        }
        waveTotal = waveMobs.size();
        session.setWaveEnemyCount(waveTotal);
        broadcast("THORIDROP - Wave " + wave + "/5: " + waveMobs.size() + " enemies!");
        if (waveMobs.isEmpty()) broadcast("No safe enemy spawn locations. Use /thoridrop stop and try open terrain.");
    }

    private void setCrateOpeningStage(int stage) {
        if (dropLevel == null || dropPosition == null || session == null) return;
        Block block = switch (session.rarity()) {
            case COMMON -> stage == 1 ? DropBlocks.COMMON_OPENING1.get()
                : stage == 2 ? DropBlocks.COMMON_OPENING2.get() : DropBlocks.COMMON_OPEN.get();
            case RARE -> stage == 1 ? DropBlocks.RARE_OPENING1.get()
                : stage == 2 ? DropBlocks.RARE_OPENING2.get() : DropBlocks.RARE_OPEN.get();
            case LEGENDARY -> stage == 1 ? DropBlocks.LEGENDARY_OPENING1.get()
                : stage == 2 ? DropBlocks.LEGENDARY_OPENING2.get() : DropBlocks.LEGENDARY_OPEN.get();
        };
        dropLevel.setBlockAndUpdate(dropPosition, block.defaultBlockState());
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (session == null || dropPosition == null || dropLevel == null) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level() != dropLevel || !event.getPos().equals(dropPosition)) return;
        if (session.phase() == DropSession.Phase.REWARDS) {
            if (!chestOpen) {
                player.sendSystemMessage(Component.literal("El cofre se está abriendo..."));
                event.setCanceled(true);
                return;
            }
            if (!participants.contains(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Thoridrop: no participaste en el combate."));
            } else if (claimedRewards.add(player.getUUID())) {
                grantRewards(player);
                if (claimedRewards.containsAll(participants)) {
                    dropLevel.removeBlock(dropPosition, false);
                    broadcast("Todos los participantes han recogido sus recompensas. El suministro ha desaparecido.");
                    session.finish();
                    session = null;
                    dropLevel = null;
                    dropPosition = null;
                }
            } else {
                player.sendSystemMessage(Component.literal("Thoridrop: ya recogiste tu recompensa."));
            }
            event.setCanceled(true);
            return;
        }
        if (session.activate()) {
            waveDelay = 40;
            broadcast("Thoridrop activated! Wave 1 begins in 2 seconds.");
        }
        // Custom supply crates never expose a vanilla chest inventory.
        event.setCanceled(true);
    }
}
