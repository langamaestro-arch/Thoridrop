package com.langamaestro.thoridrop;

import java.nio.file.*;
import java.util.*;

/** Durable event checkpoint; recovery restarts interrupted combat, never replays claimed loot. */
public final class ThoridropCheckpoint {
    private static final Path FILE=Paths.get("config","thoridrop-active-event.properties");
    public record Snapshot(UUID id,String dimension,int x,int y,int z,
                           DropSession.Rarity rarity,String phase,String mobs) {
        public Snapshot(UUID id,String dimension,int x,int y,int z,
                        DropSession.Rarity rarity,String phase) {
            this(id,dimension,x,y,z,rarity,phase,"");
        }
        public java.util.List<UUID> mobIds() {
            java.util.List<UUID> result=new java.util.ArrayList<>();
            for(String entry:mobs.split(",")) {
                try {result.add(UUID.fromString(entry));}
                catch(IllegalArgumentException ignored) {}
            }
            return result;
        }
    }
    private ThoridropCheckpoint(){}
    public static synchronized void write(Snapshot snapshot) {
        Properties p=new Properties();
        p.setProperty("id",snapshot.id().toString());
        p.setProperty("dimension",snapshot.dimension());
        p.setProperty("x",Integer.toString(snapshot.x()));
        p.setProperty("y",Integer.toString(snapshot.y()));
        p.setProperty("z",Integer.toString(snapshot.z()));
        p.setProperty("rarity",snapshot.rarity().name());
        p.setProperty("phase",snapshot.phase());
        p.setProperty("mobs",snapshot.mobs());
        try{
            Files.createDirectories(FILE.getParent());
            Path tmp=FILE.resolveSibling(FILE.getFileName()+".tmp");
            try(var out=Files.newOutputStream(tmp)){p.store(out,"Thoridrop active challenge checkpoint");}
            try{Files.move(tmp,FILE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception e){throw new IllegalStateException("Cannot checkpoint Thoridrop",e);}
    }
    public static synchronized Snapshot read() {
        if(!Files.exists(FILE))return null;
        try{
            Properties p=new Properties();
            try(var in=Files.newInputStream(FILE)){p.load(in);}
            return new Snapshot(UUID.fromString(p.getProperty("id")),
                p.getProperty("dimension",""),Integer.parseInt(p.getProperty("x")),
                Integer.parseInt(p.getProperty("y")),Integer.parseInt(p.getProperty("z")),
                DropSession.Rarity.valueOf(p.getProperty("rarity")),p.getProperty("phase","AVAILABLE"),p.getProperty("mobs",""));
        }catch(Exception e){throw new IllegalStateException("Corrupt Thoridrop event checkpoint",e);}
    }
    public static synchronized void clear() {
        try{Files.deleteIfExists(FILE);}
        catch(Exception e){throw new IllegalStateException("Could not clear Thoridrop checkpoint",e);}
    }
}
