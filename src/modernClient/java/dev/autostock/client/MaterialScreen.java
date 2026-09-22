package dev.autostock.client;

import dev.autostock.core.StockPlanner;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;

final class MaterialScreen extends CompatGuiBase {
    private final StockPlanner.Row row;
    MaterialScreen(StockPlanner.Row row) { this.row = row; title = "材料详情"; }
    @Override public void initGui() {
        super.initGui();
        addButton(new ButtonGeneric(width - 78, 8, 64, 20, "← 返回"), (button, mouse) -> closeGui(true));
        addLabel(14, 42, width - 28, 160, 0xFFFFFFFF,
                StockReportScreen.itemName(row.item()), "物品 ID：" + row.item(),
                "总需求：" + row.required(), "已有备货：" + row.stocked(), "在途数量：" + row.inTransit(), "仍需取料：" + row.pending(),
                "材料区库存：" + row.available(), "快照缺口：" + row.shortage(), "来源容器数：" + row.sources(),
                "状态：" + (row.shortage() > 0 ? "缺货" : row.pending() > 0 ? "待备，材料充足" : "已备足"));
    }
}

