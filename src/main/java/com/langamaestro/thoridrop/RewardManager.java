package com.langamaestro.thoridrop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

/** Server-side weighted personal loot tables; properties are reloadable without rebuilding. */
public final class RewardManager {
    private static final java.util.Random RANDOM = new java.util.Random();
    private static final Map<String,List<Entry>> TABLES = new HashMap<>();
    private static final Set<String> CLAIMS = new HashSet<>();
    private static final Path CLAIM_FILE = Paths.get("config","thoridrop-reward-claims.properties");
    private static final Path EQUIPMENT_FILE = Paths.get("config","thoridrop-equipment.properties");
    private static final Properties EQUIPMENT = new Properties();
    private static final Path LOOT_FILE = Paths.get("config","thoridrop-loot.properties");
    private record Entry(String id,int min,int max,int chance) {}

    private RewardManager() {}

    public static void load() {
        Properties p = new Properties();
        p.setProperty("normal","minecraft:iron_ingot,4,8,100;minecraft:bread,8,16,100;minecraft:iron_sword,1,1,65;minecraft:iron_chestplate,1,1,35;minecraft:iron_pickaxe,1,1,45;minecraft:iron_axe,1,1,35;minecraft:iron_helmet,1,1,25;minecraft:iron_leggings,1,1,25;minecraft:iron_boots,1,1,25;minecraft:emerald,1,3,35;thoridrop:supply_fragment,1,2,100");
        p.setProperty("mythic","minecraft:diamond,4,8,100;minecraft:golden_apple,2,4,100;minecraft:experience_bottle,12,24,100;minecraft:diamond_sword,1,1,60;minecraft:diamond_chestplate,1,1,45;minecraft:diamond_pickaxe,1,1,45;thoridrop:supply_fragment,4,7,100;thoridrop:victory_trophy,1,1,25");
        p.setProperty("legendary","minecraft:diamond,10,16,100;minecraft:enchanted_golden_apple,1,2,80;minecraft:netherite_scrap,2,4,100;minecraft:experience_bottle,24,48,100;minecraft:netherite_sword,1,1,70;minecraft:netherite_chestplate,1,1,65;minecraft:netherite_pickaxe,1,1,65;minecraft:netherite_helmet,1,1,45;minecraft:netherite_leggings,1,1,45;minecraft:netherite_boots,1,1,45;thoridrop:supply_fragment,8,12,100;thoridrop:victory_trophy,1,1,100");
        try {
            Files.createDirectories(LOOT_FILE.getParent());
            if (Files.exists(LOOT_FILE)) try (var in=Files.newInputStream(LOOT_FILE)) { p.load(in); }
            Map<String,List<Entry>> parsed = new HashMap<>();
            for (String tier : List.of("normal","mythic","legendary")) {
                List<Entry> entries = new ArrayList<>();
                for (String spec : p.getProperty(tier,"").split(";")) {
                    String[] v=spec.trim().split(",");
                    if (v.length!=4) continue;
                    try {
                        int min=Integer.parseInt(v[1].trim()),max=Integer.parseInt(v[2].trim()),chance=Integer.parseInt(v[3].trim());
                        String id=v[0].trim();
                        if (min<1 || max<min || max>64 || chance<0 || chance>100) continue;
                        Identifier key=Identifier.parse(id);
                        if (!BuiltInRegistries.ITEM.containsKey(key)) continue;
                        entries.add(new Entry(id,min,max,chance));
                    } catch (Exception ignored) {}
                }
                if (entries.isEmpty()) throw new IllegalArgumentException("No valid loot entries for "+tier);
                parsed.put(tier,List.copyOf(entries));
            }
            TABLES.clear();TABLES.putAll(parsed);
            try(var out=Files.newOutputStream(LOOT_FILE)) {p.store(out,"Thoridrop loot: item_id,min,max,chance_percent; entries separated by semicolons. /thoridrop reload");}
        } catch(Exception e) { throw new IllegalStateException("Cannot load Thoridrop loot configuration",e); }
        EQUIPMENT.clear();
        EQUIPMENT.setProperty("mythic.enchant_level","22");
        EQUIPMENT.setProperty("legendary.enchant_level","42");
        EQUIPMENT.setProperty("mythic.guaranteed_items","1");
        EQUIPMENT.setProperty("legendary.guaranteed_items","2");
        try {
            if(Files.exists(EQUIPMENT_FILE)) try(var in=Files.newInputStream(EQUIPMENT_FILE)){EQUIPMENT.load(in);}
            try(var out=Files.newOutputStream(EQUIPMENT_FILE)){EQUIPMENT.store(out,"Thoridrop enchanted equipment: enchant level and guaranteed equipment pieces per player");}
        } catch(Exception e){throw new IllegalStateException("Cannot load equipment configuration",e);}
        try {
            if(Files.exists(CLAIM_FILE)) for(String line:Files.readAllLines(CLAIM_FILE)) if(!line.isBlank()&&!line.startsWith("#")) CLAIMS.add(line.trim());
        } catch(Exception e) {throw new IllegalStateException("Cannot read Thoridrop reward claims",e);}
    }

