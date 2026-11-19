package com.langamaestro.thoridrop;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-event per-player respawn budget; one life consumed for every death during combat. */
public final class DropRespawnBudget {
    private final Map<UUID,Integer> deaths=new HashMap<>();
    private final Set<UUID> eliminated=new HashSet<>();
    private int freeRespawns;

    public DropRespawnBudget(int freeRespawns) {
        setLimit(freeRespawns);
    }
    public void setLimit(int count) {
        freeRespawns=Math.max(0,Math.min(100,count));
    }
    public int limit(){return freeRespawns;}
    public void reset() {
        deaths.clear();
        eliminated.clear();
    }
    /** Returns true only for a death that still permits a Thoridrop-area respawn. */
    public boolean onDeath(UUID player) {
        if(eliminated.contains(player))return false;
        int count=deaths.merge(player,1,Integer::sum);
        if(count>freeRespawns) {
            eliminated.add(player);
            return false;
        }
        return true;
    }
    public int remaining(UUID player) {
        return Math.max(0,freeRespawns-deaths.getOrDefault(player,0));
    }
    public boolean eliminated(UUID player) {
        return eliminated.contains(player);
    }
}
