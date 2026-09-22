package dev.autostock.client;

import dev.autostock.client.mixin.GuiListBaseAccessor;
import dev.autostock.server.BuildingMaterials;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.registry.Registries;
import java.util.Map;
import java.util.TreeMap;

/** Captures every row in the current filtered table, not only the scrolled viewport. */
public final class LitematicaResult {
    record Snapshot(String name, int multiplier, Map<String, Long> counts) {
        Snapshot { counts = Map.copyOf(counts); }
    }
    private static Snapshot latest;
    private static String error = "请先打开 Litematica 材料列表，确认筛选与倍率后关闭该列表";
    private static ClientDraft draft;
    static void register(ClientDraft value) {
        draft=value;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof GuiMaterialList list) ScreenEvents.remove(screen).register(removed -> capture(list));
        });
    }
    public static void addStockButton(GuiMaterialList screen) {
        var client=net.minecraft.client.MinecraftClient.getInstance();
        for(var widget:((dev.autostock.client.mixin.GuiWidgetsAccessor)screen).autostock$widgets())
            if(widget instanceof fi.dy.masa.malilib.gui.widgets.WidgetLabel&&widget.getY()>=screen.getScreenHeight()-36&&widget.getY()<screen.getScreenHeight()-16)widget.setY(widget.getY()-14);
        String main=fi.dy.masa.malilib.util.StringUtils.translate(fi.dy.masa.litematica.gui.GuiMainMenu.ButtonListenerChangeMenu.ButtonType.MAIN_MENU.getLabelKey());
        int mainWidth=client.textRenderer.getWidth(main)+20,buttonWidth=client.textRenderer.getWidth("假人备货")+20;
        int x=screen.getScreenWidth()-10-mainWidth-1-buttonWidth;
        screen.addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(x,screen.getScreenHeight()-36,buttonWidth,20,"假人备货"),(button,mouse)->{
            try {
                if(draft==null)throw new IllegalArgumentException("自动备货尚未初始化");
                for(var role:dev.autostock.core.RegionRole.values())if(!draft.saved(role))throw new IllegalArgumentException("请先设置"+role.label());
                if(draft.hasConflict())throw new IllegalArgumentException("空盒区与备货区重叠");
                if(screen.getMaterialList()==null)throw new IllegalArgumentException("请先选择原理图材料列表");
                capture(screen);latest();
                if(draft.frozen())draft.copyAsDraft();else draft.importTotal();
                draft.freezeDemand();
                var next=new DraftScreen(draft);client.setScreen(next);next.selectTab(2);
            }catch(IllegalArgumentException|ArithmeticException error){
                screen.addGuiMessage(fi.dy.masa.malilib.gui.Message.MessageType.ERROR,5000,error.getMessage());
            }
        });
    }
    static void capture(GuiMaterialList screen) {
        latest = null;
        try {
            var materialList = screen.getMaterialList();
            int multiplier = materialList.getMultiplier();
            if (multiplier < 1) throw new IllegalArgumentException("Litematica 倍率无效");
            var counts = new TreeMap<String, Long>();
            var widget = ((GuiListBaseAccessor) screen).autostock$getListWidget();
            for (Object row : widget.getCurrentEntries()) {
                if (!(row instanceof MaterialListEntry entry)) continue;
                BuildingMaterials.requireSupported(entry.getStack().getItem());
                long count = Math.multiplyExact((long) entry.getCountTotal(), multiplier);
                if (count < 0) throw new IllegalArgumentException("材料数量无效");
                if (count == 0) continue;
                counts.merge(Registries.ITEM.getId(entry.getStack().getItem()).toString(), count, Math::addExact);
            }
            if (counts.size() > 512 || counts.values().stream().mapToLong(Long::longValue).reduce(0L, Math::addExact) > 1_000_000_000L) {
                throw new IllegalArgumentException("材料数量超过首版上限");
            }
            latest = new Snapshot(materialList.getName(), multiplier, counts);
        } catch (RuntimeException failure) { error = failure.getMessage() == null ? "读取当前材料表失败" : failure.getMessage(); }
    }
    static Snapshot latest() { if (latest == null) throw new IllegalArgumentException(error); return latest; }
    static void clear() { latest = null; error = "请重新打开 Litematica 材料列表，确认结果后关闭"; }
}


