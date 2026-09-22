package dev.autostock.client;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.gui.GuiBase;
import net.minecraft.client.gui.DrawContext;
final class SchematicDetailScreen extends CompatGuiBase {
    private final LitematicaSchematic schematic;private String message="";
    SchematicDetailScreen(LitematicaSchematic schematic,GuiBase parent){this.schematic=schematic;setParent(parent);title="";useTitleHierarchy=false;}
    @Override public boolean shouldPause(){return false;}
    @Override public void initGui(){super.initGui();addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(width-82,10,70,20,"← 返回"),(b,m)->closeGui(true));int w=(width-36)/3;
        addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(12,46,w,22,"查看完整材料列表"),(b,m)->Schematics.materials(schematic,this));
        addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(18+w,46,w,22,"重载"),(b,m)->{try{Schematics.reload(schematic);message="已重载；冻结任务需求保持不变";}catch(IllegalArgumentException e){message=e.getMessage();}});
        addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(24+2*w,46,w,22,"卸载"),(b,m)->{try{Schematics.unload(schematic);closeGui(true);}catch(IllegalArgumentException e){message=e.getMessage();}});
        addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(12,78,width-24,24,"生成备货清单"),(b,m)->Schematics.materials(schematic,this));
    }
    @Override protected void drawScreenBackgroundCompat(DrawContext c,int x,int y){c.fill(0,0,width,height,0x80000000);}
    @Override protected void drawContentsCompat(DrawContext c,int x,int y,float delta){
        c.drawText(textRenderer,"原理图详情",12,16,0xFFE8E8EA,false);var size=schematic.getTotalSize();String[] lines={schematic.getMetadata().getName(),"尺寸 "+size.getX()+" × "+size.getY()+" × "+size.getZ()+" · "+schematic.getSubRegionCount()+" 子区",schematic.getFile()==null?"内存原理图":schematic.getFile().toString()};
        for(int i=0;i<lines.length;i++)c.drawText(textRenderer,textRenderer.trimToWidth(lines[i],width-24),12,119+i*17,0xFF9A9AA2,false);
        c.drawText(textRenderer,textRenderer.trimToWidth(message,width-24),12,height-26,0xFFFBBF24,false);
    }
}

