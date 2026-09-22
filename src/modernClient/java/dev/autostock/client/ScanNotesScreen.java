package dev.autostock.client;

import dev.autostock.core.ScanReport;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.util.ArrayList;
import java.util.List;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class ScanNotesScreen extends CompatGuiBase {
    private final ScanReport report;
    private List<FormattedCharSequence> lines = List.of();
    private int page;

    ScanNotesScreen(ScanReport report) { this.report = report; title = "扫描说明与限制"; }

    @Override public void initGui() {
        super.initGui();
        var wrapped = new ArrayList<FormattedCharSequence>();
        var notes = new ArrayList<>(report.notes());
        notes.add("容器数：材料 " + report.materialContainers() + "，空盒 " + report.emptyContainers()
                + "，备货 " + report.outputContainers() + "；扫描用时 " + report.scanTicks() + " tick。");
        notes.add("扫描不判断寻路、交互距离或开盖空间；库存充足不代表假人能够取到。");
        for (int i = 0; i < notes.size(); i++) {
            wrapped.addAll(textRenderer.split(Component.literal((i + 1) + ". " + notes.get(i)), Math.max(1, width - 28)));
            wrapped.add(FormattedCharSequence.EMPTY);
        }
        lines = List.copyOf(wrapped);
        page = Math.min(page, pageCount() - 1);
        addButton(new ButtonGeneric(width - 78, 8, 64, 20, "← 返回"), (b, m) -> closeGui(true));
        addButton(new ButtonGeneric(12, height - 27, 62, 20, "上一页"), (b, m) -> page = Math.max(0, page - 1));
        addButton(new ButtonGeneric(78, height - 27, 62, 20, "下一页"), (b, m) -> page = Math.min(pageCount() - 1, page + 1));
    }

    private int pageSize() { return Math.max(1, (height - 85) / 14); }
    private int pageCount() { return Math.max(1, (lines.size() + pageSize() - 1) / pageSize()); }

    @Override protected void drawContentsCompat(GuiContext context, int mouseX, int mouseY, float delta) {
        super.drawContentsCompat(context, mouseX, mouseY, delta);
        int start = page * pageSize();
        for (int i = 0; i < pageSize() && start + i < lines.size(); i++) {
            context.drawString(textRenderer, lines.get(start + i), 12, 38 + i * 14, 0xFFD8E3F0);
        }
        context.drawString(textRenderer, "页 " + (page + 1) + "/" + pageCount(), 150, height - 21, 0xFFB8C5D9);
    }
}

