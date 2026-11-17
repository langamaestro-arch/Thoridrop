package com.langamaestro.thoridrop;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.item.ItemEntity;

/** Fixed-priced, server-defined catalogue. Never accept prices or item ids from the client. */
public final class ThoridropShop {
    public record Offer(int id, String group, String title, int cost, Item item, int amount, int enchantLevel) {}
    private static final List<Offer> OFFERS = List.of(
        new Offer(1,"Materiales","Hierro x16",15,Items.IRON_INGOT,16,0),
        new Offer(2,"Materiales","Diamantes x5",60,Items.DIAMOND,5,0),
        new Offer(3,"Consumibles","Manzanas doradas x4",85,Items.GOLDEN_APPLE,4,0),
        new Offer(4,"Armas","Espada del guardián",250,Items.NETHERITE_SWORD,1,37),
        new Offer(5,"Armaduras","Peto de diamante",140,Items.DIAMOND_CHESTPLATE,1,28),
        new Offer(6,"Herramientas","Pico de diamante",125,Items.DIAMOND_PICKAXE,1,30),
        new Offer(7,"Armas","Arco mítico",190,Items.BOW,1,36),
        new Offer(8,"Armaduras","Casco de netherita",280,Items.NETHERITE_HELMET,1,42),
        new Offer(9,"Consumibles","Zanahorias doradas x12",95,Items.GOLDEN_CARROT,12,0),
        new Offer(10,"Materiales","Esmeraldas x12",55,Items.EMERALD,12,0),
        new Offer(11,"Herramientas","Hacha legendaria",245,Items.NETHERITE_AXE,1,40),
        new Offer(12,"Consumibles","Flechas x64",35,Items.ARROW,64,0)
    );
    private ThoridropShop() {}
    public static List<Offer> offers() { return OFFERS; }
    public static Offer offer(int id) {
        return OFFERS.stream().filter(o -> o.id() == id).findFirst().orElse(null);
    }
    public static ItemStack preview(int id) {
        Offer o=offer(id);
        return o==null?ItemStack.EMPTY:new ItemStack(o.item(),o.amount());
    }
    public static int buy(ServerPlayer player, int id) {
        Offer o=offer(id);
        if(o==null)return 0;
        if(DropPoints.balance(player.getUUID())<o.cost()) {
            player.sendSystemMessage(Component.literal("§cThoridrop: faltan "+
                (o.cost()-DropPoints.balance(player.getUUID()))+" puntos."));
            return 0;
        }
        ItemStack reward=new ItemStack(o.item(),o.amount());
        if(o.enchantLevel()>0) {
            reward=EnchantmentHelper.enchantItem(player.getRandom(),reward,o.enchantLevel(),
                player.level().registryAccess(),java.util.Optional.empty());
            reward.set(DataComponents.CUSTOM_NAME,Component.literal("§6✦ "+o.title()));
        }
        // Balance is persisted before delivery. On failure no second grant is possible.
        if(!DropPoints.spend(player.getUUID(),o.cost()))return 0;
        if(!player.getInventory().add(reward) && !reward.isEmpty())
            player.level().addFreshEntity(new ItemEntity(player.level(),player.getX(),player.getY(),player.getZ(),reward));
        DropStatistics.add(player.getUUID(),"purchases");
        player.sendSystemMessage(Component.literal("§6✦ Compra: "+o.title()+
            " (-"+o.cost()+" puntos). Saldo: "+DropPoints.balance(player.getUUID())));
        return 1;
    }
}
