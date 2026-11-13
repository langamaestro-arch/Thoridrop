package com.langamaestro.thoridrop;

import java.util.Objects;
import java.util.Random;

/**
 * Server tick scheduler for the next supply drop.
 * The owner calls tick() once per server tick, and only while a server is running.
 */
public final class DropScheduler {
    public static final int DEFAULT_INTERVAL_TICKS = 60 * 60 * 20;
    private final int intervalTicks;
    private final Random random;
    private int ticksRemaining;

    public DropScheduler(int intervalTicks, Random random) {
        if (intervalTicks < 1) throw new IllegalArgumentException("intervalTicks must be positive");
        this.intervalTicks = intervalTicks;
        this.random = Objects.requireNonNull(random);
        this.ticksRemaining = intervalTicks;
    }

    /** Returns true exactly once when a new drop is due. */
    public boolean tick(boolean hasActiveDrop, boolean hasOnlinePlayers) {
        if (!hasOnlinePlayers || hasActiveDrop) return false;
        if (--ticksRemaining > 0) return false;
        ticksRemaining = intervalTicks;
        return true;
    }

    public void reset() { ticksRemaining = intervalTicks; }
    public int ticksRemaining() { return ticksRemaining; }

    public DropSession.Rarity rollRarity() {
        int roll = random.nextInt(100);
        if (roll < 50) return DropSession.Rarity.COMMON;
        if (roll < 85) return DropSession.Rarity.RARE;
        return DropSession.Rarity.LEGENDARY;
    }
}
