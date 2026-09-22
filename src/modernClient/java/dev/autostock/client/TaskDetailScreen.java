package dev.autostock.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;

final class TaskDetailScreen extends CompatGuiBase {
    private final ClientDraft draft;
    TaskDetailScreen(ClientDraft draft,GuiBase parent){this.draft=draft;setParent(parent);title="任务详情";}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void initGui(){super.initGui();int w=(width-36)/3;
        addButton(new ButtonGeneric(width-84,10,72,20,"← 返回"),(b,m)->closeGui(true));
        addButton(new ButtonGeneric(12,48,w,20,"启动 / 暂停"),(b,m)->draft.perform(draft::taskToggle));
        addButton(new ButtonGeneric(18+w,48,w,20,"取消"),(b,m)->draft.perform(draft::taskCancel));
        addButton(new ButtonGeneric(24+w*2,48,w,20,"查看假人"),(b,m)->ClientMinecraftCompat.setScreen(mc,new BotInventoryScreen(this)));
        addButton(new ButtonGeneric(12,72,100,20,"备货计划详情"),(b,m)->ClientMinecraftCompat.setScreen(mc,new PlanDetailScreen(draft,this)));
    }
    @Override protected void drawScreenBackgroundCompat(GuiContext c,int x,int y){c.fill(0,0,width,height,0x80000000);}
    @Override protected void drawContentsCompat(GuiContext c,int x,int y,float delta){
        String[] lines={"任务："+(draft.frozenId()==null?"尚未冻结":draft.frozenId()),draft.status()};
        for(int i=0;i<lines.length;i++)c.drawString(textRenderer,textRenderer.plainSubstrByWidth(lines[i],width-24),12,100+i*18,0xFFCCD8E5,false);
        var t=draft.taskStatus();if(t!=null){
            c.drawString(textRenderer,"交付 "+t.delivered()+" / "+t.required()+" · 在途 "+t.inTransit(),12,142,0xFF4ADE80,false);
            c.drawString(textRenderer,"目标："+(t.target()==null?"无":t.target().toShortString()),12,160,0xFFAAAAAA,false);
            c.drawString(textRenderer,"路径 "+t.path().size()+" 节点 · "+t.state(),12,178,0xFFAAAAAA,false);
        }
    }
}



