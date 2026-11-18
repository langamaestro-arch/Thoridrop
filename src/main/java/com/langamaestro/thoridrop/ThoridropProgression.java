package com.langamaestro.thoridrop;

import java.nio.file.*;
import java.time.*;
import java.time.temporal.WeekFields;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

/** Persistent Thoridrop levels, prestige, named collections and rolling missions. */
public final class ThoridropProgression {
    private static final Path FILE=Paths.get("config","thoridrop-progression.properties");
    private static final Properties STATE=new Properties();
    private ThoridropProgression() {}
    public static synchronized void load() {
        STATE.clear();
        try {
            Files.createDirectories(FILE.getParent());
            if(Files.exists(FILE))try(var in=Files.newInputStream(FILE)){STATE.load(in);}
        }catch(Exception ex){throw new IllegalStateException("Thoridrop progression unreadable",ex);}
    }
    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp=FILE.resolveSibling(FILE.getFileName()+".tmp");
            try(var out=Files.newOutputStream(tmp)){STATE.store(out,"Thoridrop RPG progression and missions");}
            if(Files.exists(FILE))Files.copy(FILE,FILE.resolveSibling(FILE.getFileName()+".bak"),
                StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(tmp,FILE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception ex){throw new IllegalStateException("Cannot save Thoridrop progression",ex);}
    }
    private static int read(UUID id,String key) {
        try {return Math.max(0,Integer.parseInt(STATE.getProperty(id+"."+key,"0")));}
        catch(NumberFormatException ex){return 0;}
    }
    private static void set(UUID id,String key,int value) {
        STATE.setProperty(id+"."+key,Integer.toString(Math.max(0,value)));
    }
    public static synchronized int level(UUID id){return Math.min(100,1+read(id,"xp")/100);}
    public static synchronized int xp(UUID id){return read(id,"xp");}
    public static synchronized int prestige(UUID id){return read(id,"prestige");}
    public static synchronized String title(UUID id) {
        int lvl=level(id);
        return prestige(id)>0?"Prestigio "+prestige(id):lvl>=75?"Leyenda":lvl>=40?"Veterano":lvl>=15?"Cazador":"Recluta";
    }
    private static String today() {return LocalDate.now(ZoneOffset.UTC).toString();}
    private static String week() {
        LocalDate date=LocalDate.now(ZoneOffset.UTC);
        return date.getYear()+"-"+date.get(WeekFields.ISO.weekOfWeekBasedYear());
    }
    private static void rollover(UUID id) {
        String prefix=id+".";
        String today=today(),week=week();
        if(!today.equals(STATE.getProperty(prefix+"day",""))) {
            STATE.setProperty(prefix+"day",today);
            set(id,"d_wins",0);set(id,"d_kills",0);set(id,"d_claimed",0);
        }
        if(!week.equals(STATE.getProperty(prefix+"week",""))) {
            STATE.setProperty(prefix+"week",week);
            set(id,"w_wins",0);set(id,"w_legendary",0);set(id,"w_claimed",0);set(id,"w_legendary_claimed",0);
        }
    }
    public static synchronized void onVictory(UUID id,DropSession.Rarity rarity,
            int enemies,boolean flawless) {
        rollover(id);
        int gain=switch(rarity) {case COMMON->60;case RARE->130;case LEGENDARY->240;};
        if(flawless)gain+=30;
        set(id,"xp",Math.min(9900,read(id,"xp")+gain));
        set(id,"d_wins",read(id,"d_wins")+1);
        set(id,"w_wins",read(id,"w_wins")+1);
        if(rarity==DropSession.Rarity.LEGENDARY)
            set(id,"w_legendary",read(id,"w_legendary")+1);
        set(id,"d_kills",read(id,"d_kills")+enemies);
        save();
    }
    /** Tasks are paid only once per UTC day/week, even after reconnect. */
    public static synchronized int claimMissions(UUID id) {
        rollover(id);
        int points=0;
        if(read(id,"d_wins")>=1 && read(id,"d_claimed")==0) {
            set(id,"d_claimed",1);points+=15;
        }
        if(read(id,"w_wins")>=3 && read(id,"w_claimed")==0) {
            set(id,"w_claimed",1);points+=60;
        }
        if(read(id,"w_legendary")>=1 && read(id,"w_legendary_claimed")==0) {
            set(id,"w_legendary_claimed",1);points+=75;
        }
        if(points>0) {
            save();
            DropPoints.earn(id,points);
        } else save();
        return points;
    }
    public static synchronized String missions(UUID id) {
        rollover(id);
        return "Diaria 1 desafío "+read(id,"d_wins")+"/1 ["+(read(id,"d_claimed")>0?"COBRADA":"15 pts")+"]"
            +"|Semanal 3 desafíos "+read(id,"w_wins")+"/3 ["+(read(id,"w_claimed")>0?"COBRADA":"60 pts")+"]"
            +"|Semanal 1 legendario "+read(id,"w_legendary")+"/1 ["+(read(id,"w_legendary_claimed")>0?"COBRADA":"75 pts")+"]";
    }
    public static synchronized boolean prestige(ServerPlayer player) {
        UUID id=player.getUUID();
        if(level(id)<100) {
            player.sendSystemMessage(Component.literal("Thoridrop: alcanza nivel 100 antes de obtener prestigio."));
            return false;
        }
        set(id,"prestige",read(id,"prestige")+1);
        set(id,"xp",0);
        save();
        player.sendSystemMessage(Component.literal("§6✦ Prestigio Thoridrop "+prestige(id)+" desbloqueado."));
        return true;
    }
    public static synchronized void collect(UUID id,String key) {
        if(!key.matches("[a-z_]{1,32}"))return;
        STATE.setProperty(id+".collection."+key,"true");
        save();
    }
    public static synchronized String collection(UUID id) {
        return "Hacha mítica: "+(Boolean.parseBoolean(STATE.getProperty(id+".collection.mythic_axe"))?"✔":"○")
            +" | Hacha legendaria: "+(Boolean.parseBoolean(STATE.getProperty(id+".collection.legendary_axe"))?"✔":"○");
    }
}
