package dev.autostock.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;

final class ColorSettingsScreen extends CompatGuiBase {
    ColorSettingsScreen(GuiBase parent){setParent(parent);title="显示颜色";}
    @Override public void initGui(){super.initGui();String[] labels={"材料区","空盒区","备货区","计划路径","HUD 文字","错误提示"};int left=Math.max(12,(width-300)/2);
        for(int i=0;i<6;i++){final int index=i;addButton(new ButtonGeneric(left+(i%2)*154,48+(i/2)*34,146,24,labels[i]+"  "+ColorEditor.format(ClientSettings.get().colors[i])),(b,m)->ColorEditor.open(this,ClientSettings.get().colors[index],value->{ClientSettings.get().colors[index]=value;ClientSettings.get().save();initGui();}));}
        addButton(new ButtonGeneric(width-84,10,72,20,"← 返回"),(b,m)->closeGui(true));
    }
    @Override protected void drawScreenBackgroundCompat(GuiContext c,int x,int y){c.fill(0,0,width,height,0x80000000);}
    @Override public boolean isPauseScreen(){return false;}
}

