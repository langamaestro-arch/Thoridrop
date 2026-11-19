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
        System.out.println("Thoridrop respawn budget tests passed");
    }
}
