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
    private long automaticTicks;
    private static final long AUTO_INTERVAL_TICKS = 20L * 60 * 60; // 60 minutes
    private static final int[] AUTO_RARITY_WEIGHTS = {6, 3, 1}; // Normal / Mythic / Legendary
    private int arrivalTicks;
    private Block arrivingCrate;
    private net.minecraft.server.level.ServerBossEvent eventBar;
    private final java.util.Map<UUID, net.minecraft.server.level.ServerBossEvent> locatorBars = new java.util.HashMap<>();
    private final java.util.Set<UUID> waveMobs = new java.util.HashSet<>();
    private final java.util.Map<UUID, java.util.List<net.minecraft.world.item.ItemStack>> deathInventories = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> deathExperience = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> missingMobTicks = new java.util.HashMap<>();
    private final java.util.Set<UUID> respawnPending = new java.util.HashSet<>();
    private int guardianAttackTicks;
    private BlockPos beaconLightPosition;
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
                    arrivalTicks = 0;
                    removeBeaconLight();
                    clearHud();
                    removeBeaconLight();
                    clearHud();
                    session.finish();
                    session = null;
                    dropLevel = null;
                    dropPosition = null;
                    arrivalTicks = 0;
                    context.getSource().sendSuccess(() -> Component.literal("Thoridrop event stopped."), true);
                    return 1;
                }))
        );
    }

    private static net.minecraft.ChatFormatting rarityColor(DropSession.Rarity rarity) {
        return switch (rarity) {
            case COMMON -> net.minecraft.ChatFormatting.AQUA;
            case RARE -> net.minecraft.ChatFormatting.LIGHT_PURPLE;
            case LEGENDARY -> net.minecraft.ChatFormatting.GOLD;
        };
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
        // Keep the destination clear while the supply crate descends.
        arrivingCrate = crate;
        arrivalTicks = 55;
        dropLevel = level;
        dropPosition = pos.immutable();
        session = new DropSession(UUID.randomUUID(), rarity, 5);
        clearHud();
        eventBar = new net.minecraft.server.level.ServerBossEvent(
            UUID.randomUUID(), Component.literal("✦ THORIDROP  •  " + rarityName(rarity)),
            switch (rarity) {
                case COMMON -> net.minecraft.world.BossEvent.BossBarColor.BLUE;
                case RARE -> net.minecraft.world.BossEvent.BossBarColor.PURPLE;
                case LEGENDARY -> net.minecraft.world.BossEvent.BossBarColor.RED;
            },
            net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
        eventBar.setProgress(1.0f);
        claimedRewards.clear();
        participants.clear();
        waveTotal = 0;
        guardianAttackTicks = 0;
        beaconLightPosition = null;
        chestOpen = false;
        chestOpeningTicks = -1;
        player.sendSystemMessage(Component.literal("¡Suministro detectado! Mira al cielo: aterriza en unos segundos."));
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
        if (session == null) {
            // Defensive cleanup of stale boss bars even after an interrupted event.
            if (eventBar != null || !locatorBars.isEmpty()) clearHud();
            if (++automaticTicks >= AUTO_INTERVAL_TICKS) {
                automaticTicks = 0;
                var server = event.getServer();
                var players = server.getPlayerList().getPlayers();
                if (!players.isEmpty()) {
                    ServerPlayer target = players.get(net.minecraft.util.RandomSource.create().nextInt(players.size()));
                    DropSession.Rarity rarity = switch (net.minecraft.util.RandomSource.create().nextInt(10)) {
                        case 0 -> DropSession.Rarity.LEGENDARY;
                        case 1, 2, 3 -> DropSession.Rarity.RARE;
                        default -> DropSession.Rarity.COMMON;
                    };
                    createDrop(target, rarity);
                    if (session != null) {
                        broadcast("¡Suministro automático " + rarityName(rarity) + " detectado! Usa el localizador superior.");
                        for (ServerPlayer online : server.getPlayerList().getPlayers()) online.level().playSound(null, online.blockPosition(), net.minecraft.sounds.SoundEvents.RAID_HORN.value(), net.minecraft.sounds.SoundSource.MASTER, 0.8f, 1.0f);
                    }
                }
            }
            return;
        }
        if (dropLevel == null || dropPosition == null) return;
        if (arrivalTicks > 0) {
            tickArrival();
            return;
        }
        if (session.phase() == DropSession.Phase.COMBAT) {
            if (session.wave() == 5 && session.rarity() == DropSession.Rarity.LEGENDARY && waveDelay == 0 && !waveMobs.isEmpty()) tickGuardianAbility();
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
                    UUID mobId = it.next();
                    net.minecraft.world.entity.Entity entity = dropLevel.getEntity(mobId);
                    boolean defeated = entity instanceof net.minecraft.world.entity.LivingEntity living && !living.isAlive();
                    if (entity == null) {
                        // Do not advance waves when the chunk is unloaded or the player dies.
                        // After 30 seconds with an active participant nearby, recover a missing mob.
                        boolean nearby = dropLevel.players().stream().anyMatch(p ->
                            p.distanceToSqr(dropPosition.getX() + 0.5, dropPosition.getY() + 0.5,
                                dropPosition.getZ() + 0.5) < 32 * 32);
                        if (nearby && missingMobTicks.merge(mobId, 1, Integer::sum) >= 600) defeated = true;
                    } else missingMobTicks.remove(mobId);
                    if (defeated) {
                        missingMobTicks.remove(mobId);
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
        if (++locatorTicks % 5 != 0) return;
        if (session.phase() == DropSession.Phase.AVAILABLE || session.phase() == DropSession.Phase.COMBAT) {
            ensureBeaconLight();
            double bx = dropPosition.getX() + 0.5, by = dropPosition.getY() + 1.0, bz = dropPosition.getZ() + 0.5;
            var beaconParticle = switch (session.rarity()) {
                case COMMON -> net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME;
                case RARE -> net.minecraft.core.particles.ParticleTypes.PORTAL;
                case LEGENDARY -> net.minecraft.core.particles.ParticleTypes.FLAME;
            };
            for (int height = 0; height < 32; height += 2) {
                dropLevel.sendParticles(beaconParticle, bx, by + height, bz, 5, 0.12, 0.25, 0.12, 0.005);
                dropLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, bx, by + height, bz, 1, 0.06, 0.20, 0.06, 0.001);
            }
        }
        if (session.phase() == DropSession.Phase.AVAILABLE) {
            if (eventBar != null) eventBar.removeAllPlayers();
            for (ServerPlayer player : dropLevel.players()) {
                net.minecraft.server.level.ServerBossEvent bar = locatorBars.computeIfAbsent(
                    player.getUUID(), id -> new net.minecraft.server.level.ServerBossEvent(
                        UUID.randomUUID(), Component.literal("THORIDROP  •  LOCALIZADOR"),
                        switch (session.rarity()) {
                            case COMMON -> net.minecraft.world.BossEvent.BossBarColor.BLUE;
                            case RARE -> net.minecraft.world.BossEvent.BossBarColor.PURPLE;
                            case LEGENDARY -> net.minecraft.world.BossEvent.BossBarColor.RED;
                        }, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS));
                if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
                double dx = dropPosition.getX() + 0.5 - player.getX();
                double dz = dropPosition.getZ() + 0.5 - player.getZ();
                int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
                double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
                double delta = net.minecraft.util.Mth.wrapDegrees(targetYaw - player.getYRot());
                int slot = (int) Math.round(4.0 + Math.max(-1.0, Math.min(1.0, delta / 90.0)) * 4.0);
                StringBuilder compass = new StringBuilder();
                for (int i = 0; i < 9; i++) compass.append(i == slot ? "▣" : "─");
                boolean aligned = Math.abs(delta) < 12.0;
                bar.setName(Component.literal("✦ THORIDROP  ").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD)
                    .append(Component.literal(rarityName(session.rarity()) + "  ").withStyle(rarityColor(session.rarity()), net.minecraft.ChatFormatting.BOLD))
                    .append(Component.literal(compass.toString()).withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD))
                    .append(Component.literal("  " + distance + "m  ").withStyle(net.minecraft.ChatFormatting.YELLOW))
                    .append(Component.literal(aligned ? "◆ OBJETIVO ENFRENTE ◆" : delta > 0 ? "◀ GIRA A LA DERECHA" : "GIRA A LA IZQUIERDA ▶")
                        .withStyle(aligned ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD))
                    );
                bar.setProgress(Math.max(0.05f, Math.min(1.0f, 1.0f - distance / 250.0f)));
            }
            for (UUID id : java.util.List.copyOf(locatorBars.keySet())) {
                boolean present = dropLevel.players().stream().anyMatch(p -> p.getUUID().equals(id));
                if (!present) {
                    locatorBars.get(id).removeAllPlayers();
                    locatorBars.remove(id);
                }
            }
            return;
        }
        for (var bar : locatorBars.values()) bar.removeAllPlayers();
        locatorBars.clear();
        if (eventBar == null) return;
        for (ServerPlayer player : dropLevel.players()) {
            if (!eventBar.getPlayers().contains(player)) eventBar.addPlayer(player);
        }
        for (ServerPlayer player : java.util.List.copyOf(eventBar.getPlayers())) {
            if (player.level() != dropLevel) eventBar.removePlayer(player);
        }
        String state = switch (session.phase()) {
            case AVAILABLE -> "BUSCANDO SUMINISTRO";
            case COMBAT -> (session.wave() == 5 ? "GUARDIÁN FINAL  •  " : "") + "OLEADA " + session.wave() + "/5  •  " + (waveDelay > 0 ? "SIGUIENTE EN " + ((waveDelay + 19) / 20) + "s  •  " : "") + "ELIMINADOS "
                + Math.max(0, waveTotal - waveMobs.size()) + "/" + waveTotal;
            case REWARDS -> "RECOMPENSAS DESBLOQUEADAS  •  ABRE EL COFRE";
            case FINISHED -> "EVENTO FINALIZADO";
        };
        eventBar.setName(Component.literal("✦ THORIDROP  ").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD)
            .append(Component.literal(rarityName(session.rarity())).withStyle(rarityColor(session.rarity()), net.minecraft.ChatFormatting.BOLD))
            .append(Component.literal("  ◆  ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
            .append(Component.literal(state).withStyle(session.phase() == DropSession.Phase.REWARDS
                ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD))
            );
        if (session.phase() == DropSession.Phase.COMBAT && session.wave() == 5 && !waveMobs.isEmpty()) {
            net.minecraft.world.entity.Entity guardian = dropLevel.getEntity(waveMobs.iterator().next());
            if (guardian instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                hideNicktaleBossBars(living);
                eventBar.setProgress(Math.max(0.0f, Math.min(1.0f, living.getHealth() / living.getMaxHealth())));
                eventBar.setName(Component.literal("✦ THORIDROP  " + rarityName(session.rarity())
                    + "  ◆  GUARDIÁN " + Math.round(living.getHealth()) + "/" + Math.round(living.getMaxHealth()) + " HP")
                    .withStyle(rarityColor(session.rarity()), net.minecraft.ChatFormatting.BOLD));
                return;
            }
        }
        eventBar.setProgress(session.phase() == DropSession.Phase.COMBAT && waveTotal > 0
            ? Math.max(0.0f, Math.min(1.0f, (float) waveMobs.size() / waveTotal)) : 1.0f);
    }

    private void tickArrival() {
        arrivalTicks--;
        double x = dropPosition.getX() + 0.5;
        double y = dropPosition.getY() + 0.5;
        double z = dropPosition.getZ() + 0.5;
        double altitude = y + arrivalTicks * 0.6;
        // Dense moving particle shell makes the falling crate visible from below.
        dropLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
            x, altitude, z, 12, 0.45, 0.35, 0.45, 0.015);
        dropLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
            x, altitude, z, 6, 0.3, 0.25, 0.3, 0.025);
        if (arrivalTicks % 10 == 0) {
            dropLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                x, altitude + 1, z, 16, 0.65, 0.2, 0.65, 0.025);
        }
        if (arrivalTicks == 0) {
            if (dropLevel.getBlockState(dropPosition).isAir()) {
                dropLevel.setBlockAndUpdate(dropPosition, arrivingCrate.defaultBlockState());
                dropLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    x, y, z, 75, 1.1, 0.45, 1.1, 0.12);
                dropLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    x, y, z, 55, 1.0, 0.3, 1.0, 0.06);
                dropLevel.playSound(null, dropPosition, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.7f);
                broadcast("¡THORIDROP HA ATERRIZADO! Acércate y activa el suministro.");
            } else {
                broadcast("Thoridrop: aterrizaje cancelado, el destino está bloqueado.");
                removeBeaconLight();
                clearHud();
                session = null;
                dropLevel = null;
                dropPosition = null;
            }
        }
    }

    /** A non-destructive local light source for the beacon; only occupies existing air. */
    private void ensureBeaconLight() {
        if (beaconLightPosition != null || dropLevel == null || dropPosition == null) return;
        BlockPos candidate = dropPosition.above(3);
        if (dropLevel.getBlockState(candidate).isAir()) {
            dropLevel.setBlockAndUpdate(candidate, Blocks.LIGHT.defaultBlockState());
            beaconLightPosition = candidate.immutable();
        }
    }

    private void removeBeaconLight() {
        if (beaconLightPosition != null && dropLevel != null
                && dropLevel.getBlockState(beaconLightPosition).is(Blocks.LIGHT)) {
            dropLevel.removeBlock(beaconLightPosition, false);
        }
        beaconLightPosition = null;
    }

    /** Distinctive fifth-wave guardian ability: a periodic telegraphed area attack. */
    private void tickGuardianAbility() {
        if (++guardianAttackTicks % 100 != 0) return;
        for (UUID id : waveMobs) {
            net.minecraft.world.entity.Entity entity = dropLevel.getEntity(id);
            if (!(entity instanceof net.minecraft.world.entity.Mob guardian) || !guardian.isAlive()) continue;
            double x = guardian.getX(), y = guardian.getY() + 1, z = guardian.getZ();
            var particle = session.rarity() == DropSession.Rarity.LEGENDARY
                ? net.minecraft.core.particles.ParticleTypes.FLAME
                : net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME;
            dropLevel.sendParticles(particle, x, y, z, 55, 2.5, 0.4, 2.5, 0.04);
            for (ServerPlayer player : dropLevel.players()) {
                if (player.distanceToSqr(x, y, z) > 7 * 7 || player.isCreative() || player.isSpectator()) continue;
                int duration = session.rarity() == DropSession.Rarity.LEGENDARY ? 100 : 60;
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOWNESS, duration, 0));
                if (session.rarity() != DropSession.Rarity.COMMON) {
                    player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.WEAKNESS, duration, 0));
                }
                player.sendSystemMessage(Component.literal("¡El Guardián libera una onda de energía!"));
            }
        }
    }

    // Nicktale owns its boss bars. Hide only the bar fields of the active Thoridrop guardian,
    // leaving all other dungeon encounters and their bars untouched.
    private void hideNicktaleBossBars(net.minecraft.world.entity.LivingEntity guardian) {
        Class<?> current = guardian.getClass();
        while (current != null && current != Object.class) {
            for (java.lang.reflect.Field field : current.getDeclaredFields()) {
                if (!net.minecraft.server.level.ServerBossEvent.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(guardian);
                    if (value instanceof net.minecraft.server.level.ServerBossEvent bar) bar.setVisible(false);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    LOGGER.debug("Cannot hide external boss bar for {}", guardian.getType(), ex);
                }
            }
            current = current.getSuperclass();
        }
    }

    private void clearHud() {
        if (eventBar != null) { eventBar.removeAllPlayers(); eventBar = null; }
        for (var bar : locatorBars.values()) bar.removeAllPlayers();
        locatorBars.clear();
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
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 2),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, 8 + random.nextInt(8))};
                case LEGENDARY -> new net.minecraft.world.item.ItemStack[] {
                    new net.minecraft.world.item.ItemStack(diamondArmor[firstArmorIndex]),
                    new net.minecraft.world.item.ItemStack(diamondArmor[(firstArmorIndex + 1 + random.nextInt(diamondArmor.length - 1)) % diamondArmor.length]),
                    new net.minecraft.world.item.ItemStack(diamondGear[random.nextInt(diamondGear.length)]),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 5 + random.nextInt(4)),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, 16),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 4),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, 1),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, 16 + random.nextInt(16))};
            };
            for (net.minecraft.world.item.ItemStack item : loot) {
                if (!player.getInventory().add(item)) {
                    net.minecraft.world.entity.item.ItemEntity dropped = new net.minecraft.world.entity.item.ItemEntity(dropLevel, player.getX(), player.getY(), player.getZ(), item);
                    dropLevel.addFreshEntity(dropped);
                }
            }
            player.sendSystemMessage(Component.literal("Thoridrop: personal rewards delivered!"));
            player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
        }
    }

    private void broadcast(String message) {
        for (ServerPlayer p : dropLevel.players()) p.sendSystemMessage(Component.literal(message));
    }

    private void spawnWave() {
        if (session == null || session.phase() != DropSession.Phase.COMBAT) return;
        waveMobs.clear();
        guardianAttackTicks = 0;
        missingMobTicks.clear();
        int wave = session.wave();
        int extra = session.rarity() == DropSession.Rarity.LEGENDARY ? 3
                  : session.rarity() == DropSession.Rarity.RARE ? 1 : 0;
        int count = wave == 5 ? 1 : 2 + wave + extra;
        String[] mobIds = switch (session.rarity()) {
            case COMMON -> switch (wave) {
                case 1 -> new String[]{"zombie"};
                case 2 -> new String[]{"zombie", "skeleton"};
                case 3 -> new String[]{"skeleton", "spider"};
                case 4 -> new String[]{"husk", "stray", "skeleton"};
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
                case 3 -> new String[]{"witch", "vindicator", "pillager"};
                case 4 -> new String[]{"pillager", "evoker", "vindicator", "skeleton"};
                default -> new String[]{"ravager", "vindicator"};
            };
        };
        // Draw one random fifth-wave opponent from the configured Nicktale rarity pool.
        // All entity IDs are namespaced, so vanilla and Nicktale mobs can share this path.
        if (wave == 5) {
            String[] pool = switch (session.rarity()) {
                case COMMON -> new String[]{"mutant_zombie", "violet_flying_witch",
                    "fortress_guard", "vix_eclipse_fox"};
                case RARE -> new String[]{"abyssal_shagbeast", "gravebreaker_brute", "don_cascabel", "blueflame_elder", "buried_axe_knight", "voidborne_lich"};
                case LEGENDARY -> new String[]{"black_knight", "cinderhorn_demon",
                    "runebound_colossus", "armored_skeleton", "widow_of_the_well",
                    "sultan_eternal_sands", "eclipse_colossus", "cerberus"};
            };
            java.util.List<String> available = new java.util.ArrayList<>();
            for (String id : pool) {
                var key = net.minecraft.resources.Identifier.fromNamespaceAndPath("nicktaleepicdungeons", id);
                if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(key)) available.add(id);
            }
            if (!available.isEmpty()) {
                String chosen = available.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(available.size()));
                mobIds = new String[]{"nicktaleepicdungeons:" + chosen};
                broadcast("¡Jefe final seleccionado: " + chosen.replace('_', ' ') + "!");
            } else {
                mobIds = new String[]{"minecraft:ravager"};
                broadcast("No hay jefes de Nicktale registrados; usando guardián alternativo.");
            }
        } else {
            for (int j = 0; j < mobIds.length; j++) mobIds[j] = "minecraft:" + mobIds[j];
        }
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            int x = dropPosition.getX() + (int)Math.round(Math.cos(angle) * 9);
            int z = dropPosition.getZ() + (int)Math.round(Math.sin(angle) * 9);
            BlockPos spawn = dropLevel.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, dropPosition.getY(), z));
            if (!dropLevel.getBlockState(spawn).isAir() || !dropLevel.getBlockState(spawn.above()).isAir()) {
                spawn = dropPosition.above(3);
            }
            String mobId = mobIds[i % mobIds.length];
            net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse(mobId));
            if (type == null) type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse("minecraft:ravager"));
            net.minecraft.world.entity.Entity entity = type.create(dropLevel, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (entity == null) entity = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse("minecraft:ravager")).create(dropLevel, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (entity == null) continue;
            entity.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            if (wave < 5 && entity instanceof net.minecraft.world.entity.Mob ranged) {
                if (mobId.equals("minecraft:skeleton") || mobId.equals("minecraft:stray")) {
                    ranged.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
                } else if (mobId.equals("minecraft:pillager")) {
                    ranged.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CROSSBOW));
                }
            }
            if (wave == 5 && entity instanceof net.minecraft.world.entity.LivingEntity boss) {
                boss.setCustomName(Component.literal("✦ GUARDIÁN " + rarityName(session.rarity()) + " ✦").withStyle(rarityColor(session.rarity()), net.minecraft.ChatFormatting.BOLD));
                boss.setCustomNameVisible(true);
                // Apply health only to the entity spawned for this Thoridrop event.
                if (boss instanceof net.minecraft.world.entity.Mob mobBoss) {
                    var maxHealth = mobBoss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                    if (maxHealth != null) {
                        double targetHp = switch (session.rarity()) {
                            case COMMON -> 100.0;
                            case RARE -> 250.0;
                            case LEGENDARY -> 450.0;
                        };
                        maxHealth.setBaseValue(targetHp);
                        mobBoss.setHealth((float) targetHp);
                    }
                }
                if (session.rarity() == DropSession.Rarity.LEGENDARY) boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 20 * 60 * 10, 1));
                if (session.rarity() == DropSession.Rarity.LEGENDARY) boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, 20 * 60 * 10, 1));
                if (session.rarity() == DropSession.Rarity.LEGENDARY) boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 20 * 60 * 10, 0));
                boss.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 20 * 60 * 10, 0));
            }
            if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setPersistenceRequired();
            if (dropLevel.addFreshEntity(entity)) waveMobs.add(entity.getUUID());
        }
        if (waveMobs.isEmpty()) {
            net.minecraft.world.entity.Entity fallback = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse("minecraft:ravager")).create(dropLevel, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (fallback != null) {
                fallback.setPos(dropPosition.getX() + 0.5, dropPosition.getY() + 2, dropPosition.getZ() + 0.5);
                if (fallback instanceof net.minecraft.world.entity.Mob mob) mob.setPersistenceRequired();
                if (dropLevel.addFreshEntity(fallback)) waveMobs.add(fallback.getUUID());
            }
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

    private boolean fightingNearDrop(ServerPlayer player) {
        return session != null && session.phase() == DropSession.Phase.COMBAT
            && dropLevel != null && dropPosition != null && player.level() == dropLevel
            && player.distanceToSqr(dropPosition.getX() + 0.5, dropPosition.getY() + 0.5,
                dropPosition.getZ() + 0.5) <= 32 * 32;
    }

    @SubscribeEvent
    public void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !fightingNearDrop(player)) return;
        UUID id = player.getUUID();
        participants.add(id);
        java.util.List<net.minecraft.world.item.ItemStack> saved = new java.util.ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            saved.add(player.getInventory().getItem(i).copy());
        }
        deathInventories.put(id, saved);
        respawnPending.add(id);
        deathExperience.put(id, player.totalExperience);
        player.sendSystemMessage(Component.literal("Thoridrop: conservarás tu inventario y reaparecerás junto al suministro."));
    }

    @SubscribeEvent
    public void onPlayerDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && deathInventories.containsKey(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        java.util.List<net.minecraft.world.item.ItemStack> saved = deathInventories.remove(player.getUUID());
        if (saved == null) return;
        for (int i = 0; i < saved.size() && i < player.getInventory().getContainerSize(); i++) {
            player.getInventory().setItem(i, saved.get(i));
        }
        Integer experience = deathExperience.remove(player.getUUID());
        if (experience != null) player.giveExperiencePoints(experience);
        if (respawnPending.remove(player.getUUID()) && session != null && dropLevel != null
                && dropPosition != null && session.phase() == DropSession.Phase.COMBAT) {
            // The crate position is authoritative: respawn directly on top of it.
            dropLevel.getChunkAt(dropPosition);
            BlockPos feet = dropPosition.above();
            BlockPos head = feet.above();
            if (isSupplyCrate(dropLevel.getBlockState(dropPosition).getBlock())
                    && dropLevel.getBlockState(feet).getCollisionShape(dropLevel, feet).isEmpty()
                    && dropLevel.getBlockState(head).getCollisionShape(dropLevel, head).isEmpty()) {
                player.teleportTo(dropLevel, dropPosition.getX() + 0.5, dropPosition.getY() + 1.05,
                    dropPosition.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
                player.sendSystemMessage(Component.literal("Thoridrop: has reaparecido sobre el suministro con tu equipo."));
            } else {
                player.sendSystemMessage(Component.literal("Thoridrop: el espacio sobre el cofre está obstruido; se mantiene tu respawn normal."));
            }
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (session == null || dropPosition == null || dropLevel == null || arrivalTicks > 0) return;
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
                    removeBeaconLight();
                    broadcast("Todos los participantes han recogido sus recompensas. El suministro ha desaparecido.");
                    clearHud();
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
