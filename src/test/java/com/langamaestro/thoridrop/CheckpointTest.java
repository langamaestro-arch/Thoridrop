package com.langamaestro.thoridrop;
import java.util.UUID;
public final class CheckpointTest {
    public static void main(String[] args) {
        UUID event=UUID.randomUUID(),mob=UUID.randomUUID();
        var snapshot=new ThoridropCheckpoint.Snapshot(event,"minecraft:overworld",42,75,-20,
            DropSession.Rarity.RARE,"COMBAT",mob.toString());
        ThoridropCheckpoint.write(snapshot);
        var restored=ThoridropCheckpoint.read();
        if(restored==null || !restored.id().equals(event)
            || !restored.mobIds().contains(mob) || restored.x()!=42
            || !restored.phase().equals("COMBAT")) throw new AssertionError("Checkpoint failed");
        ThoridropCheckpoint.clear();
        if(ThoridropCheckpoint.read()!=null)throw new AssertionError("Checkpoint not cleared");
        System.out.println("Thoridrop checkpoint smoke test passed.");
    }
}
