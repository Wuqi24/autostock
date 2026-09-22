package dev.autostock.client;

import dev.autostock.core.ScanReport;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.DrawContext;

final class PackingScreen extends CompatGuiBase {
    private final ScanReport report;
    private int boxIndex;
    private int slotPage;
    PackingScreen(ScanReport report) { this.report = report; title = "装盒计划预览"; }
    @Override public void initGui() {
        super.initGui();
        addButton(new ButtonGeneric(width - 78, 8, 64, 20, "← 返回"), (b, m) -> closeGui(true));
        addButton(new ButtonGeneric(12, height - 27, 62, 20, "上一盒"), (b, m) -> { boxIndex = Math.max(0, boxIndex - 1); slotPage = 0; });
        addButton(new ButtonGeneric(78, height - 27, 62, 20, "下一盒"), (b, m) -> { boxIndex = Math.min(Math.max(0, report.plan().preview().size() - 1), boxIndex + 1); slotPage = 0; });
        addButton(new ButtonGeneric(148, height - 27, 62, 20, "上一页"), (b, m) -> slotPage = Math.max(0, slotPage - 1));
        addButton(new ButtonGeneric(214, height - 27, 62, 20, "下一页"), (b, m) -> {
            if (!report.plan().preview().isEmpty()) slotPage = Math.min((report.plan().preview().get(boxIndex).slots().size() - 1) / pageSize(), slotPage + 1);
        });
    }
    private int pageSize() { return Math.max(1, (height - 159) / 14); }
    @Override protected void drawContentsCompat(DrawContext c, int mx, int my, float delta) {
        super.drawContentsCompat(c, mx, my, delta);
        boolean complete = report.plan().complete();
        boolean missingBoxes = report.emptyBoxes() < report.plan().boxes();
        boolean missingOutput = report.outputSlots() < report.plan().boxes();
        String capacity = missingBoxes && missingOutput ? "空盒和备货槽位不足"
                : missingBoxes ? "空盒不足" : missingOutput ? "备货区槽位不足"
                : !complete ? "缺料或不可装盒，最终容量待重算" : "空盒与备货槽位充足";
        line(c, (complete ? "预计需 " : "可用材料计划 ") + report.plan().boxes() + " 盒；" + capacity, 38, 0xFFFFD782);
        line(c, "空盒可用 " + report.emptyBoxes() + "；备货空槽 " + report.outputSlots() + "；仅预览前 8 盒", 53, 0xFFB8C5D9);
        line(c, "按实际堆叠兼容性分槽；不补填已有备货盒，不执行装盒", 68, 0xFFB8C5D9);
        var preview = report.plan().preview();
        if (preview.isEmpty()) { line(c, "当前没有新盒；请检查需求、已有备货及缺口", 96, 0xFFFFFFFF); return; }
        var box = preview.get(boxIndex);
        int count = box.slots().size();
        boolean full = count == 27 && box.slots().stream().allMatch(slot -> slot.count() == slot.limit());
        String state = full ? "装满" : count == 27 ? "本计划无有效容量" : "部分剩余";
        line(c, "盒 " + box.number() + " · " + state + "（占用 " + count + "/27 槽）", 91, 0xFF8CD8B0);
        int start = slotPage * pageSize();
        for (int n = 0; n < pageSize() && start + n < count; n++) {
            var slot = box.slots().get(start + n);
            line(c, "槽 " + (start + n + 1) + "  " + StockReportScreen.itemName(slot.item()) + "  " + slot.count() + "/" + slot.limit(), 112 + n * 14, 0xFFFFFFFF);
        }
    }
    private void line(DrawContext c, String text, int y, int color) {
        c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(text, Math.max(1, width - 24)), 12, y, color);
    }
}

