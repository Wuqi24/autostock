package dev.autostock.client;

import dev.autostock.core.HudLayout;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;

final class HudPositionScreen extends CompatGuiBase {
    private static final int MENU_WIDTH = 96;
    private static final int MENU_BASE_HEIGHT = 97;
    private final ClientDraft draft;
    private float[] positions;
    private boolean[] visible;private int[] opacity,bg,textColors,accent;
    private int dragging=-1,menu=-1,menuX,menuY;
    private boolean draggingOpacity;
    private double offsetX,offsetY;
    HudPositionScreen(ClientDraft draft,GuiBase parent){this.draft=draft;var s=ClientSettings.get();positions=s.hudPositions.clone();visible=new boolean[]{s.showRegionHud,s.showTaskHud};opacity=s.hudOpacity.clone();bg=s.hudBg.clone();textColors=s.hudText.clone();accent=s.hudAccent.clone();setParent(parent);title="";useTitleHierarchy=false;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor c,int x,int y,float delta){}
    @Override protected void drawScreenBackgroundCompat(GuiContext c,int x,int y){c.fill(0,0,width,height,0x401C1C22);}
    @Override public void initGui(){super.initGui();}
    @Override public void onClose(){save(true);}
    @Override protected void closeGui(boolean toParent){save(toParent);}
    private void save(boolean toParent){var s=ClientSettings.get();var oldPositions=s.hudPositions;var oldOpacity=s.hudOpacity;var oldBg=s.hudBg;var oldText=s.hudText;var oldAccent=s.hudAccent;boolean oldRegion=s.showRegionHud,oldTask=s.showTaskHud;try{s.hudOpacity=opacity.clone();s.hudBg=bg.clone();s.hudText=textColors.clone();s.hudAccent=accent.clone();s.hudPositions=positions.clone();s.showRegionHud=visible[0];s.showTaskHud=visible[1];s.save();ClientMinecraftCompat.setScreen(mc,toParent?getParent():null);}catch(IllegalArgumentException e){s.hudOpacity=oldOpacity;s.hudBg=oldBg;s.hudText=oldText;s.hudAccent=oldAccent;s.hudPositions=oldPositions;s.showRegionHud=oldRegion;s.showTaskHud=oldTask;draft.perform(()->{throw e;});}}
    private HudLayout.Rect bounds(int i){return HudPanels.bounds(i,width,height,positions);}
    @Override protected void drawContentsCompat(GuiContext c,int mx,int my,float delta){
        for(int i=1;i>=0;i--){var r=bounds(i);HudPanels.renderPanel(c,draft,r,i,new HudPanels.Style(opacity[i],bg[i],textColors[i],accent[i]));if(!visible[i]){c.fill(r.x(),r.y(),r.x()+r.width(),r.y()+r.height(),0xA0000000);HudPanels.text(c,"已隐藏 · 右键设置",r.x()+4,r.y()+r.height()/2,r.width()-8,0xFFFFFFFF);}}
        if(menu>=0){
            c.fill(menuX-1,menuY-15,menuX+97,menuY+82,0xFF666666);
            c.fill(menuX,menuY-14,menuX+96,menuY+81,0xF0202020);
            HudPanels.text(c,menu==0?"区域 HUD":"任务 HUD",menuX+5,menuY-11,86,0xFFFFFFFF);
            c.fill(menuX+4,menuY-2,menuX+92,menuY-1,0xFF505050);
            drawMenu(c);
        }
    }

    private void drawMenu(GuiContext c){
        int id=menu;
        drawButton(c,menuX+4,menuY+1,88,12,visible[id]?"显示 HUD：开":"显示 HUD：关",visible[id]?0xFF315F3A:0xFF593333);
        int trackX=menuX+4,trackY=menuY+15,trackWidth=88;
        c.fill(trackX,trackY,trackX+trackWidth,trackY+12,0xFF30343A);
        c.fill(trackX,trackY,trackX+(trackWidth*opacity[id])/100,trackY+12,0xFF4D78B8);
        HudPanels.text(c,"透明度 "+opacity[id]+"%",trackX+4,trackY+2,trackWidth-8,0xFFFFFFFF);
        String[] labels={"文字颜色","强调色","背景颜色"};
        int[] colors={textColors[id],accent[id],bg[id]};
        for(int n=0;n<3;n++){int y=menuY+30+n*12;HudPanels.text(c,labels[n],menuX+5,y+2,68,0xFFDDDDDD);c.fill(menuX+78,y,menuX+90,y+10,colors[n]);c.fill(menuX+78,y,menuX+90,y+1,0xFFFFFFFF);c.fill(menuX+78,y+9,menuX+90,y+10,0xFF777777);}
        drawButton(c,menuX+4,menuY+67,88,12,"重置此 HUD",0xFF3A3A3A);
    }

    private void drawButton(GuiContext c,int x,int y,int width,int height,String label,int background){
        c.fill(x-1,y-1,x+width+1,y+height+1,0xFF777777);c.fill(x,y,x+width,y+height,background);HudPanels.text(c,label,x+4,y+2,width-8,0xFFFFFFFF);
    }

    private boolean inMenu(double x,double y){return menu>=0&&x>=menuX&&x<menuX+MENU_WIDTH&&y>=menuY-15&&y<menuY+81;}
    private boolean in(double x,double y,int left,int top,int itemWidth,int itemHeight){return x>=left&&x<left+itemWidth&&y>=top&&y<top+itemHeight;}
    private void setOpacity(double x){opacity[menu]=Math.max(0,Math.min(100,(int)Math.round((x-(menuX+4))*100.0/88.0)));}
    private void resetMenu(){int id=menu;positions[id*2]=positions[id*2+1]=-1;visible[id]=true;opacity[id]=62;bg[id]=0xFF14161E;textColors[id]=0xFFD8D8DC;accent[id]=id==0?0xFF7BA4F4:0xFF4ADE80;}

