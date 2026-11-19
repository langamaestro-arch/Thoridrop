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
    private static Thoridrop INSTANCE;
    public static Thoridrop instance() {return INSTANCE;}

    private ServerLevel dropLevel;
    private BlockPos dropPosition;
    private DropSession session;
    private int locatorTicks;
    private long automaticTicks;
    private static final long AUTO_INTERVAL_TICKS = 20L * 60 * 60; // 60 minutes
    private static final int[] AUTO_RARITY_WEIGHTS = {6, 3, 1}; // Normal / Mythic / Legendary
    private int arrivalTicks;
    private boolean configLoaded;
    private boolean multiplayerEnabled = true;
    private boolean preserveInventoryOnEventDeath = true;
    private int maxChallengeRespawns = 5;
    private final DropRespawnBudget respawnBudget = new DropRespawnBudget(5);
    private int participationRadius = 24;
    private int rewardClaimTimeoutTicks = 20 * 600;
    private int rewardTicks;
    private int mobMultiplierPercent = 100;
    private int extraMobsPerPlayer = 2;
    private int maxAdditionalMobs = 12;
    private int flawlessDiamondBonus = 2;
    private int randomModifierChance = 25;
    private int modifierType; // 0 none, 1 swarm, 2 haste, 3 bonus
    private final java.util.Set<UUID> eventDeaths = new java.util.HashSet<>();
    private final java.util.Map<UUID,Integer> playerOutOfRangeTicks = new java.util.HashMap<>();
    private int abandonSeconds = 180;
    private int combatTicks;
    private boolean checkedRecovery;
    private int emptyWaveRetries;
    private final java.util.Set<UUID> staleRecoveredMobs=new java.util.HashSet<>();
    private int staleCleanupTicks;
    private int timedChallengeSeconds = 480;
    private int shopPointsNormal = 20, shopPointsMythic = 55, shopPointsLegendary = 130;
    private int cooperativeBonusPoints = 15;
    private int specialBonusPoints = 30;
    private int exclusiveGearChanceMythic = 20, exclusiveGearChanceLegendary = 45;
    private final java.util.Map<UUID,Integer> eventHits = new java.util.HashMap<>();
    private final java.util.Map<UUID,Integer> eventKills = new java.util.HashMap<>();
    private boolean timedChallengeSucceeded;
    private int teamSizeAtVictory;
    private long intervalTicks = 72000L;
    private int spawnDistanceBlocks = 160;
    private int arrivalTimeoutTicks = 6000;
    private int availableTicks;
    private boolean automaticDrop;
    private int specialEventChance = 10;
    private boolean specialEvent;
    // Event variant 0 normal, 1 siege, 2 veteran elites, 3 cursed wave.
    private int extraordinaryVariant;
    private int eliteSpawnChancePercent = 22;
    private int extraordinaryExtraMobs = 4;
    private final java.util.Set<UUID> eliteWaveMobs = new java.util.HashSet<>();
    private int eliteAbilityTicks;
    private String waveBiomeLabel = "Llanura";
    private boolean globalAnnouncements = true, arrivalSounds = true, arrivalParticles = true;
    private final java.util.Set<UUID> completedStats = new java.util.HashSet<>();
    private String guardianDisplayName = "GUARDIÁN";
    private int normalWeight = 60, mythicWeight = 30, legendaryWeight = 10;

    private void loadConfig() {
        java.nio.file.Path path = java.nio.file.Paths.get("config", "thoridrop.properties");
        java.util.Properties props = new java.util.Properties();
        props.setProperty("frequency_minutes", "60");
        props.setProperty("spawn_distance_blocks", "160");
        props.setProperty("arrival_timeout_seconds", "300");
        props.setProperty("normal_weight", "60");
        props.setProperty("mythic_weight", "30");
        props.setProperty("legendary_weight", "10");
        props.setProperty("global_announcements", "true");
        props.setProperty("arrival_sounds", "true");
        props.setProperty("arrival_particles", "true");
        props.setProperty("special_event_chance_percent", "10");
        props.setProperty("elite_enemy_chance_percent", "22");
        props.setProperty("extraordinary_extra_mobs", "4");
        props.setProperty("multiplayer_enabled", "true");
        props.setProperty("participation_radius_blocks", "24");
        props.setProperty("reward_claim_timeout_seconds", "600");
        props.setProperty("keep_inventory_on_event_death", "true");
        props.setProperty("max_event_respawns", "5");
        props.setProperty("mob_count_percent", "100");
        props.setProperty("extra_mobs_per_additional_player", "2");
        props.setProperty("max_extra_mobs", "12");
        props.setProperty("flawless_bonus_diamonds", "2");
        props.setProperty("random_modifier_chance_percent", "25");
        props.setProperty("abandon_timeout_seconds", "180");
        props.setProperty("timed_challenge_seconds", "480");
        props.setProperty("points_normal", "20");
        props.setProperty("points_mythic", "55");
        props.setProperty("points_legendary", "130");
        props.setProperty("points_cooperative_bonus", "15");
        props.setProperty("points_special_bonus", "30");
        props.setProperty("exclusive_gear_chance_mythic", "20");
        props.setProperty("exclusive_gear_chance_legendary", "45");
        try {
            java.nio.file.Files.createDirectories(path.getParent());
            if (java.nio.file.Files.exists(path)) {
                try (var input = java.nio.file.Files.newInputStream(path)) { props.load(input); }
            }
            intervalTicks = Math.max(1, Math.min(10080, Integer.parseInt(props.getProperty("frequency_minutes", "60")))) * 1200L;
            spawnDistanceBlocks = Math.max(32, Math.min(1024, Integer.parseInt(props.getProperty("spawn_distance_blocks", "160"))));
            arrivalTimeoutTicks = Math.max(30, Math.min(7200, Integer.parseInt(props.getProperty("arrival_timeout_seconds", "300")))) * 20;
            int n = Math.max(0, Math.min(10000, Integer.parseInt(props.getProperty("normal_weight", "60"))));
            int m = Math.max(0, Math.min(10000, Integer.parseInt(props.getProperty("mythic_weight", "30"))));
            int g = Math.max(0, Math.min(10000, Integer.parseInt(props.getProperty("legendary_weight", "10"))));
            if (n + m + g > 0) { normalWeight = n; mythicWeight = m; legendaryWeight = g; }
            globalAnnouncements = Boolean.parseBoolean(props.getProperty("global_announcements", "true"));
            arrivalSounds = Boolean.parseBoolean(props.getProperty("arrival_sounds", "true"));
            arrivalParticles = Boolean.parseBoolean(props.getProperty("arrival_particles", "true"));
            specialEventChance = Math.max(0, Math.min(100, Integer.parseInt(props.getProperty("special_event_chance_percent", "10"))));
            eliteSpawnChancePercent = Math.max(0,Math.min(75,Integer.parseInt(props.getProperty("elite_enemy_chance_percent","22"))));
            extraordinaryExtraMobs = Math.max(0,Math.min(12,Integer.parseInt(props.getProperty("extraordinary_extra_mobs","4"))));
            multiplayerEnabled = Boolean.parseBoolean(props.getProperty("multiplayer_enabled", "true"));
            preserveInventoryOnEventDeath = Boolean.parseBoolean(props.getProperty("keep_inventory_on_event_death", "true"));
            maxChallengeRespawns = Math.max(0,Math.min(100,Integer.parseInt(props.getProperty("max_event_respawns","5"))));
            respawnBudget.setLimit(maxChallengeRespawns);
            participationRadius = Math.max(8, Math.min(96, Integer.parseInt(props.getProperty("participation_radius_blocks", "24"))));
            rewardClaimTimeoutTicks = Math.max(30, Math.min(86400, Integer.parseInt(props.getProperty("reward_claim_timeout_seconds", "600")))) * 20;
            mobMultiplierPercent = Math.max(25, Math.min(300, Integer.parseInt(props.getProperty("mob_count_percent", "100"))));
            extraMobsPerPlayer = Math.max(0, Math.min(10, Integer.parseInt(props.getProperty("extra_mobs_per_additional_player", "2"))));
            maxAdditionalMobs = Math.max(0, Math.min(40, Integer.parseInt(props.getProperty("max_extra_mobs", "12"))));
            flawlessDiamondBonus = Math.max(0, Math.min(32, Integer.parseInt(props.getProperty("flawless_bonus_diamonds", "2"))));
            randomModifierChance = Math.max(0, Math.min(100, Integer.parseInt(props.getProperty("random_modifier_chance_percent", "25"))));
            abandonSeconds = Math.max(30, Math.min(3600, Integer.parseInt(props.getProperty("abandon_timeout_seconds", "180"))));
            timedChallengeSeconds = Math.max(60, Math.min(3600, Integer.parseInt(props.getProperty("timed_challenge_seconds","480"))));
            shopPointsNormal = Math.max(0, Math.min(10000, Integer.parseInt(props.getProperty("points_normal","20"))));
            shopPointsMythic = Math.max(0, Math.min(10000, Integer.parseInt(props.getProperty("points_mythic","55"))));
            shopPointsLegendary = Math.max(0, Math.min(10000, Integer.parseInt(props.getProperty("points_legendary","130"))));
            cooperativeBonusPoints = Math.max(0, Math.min(10000, Integer.parseInt(props.getProperty("points_cooperative_bonus","15"))));
            specialBonusPoints = Math.max(0, Math.min(10000, Integer.parseInt(props.getProperty("points_special_bonus","30"))));
            exclusiveGearChanceMythic = Math.max(0, Math.min(100, Integer.parseInt(props.getProperty("exclusive_gear_chance_mythic","20"))));
            exclusiveGearChanceLegendary = Math.max(0, Math.min(100, Integer.parseInt(props.getProperty("exclusive_gear_chance_legendary","45"))));
            try (var output = java.nio.file.Files.newOutputStream(path)) {
                props.store(output, "Thoridrop server settings; use /thoridrop reload to apply changes");
            }
        } catch (Exception ex) { LOGGER.error("Thoridrop config load failed", ex); }
        RewardManager.load();
        DropStatistics.load();
        DropPoints.load();
        ThoridropProgression.load();
        configLoaded = true;
    }

    private void expireUnclaimedDrop() {
        if (dropLevel == null || dropPosition == null || session == null) return;
        if (isSupplyCrate(dropLevel.getBlockState(dropPosition).getBlock())) dropLevel.removeBlock(dropPosition, false);
        for (UUID id : waveMobs) {
            var mob = dropLevel.getEntity(id);
            if (mob != null) mob.discard();
        }
        waveMobs.clear();
        removeBeaconLight();
        clearHud();
        broadcast("El suministro ha desaparecido: nadie llego a tiempo.");
        session.finish();
        session = null;
        dropLevel = null;
        dropPosition = null;
        arrivalTicks = 0;
    }

    private Block arrivingCrate;
    private net.minecraft.server.level.ServerBossEvent eventBar;
    private final java.util.Map<UUID, net.minecraft.server.level.ServerBossEvent> locatorBars = new java.util.HashMap<>();
    private final java.util.Set<UUID> waveMobs = new java.util.HashSet<>();
    private final java.util.Map<UUID, java.util.List<net.minecraft.world.item.ItemStack>> deathInventories = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> deathExperience = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> missingMobTicks = new java.util.HashMap<>();
    private final java.util.Set<UUID> respawnPending = new java.util.HashSet<>();
    private final java.util.Map<UUID, Integer> respawnTeleportQueue = new java.util.HashMap<>();
    private int guardianAttackTicks;
    private int towerCheckTicks;
    private int towerReinforcements;
    private BlockPos beaconLightPosition;
    private int waveDelay;
    private int waveTotal;
    private boolean chestOpen;
    private int chestOpeningTicks;
    private final java.util.Set<UUID> participants = new java.util.HashSet<>();
    private final java.util.Set<UUID> claimedRewards = new java.util.HashSet<>();

    public Thoridrop(IEventBus modBus) {
        INSTANCE=this;
        modBus.addListener(ThoridropMenuNetwork::register);
        DropBlocks.register(modBus);
        DropItems.register(modBus);
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("Thoridrop initialized");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("thoridrop")
            .then(Commands.literal("reload")
                .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN))
                .executes(context -> {
                    loadConfig();
                    context.getSource().sendSuccess(() -> Component.literal("Thoridrop config reloaded."), true);
                    return 1;
                }))
            .then(Commands.literal("stats")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal(DropStatistics.summary(player.getUUID())));
                    return 1;
                }))
            .then(Commands.literal("ranking")
                .executes(context -> { context.getSource().sendSuccess(() -> Component.literal(DropStatistics.leaderboard("wins", 10)), false); return 1; })
                .then(Commands.literal("legendario").executes(context -> { context.getSource().sendSuccess(() -> Component.literal(DropStatistics.leaderboard("wins_legendary", 10)), false); return 1; }))
                .then(Commands.literal("sinmorir").executes(context -> { context.getSource().sendSuccess(() -> Component.literal(DropStatistics.leaderboard("flawless", 10)), false); return 1; })))
            .then(Commands.literal("records")
                .executes(context -> {
                    ServerPlayer player=context.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal(DropStatistics.detailed(player.getUUID())));
                    return 1;
                }))
            .then(Commands.literal("logros")
                .executes(context -> {
                    ServerPlayer player=context.getSource().getPlayerOrException();
                    int wins=DropStatistics.value(player.getUUID(),"wins");
                    int flawless=DropStatistics.value(player.getUUID(),"flawless");
                    int legendary=DropStatistics.value(player.getUUID(),"wins_legendary");
                    int teams=DropStatistics.value(player.getUUID(),"team_wins");
                    player.sendSystemMessage(Component.literal("§6✦ LOGROS THORIDROP§r | Desafíos: "+wins+"/10, /25, /50, /100"
                        +" | Legendarios: "+legendary+"/1 | Sin morir: "+flawless+"/1 | Cooperativos: "+teams+"/1"));
                    return 1;
                }))
            .then(Commands.literal("puntos")
                .executes(context -> {
                    ServerPlayer player=context.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("§6✦ THORIDROP§r Saldo: "+DropPoints.balance(player.getUUID())+" puntos."));
                    return 1;
                }))
            .then(Commands.literal("tienda")
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("§6Tienda Thoridrop§r | hierro: 60 | diamantes: 240 | manzanas: 340 | arma: 1000. Compra: /thoridrop tienda comprar <objeto>"), false);
                    return 1;
                })
                .then(Commands.literal("comprar")
                    .then(Commands.literal("hierro").executes(context -> buyShopItem(context.getSource().getPlayerOrException(),"hierro",15)))
                    .then(Commands.literal("diamantes").executes(context -> buyShopItem(context.getSource().getPlayerOrException(),"diamantes",60)))
                    .then(Commands.literal("manzanas").executes(context -> buyShopItem(context.getSource().getPlayerOrException(),"manzanas",85)))
                    .then(Commands.literal("arma").executes(context -> buyShopItem(context.getSource().getPlayerOrException(),"arma",250)))))
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
                    ThoridropCheckpoint.clear();
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
        if (automaticDrop) {
            double angle = Math.toRadians(net.minecraft.util.RandomSource.create().nextInt(360));
            int x = player.blockPosition().getX() + (int) Math.round(Math.cos(angle) * spawnDistanceBlocks);
            int z = player.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * spawnDistanceBlocks);
            pos = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, player.blockPosition().getY(), z));
        }
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
        ThoridropCheckpoint.write(new ThoridropCheckpoint.Snapshot(
            session.eventId(),dropLevel.dimension().toString(),pos.getX(),pos.getY(),pos.getZ(),
            rarity,"AVAILABLE"));
        specialEvent = java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < specialEventChance;
        extraordinaryVariant = specialEvent ? 1+java.util.concurrent.ThreadLocalRandom.current().nextInt(3) : 0;
        modifierType = java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < randomModifierChance
            ? 1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(6) : 0;
        availableTicks = 0;
        guardianDisplayName = "GUARDIÁN";
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
        respawnBudget.reset();
        completedStats.clear();
        eventDeaths.clear();
        eventHits.clear();
        eventKills.clear();
        eliteWaveMobs.clear();
        eliteAbilityTicks=0;
        waveBiomeLabel="Llanura";
        emptyWaveRetries=0;
        combatTicks = 0;
        timedChallengeSucceeded = false;
        teamSizeAtVictory = 0;
        playerOutOfRangeTicks.clear();
        rewardTicks = 0;
        waveTotal = 0;
        guardianAttackTicks = 0;
        beaconLightPosition = null;
        chestOpen = false;
        chestOpeningTicks = -1;
        DropStatistics.remember(player.getUUID(), player.getGameProfile().name());
        DropStatistics.add(player.getUUID(), "events_started");
        if(modifierType != 0) broadcast("✦ Modificador del desafío: " + switch(modifierType) { case 1 -> "HORDA ENEMIGA"; case 2 -> "ENEMIGOS VELOCES";
            case 3 -> "BOTÍN EXTRA"; case 4 -> "ENEMIGOS ÉLITE";
            case 5 -> "CONTRARRELOJ"; default -> "OLEADA SORPRESA"; });
        DropStatistics.add(player.getUUID(), "started_" + rarity.name().toLowerCase(java.util.Locale.ROOT));
        player.sendSystemMessage(Component.literal("¡Suministro detectado! Mira al cielo: aterriza en unos segundos."));
        if (specialEvent) broadcast("§6✦ ¡EVENTO EXTRAORDINARIO: "+switch(extraordinaryVariant) {
            case 1 -> "ASEDIO"; case 2 -> "GUARDIA ÉLITE"; default -> "MALDICIÓN";
        }+"!§r Recompensas adicionales al vencer.");
        if (globalAnnouncements) broadcast("Suministro " + rarityName(rarity) + " en X=" + pos.getX() + " Z=" + pos.getZ() + " | " + (arrivalTimeoutTicks / 20) + " segundos para llegar.");
        player.sendSystemMessage(Component.literal("Thoridrop " + rarityName(rarity)
                + " supply crate at " + pos.toShortString() + ". Right-click to activate."));
        return 1;
    }

    /** An interrupted combat resumes as an unopened challenge at the same crate.
     *  A REWARDS checkpoint is cleaned, never reissued, to prevent duplicated loot.
     */
    private void restorePreviousChallenge(net.minecraft.server.MinecraftServer server) {
        ThoridropCheckpoint.Snapshot old;
        try{old=ThoridropCheckpoint.read();}catch(RuntimeException ex) {
            LOGGER.error("Thoridrop checkpoint unreadable; keeping stable gameplay",ex);
            return;
        }
        if(old==null)return;
        for(ServerLevel level:server.getAllLevels()) {
            if(!level.dimension().toString().equals(old.dimension()))continue;
            BlockPos pos=new BlockPos(old.x(),old.y(),old.z());
            level.getChunkAt(pos);
            staleRecoveredMobs.addAll(old.mobIds());
            for(UUID mobId:staleRecoveredMobs) {
                net.minecraft.world.entity.Entity mob=level.getEntity(mobId);
                if(mob!=null)mob.discard();
            }
            Block at=level.getBlockState(pos).getBlock();
            if(!isSupplyCrate(at)) {ThoridropCheckpoint.clear();return;}
            if(old.phase().equals("REWARDS")) {
                level.removeBlock(pos,false);
                ThoridropCheckpoint.clear();
                LOGGER.info("Discarded interrupted completed crate {} without replaying rewards",pos);
                return;
            }
            Block closed=switch(old.rarity()) {
                case COMMON -> DropBlocks.COMMON.get();
                case RARE -> DropBlocks.RARE.get();
                case LEGENDARY -> DropBlocks.LEGENDARY.get();
            };
            level.setBlockAndUpdate(pos,closed.defaultBlockState());
            session=new DropSession(old.id(),old.rarity(),5);
            dropLevel=level;dropPosition=pos.immutable();arrivalTicks=0;
            availableTicks=0;chestOpen=false;chestOpeningTicks=-1;
            specialEvent=false;extraordinaryVariant=0;modifierType=0;rewardTicks=0;eliteWaveMobs.clear();
            participants.clear();claimedRewards.clear();completedStats.clear();
            respawnBudget.reset();
            eventHits.clear();eventKills.clear();eventDeaths.clear();waveMobs.clear();
            eventBar=new net.minecraft.server.level.ServerBossEvent(
                UUID.randomUUID(),Component.literal("✦ THORIDROP  •  "+rarityName(old.rarity())),
                switch(old.rarity()) {
                    case COMMON -> net.minecraft.world.BossEvent.BossBarColor.BLUE;
                    case RARE -> net.minecraft.world.BossEvent.BossBarColor.PURPLE;
                    case LEGENDARY -> net.minecraft.world.BossEvent.BossBarColor.RED;
                },net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
            eventBar.setProgress(1.0f);
            ThoridropCheckpoint.write(new ThoridropCheckpoint.Snapshot(
                old.id(),old.dimension(),old.x(),old.y(),old.z(),old.rarity(),"AVAILABLE"));
            LOGGER.info("Recovered interrupted Thoridrop {} at {}: restart by activating crate",old.rarity(),pos);
            return;
        }
        LOGGER.warn("Thoridrop checkpoint dimension not loaded: {}",old.dimension());
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
        if (!respawnTeleportQueue.isEmpty()) {
            var iterator = respawnTeleportQueue.entrySet().iterator();
            while (iterator.hasNext()) {
                var pending = iterator.next();
                if (pending.getValue() > 1) {
                    pending.setValue(pending.getValue() - 1);
                    continue;
                }
                iterator.remove();
                if (session == null || dropLevel == null || dropPosition == null
                        || session.phase() != DropSession.Phase.COMBAT
                        || respawnBudget.eliminated(pending.getKey())) continue;
                ServerPlayer player = dropLevel.getServer().getPlayerList().getPlayer(pending.getKey());
                if (player == null) continue;
                dropLevel.getChunkAt(dropPosition);
                // Flames and other decorative blocks above the crate can have collision.
                // Search for a safe nearby location instead of silently using world spawn.
                BlockPos landing = null;
                for (int radius = 0; radius <= 5 && landing == null; radius++) {
                    for (int dx = -radius; dx <= radius && landing == null; dx++) {
                        for (int dz = -radius; dz <= radius && landing == null; dz++) {
                            BlockPos ground = dropPosition.offset(dx, 0, dz);
                            BlockPos feet = ground.above();
                            if (dropLevel.getBlockState(ground).getCollisionShape(dropLevel, ground).isEmpty()) continue;
                            if (!dropLevel.getBlockState(feet).getCollisionShape(dropLevel, feet).isEmpty()) continue;
                            if (!dropLevel.getBlockState(feet.above()).getCollisionShape(dropLevel, feet.above()).isEmpty()) continue;
                            landing = feet;
                        }
                    }
                }
                if (landing != null) {
                    player.teleportTo(dropLevel, landing.getX() + 0.5, landing.getY() + 0.05,
                        landing.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
                    player.sendSystemMessage(Component.literal("Thoridrop: has reaparecido junto al suministro."));
                }
            }
        }
        if (!configLoaded) loadConfig();
        if(!staleRecoveredMobs.isEmpty() && dropLevel!=null && ++staleCleanupTicks%20==0) {
            staleRecoveredMobs.removeIf(id -> {
                net.minecraft.world.entity.Entity mob=dropLevel.getEntity(id);
                if(mob!=null) {mob.discard();return true;}
                return false;
            });
        }
        if(!checkedRecovery) {
            checkedRecovery=true;
            restorePreviousChallenge(event.getServer());
        }
        if (session == null) {
            // Defensive cleanup of stale boss bars even after an interrupted event.
            if (eventBar != null || !locatorBars.isEmpty()) clearHud();
            if (++automaticTicks >= intervalTicks) {
                automaticTicks = 0;
                var server = event.getServer();
                var players = server.getPlayerList().getPlayers();
                if (!players.isEmpty()) {
                    ServerPlayer target = players.get(net.minecraft.util.RandomSource.create().nextInt(players.size()));
                    int roll = net.minecraft.util.RandomSource.create().nextInt(normalWeight + mythicWeight + legendaryWeight);
                    DropSession.Rarity rarity = roll < normalWeight ? DropSession.Rarity.COMMON
                        : roll < normalWeight + mythicWeight ? DropSession.Rarity.RARE : DropSession.Rarity.LEGENDARY;
                    automaticDrop = true;
                    try { createDrop(target, rarity); } finally { automaticDrop = false; }
                    if (session != null) {
                        if (globalAnnouncements) broadcast("¡Suministro automático " + rarityName(rarity) + " detectado! Usa el localizador superior.");
                        if (arrivalSounds) for (ServerPlayer online : server.getPlayerList().getPlayers()) online.level().playSound(null, online.blockPosition(), net.minecraft.sounds.SoundEvents.RAID_HORN.value(), net.minecraft.sounds.SoundSource.MASTER, 0.8f, 1.0f);
                    }
                }
            }
            return;
        }
        if (dropLevel == null || dropPosition == null) return;
        if (session.phase() == DropSession.Phase.AVAILABLE) {
            boolean reached = dropLevel.players().stream().anyMatch(p -> !p.isSpectator()
                && p.distanceToSqr(dropPosition.getX() + 0.5, dropPosition.getY() + 0.5,
                    dropPosition.getZ() + 0.5) <= 64.0);
            if (reached) availableTicks = -1;
            if (availableTicks >= 0 && ++availableTicks >= arrivalTimeoutTicks) {
                expireUnclaimedDrop();
                return;
            }
        }
        if (arrivalTicks > 0) {
            tickArrival();
            return;
        }
        if (session.phase() == DropSession.Phase.COMBAT) {
            combatTicks++;
            if(waveDelay==0 && session.wave()<5 && ++eliteAbilityTicks%80==0)
                tickEliteAbilities();
            if (session.wave() == 5 && session.rarity() == DropSession.Rarity.LEGENDARY && waveDelay == 0 && !waveMobs.isEmpty()) tickGuardianAbility();
            for (ServerPlayer player : dropLevel.players()) {
                if ((multiplayerEnabled || participants.isEmpty() || participants.contains(player.getUUID()))
                        && !player.isSpectator() && !player.isCreative()
                        && !respawnBudget.eliminated(player.getUUID())
                        && player.distanceToSqr(dropPosition.getX() + 0.5, dropPosition.getY() + 0.5, dropPosition.getZ() + 0.5) <= participationRadius * participationRadius) {
                    if(participants.add(player.getUUID())) DropStatistics.remember(player.getUUID(), player.getGameProfile().name());
                    playerOutOfRangeTicks.remove(player.getUUID());
                }
            }
            // Players who leave or disconnect long enough stop occupying a reward slot.
            if (++guardianAttackTicks % 20 == 0) {
                for (UUID id : java.util.Set.copyOf(participants)) {
                    ServerPlayer online = dropLevel.getServer().getPlayerList().getPlayer(id);
                    boolean absent = online == null || online.level()!=dropLevel || online.distanceToSqr(dropPosition.getX()+0.5, dropPosition.getY()+0.5, dropPosition.getZ()+0.5) > 96 * 96;
                    if(absent && playerOutOfRangeTicks.merge(id,20,Integer::sum) >= abandonSeconds * 20) {
                        participants.remove(id); playerOutOfRangeTicks.remove(id);
                    } else if (!absent) playerOutOfRangeTicks.remove(id);
                }
            }
            if (waveDelay == 0 && guardianAttackTicks % 20 == 0) {
                directWaveEnemiesToPlayers();
                checkTowerExploit();
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
                    // Recovery must never replay completed claims.
                    ThoridropCheckpoint.write(new ThoridropCheckpoint.Snapshot(
                        session.eventId(),dropLevel.dimension().toString(),dropPosition.getX(),
                        dropPosition.getY(),dropPosition.getZ(),session.rarity(),"REWARDS"));
                    // A spectator standing by the crate does not earn a reward.
                    participants.removeIf(id -> respawnBudget.eliminated(id) ||
                        (eventHits.getOrDefault(id,0) == 0 && eventKills.getOrDefault(id,0) == 0));
                    teamSizeAtVictory = participants.size();
                    if(teamSizeAtVictory>=2) {
                        var scoreboard=new java.util.ArrayList<>(participants);
                        scoreboard.sort(java.util.Comparator.comparingInt(
                            (UUID id)->eventHits.getOrDefault(id,0)).reversed());
                        StringBuilder report=new StringBuilder("§6✦ Contribución del equipo: ");
                        for(int i=0;i<Math.min(3,scoreboard.size());i++) {
                            UUID id=scoreboard.get(i);
                            ServerPlayer winner=dropLevel.getServer().getPlayerList().getPlayer(id);
                            report.append(i==0?"":" | ").append(i+1).append(". ")
                                .append(winner!=null?winner.getGameProfile().name():id.toString().substring(0,8))
                                .append(" ").append(eventHits.getOrDefault(id,0)).append(" impactos");
                        }
                        broadcast(report.toString());
                    }
                    timedChallengeSucceeded = modifierType == 5 && combatTicks <= timedChallengeSeconds * 20;
                    for (UUID id : participants) if (completedStats.add(id)) {
                        DropStatistics.add(id, "wins");
                        DropStatistics.bestTime(id, combatTicks);
                        ServerPlayer achiever=dropLevel.getServer().getPlayerList().getPlayer(id);
                        if(achiever!=null)ThoridropProgression.onVictory(
                            achiever,session.rarity(),eventKills.getOrDefault(id,0),
                            !eventDeaths.contains(id),teamSizeAtVictory>=2);
                        DropStatistics.recordHistory(id,session.rarity().name(),
                            combatTicks/20,eventHits.getOrDefault(id,0),
                            eventKills.getOrDefault(id,0),teamSizeAtVictory,!eventDeaths.contains(id));
                        if(achiever!=null)
                            ThoridropAdvancements.grantVictory(achiever,session.rarity(),
                                DropStatistics.value(id,"wins"),!eventDeaths.contains(id),teamSizeAtVictory>=2);
                        DropStatistics.streak(id,true);
                        if(teamSizeAtVictory >= 2) DropStatistics.add(id,"team_wins");
                        if(timedChallengeSucceeded) DropStatistics.add(id,"timed_wins");
                        if(specialEvent) DropStatistics.add(id,"special_wins");
                        int victories=DropStatistics.value(id,"wins");
                        if(victories==10 || victories==25 || victories==50 || victories==100)
                            awardAchievement(id,"Veterano Thoridrop: "+victories+" victorias");
                        if(session.rarity()==DropSession.Rarity.LEGENDARY && DropStatistics.value(id,"wins_legendary")==0)
                            awardAchievement(id,"Conquistador legendario");
                        if(teamSizeAtVictory>=2 && DropStatistics.value(id,"team_wins")==1)
                            awardAchievement(id,"Victoria en equipo");
                        if(!eventDeaths.contains(id) && DropStatistics.value(id,"flawless")==0)
                            awardAchievement(id,"Superviviente impecable");
                        if(!eventDeaths.contains(id)) DropStatistics.add(id,"flawless");
                        DropStatistics.add(id, "wins_" + session.rarity().name().toLowerCase(java.util.Locale.ROOT));
                    }
                    if (globalAnnouncements) broadcast("¡Cinco oleadas completadas! Los participantes pueden recoger su recompensa.");
                }
            }
        }
        if (session.phase() == DropSession.Phase.REWARDS && ++rewardTicks > rewardClaimTimeoutTicks) {
            // Offline participants must not lock the world event forever.
            if (dropLevel != null && dropPosition != null && isSupplyCrate(dropLevel.getBlockState(dropPosition).getBlock()))
                dropLevel.removeBlock(dropPosition, false);
            removeBeaconLight();
            clearHud();
            session.finish();
            session = null;
            dropLevel = null;
            dropPosition = null;
            return;
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
                if (arrivalParticles) dropLevel.sendParticles(beaconParticle, bx, by + height, bz, 5, 0.12, 0.25, 0.12, 0.005);
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
                    .append(Component.literal("  " + distance + "m  " + (availableTicks >= 0 ? "⏳ " + Math.max(0, (arrivalTimeoutTicks - availableTicks + 19) / 20) + "s  " : "✓ LOCALIZADO  ")).withStyle(net.minecraft.ChatFormatting.YELLOW))
                    .append(Component.literal(aligned ? "◆ OBJETIVO ENFRENTE ◆" : delta > 0 ? "◀ GIRA A LA DERECHA" : "GIRA A LA IZQUIERDA ▶")
                        .withStyle(aligned ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.WHITE, net.minecraft.ChatFormatting.BOLD))
                    );
                bar.setProgress(availableTicks >= 0 ? Math.max(0.0f, Math.min(1.0f, (float)(arrivalTimeoutTicks - availableTicks) / arrivalTimeoutTicks)) : 1.0f);
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
                    + "  ◆  " + guardianDisplayName + " " + Math.round(living.getHealth()) + "/" + Math.round(living.getMaxHealth()) + " HP")
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

    /**
     * Keep wave mobs actively engaging nearby players. This makes standing on a pillar
     * less effective when combined with ranged skeleton/pillager support in every wave.
     * Never target creative/spectator players or players outside the event area.
     */
    private void directWaveEnemiesToPlayers() {
        if (dropLevel == null || dropPosition == null || session == null) return;
        for (UUID id : waveMobs) {
            var entity = dropLevel.getEntity(id);
            if (!(entity instanceof net.minecraft.world.entity.Mob mob) || !mob.isAlive()) continue;
            ServerPlayer nearest = null;
            double best = 28 * 28;
            for (ServerPlayer player : dropLevel.players()) {
                if (player.isCreative() || player.isSpectator()) continue;
                if (player.distanceToSqr(dropPosition.getX() + 0.5, dropPosition.getY() + 0.5,
                    dropPosition.getZ() + 0.5) > 32 * 32) continue;
                double distance = mob.distanceToSqr(player);
                if (distance < best) { best = distance; nearest = player; }
            }
            if (nearest != null) {
                if (session.wave() == 5) continue; // Nicktale controls its own boss AI.
                mob.setTarget(nearest);
                // Fifth-wave guardians also need an explicit navigation request:
                // target assignment alone does not start pathfinding for some Nicktale mobs.
                if (mob.getNavigation().isDone() && mob.distanceToSqr(nearest) > 3.0 * 3.0) {
                    mob.getNavigation().moveTo(nearest, session.wave() == 5 ? 1.65 : 1.15);
                }
            }
        }
    }


    private void suppressNicktaleSpecials() {
        if (session == null || session.wave() != 5 || session.rarity() == DropSession.Rarity.LEGENDARY) return;
        for (UUID id : waveMobs) {
            var entity = dropLevel.getEntity(id);
            if (!(entity instanceof net.minecraft.world.entity.Mob boss)) continue;
            if (!net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).getNamespace().equals("nicktaleepicdungeons")) continue;
            try {
                java.lang.reflect.Field cooldown = boss.getClass().getDeclaredField("cooldown");
                cooldown.setAccessible(true);
                cooldown.setInt(boss, 12000);
            } catch (NoSuchFieldException ignored) {
                // Not all Nicktale entities use the same attack scheduler.
            } catch (ReflectiveOperationException | RuntimeException ex) {
                LOGGER.debug("Could not suppress boss cooldown", ex);
            }
        }
    }

    private void addBasicMeleeGoal(net.minecraft.world.entity.Mob boss) {
        if (!(boss instanceof net.minecraft.world.entity.PathfinderMob pathfinder)) return;
        try {
            java.lang.reflect.Field goals = net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");
            goals.setAccessible(true);
            var selector = (net.minecraft.world.entity.ai.goal.GoalSelector) goals.get(boss);
            selector.addGoal(2, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(pathfinder, 1.1, true));
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOGGER.warn("Cannot install basic melee AI for Thoridrop boss", ex);
        }
    }

    private void checkTowerExploit() {
        if (session == null || session.wave() >= 5 || towerReinforcements >= 2) return;
        if (++towerCheckTicks < 12) return;
        towerCheckTicks = 0;
        ServerPlayer elevated = null;
        for (ServerPlayer p : dropLevel.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            if (p.distanceToSqr(dropPosition.getX() + 0.5, dropPosition.getY() + 0.5,
                dropPosition.getZ() + 0.5) > 28 * 28) continue;
            if (p.getY() >= dropPosition.getY() + 3.5) { elevated = p; break; }
        }
        if (elevated == null) return;
        String typeId = session.rarity() == DropSession.Rarity.COMMON ? "minecraft:skeleton" : "minecraft:pillager";
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse(typeId));
        if (type == null) return;
        var entity = type.create(dropLevel, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (!(entity instanceof net.minecraft.world.entity.Mob mob)) return;
        double angle = net.minecraft.util.RandomSource.create().nextDouble() * Math.PI * 2;
        int x = elevated.blockPosition().getX() + (int)Math.round(Math.cos(angle) * 8);
        int z = elevated.blockPosition().getZ() + (int)Math.round(Math.sin(angle) * 8);
        BlockPos ground = dropLevel.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            new BlockPos(x, dropPosition.getY(), z));
        mob.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
            new net.minecraft.world.item.ItemStack(session.rarity() == DropSession.Rarity.COMMON
                ? net.minecraft.world.item.Items.BOW : net.minecraft.world.item.Items.CROSSBOW));
        mob.setPersistenceRequired();
        mob.setTarget(elevated);
        if (dropLevel.addFreshEntity(mob)) {
            waveMobs.add(mob.getUUID());
            session.setWaveEnemyCount(session.livingWaveEnemies() + 1);
            waveTotal++;
            towerReinforcements++;
            broadcast("Refuerzos a distancia: el suministro esta siendo defendido.");
        }
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

    private void awardAchievement(UUID id, String achievement) {
        if(dropLevel==null) return;
        ServerPlayer p=dropLevel.getServer().getPlayerList().getPlayer(id);
        if(p!=null) p.sendSystemMessage(Component.literal("§6✦ LOGRO THORIDROP: §e"+achievement));
    }

    /** Server-owned catalogue shared with the menu and legacy commands. */
    void menuPurchase(ServerPlayer player,int offerId) {
        if(offerId==20) {
            ThoridropProgression.prestige(player);
        } else if((offerId>=1 && offerId<=12) || (offerId>=21 && offerId<=29)) {
            ThoridropShop.buy(player,offerId);
        }
    }

    private int buyShopItem(ServerPlayer player,String sku,int ignoredCost) {
        return ThoridropShop.buy(player,switch(sku) {
            case "hierro" -> 1; case "diamantes" -> 2;
            case "manzanas" -> 3; case "arma" -> 4; default -> -1;
        });
    }

    private void grantRewards(ServerPlayer player) {
        RewardManager.give(player,session.rarity());
        // A separate, named enchanted piece is an additional reward, never a replacement
        // for the working personal-loot configuration.
        int chance = switch(session.rarity()) {
            case COMMON -> 0;
            case RARE -> exclusiveGearChanceMythic;
            case LEGENDARY -> exclusiveGearChanceLegendary;
        };
        if (player.getRandom().nextInt(100) < chance) {
            boolean legendary = session.rarity() == DropSession.Rarity.LEGENDARY;
            // Six variants per difficulty; original six items keep the same collection keys.
            int variant = player.getRandom().nextInt(6);
            net.minecraft.world.item.Item item = legendary ? switch(variant) {
                case 0 -> net.minecraft.world.item.Items.NETHERITE_AXE;
                case 1 -> net.minecraft.world.item.Items.NETHERITE_SWORD;
                case 2 -> net.minecraft.world.item.Items.NETHERITE_CHESTPLATE;
                case 3 -> net.minecraft.world.item.Items.BOW;
                case 4 -> net.minecraft.world.item.Items.NETHERITE_PICKAXE;
                default -> net.minecraft.world.item.Items.NETHERITE_BOOTS;
            } : switch(variant) {
                case 0 -> net.minecraft.world.item.Items.DIAMOND_AXE;
                case 1 -> net.minecraft.world.item.Items.DIAMOND_SWORD;
                case 2 -> net.minecraft.world.item.Items.DIAMOND_PICKAXE;
                case 3 -> net.minecraft.world.item.Items.BOW;
                case 4 -> net.minecraft.world.item.Items.DIAMOND_CHESTPLATE;
                default -> net.minecraft.world.item.Items.DIAMOND_SHOVEL;
            };
            String collectionKey = legendary ? switch(variant) {
                case 0 -> "legendary_axe";
                case 1 -> "legendary_blade";
                case 2 -> "legendary_armor";
                case 3 -> "legendary_bow";
                case 4 -> "legendary_tool";
                default -> "legendary_boots";
            } : switch(variant) {
                case 0 -> "mythic_axe";
                case 1 -> "mythic_blade";
                case 2 -> "mythic_tool";
                case 3 -> "mythic_bow";
                case 4 -> "mythic_armor";
                default -> "mythic_shovel";
            };
            String name = legendary ? switch(variant) {
                case 0 -> "Hacha de la Tormenta";
                case 1 -> "Espada del Caballero Negro";
                case 2 -> "Coraza del Guardián";
                case 3 -> "Arco de la Tempestad";
                case 4 -> "Pico de las Profundidades";
                default -> "Botas del Conquistador";
            } : switch(variant) {
                case 0 -> "Hacha mítica Thoridrop";
                case 1 -> "Espada del Desafiante";
                case 2 -> "Pico del Explorador";
                case 3 -> "Arco del Cazador";
                case 4 -> "Coraza de la Aurora";
                default -> "Pala del Buscador";
            };
            var exclusive = new net.minecraft.world.item.ItemStack(item);
            exclusive = net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(
                player.getRandom(), exclusive, legendary ? 50 : 28,
                player.level().registryAccess(), java.util.Optional.empty());
            exclusive.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal((legendary ? "§6✦ " : "§d✦ ")+name));
            if(!player.getInventory().add(exclusive) && !exclusive.isEmpty())
                player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                    player.level(),player.getX(),player.getY(),player.getZ(),exclusive));
            ThoridropProgression.collect(player.getUUID(),collectionKey);
            player.sendSystemMessage(Component.literal("§6✦ ¡Recompensa exclusiva de Thoridrop!"));
        }
        if (specialEvent) {
            int bonus = switch (session.rarity()) {case COMMON -> 2; case RARE -> 4; case LEGENDARY -> 8;};
            net.minecraft.world.item.ItemStack extra = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, bonus);
            if (!player.getInventory().add(extra) && !extra.isEmpty()) {
                var dropped = new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), extra);
                dropped.setPickUpDelay(20);
                player.level().addFreshEntity(dropped);
            }
            player.sendSystemMessage(Component.literal("Recompensa extraordinaria: +" + bonus + " diamantes."));
        }
        player.level().playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
            net.minecraft.sounds.SoundSource.PLAYERS,0.8f,1.2f);
    }

    @SubscribeEvent
    public void onEventMobDamaged(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if(session==null || session.phase()!=DropSession.Phase.COMBAT || dropLevel==null) return;
        if(!waveMobs.contains(event.getEntity().getUUID())) return;
        if(event.getSource().getEntity() instanceof ServerPlayer player) {
            session.recordDamage(player.getUUID(),1.0);
            eventHits.merge(player.getUUID(), 1, Integer::sum);
            DropStatistics.add(player.getUUID(),"hits");
        }
    }
    private void broadcast(String message) {
        for (ServerPlayer p : dropLevel.players()) p.sendSystemMessage(Component.literal(message));
    }

    /**
     * Find a safe standing position on ordinary terrain, paths or snow layers.
     * Do not alter blocks or place enemies within eight blocks of the crate.
     */
    private BlockPos findSafeWaveSpawn(double angle,int slot) {
        if(dropLevel==null || dropPosition==null) return null;
        for(int attempt=0;attempt<120;attempt++) {
            int radius=10+(attempt/24)*4;
            double offset=angle+attempt*(Math.PI*2.0/24.0)+slot*0.01;
            int px=dropPosition.getX()+(int)Math.round(Math.cos(offset)*radius);
            int pz=dropPosition.getZ()+(int)Math.round(Math.sin(offset)*radius);
            if(Math.abs(px-dropPosition.getX())<8 && Math.abs(pz-dropPosition.getZ())<8)continue;
            BlockPos top=dropLevel.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(px,dropPosition.getY(),pz));
            // Heightmaps may return a snow layer, foliage or the air above a path.
            for(int dy=2;dy>=-3;dy--) {
                BlockPos feet=top.offset(0,dy,0);
                BlockPos below=feet.below();
                var floor=dropLevel.getBlockState(below);
                if(floor.getCollisionShape(dropLevel,below).isEmpty()) {
                    // A thin snow layer lies above a valid full support block.
                    if(!floor.is(net.minecraft.world.level.block.Blocks.SNOW)
                        || dropLevel.getBlockState(below.below())
                             .getCollisionShape(dropLevel,below.below()).isEmpty())continue;
                }
                if(!dropLevel.getBlockState(feet).getCollisionShape(dropLevel,feet).isEmpty()
                    || !dropLevel.getBlockState(feet.above()).getCollisionShape(dropLevel,feet.above()).isEmpty()
                    || !dropLevel.getFluidState(feet).isEmpty()
                    || !dropLevel.getFluidState(feet.above()).isEmpty())continue;
                // Keep mobs around the heightmap surface, never in underground cavities.
                if(Math.abs(feet.getY()-top.getY())>2)continue;
                return feet.immutable();
            }
        }
        return null;
    }

    private String[] biomeWaveEnemies(String[] base,int wave) {
        if(dropLevel==null || dropPosition==null)return base;
        var biome=dropLevel.getBiome(dropPosition);
        String name=biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
        boolean hard=session.rarity()==DropSession.Rarity.LEGENDARY;
        boolean mythic=session.rarity()==DropSession.Rarity.RARE;
        if(biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_SNOWY)
           ||name.contains("frozen")||name.contains("snow")) {
            waveBiomeLabel="Nieve";
            return hard?new String[]{"stray","vindicator","pillager","witch"}:
                mythic?new String[]{"stray","pillager","vindicator"}:
                wave>=3?new String[]{"stray","skeleton","spider"}:
                    new String[]{"stray","zombie","skeleton"};
        }
        if(name.contains("desert")||name.contains("badlands")) {
            waveBiomeLabel="Desierto";
            return hard?new String[]{"husk","vindicator","pillager","witch"}:
                mythic?new String[]{"husk","pillager","vindicator"}:
                wave>=3?new String[]{"husk","pillager","spider"}:
                    new String[]{"husk","skeleton","husk"};
        }
        if(name.contains("swamp")||name.contains("mangrove")) {
            waveBiomeLabel="Pantano";
            return hard?new String[]{"bogged","witch","vindicator","pillager"}:
                mythic?new String[]{"bogged","witch","pillager"}:
                wave>=3?new String[]{"bogged","witch","spider"}:
                    new String[]{"bogged","zombie","spider"};
        }
        if(name.contains("jungle")||name.contains("forest")||name.contains("taiga")) {
            waveBiomeLabel="Bosque";
            return hard?new String[]{"spider","vindicator","pillager","witch"}:
                mythic?new String[]{"spider","pillager","vindicator"}:
                wave>=3?new String[]{"spider","skeleton","pillager"}:
                    new String[]{"spider","zombie","skeleton"};
        }
        waveBiomeLabel="Llanura";
        return base;
    }

    private void tickEliteAbilities() {
        if(dropLevel==null)return;
        for(UUID id:java.util.Set.copyOf(eliteWaveMobs)) {
            var entity=dropLevel.getEntity(id);
            if(!(entity instanceof net.minecraft.world.entity.Mob elite) || !elite.isAlive()) {
                eliteWaveMobs.remove(id);
                continue;
            }
            // Nearby elites debuff players; bosses are excluded, and no terrain is changed.
            for(ServerPlayer player:dropLevel.players()) {
                if(player.isSpectator()||player.isCreative()
                    ||player.distanceToSqr(elite)>16.0)continue;
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOWNESS,50,0));
                dropLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    elite.getX(),elite.getY()+1.1,elite.getZ(),8,0.6,0.4,0.6,0.02);
            }
        }
    }

    private void spawnWave() {
        if (session == null || session.phase() != DropSession.Phase.COMBAT) return;
        waveMobs.clear();
        eliteWaveMobs.clear();
        guardianAttackTicks = 0;
        towerCheckTicks = 0;
        towerReinforcements = 0;
        missingMobTicks.clear();
        int wave = session.wave();
        int extra = session.rarity() == DropSession.Rarity.LEGENDARY ? 3
                  : session.rarity() == DropSession.Rarity.RARE ? 1 : 0;
        int count = wave == 5 ? 1 : 2 + wave + extra;
        if (wave != 5) {
            count = Math.max(1, count * mobMultiplierPercent / 100);
            int additional = Math.min(maxAdditionalMobs, Math.max(0, participants.size()-1) * extraMobsPerPlayer);
            count += additional;
            if (modifierType == 1) count += Math.max(2, count / 2);
            if (modifierType == 6 && wave == 4) count += 5;
            if (specialEvent && extraordinaryVariant==1) count += extraordinaryExtraMobs;
            count = Math.min(50, count);
        }
        String[] mobIds = switch (session.rarity()) {
            case COMMON -> switch (wave) {
                case 1 -> new String[]{"zombie", "skeleton"};
                case 2 -> new String[]{"zombie", "skeleton"};
                case 3 -> new String[]{"skeleton", "spider"};
                case 4 -> new String[]{"husk", "stray", "skeleton"};
                default -> new String[]{"ravager"};
            };
            case RARE -> switch (wave) {
                case 1 -> new String[]{"husk", "skeleton", "pillager"};
                case 2 -> new String[]{"pillager", "stray"};
                case 3 -> new String[]{"vindicator", "witch", "pillager"};
                case 4 -> new String[]{"vindicator", "pillager"};
                default -> new String[]{"ravager", "vindicator"};
            };
            case LEGENDARY -> switch (wave) {
                case 1 -> new String[]{"vindicator", "pillager"};
                case 2 -> new String[]{"witch", "vindicator", "pillager"};
                case 3 -> new String[]{"witch", "vindicator", "pillager"};
                case 4 -> new String[]{"pillager", "evoker", "vindicator", "skeleton"};
                default -> new String[]{"ravager", "vindicator"};
            };
        };
        // Fixed Nicktale guardians, with their native attributes, attacks and AI.
        if (wave == 5) {
            String chosen = switch (session.rarity()) {
                case COMMON -> "gravebreaker_brute";
                case RARE -> "buried_axe_knight";
                case LEGENDARY -> "black_knight";
            };
            var key = net.minecraft.resources.Identifier.fromNamespaceAndPath("nicktaleepicdungeons", chosen);
            if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
                mobIds = new String[]{"nicktaleepicdungeons:" + chosen};
                guardianDisplayName = switch (chosen) {
                    case "gravebreaker_brute" -> "QUEBRATUMBAS";
                    case "buried_axe_knight" -> "CABALLERO DEL HACHA SEPULTADA";
                    case "black_knight" -> "CABALLERO NEGRO";
                    default -> chosen.replace('_', ' ').toUpperCase(java.util.Locale.ROOT);
                };
                broadcast("¡Jefe final: " + guardianDisplayName + "!");
            } else {
                mobIds = new String[]{"minecraft:ravager"};
                guardianDisplayName = "RAVAGER";
                broadcast("Falta el jefe de Nicktale " + chosen + "; usando guardián alternativo.");
            }
        } else {
            mobIds = biomeWaveEnemies(mobIds,wave);
            for (int j = 0; j < mobIds.length; j++) mobIds[j] = "minecraft:" + mobIds[j];
            if(wave==1)broadcast("§e✦ Oleadas del bioma: "+waveBiomeLabel+"§r");
        }
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            BlockPos spawn=findSafeWaveSpawn(angle,i);
            if(spawn==null) {
                LOGGER.warn("Thoridrop: no safe spawn for wave {} enemy {}, will retry if all fail",wave,i);
                continue;
            }
            String mobId = mobIds[i % mobIds.length];
            net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse(mobId));
            if (type == null) type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse("minecraft:ravager"));
            net.minecraft.world.entity.Entity entity = type.create(dropLevel, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (entity == null) entity = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(net.minecraft.resources.Identifier.parse("minecraft:ravager")).create(dropLevel, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (entity == null) continue;
            entity.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            boolean isElite=wave<5 && entity instanceof net.minecraft.world.entity.Mob
                && (modifierType==4 || (specialEvent && extraordinaryVariant==2)
                || java.util.concurrent.ThreadLocalRandom.current().nextInt(100)<eliteSpawnChancePercent
                    && (session.rarity()!=DropSession.Rarity.COMMON || wave>=3));
            if(isElite && entity instanceof net.minecraft.world.entity.Mob elite) {
                eliteWaveMobs.add(entity.getUUID());
                String title = switch(i%3) {
                    case 0 -> "§6✦ CENTINELA ÉLITE"; case 1 -> "§d✦ CAZADOR ÉLITE";
                    default -> "§c✦ BRUTO ÉLITE";
                };
                elite.setCustomName(Component.literal(title));
                elite.setCustomNameVisible(true);
                elite.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.STRENGTH,20*1200,0));
                if(i%3==1)elite.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SPEED,20*1200,0));
                elite.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                elite.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                elite.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE,20*1200,0));
            }
            if (wave<5 && specialEvent && extraordinaryVariant==3
                && entity instanceof net.minecraft.world.entity.LivingEntity cursed)
                cursed.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SPEED,20*1200,0));
            if (wave < 5 && modifierType == 2 && entity instanceof net.minecraft.world.entity.LivingEntity fast) {
                fast.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 20 * 1200, 0));
            }
            if (wave < 5 && entity instanceof net.minecraft.world.entity.Mob ranged) {
                if (mobId.equals("minecraft:skeleton") || mobId.equals("minecraft:stray")) {
                    ranged.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
                } else if (mobId.equals("minecraft:pillager")) {
                    ranged.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CROSSBOW));
                }
            }
            // Do not override fifth-wave guardian health, movement, effects, name or combat goals.
            if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setPersistenceRequired();
            if (dropLevel.addFreshEntity(entity)) waveMobs.add(entity.getUUID());
        }
        if(waveMobs.isEmpty()) {
            // Repeatedly failing to find a safe location cannot lock the server forever.
            if(++emptyWaveRetries>=12) {
                broadcast("Thoridrop: no hay terreno apto para enemigos. Desafío cancelado sin recompensas.");
                if(dropLevel!=null && dropPosition!=null && isSupplyCrate(dropLevel.getBlockState(dropPosition).getBlock()))
                    dropLevel.removeBlock(dropPosition,false);
                removeBeaconLight();clearHud();session.finish();session=null;
                dropLevel=null;dropPosition=null;ThoridropCheckpoint.clear();
                return;
            }
            waveDelay=100;
            broadcast("Thoridrop: buscando zona segura para los enemigos (reintento en 5 s).");
            LOGGER.warn("Thoridrop wave {} could not spawn enemies; retry scheduled",wave);
            return;
        }
        emptyWaveRetries=0;
        ThoridropCheckpoint.write(new ThoridropCheckpoint.Snapshot(
            session.eventId(),dropLevel.dimension().toString(),
            dropPosition.getX(),dropPosition.getY(),dropPosition.getZ(),
            session.rarity(),"COMBAT",
            String.join(",",waveMobs.stream().map(UUID::toString).toList())));
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
                dropPosition.getZ() + 0.5) <= 96 * 96;
    }

    @SubscribeEvent
    public void onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            if (session != null && session.phase()==DropSession.Phase.COMBAT
                && waveMobs.contains(event.getEntity().getUUID())
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
                eventKills.merge(killer.getUUID(),1,Integer::sum);
                DropStatistics.add(killer.getUUID(),"kills");
            }
            return;
        }
        if (!fightingNearDrop(player) || respawnBudget.eliminated(player.getUUID())) return;
        UUID id=player.getUUID();
        eventDeaths.add(id);
        DropStatistics.streak(id,false);
        DropStatistics.add(id,"deaths");
        boolean respawnAtCrate=respawnBudget.onDeath(id);

        // Preserve the existing inventory/experience setting for deaths one through six.
        // Only the location changes once the Thoridrop respawn allowance is exhausted.
        if(preserveInventoryOnEventDeath) {
            java.util.List<net.minecraft.world.item.ItemStack> saved=new java.util.ArrayList<>();
            for(int i=0;i<player.getInventory().getContainerSize();i++)
                saved.add(player.getInventory().getItem(i).copy());
            deathInventories.put(id,saved);
            deathExperience.put(id,player.totalExperience);
        }
        if(respawnAtCrate) {
            participants.add(id);
            respawnPending.add(id);
            player.sendSystemMessage(Component.literal("§6✦ THORIDROP §r| Reapariciones restantes: "
                +respawnBudget.remaining(id)+"/"+respawnBudget.limit()));
        } else {
            respawnPending.remove(id);
            respawnTeleportQueue.remove(id);
            participants.remove(id);
            playerOutOfRangeTicks.remove(id);
            player.sendSystemMessage(Component.literal(
                "§c✦ THORIDROP | Has agotado tus reapariciones. Desafío perdido. "
                +"Volverás a tu punto de reaparición normal."));
        }
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
        UUID id=player.getUUID();
        java.util.List<net.minecraft.world.item.ItemStack> saved=deathInventories.remove(id);
        if(saved!=null) {
            for(int i=0;i<saved.size() && i<player.getInventory().getContainerSize();i++)
                player.getInventory().setItem(i,saved.get(i));
        }
        Integer experience=deathExperience.remove(id);
        if(experience!=null)player.giveExperiencePoints(experience);
        // Vanilla selects the bed/anchor/world spawn. Only the first N Thoridrop deaths
        // override this on the next server tick; the sixth death uses vanilla unchanged.
        if(respawnPending.remove(id) && !respawnBudget.eliminated(id))
            respawnTeleportQueue.put(id,2);
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
            if (respawnBudget.eliminated(player.getUUID()) ||
                !participants.contains(player.getUUID())
                    || (eventHits.getOrDefault(player.getUUID(),0)==0 &&
                        eventKills.getOrDefault(player.getUUID(),0)==0)) {
                player.sendSystemMessage(Component.literal("Thoridrop: no participaste en el combate."));
            } else if (claimedRewards.contains(player.getUUID()) || RewardManager.hasClaimed(session.eventId(),player.getUUID())) {
                player.sendSystemMessage(Component.literal("Thoridrop: ya recogiste tu recompensa."));
            } else if (RewardManager.claim(session.eventId(),player.getUUID())) {
                claimedRewards.add(player.getUUID());
                grantRewards(player);
                DropStatistics.add(player.getUUID(), "claims");
                int earned = switch(session.rarity()) { case COMMON -> shopPointsNormal; case RARE -> shopPointsMythic; case LEGENDARY -> shopPointsLegendary; };
                if(teamSizeAtVictory >= 2) earned += cooperativeBonusPoints;
                if(specialEvent) earned += specialBonusPoints;
                if(timedChallengeSucceeded) earned += 25;
                DropPoints.earn(player.getUUID(),earned);
                // Mission payouts are independent of shop balances and one-time per period.
                int missionBonus=ThoridropProgression.claimMissions(player.getUUID());
                if(missionBonus>0)player.sendSystemMessage(Component.literal(
                    "§6✦ Misiones completadas: +"+missionBonus+" puntos"));
                player.sendSystemMessage(Component.literal("§6+ "+earned+" puntos Thoridrop§r | Saldo: "+DropPoints.balance(player.getUUID())));
                int damage = eventHits.getOrDefault(player.getUUID(),0);
                int best = eventHits.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                if(teamSizeAtVictory >= 2 && damage == best && best > 0)
                    player.sendSystemMessage(Component.literal("✦ Mayor contribución del equipo: "+damage+" impactos."));
                if(!eventDeaths.contains(player.getUUID()) && flawlessDiamondBonus > 0) {
                    int bonus = flawlessDiamondBonus + (modifierType == 3 ? 2 : 0);
                    var prize = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, bonus);
                    if(!player.getInventory().add(prize) && !prize.isEmpty())
                        player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), prize));
                    player.sendSystemMessage(Component.literal("✦ Desafío sin morir: +" + bonus + " diamantes."));
                } else if (modifierType == 3) {
                    var prize = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 3);
                    if(!player.getInventory().add(prize) && !prize.isEmpty()) player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), prize));
                }
                DropStatistics.add(player.getUUID(), "claims_" + session.rarity().name().toLowerCase(java.util.Locale.ROOT));
                if (claimedRewards.containsAll(participants)) {
                    dropLevel.removeBlock(dropPosition, false);
                    removeBeaconLight();
                    broadcast("Todos los participantes han recogido sus recompensas. El suministro ha desaparecido.");
                    clearHud();
                    session.finish();
                    ThoridropCheckpoint.clear();
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
            ThoridropCheckpoint.write(new ThoridropCheckpoint.Snapshot(
                session.eventId(),dropLevel.dimension().toString(),dropPosition.getX(),
                dropPosition.getY(),dropPosition.getZ(),session.rarity(),"COMBAT"));
            waveDelay = 40;
            broadcast("Thoridrop activated! Wave 1 begins in 2 seconds.");
        }
        // Custom supply crates never expose a vanilla chest inventory.
        event.setCanceled(true);
    }
}
