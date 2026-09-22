package dev.autostock.client;

import dev.autostock.core.ScanReport;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.util.Locale;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

final class StockReportScreen extends CompatGuiBase {
    private final ScanReport report;
    private int page;
    StockReportScreen(ScanReport report) { this.report = report; title = "备货计划 · 库存快照"; }
    @Override public void initGui() {
        super.initGui();
        addButton(new ButtonGeneric(width - 78, 8, 64, 20, "← 返回"), (b, m) -> closeGui(true));
        addButton(new ButtonGeneric(12, height - 27, 60, 20, "上一页"), (b, m) -> page = Math.max(0, page - 1));
        addButton(new ButtonGeneric(76, height - 27, 60, 20, "下一页"), (b, m) -> page = Math.min(pageCount() - 1, page + 1));
        addButton(new ButtonGeneric(146, height - 27, 80, 20, "装盒预览"),
                (b, m) -> GuiBase.openGui(new PackingScreen(report).setParent(this)));
        addButton(new ButtonGeneric(236, height - 27, 80, 20, "扫描说明"),
                (b, m) -> GuiBase.openGui(new ScanNotesScreen(report).setParent(this)));
    }
    private int pageSize() { return Math.max(1, (height - 144) / 14); }
    private int pageCount() { return Math.max(1, (report.plan().rows().size() + pageSize() - 1) / pageSize()); }
    @Override protected void drawContentsCompat(GuiContext c, int mx, int my, float delta) {
        super.drawContentsCompat(c, mx, my, delta);
        line(c, "原理图：" + report.schematicName() + " · 页 " + (page + 1) + "/" + pageCount(), 12, 38, width - 24, 0xFFFFFFFF);
        line(c, (report.plan().complete() ? "本次待备 " : "可用材料的部分计划 ") + report.plan().boxes()
                + " 盒 · 空盒 " + report.emptyBoxes() + " · 备货空槽 " + report.outputSlots(), 12, 53, width - 24, 0xFFFFD782);
        line(c, "蓝：材料 / 紫：空盒 / 绿：备货；点击材料行查看精确数量", 12, 68, width - 24, 0xFFB8C5D9);
        line(c, "当前仅规划；库存或来源变化后需重新扫描", 12, 83, width - 24, 0xFFB8C5D9);
        int col = Math.max(27, Math.min(65, (width - 130) / 7));
        int x = width - 12 - col * 7;
        String[] headers = {"需求", "已备", "在途", "待取", "库存", "缺口", "来源"};
        line(c, "材料", 12, 101, x - 18, 0xFFB8C5D9);
        for (int i = 0; i < 7; i++) line(c, headers[i], x + i * col, 101, col - 3, 0xFFB8C5D9);
        int start = page * pageSize();
        for (int n = 0; n < pageSize() && start + n < report.plan().rows().size(); n++) {
            var row = report.plan().rows().get(start + n);
            int color = row.shortage() > 0 ? 0xFFFF8585 : row.pending() > 0 ? 0xFFFFD782 : 0xFF8CD8B0;
            int y = 116 + n * 14;
            line(c, itemName(row.item()), 12, y, x - 18, color);
            long[] values = {row.required(), row.stocked(), row.inTransit(), row.pending(), row.available(), row.shortage(), row.sources()};
            for (int i = 0; i < 7; i++) line(c, compact(values[i]), x + i * col, y, col - 3, color);
        }
    }
    private boolean handleMouseClicked(double x, double y, int button) {
        if (button == 0 && y >= 116 && y < height - 35) {
            int row = page * pageSize() + ((int)y - 116) / 14;
            if (row < report.plan().rows().size()) {
                GuiBase.openGui(new MaterialScreen(report.plan().rows().get(row)).setParent(this));
                return true;
            }
        }
        return false;
    }
    //? if <=1.21.8 {
    /*@Override public boolean onMouseClicked(int x,int y,int button){return handleMouseClicked(x,y,button)||super.onMouseClicked(x,y,button);}
    *///?} else {
    @Override public boolean onMouseClicked(net.minecraft.client.input.MouseButtonEvent click,boolean doubled){return handleMouseClicked(click.x(),click.y(),click.button())||super.onMouseClicked(click,doubled);}
    //?}
    static String itemName(String id) { return new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))).getHoverName().getString(); }
    private static String compact(long value) {
        if (value >= 100_000_000) return String.format(Locale.ROOT, "%.1f亿", value / 100_000_000.0);
        if (value >= 100_000) return String.format(Locale.ROOT, "%.1f万", value / 10_000.0);
        return Long.toString(value);
    }
    private void line(GuiContext c, String text, int x, int y, int available, int color) {
        c.drawString(textRenderer, textRenderer.plainSubstrByWidth(text, Math.max(1, available)), x, y, color);
    }
}

