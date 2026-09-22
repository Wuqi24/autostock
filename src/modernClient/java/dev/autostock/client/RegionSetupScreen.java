package dev.autostock.client;

import dev.autostock.core.RegionRole;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only HUD. No mouse handler and no cursor/focus changes; only explicit keyboard shortcuts. */
final class RegionSetupScreen extends Screen {
    private static RegionSetupScreen overlay;
    static boolean lastSave;
    private static RegionRole current=RegionRole.MATERIAL;
    private final ClientDraft draft;
    RegionSetupScreen(ClientDraft draft) {super(Component.literal("区域设置"));this.draft=draft;}
    static boolean active(){return overlay!=null;}
    static void select(RegionRole role){current=role;lastSave=false;}
    static RegionRole currentRole(){return current;}
    static void open(ClientDraft draft){overlay=new RegionSetupScreen(draft);ClientMinecraftCompat.setScreen(net.minecraft.client.Minecraft.getInstance(),null);}
    static void toggle(ClientDraft draft){if(active())overlay=null;else open(draft);}
    static void tickOverlay(){if(net.minecraft.client.Minecraft.getInstance().level==null)overlay=null;}
    static void renderOverlay(GuiGraphicsExtractor c,ClientDraft draft){HudPanels.render(GuiContext.fromGuiGraphics(c),draft,active());}
    static boolean scroll(double amount) {
        if(!active()||ClientMinecraftCompat.screen(net.minecraft.client.Minecraft.getInstance())!=null||amount==0||!ClientSettings.get().binding(ClientSettings.Action.CYCLE).matches(-1,ClientKeys.modifiers()))return false;
        lastSave=false;current=RegionRole.values()[Math.floorMod(current.ordinal()+(amount>0?-1:1),3)];return true;
    }
    static void saveOverlay(){if(overlay!=null)overlay.draft.perform(()->{lastSave=true;overlay.draft.prepareRegionEdit();overlay.draft.capture(current);overlay.draft.scanRegions();});}
    static int color(RegionRole role){return ClientSettings.get().colors[role.ordinal()];}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(GuiGraphicsExtractor c,int mx,int my,float delta){}
    @Override public void extractRenderState(GuiGraphicsExtractor c,int mx,int my,float delta){HudPanels.render(GuiContext.fromGuiGraphics(c),draft,true);}
    static void panel(GuiContext c,int x,int y,int w,int h,int border,int background){
        rounded(c,x,y,w,h,8,border);rounded(c,x+1,y+1,w-2,h-2,7,background);
    }
    private static void rounded(GuiContext c,int x,int y,int w,int h,int radius,int color){
        int r=Math.min(radius,Math.min(w,h)/2);for(int row=0;row<h;row++){
            int inset=0;if(row<r)inset=(int)Math.ceil(r-Math.sqrt(r*r-Math.pow(r-row-.5,2)));
            else if(row>=h-r)inset=(int)Math.ceil(r-Math.sqrt(r*r-Math.pow(row-(h-r)+.5,2)));
            c.fill(x+inset,y+row,x+w-inset,y+row+1,color);
        }
    }
}

