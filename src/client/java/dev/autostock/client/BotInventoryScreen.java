package dev.autostock.client;

import fi.dy.masa.malilib.gui.GuiBase;
import net.minecraft.client.gui.DrawContext;

/** Owner-only read-only live inventory with a header that never inherits parent titles. */
final class BotInventoryScreen extends CompatGuiBase {
    BotInventoryScreen(GuiBase parent){setParent(parent);title="";useTitleHierarchy=false;}
    @Override public boolean shouldPause(){return false;}
    @Override public void initGui(){super.initGui();addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(width-82,8,70,20,"← 返回"),(b,m)->closeGui(true));int w=76;
        var data=InventoryView.snapshot;boolean online=data!=null&&data.state().equals("ONLINE");boolean present=online||data!=null&&data.state().equals("OFFLINE");
        if(online)addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(width/2-80,height-27,w,18,"召回"),(b,m)->command("autostock-recall"));
        if(present)addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(online?width/2+4:width/2-38,height-27,w,18,"删除假人"),(b,m)->command("autostock-delete"));
    }
    void refreshInventory(){initGui();if(getParent() instanceof PreviewScreen preview)preview.refreshInventory();}
    private void command(String value){BotControls.send(value.substring("autostock-".length()),AutoStockClient.activeDraft);}
    @Override protected void drawScreenBackgroundCompat(DrawContext c,int x,int y){c.fill(0,0,width,height,0x80000000);c.fill(0,0,width,34,0x60303030);}
    @Override protected void drawContentsCompat(DrawContext c,int mx,int my,float delta){
        c.drawText(textRenderer,"假人详情",12,14,0xFFE8E8EA,false);
        boolean columns=width>=400;
        int left=columns?width-210:Math.max(12,(width-198)/2),top=columns?62:82;
        int textWidth=columns?left-24:width-24;
        c.drawText(textRenderer,textRenderer.trimToWidth(InventoryView.status(),textWidth),12,43,0xFF9A9AA2,false);
        var draft=AutoStockClient.activeDraft;var task=draft==null?null:draft.taskStatus();
        String behavior=task!=null&&(task.state().equals("RUNNING")||task.state().equals("PAUSED"))?task.detail():"空闲 · 无自动执行";
        if(columns){int yy=104;for(var line:textRenderer.wrapLines(net.minecraft.text.Text.literal("当前动作："+behavior),textWidth)){c.drawText(textRenderer,line,12,yy,0xFFF09040,false);yy+=12;if(yy>height-45)break;}}
        else c.drawText(textRenderer,textRenderer.trimToWidth("当前动作："+behavior,width-24),12,61,0xFFF09040,false);
        var data=InventoryView.snapshot;if(data==null)return;
        String health="血量 "+String.format(java.util.Locale.ROOT,"%.1f",data.health())+" · "+data.position().toShortString();
        if(columns){c.drawText(textRenderer,"生命值："+String.format(java.util.Locale.ROOT,"%.1f",data.health()),12,62,0xFF9A9AA2,false);c.drawText(textRenderer,textRenderer.trimToWidth("位置："+data.position().toShortString(),textWidth),12,81,0xFF9A9AA2,false);c.drawText(textRenderer,"背包",left,43,0xFFE8E8EA,false);}
        else c.drawText(textRenderer,textRenderer.trimToWidth(health,width/2-12),width/2,43,0xFF9A9AA2,false);
        int hovered=-1;
        for(int row=0;row<4;row++)for(int col=0;col<9;col++){
            int index=row==3?col:9+row*9+col,x=left+col*22,y=top+row*22+(row==3?5:0);
            int color=switch(data.categories().get(index)){case 2->0xFFA78BFA;case 3->0xFF4ADE80;default->0xFF3A3A3D;};
            c.fill(x,y,x+21,y+21,color);c.fill(x+1,y+1,x+20,y+20,0xFF252526);
            var stack=data.slots().get(index);c.drawItem(stack,x+3,y+3);
            //? if <=1.21.1 {
            /*c.drawItemInSlot(textRenderer,stack,x+3,y+3);
            *///?} else {
            c.drawStackOverlay(textRenderer,stack,x+3,y+3);
            //?}
            if(mx>=x&&mx<x+21&&my>=y&&my<y+21){c.fill(x+1,y+1,x+20,y+20,0x25FFFFFF);hovered=index;}
        }
        c.drawText(textRenderer,"紫：空盒  绿：含物品盒 · 只读",left,top+100,0xFF9A9AA2,false);
        if(hovered>=0&&!data.slots().get(hovered).isEmpty())c.drawItemTooltip(textRenderer,data.slots().get(hovered),mx,my);
    }
}
