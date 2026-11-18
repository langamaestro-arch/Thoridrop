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
        return date.get(WeekFields.ISO.weekBasedYear())+"-"+date.get(WeekFields.ISO.weekOfWeekBasedYear());
    }
    private static void rollover(UUID id) {
        String prefix=id+".";
        String today=today(),week=week();
        if(!today.equals(STATE.getProperty(prefix+"day",""))) {
            STATE.setProperty(prefix+"day",today);
            set(id,"d_wins",0);set(id,"d_kills",0);set(id,"d_claimed",0);
            set(id,"d_flawless",0);set(id,"d_mythic",0);
            set(id,"d_kills_claimed",0);set(id,"d_flawless_claimed",0);set(id,"d_mythic_claimed",0);
        }
        if(!week.equals(STATE.getProperty(prefix+"week",""))) {
            STATE.setProperty(prefix+"week",week);
            set(id,"w_wins",0);set(id,"w_legendary",0);set(id,"w_claimed",0);set(id,"w_legendary_claimed",0);
            set(id,"w_kills",0);set(id,"w_coop",0);
            set(id,"w_kills_claimed",0);set(id,"w_coop_claimed",0);
        }
    }
    // Every objective is counted for the winning player, not just the owner of the crate.
    public static synchronized void onVictory(ServerPlayer player,DropSession.Rarity rarity,
                                               int enemies,boolean flawless,boolean cooperative) {
        UUID id=player.getUUID();
        rollover(id);
        int before=level(id);
        int gain=switch(rarity) {case COMMON->60;case RARE->130;case LEGENDARY->240;};
        if(flawless)gain+=30;
        set(id,"xp",Math.min(9900,read(id,"xp")+gain));
        set(id,"d_wins",read(id,"d_wins")+1);
        set(id,"w_wins",read(id,"w_wins")+1);
        if(rarity==DropSession.Rarity.LEGENDARY)
            set(id,"w_legendary",read(id,"w_legendary")+1);
        if(rarity!=DropSession.Rarity.COMMON)
            set(id,"d_mythic",read(id,"d_mythic")+1);
        if(flawless)set(id,"d_flawless",read(id,"d_flawless")+1);
        if(cooperative)set(id,"w_coop",read(id,"w_coop")+1);
        set(id,"d_kills",read(id,"d_kills")+Math.max(0,enemies));
        set(id,"w_kills",read(id,"w_kills")+Math.max(0,enemies));
        int after=level(id);
        java.util.List<Integer> rewards=new java.util.ArrayList<>();
        for(int milestone:new int[]{5,10,20,35,50,75,100}) {
            if(before<milestone && after>=milestone && read(id,"lv_reward_"+milestone)==0) {
                set(id,"lv_reward_"+milestone,1);
                rewards.add(milestone);
            }
        }
        save();
        for(int milestone:rewards)giveLevelReward(player,milestone);
    }

    private static void deliver(ServerPlayer player,net.minecraft.world.item.ItemStack prize) {
        if(!player.getInventory().add(prize) && !prize.isEmpty())
            player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                player.level(),player.getX(),player.getY(),player.getZ(),prize));
    }

    private static void giveLevelReward(ServerPlayer player,int milestone) {
        net.minecraft.world.item.Item item=switch(milestone) {
            case 5 -> net.minecraft.world.item.Items.IRON_INGOT;
            case 10 -> net.minecraft.world.item.Items.DIAMOND;
            case 20 -> net.minecraft.world.item.Items.GOLDEN_APPLE;
            case 35 -> net.minecraft.world.item.Items.DIAMOND_SWORD;
            case 50 -> net.minecraft.world.item.Items.NETHERITE_INGOT;
            case 75 -> net.minecraft.world.item.Items.NETHERITE_CHESTPLATE;
            default -> net.minecraft.world.item.Items.NETHER_STAR;
        };
        int amount=switch(milestone) {
            case 5 -> 12;case 10 -> 4;case 20 -> 5;case 50 -> 2;default -> 1;
        };
        int points=switch(milestone) {
            case 5 -> 50;case 10 -> 100;case 20 -> 200;case 35 -> 350;
            case 50 -> 500;case 75 -> 800;default -> 1200;
        };
        net.minecraft.world.item.ItemStack reward=new net.minecraft.world.item.ItemStack(item,amount);
        if(milestone==35 || milestone==75) {
            reward=net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(
                player.getRandom(),reward,milestone==75?45:30,
                player.level().registryAccess(),java.util.Optional.empty());
            reward.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("§6✦ Recompensa de nivel "+milestone+" Thoridrop"));
        }
        deliver(player,reward);
        DropPoints.earn(player.getUUID(),points);
        player.sendSystemMessage(Component.literal("§6✦ ¡NIVEL "+milestone+"! +"+points+
            " puntos y recompensa de progresión."));
    }

    private static int claim(UUID id,String counter,int required,String claimed,int value) {
        if(read(id,counter)<required || read(id,claimed)>0)return 0;
        set(id,claimed,1);
        return value;
    }

    /** Claiming is server-side and flags are saved before payout, so reconnect cannot duplicate it. */
    public static synchronized int claimMissions(UUID id) {
        rollover(id);
        int points=0;
        points+=claim(id,"d_wins",1,"d_claimed",15);
        points+=claim(id,"d_kills",20,"d_kills_claimed",25);
        points+=claim(id,"d_flawless",1,"d_flawless_claimed",30);
        points+=claim(id,"d_mythic",1,"d_mythic_claimed",40);
        points+=claim(id,"w_wins",3,"w_claimed",60);
        points+=claim(id,"w_legendary",1,"w_legendary_claimed",75);
        points+=claim(id,"w_kills",70,"w_kills_claimed",100);
        points+=claim(id,"w_coop",1,"w_coop_claimed",85);
        save();
        if(points>0)DropPoints.earn(id,points);
        return points;
    }

    private static String mission(UUID id,String period,String title,String counter,int target,
                                  String claimField,int points) {
        int progress=Math.min(read(id,counter),target);
        return period+" "+title+" "+progress+"/"+target+" ["+
            (read(id,claimField)>0?"COBRADA":points+" pts")+"]";
    }

    public static synchronized String missions(UUID id) {
        rollover(id);
        return String.join("|",
            mission(id,"Diaria","1 desafío ","d_wins",1,"d_claimed",15),
            mission(id,"Diaria","20 enemigos ","d_kills",20,"d_kills_claimed",25),
            mission(id,"Diaria","1 sin morir ","d_flawless",1,"d_flawless_claimed",30),
            mission(id,"Diaria","1 mítico o mejor ","d_mythic",1,"d_mythic_claimed",40),
            mission(id,"Semanal","3 desafíos ","w_wins",3,"w_claimed",60),
            mission(id,"Semanal","1 legendario ","w_legendary",1,"w_legendary_claimed",75),
            mission(id,"Semanal","70 enemigos ","w_kills",70,"w_kills_claimed",100),
            mission(id,"Semanal","1 cooperativo ","w_coop",1,"w_coop_claimed",85)
        );
    }

    public static synchronized boolean prestige(ServerPlayer player) {
        UUID id=player.getUUID();
        if(level(id)<100) {
            player.sendSystemMessage(Component.literal("Thoridrop: alcanza nivel 100 antes de obtener prestigio."));
            return false;
        }
        int rank=read(id,"prestige")+1;
        set(id,"prestige",rank);
        set(id,"xp",0);
        for(int milestone:new int[]{5,10,20,35,50,75,100})set(id,"lv_reward_"+milestone,0);
        save();
        int points=1500;
        DropPoints.earn(id,points);
        net.minecraft.world.item.ItemStack emblem=new net.minecraft.world.item.ItemStack(
            net.minecraft.world.item.Items.NETHER_STAR);
        emblem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
            Component.literal("§6✦ Emblema de prestigio Thoridrop "+rank));
        deliver(player,emblem);
        player.sendSystemMessage(Component.literal("§6✦ PRESTIGIO "+rank+
            " desbloqueado: +"+points+" puntos y un emblema especial."));
        return true;
    }

    public static synchronized void collect(UUID id,String key) {
        if(!key.matches("[a-z_]{1,32}"))return;
        STATE.setProperty(id+".collection."+key,"true");
        save();
    }
    public static synchronized String collection(UUID id) {
        String[] names={"Hacha mítica","Espada mítica","Pico mítico",
            "Arco mítico","Peto mítico","Pala mítica",
            "Hacha legendaria","Espada legendaria","Coraza legendaria",
            "Arco legendario","Pico legendario","Botas legendarias"};
        String[] ids={"mythic_axe","mythic_blade","mythic_tool",
            "mythic_bow","mythic_armor","mythic_shovel",
            "legendary_axe","legendary_blade","legendary_armor",
            "legendary_bow","legendary_tool","legendary_boots"};
        StringBuilder b=new StringBuilder();
        for(int i=0;i<ids.length;i++) {
            if(i>0)b.append("|");
            b.append(names[i]).append(": ").append(
                Boolean.parseBoolean(STATE.getProperty(id+".collection."+ids[i],"false"))?"✔":"○");
        }
        return b.toString();
    }
}
