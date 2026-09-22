package dev.autostock.client;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
final class PreviewLibrary extends CompatGuiBase {
 private int page;PreviewLibrary(GuiBase parent){setParent(parent);title="选择原理图";useTitleHierarchy=false;}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void initGui(){super.initGui();var list=Schematics.loaded();int count=Math.max(1,(height-120)/24);int max=Math.max(0,(list.size()-1)/count);page=Math.min(page,max);for(int i=0;i<count&&page*count+i<list.size();i++){var value=list.get(page*count+i);var file=SchematicCompat.file(value);addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(14,65+i*24,width-28,20,file==null?value.getMetadata().getName():file.getFileName().toString()),(b,m)->{Schematics.selected=value;Schematics.materials(value,(GuiBase)getParent());});}addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(14,height-30,65,20,"浏览"),(b,m)->Schematics.browse(this));addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(84,height-30,65,20,"←"),(b,m)->{page=Math.max(0,page-1);initGui();});addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(154,height-30,65,20,"→"),(b,m)->{page++;initGui();});addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(width-80,height-30,65,20,"关闭"),(b,m)->closeGui(true));}
 @Override protected void drawScreenBackgroundCompat(GuiContext c,int x,int y){c.fill(0,0,width,height,0xA0000000);}
 @Override protected void drawContentsCompat(GuiContext c,int x,int y,float d){c.drawString(textRenderer,"从 Litematica 已加载的原理图中选择：",14,42,0xFFAAAAAA,false);}
}
