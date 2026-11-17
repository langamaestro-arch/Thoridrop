package com.langamaestro.thoridrop;

import java.util.UUID;

/** Plain-Java smoke tests for persistence, shop balances, and records. */
public final class ProgressionTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        UUID player = UUID.randomUUID();
        DropPoints.load();
        check(DropPoints.balance(player) == 0, "Unexpected initial balance");
        check(DropPoints.earn(player, 130) == 130, "Earn");
        check(!DropPoints.spend(player, 150), "Overspending");
        check(DropPoints.spend(player, 60), "Purchase");
        DropPoints.load();
        check(DropPoints.balance(player) == 70, "Balance persistence");
        DropStatistics.load();
        DropStatistics.remember(player, "Tester");
        DropStatistics.add(player, "wins");
        check(DropStatistics.value(player, "wins") == 1, "Win counter");
        check(DropStatistics.bestTime(player, 200), "First record");
        check(!DropStatistics.bestTime(player, 250), "Worse time should not replace record");
        check(DropStatistics.bestTime(player, 180), "Better time should replace record");
        check(DropStatistics.streak(player, true) == 1, "Streak win");
        check(DropStatistics.streak(player, false) == 0, "Streak reset");
        check(DropStatistics.leaderboard("wins", 10).contains("Tester"), "Leaderboards");
        DropStatistics.load();
        check(DropStatistics.value(player, "best_ticks") == 180, "Record persistence");
        System.out.println("Thoridrop progression smoke tests passed.");
    }
}
