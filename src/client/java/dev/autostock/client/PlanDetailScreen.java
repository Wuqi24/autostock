package dev.autostock.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;

final class PlanDetailScreen extends CompatGuiBase {
    private final ClientDraft draft;private String message="";
    PlanDetailScreen(ClientDraft draft,GuiBase parent){this.draft=draft;setParent(parent);title="备货计划详情";}
    private void button(int x,int y,int w,String name,Runnable action){addButton(new ButtonGeneric(x,y,w,20,name),(b,m)->draft.perform(action));}
    private void requireReport(){if(draft.report()==null)throw new IllegalArgumentException("请先重新生成计划");}
    @Override public boolean shouldPause(){return false;}
    @Override public void initGui(){super.initGui();int w=(width-30)/2;
        button(width-84,10,72,"← 返回",()->closeGui(true));
        button(12,48,w,"重新生成计划",draft::scan);
        button(18+w,48,w,"导出计划",()->{
            requireReport();var json=new com.google.gson.JsonObject();json.add("demand",com.google.gson.JsonParser.parseString(dev.autostock.core.DraftCodec.encode(draft.proposal())));json.add("scan",new com.google.gson.Gson().toJsonTree(draft.report()));
            var path=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("autostock-plan.json");
            try{java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));message="已导出 config/autostock-plan.json（当前扫描快照）";}catch(java.io.IOException e){throw new IllegalArgumentException("计划导出失败",e);}
        });
        button(12,74,w,"材料与缺口",()->{requireReport();GuiBase.openGui(new StockReportScreen(draft.report()).setParent(this));});
        button(18+w,74,w,"装盒预览",()->{requireReport();GuiBase.openGui(new PackingScreen(draft.report()).setParent(this));});
        button(12,100,w,"扫描详情",()->{requireReport();GuiBase.openGui(new ScanNotesScreen(draft.report()).setParent(this));});
        button(18+w,100,w,"启动 / 暂停",draft::taskToggle);
    }
    @Override protected void drawScreenBackgroundCompat(DrawContext c,int x,int y){c.fill(0,0,width,height,0x80000000);}
    @Override protected void drawContentsCompat(DrawContext c,int x,int y,float delta){
        c.drawText(textRenderer,draft.materials().size()+" 种材料 / "+draft.totalItems()+" 件",12,136,0xFFDDE6F0,false);
        var report=draft.report();if(report!=null)c.drawText(textRenderer,"计划 "+report.plan().boxes()+" 盒 · 空盒 "+report.emptyBoxes()+" · 输出空槽 "+report.outputSlots(),12,153,0xFFAAAAAA,false);
        c.drawText(textRenderer,textRenderer.trimToWidth(message.isEmpty()?draft.status():message,width-24),12,height-28,0xFFE2C478,false);
    }
}

