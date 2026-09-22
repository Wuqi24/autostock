package dev.autostock.client;

import dev.autostock.core.HudLayout;
import dev.autostock.core.HudLayout.Rect;
import dev.autostock.core.RegionRole;
import dev.autostock.net.TaskStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

final class HudPanels {
    static final float CONTENT_SCALE = 0.65F;
    private static final int REGION = 0;
    private static final int TASK = 1;
    private static final int HEADER_HEIGHT = 14;
    private static final int REGION_DIVIDER_Y = 36;
    private static final int REGION_BAR = 0xFF32C7D9;
    private static final int RUNNING = 0xFF4ADE80;
    private static final int PAUSED = 0xFFF0B64A;
    private static final int IDLE = 0xFF7F8B99;
    private static final RegionRole[] ROLES = RegionRole.values();
    private static final String[] ROLE_LABELS = {"材料", "空盒", "备货"};
    private static final TaskLines EMPTY_TASK_LINES = new TaskLines("等待任务", "—", "— / —", "—", "— / —");

    private static TaskStatus cachedStatus;
    private static TaskLines cachedTaskLines;

    record Style(int opacity, int background, int text, int accent) {
    }

    private record TaskLines(String action, String material, String remaining, String target, String delivered) {
    }

    private HudPanels() {
    }

    static Style style(int panel) {
        var settings = ClientSettings.get();
        return new Style(
                settings.hudOpacity[panel],
                settings.hudBg[panel],
                settings.hudText[panel],
                settings.hudAccent[panel]
        );
    }

    static Rect bounds(int panel, int screenWidth, int screenHeight, float[] saved) {
        return HudLayout.panelBounds(panel, screenWidth, screenHeight, saved);
    }

    static String hint() {
        var settings = ClientSettings.get();
        return HudLayout.regionHint(
                settings.binding(ClientSettings.Action.CYCLE).label(),
                settings.binding(ClientSettings.Action.SAVE).label(),
                settings.binding(ClientSettings.Action.REGION).label()
        );
    }

    static String regionStatus() {
        var draft = AutoStockClient.activeDraft;
        return draft == null
                ? "等待选区"
                : RegionSetupScreen.lastSave
                ? draft.status()
                : draft.bindingLabel(RegionSetupScreen.currentRole());
    }

    static void render(GuiContext context, ClientDraft draft, boolean selection) {
        var client = Minecraft.getInstance();
        var settings = ClientSettings.get();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        var task = draft.taskStatus();
        if (settings.showTaskHud && task != null
                && ("RUNNING".equals(task.state()) || "PAUSED".equals(task.state()))) {
            renderPanel(context, draft, bounds(TASK, width, height, settings.hudPositions), TASK, style(TASK));
        }
        if (selection && settings.showRegionHud) {
            renderPanel(context, draft, bounds(REGION, width, height, settings.hudPositions), REGION, style(REGION));
        }
    }

    static void text(GuiContext context, String value, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        String fitted = HudLayout.ellipsize(value, Math.max(0, width), font::width);
        context.drawString(font, fitted, x, y, color, false);
    }

