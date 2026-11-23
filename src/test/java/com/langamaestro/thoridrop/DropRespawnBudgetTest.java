package com.langamaestro.thoridrop;

import java.util.UUID;

public final class DropRespawnBudgetTest {
    public static void main(String[] args) {
        DropRespawnBudget budget=new DropRespawnBudget(5);
        UUID player=UUID.randomUUID(),teammate=UUID.randomUUID();
        for(int n=1;n<=5;n++) {
            if(!budget.onDeath(player))throw new AssertionError("Expected crate respawn at death "+n);
            if(budget.remaining(player)!=5-n)throw new AssertionError("Remaining lives mismatch");
        }
        if(budget.onDeath(player)||!budget.eliminated(player))
            throw new AssertionError("Sixth death must eliminate the player");
        if(budget.onDeath(player)||budget.remaining(player)!=0)
            throw new AssertionError("Eliminated player cannot regain crate respawns");
        if(!budget.onDeath(teammate)||budget.remaining(teammate)!=4)
            throw new AssertionError("Teammate requires an independent life budget");
        budget.reset();
        if(budget.eliminated(player)||budget.remaining(player)!=5||!budget.onDeath(player))
            throw new AssertionError("Fresh event must reset the allowance");
        budget.setLimit(0);
        budget.reset();
        if(budget.onDeath(player))throw new AssertionError("Zero lives should force vanilla spawn");
        // Server rule: two crate respawns; the third death fails the entire event.
        budget.setLimit(2);
        budget.reset();
        if(!budget.onDeath(player) || budget.remaining(player)!=1)
            throw new AssertionError("First death should return to the event");
        if(!budget.onDeath(player) || budget.remaining(player)!=0)
            throw new AssertionError("Second death should return to the event");
        if(budget.onDeath(player) || !budget.eliminated(player))
            throw new AssertionError("Third death must fail the challenge");
        if(!budget.onDeath(teammate))
            throw new AssertionError("Other participants must retain independent death counters");
        budget.reset();
        if(budget.eliminated(player) || !budget.onDeath(player))
            throw new AssertionError("New challenges must reset death counters");
        System.out.println("Thoridrop respawn budget tests passed");
    }
}
