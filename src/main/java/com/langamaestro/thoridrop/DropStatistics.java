package com.langamaestro.thoridrop;

import java.nio.file.*;
import java.util.*;

/** Durable per-player event statistics, stored outside the world for server admins. */
public final class DropStatistics {
    private static final Path FILE=Paths.get("config","thoridrop-statistics.properties");
    private static final Properties DATA=new Properties();
    private DropStatistics(){}
    public static synchronized void load() {
        DATA.clear();
        try {
            Files.createDirectories(FILE.getParent());
            if(Files.exists(FILE))try(var in=Files.newInputStream(FILE)){DATA.load(in);}
        }catch(Exception e){throw new IllegalStateException("Cannot load Thoridrop statistics",e);}
    }
    private static int count(UUID id,String field) {
        try{return Integer.parseInt(DATA.getProperty(id+"."+field,"0"));}
        catch(NumberFormatException e){return 0;}
    }
    public static synchronized void remember(UUID id, String name) {
        String safe = name.replaceAll("[^\\p{L}\\p{N}_-]", "").substring(0, Math.min(32, name.replaceAll("[^\\p{L}\\p{N}_-]", "").length()));
        if (!safe.isBlank()) { DATA.setProperty(id+".name",safe); save(); }
    }
    public static synchronized int value(UUID id, String field) { return count(id,field); }
    public static synchronized String leaderboard(String field, int limit) {
        java.util.List<UUID> ids = new java.util.ArrayList<>();
        for (String key : DATA.stringPropertyNames()) if (key.endsWith("."+field)) {
            try { ids.add(UUID.fromString(key.substring(0,key.length()-field.length()-1))); }
            catch (IllegalArgumentException ignored) { }
        }
        ids.sort(java.util.Comparator.comparingInt((UUID id)->count(id,field)).reversed().thenComparing(UUID::toString));
        StringBuilder s = new StringBuilder("§6✦ THORIDROP §rRanking "+field+": ");
        for (int i=0;i<Math.min(limit,ids.size());i++) {
            UUID id=ids.get(i);
            if(i>0)s.append(" | ");
            s.append(i+1).append(". ").append(DATA.getProperty(id+".name",id.toString().substring(0,8)))
             .append(" (").append(count(id,field)).append(")");
        }
        return ids.isEmpty() ? "Todavía no hay resultados en el ranking." : s.toString();
    }
    private static void save() {
        try {
            Path tmp=FILE.resolveSibling(FILE.getFileName()+".tmp");
            Files.createDirectories(FILE.getParent());
            try(var out=Files.newOutputStream(tmp)){DATA.store(out,"Thoridrop player statistics");}
            try{Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception e){throw new IllegalStateException("Cannot save Thoridrop statistics",e);}
    }
    public static synchronized void add(UUID id,String field) {
        DATA.setProperty(id+"."+field,Integer.toString(count(id,field)+1));
        save();
    }
    public static synchronized String summary(UUID id) {
        return "§6✦ THORIDROP §r| Victorias: " + count(id,"wins")
            + " §7(Normal " + count(id,"wins_common")
            + ", Mítico " + count(id,"wins_rare")
            + ", Legendario " + count(id,"wins_legendary") + ")§r"
            + " | Recompensas: " + count(id,"claims")
            + " | Muertes: " + count(id,"deaths")
            + " | Suministros invocados: " + count(id,"events_started");
    }
}
