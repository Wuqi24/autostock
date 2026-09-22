package dev.autostock.client;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;
final class ConfigTransferScreen extends CompatGuiBase {
    private String message="导入会替换界面、颜色与按键设置，不修改冻结任务";
    ConfigTransferScreen(GuiBase parent){setParent(parent);title="配置导入 / 导出";}
    @Override public boolean shouldPause(){return false;}
    @Override public void initGui(){super.initGui();int left=Math.max(12,(width-300)/2);
        addButton(new ButtonGeneric(width-84,10,72,20,"← 返回"),(b,m)->closeGui(true));
        addButton(new ButtonGeneric(left,64,146,22,"导出配置"),(b,m)->{try{ClientSettings.exportConfig();message="已导出到 config/autostock-transfer.json";}catch(IllegalArgumentException e){message=e.getMessage();}});
        addButton(new ButtonGeneric(left+154,64,146,22,"导入配置"),(b,m)->{try{ClientSettings.importConfig();message="导入成功，设置已生效";}catch(IllegalArgumentException e){message=e.getMessage();}});
    }
    @Override protected void drawScreenBackgroundCompat(DrawContext c,int x,int y){c.fill(0,0,width,height,0x80000000);}
    @Override protected void drawContentsCompat(DrawContext c,int x,int y,float delta){
        c.drawText(textRenderer,"文件：config/autostock-transfer.json",12,106,0xFFCCD5DF,false);
        c.drawText(textRenderer,"先导出，或将待导入文件放到上述位置。",12,125,0xFFAAAAAA,false);
        c.drawText(textRenderer,textRenderer.trimToWidth(message,width-24),12,height-36,0xFFE2C478,false);
    }
}

