package dev.autostock.client;

import dev.autostock.core.RegionRole;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;

final class RegionDetailScreen extends CompatGuiBase {
    private final ClientDraft draft;private final RegionRole role;private int page;
    RegionDetailScreen(ClientDraft draft,RegionRole role,GuiBase parent){this.draft=draft;this.role=role;setParent(parent);title=role.label()+"详情";}
    private void action(int x,int y,int w,String label,Runnable run){addButton(new ButtonGeneric(x,y,w,20,label),(b,m)->{draft.perform(run);if(ClientMinecraftCompat.screen(mc)==this)initGui();});}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void initGui(){super.initGui();action(width-84,10,72,"← 返回",()->closeGui(true));int x=12,w=(width-30)/2;
        action(x,44,w,"重新扫描",draft::scan);
        action(x+w+6,44,w,"进入选区模式",()->{RegionSetupScreen.select(role);RegionSetupScreen.open(draft);});
        action(x,70,w,"清除区域",()->draft.clearRegion(role));
        action(x+w+6,70,w,"下一页坐标",()->page++);
    }
    @Override protected void drawScreenBackgroundCompat(GuiContext c,int x,int y){c.fill(0,0,width,height,0x80000000);}
    @Override protected void drawContentsCompat(GuiContext c,int x,int y,float delta){
        c.drawString(textRenderer,draft.regionSummary(role),12,102,RegionSetupScreen.color(role),false);
        var world=mc.level;c.drawString(textRenderer,world==null?"未连接世界":world.dimension().identifier().toString(),12,117,0xFFAAAAAA,false);
        var boxes=draft.regions().getOrDefault(role,java.util.List.of());int rows=Math.max(1,(height-180)/14);int pages=Math.max(1,(boxes.size()+rows-1)/rows);page%=pages;
        for(int i=0;i<rows&&page*rows+i<boxes.size();i++){var b=boxes.get(page*rows+i);String line=b.minX()+", "+b.minY()+", "+b.minZ()+" → "+b.maxX()+", "+b.maxY()+", "+b.maxZ();c.drawString(textRenderer,textRenderer.plainSubstrByWidth(line,width-24),12,134+i*14,0xFFDDE5EE,false);}
        c.drawString(textRenderer,textRenderer.plainSubstrByWidth(draft.status(),width-24),12,height-30,0xFFE2C478,false);
    }
}