    public static boolean hasClaimed(UUID event,UUID player) {return CLAIMS.contains(event+":"+player);}

    /** Write the claim first, atomically, to avoid duplicate rewards after server restarts. */
    public static boolean claim(UUID event,UUID player) {
        String key=event+":"+player;
        if(CLAIMS.contains(key)) return false;
        Set<String> next=new HashSet<>(CLAIMS);next.add(key);
        try {
            Files.createDirectories(CLAIM_FILE.getParent());
            Path temp=CLAIM_FILE.resolveSibling(CLAIM_FILE.getFileName()+".tmp");
            Files.write(temp,next,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            try {Files.move(temp,CLAIM_FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,CLAIM_FILE,StandardCopyOption.REPLACE_EXISTING);}
            CLAIMS.add(key);return true;
        } catch(Exception e){throw new IllegalStateException("Failed to persist Thoridrop reward claim",e);}
    }

    private static int equipmentInt(String key,int fallback) {
        try{return Math.max(0,Math.min(60,Integer.parseInt(EQUIPMENT.getProperty(key,Integer.toString(fallback)))));}catch(Exception e){return fallback;}
    }
    private static void deliver(ServerPlayer player,ItemStack stack) {
        if(!player.getInventory().add(stack)) {
            ItemEntity dropped=new ItemEntity(player.level(),player.getX(),player.getY(),player.getZ(),stack);
            dropped.setPickUpDelay(20);
            player.level().addFreshEntity(dropped);
        }
    }
    public static void give(ServerPlayer player,DropSession.Rarity rarity) {
        String tier=switch(rarity){case COMMON->"normal";case RARE->"mythic";case LEGENDARY->"legendary";};
        List<Entry> entries=TABLES.get(tier);
        if(entries==null) throw new IllegalStateException("Loot config not initialized");
        int delivered=0;
        for(Entry entry:entries) {
            if(RANDOM.nextInt(100)>=entry.chance()) continue;
            Item item=BuiltInRegistries.ITEM.getValue(Identifier.parse(entry.id()));
            if(item==null) continue;
            ItemStack stack=new ItemStack(item,entry.min()+RANDOM.nextInt(entry.max()-entry.min()+1));
            if(!tier.equals("normal") && stack.isEnchantable()) {
                stack = net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(player.getRandom(),stack, equipmentInt(tier+".enchant_level",tier.equals("legendary")?42:22),player.level().registryAccess(),java.util.Optional.empty());
            }
            deliver(player,stack);
            /*if(!player.getInventory().add(stack)) {
                ItemEntity dropped=new ItemEntity(player.level(),player.getX(),player.getY(),player.getZ(),stack);
                dropped.setPickUpDelay(20);
                player.level().addFreshEntity(dropped);
            }*/
            delivered++;
        }
        if(!tier.equals("normal")) {
            String[] pool=tier.equals("legendary") ? new String[]{"minecraft:netherite_sword","minecraft:netherite_chestplate","minecraft:netherite_pickaxe","minecraft:netherite_helmet","minecraft:netherite_leggings","minecraft:netherite_boots","minecraft:bow"} : new String[]{"minecraft:diamond_sword","minecraft:diamond_chestplate","minecraft:diamond_pickaxe","minecraft:diamond_helmet","minecraft:diamond_leggings","minecraft:diamond_boots","minecraft:bow"};
            int guaranteed=equipmentInt(tier+".guaranteed_items",tier.equals("legendary")?2:1);
            for(int i=0;i<guaranteed;i++) {
                Item item=BuiltInRegistries.ITEM.getValue(Identifier.parse(pool[RANDOM.nextInt(pool.length)]));
                if(item==null)continue;
                ItemStack stack=new ItemStack(item);
                stack=net.minecraft.world.item.enchantment.EnchantmentHelper.enchantItem(player.getRandom(),stack,equipmentInt(tier+".enchant_level",42),player.level().registryAccess(),java.util.Optional.empty());
                deliver(player,stack);
                delivered++;
            }
        }
        player.sendSystemMessage(Component.literal("Thoridrop: "+delivered+" recompensas personales entregadas."));
    }
}
