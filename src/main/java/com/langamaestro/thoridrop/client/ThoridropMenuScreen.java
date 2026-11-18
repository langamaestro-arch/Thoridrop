package com.langamaestro.thoridrop.client;

import com.langamaestro.thoridrop.ThoridropMenuNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import java.util.ArrayList;
import java.util.List;

/** The warm parchment, ruby border and framed-panel layout of the EvolutionCraft guide.
 * All prices are display-only; the dedicated server owns purchase decisions.
 */
public final class ThoridropMenuScreen extends Screen {
    private static final int INK=0xFF694022, DARK=0xFF54301B, GOLD=0xFFD58B38;
    private static final int LIGHT=0xFFFFD377, CREAM=0xFFFFE8BA, PAPER=0xFFFFF0D1;
    private static final int PANEL=0xFFF8E3BE, EDGE=0xFF9F4A2F, RED=0xFFAD4A3A;
    private static final int GREEN=0xFF4D9B51, MUTED=0xFF956B49;
    private static final String[] TABS={"Estadísticas","Logros","Clasificación","Mercado","Desafíos","Misiones","Colección","Información"};
    private static final Item[] TAB_ITEMS={Items.BOOK,Items.NETHER_STAR,Items.PAPER,Items.EMERALD,
        Items.IRON_SWORD,Items.CLOCK,Items.NETHERITE_AXE,Items.COMPASS};
    private static final String[] CATEGORIES={"Materiales","Armas","Armaduras","Herramientas","Consumibles"};
    private static final String[] NAMES={"Hierro x16","Diamantes x5","Manzanas doradas x4","Espada del guardián",
        "Peto de diamante","Pico de diamante","Arco mítico","Casco de netherita",
        "Zanahorias doradas x12","Esmeraldas x12","Hacha legendaria","Flechas x64"};
    private static final int[] COST={60,240,340,1000,560,500,760,1120,380,220,980,140};
    private static final int[] LEVEL={0,0,0,37,28,30,36,42,0,0,40,0};
    private static final String[] GROUP={"Materiales","Materiales","Consumibles","Armas",
        "Armaduras","Herramientas","Armas","Armaduras","Consumibles",
        "Materiales","Herramientas","Consumibles"};
    private static final Item[] ICONS={Items.IRON_INGOT,Items.DIAMOND,Items.GOLDEN_APPLE,Items.NETHERITE_SWORD,
        Items.DIAMOND_CHESTPLATE,Items.DIAMOND_PICKAXE,Items.BOW,Items.NETHERITE_HELMET,
        Items.GOLDEN_CARROT,Items.EMERALD,Items.NETHERITE_AXE,Items.ARROW};
    private static String snapshot="";
    private int tab=0,marketPage=0,rankPage=0,rankMode=0,confirmId=-1;
    private int x,y,w,h,cx,cy,side,tabHeight;
    private final List<PaintButton> paintButtons=new ArrayList<>();
    private record PaintButton(int bx,int by,int bw,int bh,String label,Item icon,boolean active,boolean enabled) {}
    public ThoridropMenuScreen() {
        super(Minecraft.getInstance(),Minecraft.getInstance().font,Component.literal("Thoridrop"));
    }
    public static void receive(String data){snapshot=data;}
    private String[] rows(){return snapshot.split("\\n",-1);}
    private int[] stats(){
        int[] v=new int[16];String[] rr=rows();
        if(rr.length<2)return v;
        String[] parts=rr[1].split(",");
        for(int i=0;i<Math.min(v.length,parts.length);i++)
            try{v[i]=Integer.parseInt(parts[i]);}catch(NumberFormatException ignored){}
        return v;
    }
    private int balance(){try{return Integer.parseInt(rows()[0]);}catch(Exception ignored){return 0;}}
    private int progressLevel(){try{return Integer.parseInt(rows()[15].split(",")[0]);}catch(Exception e){return 1;}}
    private int progressXp(){try{return Integer.parseInt(rows()[15].split(",")[1]);}catch(Exception e){return 0;}}
    private int prestigeCount(){try{return Integer.parseInt(rows()[15].split(",")[2]);}catch(Exception e){return 0;}}
    private String rankTitle(){try{return rows()[15].split(",")[3];}catch(Exception e){return "Recluta";}}
    private void refresh(){ClientPacketDistributor.sendToServer(new ThoridropMenuNetwork.MenuRequest(0));}
    private void action(int n){ClientPacketDistributor.sendToServer(new ThoridropMenuNetwork.MenuRequest(n));}
    private void addStyled(String text,int bx,int by,int bw,int bh,Item icon,boolean selected,Runnable click) {
        addStyled(text,bx,by,bw,bh,icon,selected,true,click);
    }
    private void addStyled(String text,int bx,int by,int bw,int bh,Item icon,boolean selected,
                           boolean enabled,Runnable click) {
        Button nativeButton=Button.builder(Component.literal(text),button->click.run())
            .bounds(bx,by,bw,bh).build();
        nativeButton.active=enabled;
        addRenderableWidget(nativeButton);
        paintButtons.add(new PaintButton(bx,by,bw,bh,text,icon,selected,enabled));
    }
    @Override protected void init() {
        w=Math.min(width-14,620); h=Math.min(height-12,440);
        x=(width-w)/2;y=(height-h)/2;
        side=Math.max(122,Math.min(170,w*29/100));
        tabHeight=Math.max(24,(h-112)/8);
        cx=x+side+34;cy=y+109;
        paintButtons.clear();
        for(int i=0;i<TABS.length;i++) {
            final int selected=i;
            addStyled(TABS[i],x+25,y+121+i*(tabHeight-4),side-38,tabHeight-9,
                TAB_ITEMS[i],tab==i,()->{tab=selected;confirmId=-1;rebuildWidgets();});
        }
        addStyled("X",x+w-49,y+21,27,23,null,false,this::onClose);
        addStyled("↻",x+w-83,y+21,27,23,null,false,this::refresh);
        if(tab==0 && progressLevel()>=100)
            addStyled("Obtener prestigio",x+w-162,y+h-46,142,21,null,true,()->action(20));
        if(tab==2) {
            addStyled("<",cx, y+h-48,25,24,null,false,()->{rankPage=Math.max(0,rankPage-1);rebuildWidgets();});
            addStyled(">",x+w-48,y+h-48,25,24,null,false,()->{rankPage=Math.min(3,rankPage+1);rebuildWidgets();});
            addStyled("Cambiar ranking",cx+37,y+h-48,125,24,null,true,()->{
                rankMode=(rankMode+1)%3;rankPage=0;rebuildWidgets();});
        }
        if(tab==3) {
            addStyled("<",cx,y+144,25,22,null,false,()->{
                marketPage=(marketPage+4)%5;confirmId=-1;rebuildWidgets();});
            addStyled(">",x+w-48,y+144,25,22,null,false,()->{
                marketPage=(marketPage+1)%5;confirmId=-1;rebuildWidgets();});
            int row=0;
            for(int i=0;i<NAMES.length;i++) {
                if(!GROUP[i].equals(CATEGORIES[marketPage]))continue;
                final int id=i+1;
                boolean affordable=balance()>=COST[i];
                addStyled(!affordable?"Sin puntos":confirmId==id?"Confirmar":"Comprar "+COST[i],
                    x+w-121,y+191+row*51,97,22,null,confirmId==id,affordable,()->{
                        if(confirmId!=id)confirmId=id;
                        else {confirmId=-1;action(id);}
                        rebuildWidgets();
                    });
                row++;
            }
        }
    }
    private void line(GuiGraphicsExtractor g,String t,int px,int py,int color){
        g.text(font,t,px,py,color,false);
    }
    private void centered(GuiGraphicsExtractor g,String t,int centerX,int py,int color){
        g.centeredText(font,t,centerX,py,color);
    }
    private void frame(GuiGraphicsExtractor g,int a,int b,int c,int d,int fill) {
        g.fill(a-3,b-3,c+3,d+3,RED);
        g.fill(a-1,b-1,c+1,d+1,LIGHT);
        g.fill(a,b,c,d,fill);
        g.outline(a,b,c-a,d-b,EDGE);
    }
    private void decorative(GuiGraphicsExtractor g) {
        g.fill(0,0,width,height,0xB9000000);
        // Pixel-layered ruby-and-gold external border; cream interior matching EvolutionCraft.
        g.fill(x-6,y-6,x+w+6,y+h+6,0xFF6E2E35);
        g.fill(x-4,y-4,x+w+4,y+h+4,0xFFE7B45A);
        g.fill(x-2,y-2,x+w+2,y+h+2,0xFF9D4931);
        g.fill(x,y,x+w,y+h,CREAM);
        for(int i=0;i<w;i+=16){
            g.fill(x+i,y-4,x+i+8,y-2,LIGHT);
            g.fill(x+i,y+h+2,x+i+8,y+h+4,LIGHT);
        }
        for(int i=0;i<h;i+=14){
            g.fill(x-4,y+i,x-2,y+i+7,LIGHT);
            g.fill(x+w+2,y+i,x+w+4,y+i+7,LIGHT);
        }
        frame(g,x+15,y+17,x+w-15,y+78,0xFFFFD879);
        g.fill(x+23,y+25,x+w-23,y+30,0xFFFFE6A6);
        g.fill(x+23,y+70,x+w-23,y+73,GOLD);
        g.item(new ItemStack(Items.ENCHANTED_BOOK),x+28,y+35);
        line(g,"✦  T H O R I D R O P  ✦",x+56,y+32,DARK);
        line(g,"C E N T R O   D E L   A V E N T U R E R O",x+56,y+49,MUTED);
        line(g,"Puntos: "+balance(),x+w-185,y+36,INK);
        frame(g,x+15,y+91,x+side+7,y+h-18,PANEL);
        frame(g,x+side+17,y+91,x+w-15,y+h-18,PAPER);
        // Section label lives inside the side panel, rather than crossing its top edge.
        g.fill(x+27,y+96,x+side-3,y+98,GOLD);
        line(g,"SECCIONES",x+34,y+99,DARK);
        // Subtle row separators and corner jewels.
        for(int i=0;i<4;i++){
            int fx=(i%2==0)?x+8:x+w-10;
            int fy=(i<2)?y+8:y+h-10;
            g.fill(fx-2,fy-2,fx+3,fy+3,GOLD);
            g.fill(fx,fy,fx+2,fy+2,0xFFAC4D85);
        }
        g.fill(x+side+29,y+97,x+w-26,y+98,GOLD);
        // Seven personal milestones: 10/25/50/100 wins, legendary, flawless and team win.
        int[] totals=stats();
        int achieved=(totals[0]>=10?1:0)+(totals[0]>=25?1:0)
            +(totals[0]>=50?1:0)+(totals[0]>=100?1:0)
            +(totals[3]>0?1:0)+(totals[6]>0?1:0)+(totals[12]>0?1:0);
        int progressX=x+w-198,progressY=y+63,progressW=115;
        line(g,"PROGRESO "+achieved+"/7",progressX,y+47,DARK);
        g.fill(progressX,progressY,progressX+progressW,progressY+6,0xFFC19A71);
        g.fill(progressX+1,progressY+1,progressX+1+(progressW-2)*achieved/7,progressY+5,GREEN);
    }
    private void section(GuiGraphicsExtractor g,String heading,Item icon){
        g.fill(cx-7,cy-17,x+w-25,cy+23,0xFFFFE1AC);
        g.fill(cx-7,cy-17,x+w-25,cy-14,GOLD);
        if(icon!=null)g.item(new ItemStack(icon),cx,cy-6);
        line(g,heading,cx+(icon==null?3:23),cy-4,DARK);
    }
    private void strip(GuiGraphicsExtractor g,int px,int py,int ww,int hh,boolean success){
        g.fill(px,py,px+ww,py+hh,success?0xFFE4F1D5:0xFFF8EAD2);
        g.fill(px,py,px+3,py+hh,success?GREEN:GOLD);
        g.fill(px,py+hh-2,px+ww,py+hh,0xFFD7B387);
    }
    private void entry(GuiGraphicsExtractor g,String label,int value,int px,int py) {
        line(g,label+":  "+value,px,py,INK);
    }
    private void drawTab(GuiGraphicsExtractor g,PaintButton b,int mx,int my) {
        boolean hover=mx>=b.bx()&&mx<b.bx()+b.bw()&&my>=b.by()&&my<b.by()+b.bh();
        int l=b.bx(),t=b.by(),r=l+b.bw(),d=t+b.bh();
        g.fill(l-2,t-2,r+2,d+2,b.active()?GOLD:EDGE);
        g.fill(l-1,t-1,r+1,d+1,0xFFFFF0CA);
        g.fill(l,t,r,d,!b.enabled()?0xFFE3D4BB:b.active()?0xFFFFD172:hover?0xFFFFE0A0:0xFFFFEBCB);
        g.fill(l+2,t+2,r-2,t+4,0xFFFFF7D9);
        g.fill(l+2,d-4,r-2,d-2,0xFFE0B887);
        if(b.icon()!=null)g.item(new ItemStack(b.icon()),l+5,t+Math.max(1,(b.bh()-16)/2));
        int textX=l+(b.icon()==null?Math.max(4,(b.bw()-font.width(b.label()))/2):25);
        String label=font.plainSubstrByWidth(b.label(),b.bw()-(b.icon()==null?8:29));
        line(g,label,textX,t+(b.bh()-8)/2,b.enabled()?DARK:MUTED);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial) {
        decorative(g);
        int[] a=stats();
        switch(tab) {
            case 0 -> {
                section(g,"ESTADÍSTICAS PERSONALES",Items.BOOK);
                int start=cy+34;
                line(g,"Nivel "+progressLevel()+"/100 · "+rankTitle()+" · Prestigio "+prestigeCount(),cx,start,GREEN);
                g.fill(cx,start+17,x+w-30,start+26,0xFFD7B58A);
                g.fill(cx+1,start+18,cx+1+Math.max(0,(x+w-32-cx)*(progressXp()%100)/100),start+25,GREEN);
                entry(g,"Victorias",a[0],cx,start+34);
                line(g,"Normal "+a[1]+"  Mítico "+a[2]+"  Legendario "+a[3],cx,start+48,MUTED);
                entry(g,"Recompensas",a[4],cx,start+64);
                entry(g,"Muertes",a[5],cx,start+78);
                entry(g,"Sin morir",a[6],cx,start+92);
                g.fill(cx,start+107,x+w-29,start+108,GOLD);
                entry(g,"Enemigos",a[7],cx,start+117);
                entry(g,"Impactos",a[8],cx,start+131);
                entry(g,"Racha",a[9],cx,start+145);
                entry(g,"Racha máxima",a[10],cx,start+159);
                line(g,"Mejor tiempo: "+(a[11]==0?"Sin récord":a[11]/20+" s"),cx,start+173,INK);
                entry(g,"Victorias cooperativas",a[12],cx,start+187);
                if(rows().length>14) {
                    String recent=rows()[14].split("\\|")[0];
                    line(g,"ÚLTIMO DESAFÍO",cx,start+205,GOLD);
                    String compact=recent.replace(" jugadores"," jug.").replace(" impactos"," golpes");
                    int maxWidth=x+w-34-cx;
                    // Split on a word boundary; never paint outside the parchment panel.
                    String first=font.plainSubstrByWidth(compact,maxWidth);
                    int boundary=first.lastIndexOf(' ');
                    if(boundary>0 && first.length()<compact.length())first=first.substring(0,boundary);
                    line(g,first,cx,start+220,MUTED);
                    if(first.length()<compact.length())
                        line(g,font.plainSubstrByWidth(compact.substring(first.length()).trim(),maxWidth),
                            cx,start+234,MUTED);
                }
            }
            case 1 -> {
                section(g,"LOGROS DE THORIDROP",Items.NETHER_STAR);
                int index=0;
                for(int goal:new int[]{10,25,50,100}){
                    boolean done=a[0]>=goal;
                    int yy=cy+35+index++*27;
                    strip(g,cx-5,yy-6,w-side-65,24,done);
                    line(g,(done?"✔ ":"◇ ")+"Veterano "+goal+" victorias ("+Math.min(a[0],goal)+"/"+goal+")",cx+8,yy+1,done?GREEN:INK);
                }
                String[] labels={"Conquistador legendario","Superviviente impecable","Victoria en equipo"};
                boolean[] check={a[3]>0,a[6]>0,a[12]>0};
                for(int i=0;i<3;i++){
                    int yy=cy+150+i*28;
                    strip(g,cx-5,yy-6,w-side-65,24,check[i]);
                    line(g,(check[i]?"✔ ":"◇ ")+labels[i],cx+8,yy+1,check[i]?GREEN:INK);
                }
            }
            case 2 -> {
                section(g,rankMode==1?"VICTORIAS LEGENDARIAS":rankMode==2?"DESAFÍOS SIN MORIR":"CLASIFICACIÓN GENERAL",Items.PAPER);
                String[] rr=rows();
                int idx=2+rankMode*4+rankPage;
                String[] players=(rr.length>idx?rr[idx]:"Sin jugadores").split(" \\| ");
                for(int i=0;i<Math.min(players.length,6);i++){
                    int yy=cy+38+i*30;
                    strip(g,cx-5,yy-5,w-side-65,26,i==0);
                    line(g,font.plainSubstrByWidth(players[i],w-side-88),cx+6,yy+3,INK);
                }
                // Page label above the navigation controls, with a single render only.
                centered(g,"Página "+(rankPage+1)+" de 4",cx+(w-side-56)/2,y+h-87,MUTED);
            }
            case 3 -> {
                section(g,"MERCADO DE RECOMPENSAS",Items.EMERALD);
                line(g,"Categoría: "+CATEGORIES[marketPage],cx+42,y+150,DARK);
                int row=0;
                for(int i=0;i<NAMES.length;i++){
                    if(!GROUP[i].equals(CATEGORIES[marketPage]))continue;
                    int yy=y+184+row*51;
                    strip(g,cx-7,yy-5,w-side-53,45,confirmId==i+1);
                    g.item(new ItemStack(ICONS[i]),cx+2,yy+4);
                    line(g,font.plainSubstrByWidth(NAMES[i],w-side-191),cx+23,yy+6,INK);
                    line(g,"Precio: "+COST[i]+" pts"+(LEVEL[i]>0?" · Encant. "+LEVEL[i]:""),
                        cx+23,yy+22,MUTED);
                    row++;
                }
                line(g,"Saldo: "+balance()+" · Compra con confirmación",cx,y+h-54,DARK);
                if(java.util.stream.IntStream.range(0,NAMES.length)
                    .anyMatch(i->GROUP[i].equals(CATEGORIES[marketPage])&&balance()<COST[i]))
                    line(g,"Los artículos sin saldo aparecen bloqueados.",cx,y+h-40,RED);
            }
            case 4 -> {
                section(g,"¿CÓMO FUNCIONAN LOS DESAFÍOS?",Items.IRON_SWORD);
                String[] lines={
                    "Los desafíos son los suministros Thoridrop.",
                    "1. Encuentra el cofre que cae del cielo.",
                    "2. Haz clic derecho para activar el reto.",
                    "3. Supera las cinco oleadas de enemigos.",
                    "4. Derrota al jefe final de la quinta oleada.",
                    "5. Reclama equipo y puntos del cofre.",
                    "Normal: más fácil · Mítico: intermedio.",
                    "Legendario: más difícil y mejor botín.",
                    "A veces hay reglas sorpresa al azar."
                };
                for(int i=0;i<lines.length;i++){
                    int yy=cy+33+i*25;
                    boolean marked=i==0 || i==5;
                    strip(g,cx-5,yy-5,w-side-65,23,marked);
                    line(g,font.plainSubstrByWidth(lines[i],w-side-87),cx+5,yy+3,marked?DARK:INK);
                }
                line(g,"Completados: "+a[0]+" · Coop.: "+a[12],cx,cy+262,MUTED);
            }
            case 5 -> {
                section(g,"MISIONES DIARIAS Y SEMANALES",Items.CLOCK);
                String[] missions=rows().length>16?rows()[16].split("\\|"):new String[0];
                for(int i=0;i<Math.min(3,missions.length);i++){
                    int yy=cy+39+i*67;
                    strip(g,cx-5,yy-5,w-side-65,58,missions[i].contains("COBRADA"));
                    line(g,font.plainSubstrByWidth(missions[i],w-side-89),cx+5,yy+14,INK);
                }
                line(g,"Reinicio UTC · Cobro al recoger el cofre",cx,cy+251,MUTED);
            }
            case 6 -> {
                section(g,"COLECCIÓN DE EQUIPO EXCLUSIVO",Items.NETHERITE_AXE);
                String[] pieces=rows().length>17?rows()[17].split("\\|"):new String[0];
                Item[] icons={Items.DIAMOND_AXE,Items.DIAMOND_SWORD,Items.DIAMOND_PICKAXE,
                    Items.NETHERITE_AXE,Items.NETHERITE_SWORD,Items.NETHERITE_CHESTPLATE};
                for(int i=0;i<Math.min(6,pieces.length);i++){
                    int yy=cy+30+i*36;
                    boolean owned=pieces[i].contains("✔");
                    strip(g,cx-5,yy-5,w-side-65,31,owned);
                    g.item(new ItemStack(icons[i]),cx+5,yy+2);
                    line(g,font.plainSubstrByWidth(pieces[i],w-side-110),cx+28,yy+7,owned?GREEN:INK);
                }
            }
            default -> {
                section(g,"INFORMACIÓN Y AYUDA",Items.COMPASS);
                String[] help={"Pulsa H para abrir o cerrar el menú",
                    "ESC cierra la pantalla",
                    "Consigue puntos al terminar desafíos",
                    "Recoge la recompensa en el cofre",
                    "Compra objetos desde el mercado",
                    "La tienda valida compras en el servidor",
                    "Las misiones se reinician automáticamente",
                    "Tu progreso se guarda por jugador"};
                for(int i=0;i<help.length;i++){
                    int yy=cy+35+i*28;
                    strip(g,cx-5,yy-5,w-side-65,24,false);
                    line(g,font.plainSubstrByWidth(help[i],w-side-85),cx+7,yy+3,INK);
                }
            }
        }
        super.extractRenderState(g,mx,my,partial);
        // Paint themed buttons over native clickable widgets; retains keyboard/mouse navigation.
        for(PaintButton b:paintButtons)drawTab(g,b,mx,my);
    }
    @Override public boolean keyPressed(KeyEvent e){
        if(ThoridropMenuClient.OPEN.isActiveAndMatches(InputConstants.getKey(e))){
            onClose();return true;
        }
        return super.keyPressed(e);
    }
    @Override public boolean isPauseScreen(){return false;}
}
