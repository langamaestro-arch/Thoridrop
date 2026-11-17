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
                rebuildWidgets();
            }).bounds(x+12,y+41+i*30,left,23).build());
        }
        addRenderableWidget(Button.builder(Component.literal("↻"),button->refresh())
            .bounds(x+w-66,y+9,24,20).build());
        addRenderableWidget(Button.builder(Component.literal("X"),button->onClose())
            .bounds(x+w-38,y+9,24,20).build());
        if(tab==3) {
            String[] names={"Hierro","Diamantes","Manzanas","Arma especial"};
            int[] prices={15,60,85,250};
            for(int i=0;i<4;i++) {
                final int action=i+1;
                addRenderableWidget(Button.builder(Component.literal("Comprar "+prices[i]),
                    button->ClientPacketDistributor.sendToServer(new ThoridropMenuNetwork.MenuRequest(action)))
                    .bounds(x+w-106,y+53+i*40,93,22).build());
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
                line(gfx,"CLASIFICACIÓN DEL SERVIDOR",cx,cy,GOLD);
                String[] rr=rows();
                for(int k=0;k<3;k++) {
                    int yy=cy+21+k*55;
                    line(gfx,k==0?"Victorias":k==1?"Legendarios":"Sin morir",cx,yy,0xFFEABDF6);
                    if(rr.length>k+2) {
                        String[] parts=rr[k+2].replace("§6✦ THORIDROP §r","").split(" \\| ");
                        for(int j=0;j<Math.min(2,parts.length);j++)
                            line(gfx,font.plainSubstrByWidth(parts[j],w-174),cx,yy+12+j*11,WHITE);
                    }
                }
            }
            case 3 -> {
                line(gfx,"MERCADO DE RECOMPENSAS",cx,cy,GOLD);
                ItemStack[] icons={new ItemStack(Items.IRON_INGOT),new ItemStack(Items.DIAMOND),
                    new ItemStack(Items.GOLDEN_APPLE),new ItemStack(Items.NETHERITE_SWORD)};
                String[] names={"16 lingotes de hierro","5 diamantes","4 manzanas doradas","Espada encantada"};
                for(int i=0;i<4;i++) {
                    int yy=cy+23+i*40;
                    gfx.fill(cx-3,yy-3,x+w-11,yy+33,0xFF342444);
                    gfx.item(icons[i],cx+3,yy+4);
                    line(gfx,names[i],cx+25,yy+9,WHITE);
                }
                line(gfx,"Compras verificadas por el servidor.",cx,cy+193,MUTED);
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
