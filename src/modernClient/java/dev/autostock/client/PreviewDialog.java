package dev.autostock.client;

import fi.dy.masa.malilib.gui.GuiBase;
import java.util.List;
import java.util.function.Supplier;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.network.chat.Component;

/** A compact native GUI dialog; long details scroll inside its content area. */
final class PreviewDialog extends CompatGuiBase {
    record Action(String text, Runnable action) { }
    private final String heading;
    private final Supplier<List<String>> lines;
    private final List<Action> actions;
    private int offset, left, top, panelWidth, panelHeight, contentHeight;

    PreviewDialog(GuiBase parent, String heading, Supplier<List<String>> lines, Action... actions) {
        setParent(parent);
        this.heading = heading;
        this.lines = lines;
        this.actions = List.of(actions);
        title = "";
        useTitleHierarchy = false;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void initGui() {
        super.initGui();
        panelWidth = Math.min(360, width - 24);
        contentHeight = 0;
        for (String line : lines.get()) contentHeight += textRenderer.split(Component.literal(line), panelWidth - 24).size() * 13;
        panelHeight = Math.min(height - 24, Math.max(110, contentHeight + 68));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int unit = Math.min(88, (panelWidth - 16) / Math.max(1, actions.size()));
        for (int i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            addButton(new fi.dy.masa.malilib.gui.button.ButtonGeneric(width / 2 - actions.size() * unit / 2 + i * unit, top + panelHeight - 30, unit - 6, 20, action.text), (b, m) -> {
                closeGui(true);
                if (action.action != null) AutoStockClient.activeDraft.perform(action.action);
            });
        }
    }

    @Override protected void drawScreenBackgroundCompat(GuiContext c, int x, int y) {
        c.fill(0, 0, width, height, 0x70000000);
        RegionSetupScreen.panel(c, left, top, panelWidth, panelHeight, 0xFF666666, 0xEE242424);
    }

    @Override protected void drawContentsCompat(GuiContext c, int x, int y, float delta) {
        c.drawString(textRenderer, textRenderer.plainSubstrByWidth(heading, panelWidth - 24), left + 12, top + 12, 0xFFFFFFFF, true);
        c.enableScissor(left + 8, top + 32, left + panelWidth - 8, top + panelHeight - 36);
        int row = top + 34 - offset;
        for (String line : lines.get()) for (var wrapped : textRenderer.split(Component.literal(line), panelWidth - 24)) {
            c.drawString(textRenderer, wrapped, left + 12, row, 0xFFDDDDDD, false);
            row += 13;
        }
        contentHeight = row - (top + 34 - offset);
        c.disableScissor();
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        offset = Math.max(0, Math.min(Math.max(0, contentHeight - panelHeight + 72), offset - (int) (vertical * 20)));
        return true;
    }
}
