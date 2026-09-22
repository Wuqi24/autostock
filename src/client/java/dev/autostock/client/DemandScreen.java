package dev.autostock.client;
import fi.dy.masa.malilib.gui.GuiBase;
import net.minecraft.client.gui.DrawContext;
final class DemandScreen extends CompatGuiBase {
    private final ClientDraft draft;private int page;
    DemandScreen(ClientDraft draft,GuiBase parent){this.draft=draft;setParent(parent);title="";useTitleHierarchy=false;}
    @Override public boolean shouldPause(){return false;}
    @Override public void initGui(){super.initGui();addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(width-82,10,70,20,"← 返回"),(b,m)->closeGui(true));addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(12,height-30,78,20,"上一页"),(b,m)->{page=Math.max(0,page-1);});addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(96,height-30,78,20,"下一页"),(b,m)->{page++;});}
    @Override protected void drawScreenBackgroundCompat(DrawContext c,int x,int y){c.fill(0,0,width,height,0x80000000);}
    @Override protected void drawContentsCompat(DrawContext c,int x,int y,float delta){
        c.drawText(textRenderer,"材料需求 · "+(draft.frozen()?"已冻结":"草稿"),12,16,0xFFE8E8EA,false);c.drawText(textRenderer,textRenderer.trimToWidth(draft.schematicName()+" · ×"+draft.multiplier()+" · "+draft.totalItems()+" 件",width-24),12,43,0xFF9A9AA2,false);
        var rows=draft.materials();int size=Math.max(1,(height-108)/18);page=Math.min(page,Math.max(0,(rows.size()-1)/size));
        for(int i=0;i<size&&page*size+i<rows.size();i++){var row=rows.get(page*size+i);var name=net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of(row.getKey())).getName().getString();c.drawText(textRenderer,textRenderer.trimToWidth(name+" × "+row.getValue(),width-24),12,68+i*18,0xFFE8E8EA,false);}
    }
}

