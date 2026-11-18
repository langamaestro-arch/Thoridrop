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
    /** Ranked player rows, encoded without protocol newlines. */
    public static synchronized String rankingPage(String field,int page,int size) {
        java.util.Set<UUID> ids=new java.util.HashSet<>();
        for(String key:DATA.stringPropertyNames()) if(key.endsWith("."+field)) {
            try {ids.add(UUID.fromString(key.substring(0,key.length()-field.length()-1)));}
            catch(IllegalArgumentException ignored){}
        }
        java.util.List<UUID> ordered=new java.util.ArrayList<>(ids);
        ordered.sort(java.util.Comparator.comparingInt((UUID p)->count(p,field)).reversed()
            .thenComparing(p->DATA.getProperty(p+".name",p.toString())));
        int begin=Math.max(0,page)*size;
        if(begin>=ordered.size())return "Sin jugadores registrados";
        StringBuilder text=new StringBuilder();
        for(int i=begin;i<Math.min(ordered.size(),begin+size);i++) {
            UUID id=ordered.get(i);
            if(i>begin)text.append(" | ");
            text.append(i+1).append(". ")
                .append(DATA.getProperty(id+".name",id.toString().substring(0,8)))
                .append(": ").append(count(id,field));
        }
        return text.toString();
    }
    /** Retain only the last five completed challenges per player. */
    public static synchronized void recordHistory(UUID id,String tier,int seconds,
           int hits,int kills,int players,boolean flawless) {
        String current=DATA.getProperty(id+".history","");
        String now=java.time.LocalDate.now().toString()+" "+tier+" "+seconds+"s"
            +" "+kills+" bajas "+hits+" impactos "+players+" jugadores"
            +(flawless?" sin morir":"");
        java.util.List<String> records=new java.util.ArrayList<>();
        records.add(now);
        for(String record:current.split("\\|",-1)) {
            if(!record.isBlank() && records.size()<5)records.add(record);
        }
        DATA.setProperty(id+".history",String.join("|",records));
        save();
    }
    public static synchronized String history(UUID id) {
        return DATA.getProperty(id+".history","Sin desafíos registrados");
    }
    private static void save() {
        try {
            Path tmp=FILE.resolveSibling(FILE.getFileName()+".tmp");
            Files.createDirectories(FILE.getParent());
            try(var out=Files.newOutputStream(tmp)){DATA.store(out,"Thoridrop player statistics");}
            if(Files.exists(FILE))Files.copy(FILE,FILE.resolveSibling(FILE.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception e){throw new IllegalStateException("Cannot save Thoridrop statistics",e);}
    }
    /** Update a personal best in ticks; smaller values are better. */
    public static synchronized boolean bestTime(UUID id, int ticks) {
        if (ticks < 1) return false;
        int previous = count(id, "best_ticks");
        if (previous != 0 && previous <= ticks) return false;
        DATA.setProperty(id + ".best_ticks", Integer.toString(ticks));
        save();
        return true;
    }
    public static synchronized int addAndGet(UUID id, String field, int value) {
        if (value < 0) throw new IllegalArgumentException("Negative statistic");
        int next = Math.addExact(count(id, field), value);
        DATA.setProperty(id+"."+field,Integer.toString(next));
        save();
        return next;
    }
    public static synchronized int streak(UUID id, boolean won) {
        int next = won ? count(id,"streak") + 1 : 0;
        DATA.setProperty(id+".streak",Integer.toString(next));
        if (next > count(id,"best_streak")) DATA.setProperty(id+".best_streak",Integer.toString(next));
        save();
        return next;
    }
    public static synchronized String detailed(UUID id) {
        int ticks = count(id,"best_ticks");
        return summary(id) + " | Enemigos derrotados: "+count(id,"kills")
            + " | Impactos: "+count(id,"hits") + " | Racha: "+count(id,"streak")
            + " (máxima "+count(id,"best_streak")+")"
            + " | Mejor tiempo: "+(ticks==0 ? "sin récord" : (ticks/20)+"s")
            + " | Victorias cooperativas: "+count(id,"team_wins");
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
