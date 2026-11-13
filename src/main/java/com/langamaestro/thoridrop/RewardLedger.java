package com.langamaestro.thoridrop;

import java.util.*;

/**
 * Immutable, shared reward specification and one-claim-per-player ledger.
 * A Minecraft adapter must translate RewardEntry into fresh ItemStacks per
 * player, persist the ledger, and perform an atomic grant/claim transaction.
 */
public final class RewardLedger {
    public record RewardEntry(String itemId, int count) {
        public RewardEntry {
            Objects.requireNonNull(itemId, "itemId");
            if (itemId.isBlank() || count < 1) throw new IllegalArgumentException("Invalid reward");
        }
    }

    private final UUID eventId;
    private final List<RewardEntry> reward;
    private final Set<UUID> eligible;
    private final Set<UUID> claimed = new HashSet<>();

    public RewardLedger(UUID eventId, List<RewardEntry> reward, Set<UUID> eligible) {
        this.eventId = Objects.requireNonNull(eventId);
        this.reward = List.copyOf(reward);
        this.eligible = Set.copyOf(eligible);
    }

    public UUID eventId() { return eventId; }
    public List<RewardEntry> rewardFor(UUID player) {
        return eligible.contains(player) ? reward : List.of();
    }
    public boolean hasClaimed(UUID player) { return claimed.contains(player); }
    public boolean mayClaim(UUID player) {
        return eligible.contains(player) && !claimed.contains(player);
    }

    /** Call only after reward delivery is durably committed by the server. */
    public boolean markClaimed(UUID player) {
        return eligible.contains(player) && claimed.add(player);
    }

    public Set<UUID> claimedSnapshot() { return Set.copyOf(claimed); }
}