    static String botName() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return "AS_----";
        }
        String uuid = player.getUUID().toString().replace("-", "");
        return "AS_" + uuid.substring(0, Math.min(4, uuid.length())).toUpperCase(java.util.Locale.ROOT);
    }

    static String itemName(String id) {
        if (id == null || id.isEmpty()) {
            return "—";
        }
        var key = Identifier.tryParse(id);
        return key == null ? id : new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getValue(key)).getHoverName().getString();
    }

    static void renderPanel(GuiContext context, ClientDraft draft, Rect rect, int panel, Style style) {
        if (rect.width() < 8 || rect.height() < 8) {
            return;
        }
        context.enableScissor(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height());
        try {
            drawChrome(context, rect, panel, draft.taskStatus(), style);
            //? if <=1.21.5 {
            /*context.getMatrices().push();
            *///?} else {
            context.pose().pushMatrix();
            //?}
            try {
                //? if <=1.21.5 {
                /*context.getMatrices().translate(rect.x() + 4, rect.y() + 1, 0);
                context.getMatrices().scale(CONTENT_SCALE, CONTENT_SCALE, 1);
                *///?} else {
                context.pose().translate(rect.x() + 4, rect.y() + 1);
                context.pose().scale(CONTENT_SCALE, CONTENT_SCALE);
                //?}
                int logicalWidth = Math.max(1, (int) Math.floor((rect.width() - 6) / CONTENT_SCALE));
                if (panel == REGION) {
                    renderRegion(context, draft, logicalWidth, style);
                } else {
                    renderTask(context, draft.taskStatus(), logicalWidth, style);
                }
            } finally {
                //? if <=1.21.5 {
                /*context.getMatrices().pop();
                *///?} else {
                context.pose().popMatrix();
                //?}
            }
        } finally {
            context.disableScissor();
        }
    }

    private static void drawChrome(GuiContext context, Rect rect, int panel, TaskStatus task, Style style) {
        int x = rect.x();
        int y = rect.y();
        int right = x + rect.width();
        int bottom = y + rect.height();
        int background = withOpacity(style.background(), style.opacity());
        int title = withOpacity(darken(style.background(), 0.72F), style.opacity());
        int border = withOpacity(0xFF647181, Math.min(100, style.opacity() + 28));
        context.fill(x, y, right, bottom, background);
        context.fill(x + 1, y + 1, right - 1, Math.min(bottom - 1, y + HEADER_HEIGHT), title);
        context.fill(x, y, right, y + 1, border);
        context.fill(x, bottom - 1, right, bottom, border);
        context.fill(x, y + 1, x + 1, bottom - 1, border);
        context.fill(right - 1, y + 1, right, bottom - 1, border);
        int bar = panel == REGION ? REGION_BAR : taskColor(task);
        context.fill(x + 1, y + 1, Math.min(right - 1, x + 3), bottom - 1, bar);
        context.fill(x + 3, Math.min(bottom - 1, y + HEADER_HEIGHT), right - 1,
                Math.min(bottom, y + HEADER_HEIGHT + 1), border);
        if (panel == REGION && y + REGION_DIVIDER_Y < bottom - 1) {
            context.fill(x + 3, y + REGION_DIVIDER_Y, right - 1, y + REGION_DIVIDER_Y + 1, border);
        }
        if (panel == TASK && task != null) {
            var font = Minecraft.getInstance().font;
            String state = HudLayout.taskStateLabel(task.state());
            int stateWidth = Math.round(font.width(state) * CONTENT_SCALE);
            int dotX = Math.max(x + 6, right - 5 - stateWidth - 5);
            int color = taskColor(task);
            context.fill(dotX + 1, y + 6, dotX + 2, y + 7, color);
            context.fill(dotX, y + 7, dotX + 3, y + 8, color);
            context.fill(dotX + 1, y + 8, dotX + 2, y + 9, color);
        }
    }

    private static void renderRegion(GuiContext context, ClientDraft draft, int width, Style style) {
        var selected = RegionSetupScreen.currentRole();
        centered(context, "正在绑定：" + selected.label(), 4, width, RegionSetupScreen.color(selected));
        int step = width / 3;
        var font = Minecraft.getInstance().font;
        for (int index = 0; index < ROLES.length; index++) {
            var role = ROLES[index];
            String state = draft.bindingLabel(role);
            boolean bound = "已绑定".equals(state);
            boolean failed = "绑定失败".equals(state);
            String value = (failed ? "✗" : bound ? "✓" : "○") + ROLE_LABELS[index];
            int color = draft.hasConflict() || failed
                    ? 0xFFF87171
                    : bound ? RegionSetupScreen.color(role) : dim(style.text());
            int left = index * step;
            int cellWidth = index == ROLES.length - 1 ? width - left : step;
            String fitted = HudLayout.ellipsize(value, Math.max(1, cellWidth - 2), font::width);
            int textX = left + Math.max(1, (cellWidth - font.width(fitted)) / 2);
            context.drawString(font, fitted, textX, 22, color, false);
        }
        centered(context, regionStatus(), 39, width, style.accent());
        centered(context, hint(), 58, width, dim(style.text()));
    }

    private static void renderTask(GuiContext context, TaskStatus task, int width, Style style) {
        var font = Minecraft.getInstance().font;
        String state = task == null ? "空闲" : HudLayout.taskStateLabel(task.state());
        int stateColor = task == null ? dim(style.text()) : taskColor(task);
        int stateWidth = font.width(state);
        text(context, botName(), 6, 4, Math.max(1, width - stateWidth - 20), style.accent());
        rightText(context, state, 6, 4, width - 6, stateColor);

        TaskLines lines = taskLines(task);
        pair(context, "当前动作", lines.action(), 25, width, style, false, true);
        pair(context, "当前材料", lines.material(), 41, width, style, false, false);
        pair(context, "缺口/在途", lines.remaining(), 57, width, style, true, false);
        pair(context, "目标容器", lines.target(), 73, width, style, true, false);
        pair(context, "已交付", lines.delivered(), 89, width, style, true, false);
    }

    private static TaskLines taskLines(TaskStatus task) {
        if (task == cachedStatus && cachedTaskLines != null) {
            return cachedTaskLines;
        }
        if (task == null) {
            return EMPTY_TASK_LINES;
        }
        String target = task.target() == null
                ? "—"
                : HudLayout.coordinates(task.target().getX(), task.target().getY(), task.target().getZ());
        cachedStatus = task;
        cachedTaskLines = new TaskLines(
                task.detail(),
                itemName(task.material()),
                task.materialRemaining() + " / " + task.inTransit(),
                target,
                task.delivered() + " / " + task.required()
        );
        return cachedTaskLines;
    }

    private static void pair(GuiContext context, String label, String value, int y, int width, Style style,
                             boolean rightAligned, boolean emphasized) {
        int valueX = 58;
        text(context, label, 6, y, valueX - 10, dim(style.text()));
        int color = emphasized ? style.accent() : style.text();
        if (rightAligned) {
            rightText(context, value, valueX, y, width - 6, color);
        } else {
            text(context, value, valueX, y, Math.max(1, width - valueX - 6), color);
        }
    }

    private static void centered(GuiContext context, String value, int y, int width, int color) {
        Font font = Minecraft.getInstance().font;
        String fitted = HudLayout.ellipsize(value, Math.max(1, width - 12), font::width);
        context.drawString(font, fitted, Math.max(6, (width - font.width(fitted)) / 2), y, color, false);
    }

    private static void rightText(GuiContext context, String value, int left, int y, int right, int color) {
        Font font = Minecraft.getInstance().font;
        String fitted = HudLayout.ellipsize(value, Math.max(1, right - left), font::width);
        context.drawString(font, fitted, Math.max(left, right - font.width(fitted)), y, color, false);
    }

    private static int taskColor(TaskStatus task) {
        if (task == null) {
            return IDLE;
        }
        return "PAUSED".equals(task.state()) ? PAUSED : RUNNING;
    }

    private static int withOpacity(int argb, int opacity) {
        int sourceAlpha = argb >>> 24;
        int alpha = Math.round(sourceAlpha * Math.max(0, Math.min(100, opacity)) / 100.0F);
        return alpha << 24 | argb & 0xFFFFFF;
    }

    private static int darken(int argb, float factor) {
        int red = Math.round((argb >> 16 & 0xFF) * factor);
        int green = Math.round((argb >> 8 & 0xFF) * factor);
        int blue = Math.round((argb & 0xFF) * factor);
        return argb & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int dim(int argb) {
        return darken(argb, 0.68F);
    }
}
