package dev.autostock.client;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
final class SchematicLibraryScreen extends CompatGuiBase {
    private int page;
    SchematicLibraryScreen(GuiBase parent){setParent(parent);title="";useTitleHierarchy=false;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void initGui(){super.initGui();int rows=Math.max(1,(height-108)/24);var schematics=Schematics.loaded();page=Math.min(page,Math.max(0,(schematics.size()-1)/rows));
        addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(width-82,10,70,20,"← 返回"),(b,m)->closeGui(true));
        addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(12,42,88,20,"浏览"),(b,m)->Schematics.browse(this));
        addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(106,42,68,20,"上一页"),(b,m)->{page=Math.max(0,page-1);initGui();});
        addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(180,42,68,20,"下一页"),(b,m)->{page++;initGui();});
        if(schematics.isEmpty()&&AutoStockClient.activeDraft!=null&&!AutoStockClient.activeDraft.materials().isEmpty())addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(12,100,width-24,20,AutoStockClient.activeDraft.schematicName(),"备货需求快照 · 非已加载原理图文件"),(b,m)->ClientMinecraftCompat.setScreen(mc,new DemandScreen(AutoStockClient.activeDraft,this)));
        for(int i=0;i<rows&&page*rows+i<schematics.size();i++){var schematic=schematics.get(page*rows+i);var file=SchematicCompat.file(schematic);addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(12,72+i*24,width-24,20,schematic.getMetadata().getName(),schematic.getSubRegionCount()+" 子区域 · "+(file==null?"内存原理图":file.getFileName().toString())),(b,m)->ClientMinecraftCompat.setScreen(mc,new SchematicDetailScreen(schematic,this)));}
    }
    @Override protected void drawScreenBackgroundCompat(GuiContext c,int x,int y){c.fill(0,0,width,height,0x80000000);}
    @Override protected void drawContentsCompat(GuiContext c,int x,int y,float delta){c.drawString(textRenderer,"原理图文件与已加载列表",12,16,0xFFE8E8EA,false);if(Schematics.loaded().isEmpty())c.drawString(textRenderer,"无已加载文件；可浏览文件，或查看下方需求快照",12,85,0xFF9A9AA2,false);}
}

