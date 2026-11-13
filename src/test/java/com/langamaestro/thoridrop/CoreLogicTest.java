package com.langamaestro.thoridrop;

import java.util.*;

/** Standalone assertions runnable with Java 25 without Minecraft dependencies. */
public final class CoreLogicTest {
    public static void main(String[] args) {
        var scheduler = new DropScheduler(3, new Random(1));
        check(!scheduler.tick(false, true), "tick 1");
        check(!scheduler.tick(false, true), "tick 2");
        check(scheduler.tick(false, true), "tick 3");
        check(!scheduler.tick(true, true), "active drop pauses timer");
        check(!scheduler.tick(false, false), "offline pauses timer");
        check(!scheduler.tick(false, true), "reset after drop");

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        var session = new DropSession(UUID.randomUUID(), DropSession.Rarity.RARE, 2);
        check(session.activate(), "activate");
        check(!session.activate(), "cannot activate twice");
        session.setWaveEnemyCount(2);
        session.recordDamage(a, 60);
        session.recordDamage(b, 40);
        check(!session.enemyDefeated(), "first mob");
        check(session.enemyDefeated(), "next wave");
        check(session.wave() == 2, "wave increment");
        session.setWaveEnemyCount(1);
        session.recordDamage(c, 1);
        check(!session.enemyDefeated(), "last wave");
        check(session.phase() == DropSession.Phase.REWARDS, "rewards phase");
        check(session.isEligible(a) && session.isEligible(b), "fighters eligible");
        check(!session.isEligible(c), "under threshold");
        var ledger = new RewardLedger(session.eventId(),
                List.of(new RewardLedger.RewardEntry("minecraft:diamond", 5)),
                session.eligiblePlayers());
        check(ledger.rewardFor(a).equals(ledger.rewardFor(b)), "identical loot");
        check(ledger.rewardFor(c).isEmpty(), "no loot for nonparticipant");
        check(ledger.mayClaim(a), "claim permitted");
        check(ledger.markClaimed(a), "claim once");
        check(!ledger.markClaimed(a), "no duplicate claim");
        check(ledger.mayClaim(b), "other player's claim independent");
        System.out.println("Thoridrop core logic tests passed");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