    private void openMenu(int panel, double clickX) {
        menu = panel;
        int maxX = Math.max(0, width - MENU_WIDTH);
        int maxY = Math.max(15, height - 113);
        var selected = bounds(panel);
        var other = bounds(1 - panel);
        int alignedY = Math.max(15, Math.min(maxY, selected.y() + selected.height() / 2 - MENU_BASE_HEIGHT / 2 + 15));
        int rightX = Math.min(maxX, selected.x() + selected.width() + 8);
        int leftX = Math.max(0, selected.x() - MENU_WIDTH - 8);
        int centeredX = Math.max(0, Math.min(maxX, selected.x() + (selected.width() - MENU_WIDTH) / 2));
        int aboveY = Math.max(15, Math.min(maxY, selected.y() - MENU_BASE_HEIGHT + 7));
        int belowY = Math.max(15, Math.min(maxY, selected.y() + selected.height() + 23));
        boolean selectedOnLeft = selected.x() + selected.width() / 2 < width / 2;
        int firstX = selectedOnLeft ? rightX : leftX;
        int secondX = selectedOnLeft ? leftX : rightX;
        boolean selectedAtBottom = selected.y() + selected.height() / 2 > height / 2;
        int[][] candidates = selectedAtBottom ? new int[][]{
                {centeredX, aboveY},
                {firstX, alignedY},
                {secondX, alignedY},
                {centeredX, belowY},
                {firstX, 15},
                {secondX, 15},
                {firstX, maxY},
                {secondX, maxY},
                {0, 15},
                {maxX, 15},
                {0, maxY},
                {maxX, maxY}
        } : new int[][]{
                {centeredX, belowY},
                {firstX, alignedY},
                {secondX, alignedY},
                {centeredX, aboveY},
                {firstX, 15},
                {secondX, 15},
                {firstX, maxY},
                {secondX, maxY},
                {0, 15},
                {maxX, 15},
                {0, maxY},
                {maxX, maxY}
        };
        int bestX = candidates[0][0];
        int bestY = candidates[0][1];
        int bestOverlap = Integer.MAX_VALUE;
        for (int[] candidate : candidates) {
            int overlap = overlapArea(candidate[0], candidate[1] - 15, MENU_WIDTH + 2, MENU_BASE_HEIGHT, selected)
                    + overlapArea(candidate[0], candidate[1] - 15, MENU_WIDTH + 2, MENU_BASE_HEIGHT, other);
            if (overlap == 0) {
                bestX = candidate[0];
                bestY = candidate[1];
                break;
            }
            if (overlap < bestOverlap) {
                bestOverlap = overlap;
                bestX = candidate[0];
                bestY = candidate[1];
            }
        }
        menuX = bestX;
        menuY = bestY;
        initGui();
    }

    private int overlapArea(int x, int y, int width, int height, HudLayout.Rect other) {
        int left = Math.max(x, other.x());
        int top = Math.max(y, other.y());
        int right = Math.min(x + width, other.x() + other.width());
        int bottom = Math.min(y + height, other.y() + other.height());
        return Math.max(0, right - left) * Math.max(0, bottom - top);
    }

    private boolean handleMouseClicked(double x,double y,int button){
        if(inMenu(x,y)){
            if(button!=0)return true;
            int id=menu;
            if(in(x,y,menuX+4,menuY+1,88,12)){visible[id]=!visible[id];return true;}
            if(in(x,y,menuX+4,menuY+15,88,12)){draggingOpacity=true;setOpacity(x);return true;}
            for(int n=0;n<3;n++)if(in(x,y,menuX+4,menuY+29+n*12,88,12)){int row=n;int[] values={textColors[id],accent[id],bg[id]};ColorEditor.open(this,values[row],value->{if(row==0)textColors[id]=value;else if(row==1)accent[id]=value;else bg[id]=value;});return true;}
            if(in(x,y,menuX+4,menuY+67,88,12)){resetMenu();return true;}
            return true;
        }
        if(menu>=0){menu=-1;initGui();}if(y>=height-32)return false;
        for(int i=1;i>=0;i--){var r=bounds(i);if(r.contains(x,y)){if(button==1){openMenu(i,x);return true;}if(button==0){dragging=i;offsetX=x-r.x();offsetY=y-r.y();return true;}}}
        return false;
    }

    private boolean handleMouseDragged(double x,double y){
        if(draggingOpacity){setOpacity(x);return true;}
        if(dragging<0)return false;
        var r=bounds(dragging);var next=HudLayout.snap(new HudLayout.Rect((int)(x-offsetX),(int)(y-offsetY),r.width(),r.height()),bounds(1-dragging),width,height-36,8).rect();
        positions[dragging*2]=(float)next.x()/Math.max(1,width-next.width());positions[dragging*2+1]=(float)next.y()/Math.max(1,height-next.height());return true;
    }

    //? if <=1.21.8 {
    /*@Override public boolean mouseClicked(double x,double y,int button){return handleMouseClicked(x,y,button)||super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return handleMouseDragged(x,y)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=-1;draggingOpacity=false;return super.mouseReleased(x,y,button);}
    *///?} else {
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click,boolean doubled){return handleMouseClicked(click.x(),click.y(),click.button())||super.mouseClicked(click,doubled);}
    @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click,double dx,double dy){return handleMouseDragged(click.x(),click.y())||super.mouseDragged(click,dx,dy);}
    @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click){dragging=-1;draggingOpacity=false;return super.mouseReleased(click);}
    //?}
}
