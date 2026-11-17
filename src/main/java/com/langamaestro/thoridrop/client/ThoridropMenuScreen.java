package com.langamaestro.thoridrop.client;

import com.langamaestro.thoridrop.ThoridropMenuNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public final class ThoridropMenuScreen extends Screen {
    private static final int GOLD=0xFFFFC56B, WHITE=0xFFF7E6CE, MUTED=0xFFB7A9C9;
    private static final String[] TABS={"Estadísticas","Logros","Clasificación","Mercado","Desafíos","Información"};
    private static String snapshot="";
    private int tab=0;
    private int x,y,w,h;
    private int marketPage=0,rankPage=0,rankMode=0,confirmId=-1;
    private static final String[] CATEGORIES={"Materiales","Armas","Armaduras","Herramientas","Consumibles"};
    private static final String[] NAMES={"Hierro x16","Diamantes x5","Manzanas doradas x4","Espada del guardián",
        "Peto de diamante","Pico de diamante","Arco mítico","Casco de netherita",
        "Zanahorias doradas x12","Esmeraldas x12","Hacha legendaria","Flechas x64"};
    private static final int[] COST={15,60,85,250,140,125,190,280,95,55,245,35};
    private static final int[] LEVEL={0,0,0,37,28,30,36,42,0,0,40,0};
    private static final String[] GROUP={"Materiales","Materiales","Consumibles","Armas",
        "Armaduras","Herramientas","Armas","Armaduras",
        "Consumibles","Materiales","Herramientas","Consumibles"};
    private static final net.minecraft.world.item.Item[] ICONS={
        Items.IRON_INGOT,Items.DIAMOND,Items.GOLDEN_APPLE,Items.NETHERITE_SWORD,
        Items.DIAMOND_CHESTPLATE,Items.DIAMOND_PICKAXE,Items.BOW,Items.NETHERITE_HELMET,
        Items.GOLDEN_CARROT,Items.EMERALD,Items.NETHERITE_AXE,Items.ARROW};

    public ThoridropMenuScreen() {
        super(Minecraft.getInstance(),Minecraft.getInstance().font,Component.literal("Thoridrop"));
    }

    public static void receive(String data) { snapshot = data; }
    private String[] rows() { return snapshot.split("\\n",-1); }
    private int[] stats() {
        int[] result=new int[16];
        String[] rows=rows();
        if(rows.length<2)return result;
        String[] parts=rows[1].split(",");
        for(int i=0;i<Math.min(result.length,parts.length);i++)
            try { result[i]=Integer.parseInt(parts[i]); } catch(NumberFormatException ignored) {}
        return result;
    }
    private int balance() {
        String[] r=rows();
        try { return Integer.parseInt(r[0]); } catch(Exception ignored) { return 0; }
    }
    private void refresh() {
        ClientPacketDistributor.sendToServer(new ThoridropMenuNetwork.MenuRequest(0));
    }

    @Override
    protected void init() {
        w=Math.min(this.width-14,500);
        h=Math.min(this.height-14,276);
        x=(this.width-w)/2;
        y=(this.height-h)/2;
        int left=120;
        for(int i=0;i<TABS.length;i++) {
            final int selected=i;
            addRenderableWidget(Button.builder(Component.literal(TABS[i]),button->{
                tab=selected;
                confirmId=-1;
                rebuildWidgets();
            }).bounds(x+12,y+41+i*30,left,23).build());
        }
        addRenderableWidget(Button.builder(Component.literal("↻"),button->refresh())
            .bounds(x+w-66,y+9,24,20).build());
        addRenderableWidget(Button.builder(Component.literal("X"),button->onClose())
            .bounds(x+w-38,y+9,24,20).build());
        if(tab==2) {
            addRenderableWidget(Button.builder(Component.literal("‹"),button->{
                rankPage=Math.max(0,rankPage-1); rebuildWidgets();
            }).bounds(x+153,y+h-36,26,20).build());
            addRenderableWidget(Button.builder(Component.literal("›"),button->{
                rankPage=Math.min(3,rankPage+1); rebuildWidgets();
            }).bounds(x+w-40,y+h-36,26,20).build());
            addRenderableWidget(Button.builder(Component.literal("Cambiar ranking"),button->{
                rankMode=(rankMode+1)%3; rankPage=0; rebuildWidgets();
            }).bounds(x+188,y+h-36,123,20).build());
        }
        if(tab==3) {
            addRenderableWidget(Button.builder(Component.literal("‹"),button->{
                marketPage=(marketPage+4)%5; confirmId=-1; rebuildWidgets();
            }).bounds(x+152,y+72,23,20).build());
            addRenderableWidget(Button.builder(Component.literal("›"),button->{
                marketPage=(marketPage+1)%5; confirmId=-1; rebuildWidgets();
            }).bounds(x+w-39,y+72,23,20).build());
            int row=0;
            for(int i=0;i<NAMES.length;i++) {
                if(!GROUP[i].equals(CATEGORIES[marketPage]))continue;
                final int action=i+1;
                final boolean isConfirm=confirmId==action;
                addRenderableWidget(Button.builder(
                    Component.literal(isConfirm?"Confirmar":("Comprar "+COST[i])),
                    button->{
                        if(confirmId!=action) {
                            confirmId=action;
                            rebuildWidgets();
                        } else {
                            confirmId=-1;
                            ClientPacketDistributor.sendToServer(
                                new ThoridropMenuNetwork.MenuRequest(action));
                            rebuildWidgets();
                        }
                    }).bounds(x+w-104,y+103+row*45,88,21).build());
                row++;
            }
        }
    }

    private void line(GuiGraphicsExtractor gfx,String line,int lx,int ly,int color) {
        gfx.text(font,line,lx,ly,color,true);
    }
    private void entry(GuiGraphicsExtractor gfx,String label,int value,int cx,int cy) {
        line(gfx,label+":  §e"+value,cx,cy,WHITE);
    }
    private void divider(GuiGraphicsExtractor gfx,int cy) {gfx.fill(x+147,cy,x+w-12,cy+1,0x667E4C9A);}

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx,int mouseX,int mouseY,float partialTick) {
        gfx.fill(0,0,width,height,0x9905000C);
        gfx.fill(x-2,y-2,x+w+2,y+h+2,0xFFB17D32);
        gfx.fill(x,y,x+w,y+h,0xFF170F27);
        gfx.fill(x+4,y+4,x+w-4,y+34,0xFF33204E);
        gfx.fill(x+139,y+38,x+141,y+h-9,0xFF986B35);
        gfx.fill(x+145,y+39,x+w-10,y+h-10,0xFF231832);
        gfx.text(font,"✦  THORIDROP  ✦",x+13,y+15,GOLD,true);
        gfx.text(font,"Puntos: "+balance(),x+152,y+15,0xFFFFDE8B,true);
        int[] a=stats();
        int cx=x+153, cy=y+50;
        switch(tab) {
            case 0 -> {
                line(gfx,"ESTADÍSTICAS PERSONALES",cx,cy,GOLD);
                entry(gfx,"Victorias",a[0],cx,cy+20);
                line(gfx,"Normal "+a[1]+"  Mítico "+a[2]+"  Legendario "+a[3],cx,cy+33,MUTED);
                entry(gfx,"Recompensas",a[4],cx,cy+49);
                entry(gfx,"Muertes",a[5],cx,cy+62);
                entry(gfx,"Sin morir",a[6],cx,cy+75);
                divider(gfx,cy+90);
                entry(gfx,"Enemigos",a[7],cx,cy+100);
                entry(gfx,"Impactos",a[8],cx,cy+113);
                entry(gfx,"Racha / Máxima",a[9],cx,cy+126);
                line(gfx,"Racha máxima "+a[10],cx,cy+139,MUTED);
                line(gfx,"Mejor tiempo: "+(a[11]<=0?"Sin récord":(a[11]/20)+"s"),cx,cy+152,WHITE);
                line(gfx,"Victorias cooperativas: "+a[12],cx,cy+165,WHITE);
                String[] rr=rows();
                if(rr.length>14)line(gfx,"Historial: "+font.plainSubstrByWidth(
                    rr[14].split("\\|")[0],w-175),cx,cy+181,MUTED);
            }
            case 1 -> {
                line(gfx,"LOGROS THORIDROP",cx,cy,GOLD);
                int i=1;
                for(int goal:new int[]{10,25,50,100}) {
                    line(gfx,(a[0]>=goal?"§a✔ ":"§7○ ")+"Veterano "+goal+" victorias ("+Math.min(a[0],goal)+"/"+goal+")",cx,cy+16*i++,WHITE);
                }
                line(gfx,(a[3]>0?"§a✔ ":"§7○ ")+"Conquistador legendario",cx,cy+85,WHITE);
                line(gfx,(a[6]>0?"§a✔ ":"§7○ ")+"Superviviente impecable",cx,cy+103,WHITE);
                line(gfx,(a[12]>0?"§a✔ ":"§7○ ")+"Victoria en equipo",cx,cy+121,WHITE);
                line(gfx,"Eventos especiales: "+a[14],cx,cy+149,MUTED);
                line(gfx,"Victorias contrarreloj: "+a[13],cx,cy+166,MUTED);
            }
            case 2 -> {
                String[] rr=rows();
                String caption=switch(rankMode) {
                    case 1 -> "VICTORIAS LEGENDARIAS";
                    case 2 -> "DESAFÍOS SIN MORIR";
                    default -> "VICTORIAS TOTALES";
                };
                line(gfx,caption,cx,cy,GOLD);
                int index=2+rankMode*4+rankPage;
                String current=rr.length>index?rr[index]:"Sin clasificaciones";
                String[] players=current.split(" \\| ");
                for(int i=0;i<Math.min(players.length,6);i++)
                    line(gfx,font.plainSubstrByWidth(players[i],w-182),cx,cy+24+i*24,WHITE);
                line(gfx,"Página "+(rankPage+1)+"/4",cx+5,cy+182,MUTED);
            }
            case 3 -> {
                line(gfx,"MERCADO DE RECOMPENSAS",cx,cy,GOLD);
                line(gfx,"Categoría: "+CATEGORIES[marketPage],cx+34,cy+30,0xFFEABDF6);
                int row=0;
                for(int i=0;i<NAMES.length;i++) {
                    if(!GROUP[i].equals(CATEGORIES[marketPage]))continue;
                    int yy=cy+53+row*45;
                    gfx.fill(cx-3,yy-3,x+w-11,yy+38,0xFF342444);
                    gfx.item(new ItemStack(ICONS[i]),cx+3,yy+5);
                    line(gfx,NAMES[i],cx+26,yy+7,WHITE);
                    line(gfx,"Precio: "+COST[i]+" pts"+
                        (LEVEL[i]>0?" • Encant. nivel "+LEVEL[i]:""),
                        cx+26,yy+20,MUTED);
                    row++;
                }
                line(gfx,"Compra con confirmación. Saldo: "+balance(),cx,cy+195,MUTED);
            }
            case 4 -> {
                line(gfx,"DESAFÍOS Y PROGRESO",cx,cy,GOLD);
                line(gfx,"Normal  •  Mítico  •  Legendario",cx,cy+21,WHITE);
                line(gfx,"Suministros invocados: "+a[15],cx,cy+41,MUTED);
                line(gfx,"Cooperativos completados: "+a[12],cx,cy+60,WHITE);
                line(gfx,"Modificadores posibles:",cx,cy+85,GOLD);
                line(gfx,"Horda  •  Velocidad  •  Botín",cx,cy+102,WHITE);
                line(gfx,"Élites  •  Contrarreloj",cx,cy+115,WHITE);
                line(gfx,"Oleada sorpresa",cx,cy+128,WHITE);
                line(gfx,"El jefe final conserva su IA original.",cx,cy+155,MUTED);
            }
            default -> {
                line(gfx,"INFORMACIÓN Y AYUDA",cx,cy,GOLD);
                line(gfx,"Tecla H: abrir y cerrar el menú",cx,cy+20,WHITE);
                line(gfx,"ESC: cerrar",cx,cy+36,WHITE);
                line(gfx,"Combate para ganar puntos.",cx,cy+60,WHITE);
                line(gfx,"Abre el cofre para cobrar.",cx,cy+76,WHITE);
                line(gfx,"Los puntos se guardan en el servidor.",cx,cy+103,WHITE);
                line(gfx,"Las compras se validan por jugador.",cx,cy+120,WHITE);
                line(gfx,"Los controles son configurables.",cx,cy+145,MUTED);
            }
        }
        super.extractRenderState(gfx,mouseX,mouseY,partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if(ThoridropMenuClient.OPEN.isActiveAndMatches(InputConstants.getKey(event))) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean isPauseScreen() {return false;}
}
