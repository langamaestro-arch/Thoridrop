package com.langamaestro.thoridrop;

import java.util.*;

/**
 * Server-side event state machine. Minecraft entity spawning and save-data
 * integration are handled separately; this class is deliberately independent
 * of the client and can be unit tested.
 */
public final class DropSession {
    public enum Phase { AVAILABLE, COMBAT, REWARDS, FINISHED }
    public enum Rarity { COMMON, RARE, LEGENDARY }

    private final UUID eventId;
    private final Rarity rarity;
    private final int waveCount;
    private final Map<UUID, Double> damageByPlayer = new HashMap<>();
    private final Set<UUID> claimed = new HashSet<>();
    private final Set<UUID> eligible = new HashSet<>();
    private Phase phase = Phase.AVAILABLE;
    private int wave = 0;
    private double totalDamage = 0;
    private int livingWaveEnemies = 0;

    public DropSession(UUID eventId, Rarity rarity, int waveCount) {
        this.eventId = Objects.requireNonNull(eventId);
        this.rarity = Objects.requireNonNull(rarity);
        if (waveCount < 1) throw new IllegalArgumentException("waveCount must be positive");
        this.waveCount = waveCount;
    }

    public UUID eventId() { return eventId; }
    public Rarity rarity() { return rarity; }
    public Phase phase() { return phase; }
    public int wave() { return wave; }
    public int waveCount() { return waveCount; }
    public int livingWaveEnemies() { return livingWaveEnemies; }

    public boolean activate() {
        if (phase != Phase.AVAILABLE) return false;
        phase = Phase.COMBAT;
        wave = 1;
        return true;
    }

    /** Called once after all enemies for the current wave have spawned. */
    public void setWaveEnemyCount(int count) {
        if (phase != Phase.COMBAT || count < 0) throw new IllegalStateException("Invalid wave enemy count");
        livingWaveEnemies = count;
    }

    /** Only actual, capped damage to an event mob should be recorded. */
    public void recordDamage(UUID player, double actualDamage) {
        if (phase != Phase.COMBAT || actualDamage <= 0 || !Double.isFinite(actualDamage)) return;
        damageByPlayer.merge(Objects.requireNonNull(player), actualDamage, Double::sum);
        totalDamage += actualDamage;
    }

    /** Returns true when the next wave should be spawned. */
    public boolean enemyDefeated() {
        if (phase != Phase.COMBAT || livingWaveEnemies <= 0) return false;
        livingWaveEnemies--;
        if (livingWaveEnemies != 0) return false;
        if (wave < waveCount) {
            wave++;
            return true;
        }
        complete();
        return false;
    }

    private void complete() {
        phase = Phase.REWARDS;
        double threshold = totalDamage * 0.05;
        for (var entry : damageByPlayer.entrySet()) {
            if (entry.getValue() >= threshold) eligible.add(entry.getKey());
        }
    }

    public boolean isEligible(UUID player) {
        return phase == Phase.REWARDS && eligible.contains(player);
    }

    /** Atomic per-session claim marker; persist before issuing items. */
    public boolean claim(UUID player) {
        return isEligible(player) && claimed.add(player);
    }

    public Set<UUID> eligiblePlayers() { return Set.copyOf(eligible); }
    public Set<UUID> claimedPlayers() { return Set.copyOf(claimed); }
    public Map<UUID, Double> damageSnapshot() { return Map.copyOf(damageByPlayer); }

    public void finish() {
        phase = Phase.FINISHED;
        livingWaveEnemies = 0;
    }
}
