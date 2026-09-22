package dev.autostock.client;

import dev.autostock.core.RegionRole;
import dev.autostock.core.StockPlanner;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class PreviewScreen extends GuiConfigsBase {
    private static final String[] TABS = {"通用", "区域", "任务", "显示", "假人", "热键"};
    private static final String[] COLOR_NAMES = {"材料区", "空盒区", "备货区", "计划路径", "边界线", "缺货高亮"};

    protected final ClientDraft draft;
    private final List<ConfigOptionWrapper> rows = new ArrayList<>();
    private final EnumMap<ClientSettings.Action, ConfigHotkey> hotkeys = new EnumMap<>(ClientSettings.Action.class);
    private final java.util.IdentityHashMap<ConfigOptionWrapper, ActionRow> actionRows = new java.util.IdentityHashMap<>();
    private int page;
    private boolean pendingGeneration;

    private record Action(String label, Runnable run) {}
    private record ActionRow(String label, String value, List<Action> actions) {}

    PreviewScreen(ClientDraft draft) {
        super(10, 50, "autostock", null, "自动备货配置");
        this.draft = draft;
        useTitleHierarchy = false;
        rebuildOptions();
    }

    boolean watchesBot() {
        return page == 4;
    }

    void navigate(int targetPage) {
        if (targetPage == page) return;
        applyPendingChanges();
        saveHotkeys();
        page = targetPage;
        rebuildOptions();
        reCreateListWidget();
        initGui();
    }

    @Override
    public void initGui() {
        super.initGui();
        clearOptions();
        if (page == 5) addKeybindChangeListener(this::saveHotkeys);

        int x = 10;
        for (int i = 0; i < TABS.length; i++) {
            int target = i;
            ButtonGeneric tab = new ButtonGeneric(x, 26, -1, 20, TABS[i]);
            tab.setEnabled(page != i);
            addButton(tab, (button, mouseButton) -> navigate(target));
            x += tab.getWidth() + 2;
        }

    }

    @Override
    protected WidgetListConfigOptions createListWidget(int x, int y) {
        return new NativeConfigList(
                x,
                y,
                getBrowserWidth(),
                getBrowserHeight(),
                getConfigWidth(),
                0F,
                useKeybindSearch(),
                this
        );
    }

    @Override
    protected int getConfigWidth() {
        return switch (page) {
            case 0 -> 250;
            case 3 -> 220;
            case 5 -> 260;
            default -> 180;
        };
    }

    @Override
    protected boolean useKeybindSearch() {
        return page == 5;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return rows;
    }

    @Override
    protected void onSettingsChanged() {
        saveHotkeys();
        ClientSettings.get().save();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingGeneration && draft.frozen()) {
            pendingGeneration = false;
            generated();
        }
    }

    @Override
    public void removed() {
        applyPendingChanges();
        saveHotkeys();
        ClientSettings.get().save();
        super.removed();
    }

    void refreshInventory() {
        if (page == 4) rebuildPage();
    }

    boolean typing() {
        return true;
    }

    private void buildActionRows() {
        switch (page) {
            case 1 -> buildRegionActions();
            case 2 -> buildTaskActions();
            case 3 -> actionRow("HUD 布局", "拖动位置并调整外观",
                    new Action("打开编辑器", () -> ClientMinecraftCompat.setScreen(mc, new HudPositionScreen(draft, this))));
            case 4 -> buildBotActions();
            default -> { }
        }
    }

    private void buildRegionActions() {
        for (RegionRole role : RegionRole.values()) {
            actionRow(role.label(), draft.bindingLabel(role),
                    new Action("详情", () -> regionDetails(role)),
                    new Action(draft.saved(role) ? "重新绑定" : "绑定", () -> bindRegion(role)));
        }
        actionRow("重叠检测", overlapSummary());
    }

    private void bindRegion(RegionRole role) {
        confirm(
                "绑定" + role.label(),
                "将使用当前 Litematica 选区绑定 " + role.label() + "。\n绑定后 Mod 将扫描区域内所有容器。",
                "确认绑定",
                () -> {
                    draft.prepareRegionEdit();
                    draft.capture(role);
                    draft.scanRegions();
                }
        );
    }

    private String overlapSummary() {
        return overlap(RegionRole.EMPTY_BOX, RegionRole.OUTPUT) + " · "
                + overlap(RegionRole.MATERIAL, RegionRole.EMPTY_BOX) + " · "
                + overlap(RegionRole.MATERIAL, RegionRole.OUTPUT);
    }

    private String overlap(RegionRole first, RegionRole second) {
        if (!draft.saved(first) || !draft.saved(second)) return first.label() + "∩" + second.label() + " 未绑定";
        boolean hit = draft.regions().getOrDefault(first, List.of()).stream()
                .anyMatch(a -> draft.regions().getOrDefault(second, List.of()).stream().anyMatch(a::overlaps));
        return first.label() + "∩" + second.label() + (hit ? " 有重叠" : " 无重叠");
    }

    private void buildTaskActions() {
        var task = draft.taskStatus();
        String taskState = task == null ? draft.status() : stateName(task.state());
        String toggle = task != null && task.state().equals("RUNNING") ? "暂停" : "启动";

        actionRow("原理图", schematicName() + " · " + blockCount() + " 方块 · " + draft.materials().size() + " 种材料",
                new Action("查看详情", this::schematicDetails));
        actionRow("生成备货清单", "当前倍率 ×" + draft.multiplier(),
                new Action("生成", this::generatePlan));
        actionRow("当前任务", taskState,
                new Action("详情", this::taskDetails),
                new Action(toggle, draft::taskToggle),
                new Action("取消", draft::taskCancel));
        actionRow("备货计划", draft.report() == null ? "尚未生成" : "已生成",
                new Action("查看详情", () -> ClientMinecraftCompat.setScreen(mc, new PlanDetailScreen(draft, this))));
    }

    private void generatePlan() {
        if (draft.frozen()) draft.copyAsDraft();
        draft.importTotal();
        draft.freezeDemand();
        pendingGeneration = true;
    }

    private void buildBotActions() {
        var bot = InventoryView.snapshot;
        if (bot == null || bot.state().equals("MISSING")) {
            actionRow("在线假人", "暂无");
        } else if (bot.state().equals("ONLINE")) {
            actionRow(HudPanels.botName(), InventoryView.status(),
                    new Action("详情", () -> ClientMinecraftCompat.setScreen(mc, new BotInventoryScreen(this))),
                    new Action("召回", () -> command("autostock-recall")));
        } else {
            actionRow(HudPanels.botName(), InventoryView.status(),
                    new Action("详情", () -> ClientMinecraftCompat.setScreen(mc, new BotInventoryScreen(this))));
        }
        actionRow("放置新假人", "使用当前目标位置", new Action("放置", () -> command("autostock-spawn")));
    }

    private void actionRow(String label, String value, Action... actions) {
        ConfigOptionWrapper wrapper = new ConfigOptionWrapper("");
        rows.add(wrapper);
        actionRows.put(wrapper, new ActionRow(label, value, List.of(actions)));
    }

    private void option(IConfigBase option) {
        rows.add(new ConfigOptionWrapper(option));
    }

    private void rebuildOptions() {
        rows.clear();
        actionRows.clear();
        hotkeys.clear();
        ClientSettings settings = ClientSettings.get();
        buildActionRows();
        switch (page) {
            case 0 -> {
                option(stringOption("自定义通知内容", "狗修金撒码，材料准备好了", settings.completionMessage,
                        "完成通知，最多 128 个字符", value -> {
                            if (!value.isBlank() && value.length() <= 128) settings.completionMessage = value;
                        }));
                option(stringOption("通知前缀", "[自动备货] ", settings.notificationPrefix,
                        "最多 32 个字符", value -> {
                            if (value.length() <= 32) settings.notificationPrefix = value;
                        }));
                option(booleanOption("完成后发送通知", true, settings.notifyComplete, "", value -> settings.notifyComplete = value));
                option(booleanOption("缺货时发送通知", true, settings.notifyShortage, "", value -> settings.notifyShortage = value));
                option(booleanOption("通知音效", false, settings.notificationSound, "", value -> settings.notificationSound = value));
                option(integerOption("扫描范围", 32, settings.scanRange, 8, 64, true, "方块", value -> settings.scanRange = value));
                option(integerOption("取货间隔", 10, settings.pickupInterval, 1, 1200, false, "刻", value -> settings.pickupInterval = value));
            }
            case 1 -> option(booleanOption("在世界中渲染区域边界", true, settings.showRegions, "", value -> settings.showRegions = value));
            case 2 -> {
                option(booleanOption("循环补货", false, settings.loop, "", value -> settings.loop = value));
                option(booleanOption("缺货时自动触发", false, settings.autoTrigger, "", value -> settings.autoTrigger = value));
            }
            case 3 -> {
                option(booleanOption("显示区域边界", true, settings.showRegions, "", value -> settings.showRegions = value));
                option(booleanOption("显示容器状态标记", true, settings.showContainers, "", value -> settings.showContainers = value));
                option(booleanOption("高亮缺货容器", true, settings.highlightShortage, "", value -> settings.highlightShortage = value));
                for (int i = 0; i < COLOR_NAMES.length; i++) option(colorOption(COLOR_NAMES[i], i));
            }
            case 4 -> {
                option(booleanOption("自动寻路", true, settings.autoPath, "", value -> settings.autoPath = value));
                option(booleanOption("QuickShulker 装盒", true, settings.quickShulker, "", value -> settings.quickShulker = value));
                option(booleanOption("静音（潜行减速）", false, settings.silent, "", value -> settings.silent = value));
                option(integerOption("移动速度", 100, settings.moveSpeed, 50, 200, true, "百分比", value -> settings.moveSpeed = value));
            }
            case 5 -> {
                for (ClientSettings.Action action : ClientSettings.Action.values()) {
                    ConfigHotkey option = new ConfigHotkey(action.label, defaultKeyStorage(action), KeybindSettings.DEFAULT);
                    option.setValueFromString(keyStorage(action));
                    hotkeys.put(action, option);
                    option(option);
                }
            }
            default -> { }
        }
    }

    private ConfigBoolean booleanOption(String name, boolean defaultValue, boolean currentValue, String comment,
                                        Consumer<Boolean> setter) {
        ConfigBoolean option = new ConfigBoolean(name, defaultValue, comment);
        option.setBooleanValue(currentValue);
        option.setValueChangeCallback(value -> {
            setter.accept(value.getBooleanValue());
            ClientSettings.get().save();
        });
        return option;
    }

    private ConfigInteger integerOption(String name, int defaultValue, int currentValue, int min, int max,
                                        boolean slider, String comment, IntConsumer setter) {
        ConfigInteger option = new ConfigInteger(name, defaultValue, min, max, slider, comment);
        option.setIntegerValue(currentValue);
        option.setValueChangeCallback(value -> {
            setter.accept(value.getIntegerValue());
            ClientSettings.get().save();
        });
        return option;
    }

    private ConfigString stringOption(String name, String defaultValue, String currentValue, String comment,
                                      Consumer<String> setter) {
        ConfigString option = new ConfigString(name, defaultValue, comment);
        option.setValueFromString(currentValue);
        option.setValueChangeCallback(value -> {
            setter.accept(value.getStringValue());
            ClientSettings.get().save();
        });
        return option;
    }

    private ConfigColor colorOption(String name, int index) {
        ClientSettings settings = ClientSettings.get();
        ConfigColor option = new ConfigColor(name, ColorEditor.format(ClientSettings.DEFAULT_COLORS[index]),
                "#AARRGGBB，可设置透明度；点击右侧色块打开调色板");
        option.setIntegerValue(settings.colors[index]);
        option.setValueChangeCallback(value -> {
            settings.colors[index] = value.getIntegerValue();
            settings.save();
        });
        return option;
    }

    private void saveHotkeys() {
        if (hotkeys.isEmpty()) return;
        for (var entry : hotkeys.entrySet()) {
            List<Integer> keys = entry.getValue().getKeybind().getKeys();
            if (!ClientSettings.get().binding(entry.getKey()).codes().equals(keys)) {
                draft.perform(() -> ClientSettings.get().bindKeys(entry.getKey(), keys));
            }
        }
    }

    private void rebuildPage() {
        applyPendingChanges();
        rebuildOptions();
        reCreateListWidget();
        if (ClientMinecraftCompat.screen(mc) == this) initGui();
    }

    private void act(Runnable action) {
        draft.perform(action);
        if (ClientMinecraftCompat.screen(mc) == this) rebuildPage();
    }

    private void applyPendingChanges() {
        WidgetListConfigOptions list = getListWidget();
        if (list != null && list.wereConfigsModified()) {
            list.applyPendingModifications();
            onSettingsChanged();
            list.clearConfigsModifiedFlag();
        }
    }

    private void modal(String heading, Supplier<List<String>> content, PreviewDialog.Action... actions) {
        ClientMinecraftCompat.setScreen(mc, new PreviewDialog(this, heading, content, actions));
    }

    private PreviewDialog.Action dialogAction(String label, Runnable action) {
        return new PreviewDialog.Action(label, action);
    }

    private void confirm(String title, String text, String button, Runnable action) {
        modal(title, () -> List.of(text), dialogAction("取消", null), dialogAction(button, action));
    }

    private static String stateName(String state) {
        return switch (state) {
            case "RUNNING" -> "运行中";
            case "PAUSED" -> "暂停";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            case "EMERGENCY" -> "紧急停止";
            case "ERROR" -> "执行失败";
            default -> state;
        };
    }

    private void regionDetails(RegionRole role) {
        modal(role.label() + " · 详情", () -> {
            var binding = draft.regionBinding;
            List<String> lines = new ArrayList<>();
            lines.add("状态  " + draft.bindingLabel(role));
            lines.add("维度  " + (mc.level == null ? "—" : mc.level.dimension().identifier()));
            for (var box : draft.regions().getOrDefault(role, List.of())) {
                lines.add("起点  " + box.minX() + ", " + box.minY() + ", " + box.minZ());
                lines.add("终点  " + box.maxX() + ", " + box.maxY() + ", " + box.maxZ());
            }
            lines.add("容器数  " + (binding == null ? "—" : binding.containers().getOrDefault(role, 0)));
            if (role == RegionRole.EMPTY_BOX) lines.add("可用空盒  " + (binding == null ? "—" : binding.emptyBoxes()));
            if (role == RegionRole.OUTPUT) lines.add("可用槽位  " + (binding == null ? "—" : binding.emptySlots()));
            return lines;
        }, dialogAction("关闭", null), dialogAction("重新扫描", draft::scanRegions));
    }

    private fi.dy.masa.litematica.schematic.LitematicaSchematic target() {
        if (Schematics.selected != null && Schematics.loaded().contains(Schematics.selected)) return Schematics.selected;
        var all = Schematics.loaded();
        var matched = all.stream().filter(schematic -> schematic.getMetadata().getName().equals(draft.schematicName())).toList();
        if (matched.size() == 1) return matched.getFirst();
        if (all.size() == 1) return all.getFirst();
        throw new IllegalArgumentException("请从原理图列表中选择");
    }

    private String blockCount() {
        try {
            return Integer.toString(target().getMetadata().getTotalBlocks());
        } catch (IllegalArgumentException error) {
            return "—";
        }
    }

    private String schematicName() {
        try {
            return SchematicCompat.file(target()) == null
                    ? target().getMetadata().getName()
                    : SchematicCompat.file(target()).getFileName().toString();
        } catch (IllegalArgumentException error) {
            return draft.schematicName();
        }
    }

    private void schematicDetails() {
        modal("原理图详情", () -> {
            List<String> lines = new ArrayList<>();
            lines.add("文件名  " + schematicName());
            lines.add("方块总数  " + blockCount());
            lines.add("材料种数  " + draft.materials().size());
            lines.add("倍率  ×" + draft.multiplier());
            lines.add("材料列表（前 4 项）");
            draft.materials().stream().limit(4)
                    .forEach(entry -> lines.add(HudPanels.itemName(entry.getKey()) + "  " + entry.getValue()));
            return lines;
        }, dialogAction("关闭", null));
    }

    private void generated() {
        modal("备货清单已生成", () -> {
            var report = draft.report();
            return List.of(
                    "原理图  " + schematicName(),
                    "材料种数  " + draft.materials().size(),
                    "总需求  " + draft.totalItems() + " 个",
                    "已有备货  " + (report == null ? "—" : report.plan().rows().stream().mapToLong(StockPlanner.Row::stocked).sum()),
                    "待备数量  " + (report == null ? "—" : report.plan().rows().stream().mapToLong(StockPlanner.Row::pending).sum()),
                    "预计成品盒  " + (report == null ? "—" : report.plan().boxes()),
                    draft.status()
            );
        }, dialogAction("关闭", null),
                dialogAction("查看明细", () -> ClientMinecraftCompat.setScreen(mc, new PlanDetailScreen(draft, this))));
    }

    private void taskDetails() {
        var task = draft.taskStatus();
        String button = task == null ? "继续" : task.state().equals("RUNNING") ? "暂停" : task.state().equals("COMPLETED") ? "完成" : "继续";
        modal((task == null ? "当前任务" : task.detail()) + " · 任务详情", () -> {
            var current = draft.taskStatus();
            if (current == null) return List.of(draft.status());
            return List.of(
                    "任务编号  " + current.taskId(),
                    "执行假人  " + HudPanels.botName(),
                    "状态  " + stateName(current.state()),
                    "预计剩余  " + (current.etaSeconds() < 0 ? "—" : current.etaSeconds() + " 秒"),
                    "当前材料  " + HudPanels.itemName(current.material()),
                    "当前进度  " + (current.required() == 0 ? 0 : 100 * current.delivered() / current.required()) + "%",
                    "还差  " + Math.max(0, current.required() - current.delivered()),
                    "目标容器  " + Objects.toString(current.target(), "—"),
                    "路径长度  " + current.path().size(),
                    "详细状态  " + current.detail()
            );
        }, dialogAction("关闭", null), dialogAction(button, button.equals("完成") ? null : draft::taskToggle));
    }

    private void command(String name) {
        BotControls.send(name.substring("autostock-".length()), draft);
    }

    private static String keyStorage(ClientSettings.Action action) {
        return storageString(ClientSettings.get().binding(action).codes());
    }

    private static String defaultKeyStorage(ClientSettings.Action action) {
        return storageString(new ClientSettings.Binding(action.key, action.mods).codes());
    }

    private static String storageString(List<Integer> keys) {
        return keys.stream()
                .filter(key -> key != -1)
                .map(KeybindMulti::getStorageStringForKeyCode)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static final class NativeConfigList extends WidgetListConfigOptions {
        private final PreviewScreen owner;

        private NativeConfigList(int x, int y, int width, int height, int configWidth, float zLevel,
                                 boolean useKeybindSearch, PreviewScreen owner) {
            super(x, y, width, height, configWidth, zLevel, useKeybindSearch, owner);
            this.owner = owner;
        }

        @Override
        protected List<String> getEntryStringsForFilter(ConfigOptionWrapper wrapper) {
            ActionRow row = owner.actionRows.get(wrapper);
            if (row == null) return super.getEntryStringsForFilter(wrapper);
            List<String> values = new ArrayList<>();
            values.add(row.label().toLowerCase());
            values.add(row.value().toLowerCase());
            row.actions().forEach(action -> values.add(action.label().toLowerCase()));
            return values;
        }

        @Override
        public int getMaxNameLengthWrapped(List<ConfigOptionWrapper> entries) {
            int maxWidth = super.getMaxNameLengthWrapped(entries);
            for (ConfigOptionWrapper wrapper : entries) {
                ActionRow row = owner.actionRows.get(wrapper);
                if (row != null) maxWidth = Math.max(maxWidth, getStringWidth(row.label()));
            }
            return maxWidth;
        }

        @Override
        protected WidgetConfigOption createListEntryWidget(int x, int y, int listIndex, boolean isOdd,
                                                            ConfigOptionWrapper wrapper) {
            ActionRow row = owner.actionRows.get(wrapper);
            if (row == null) return super.createListEntryWidget(x, y, listIndex, isOdd, wrapper);
            return new NativeActionRowWidget(
                    x,
                    y,
                    browserEntryWidth,
                    getBrowserEntryHeightFor(wrapper),
                    maxLabelWidth,
                    configWidth,
                    wrapper,
                    listIndex,
                    owner,
                    this,
                    row
            );
        }
    }

    private static final class NativeActionRowWidget extends WidgetConfigOption {
        private NativeActionRowWidget(int x, int y, int width, int height, int maxLabelWidth, int configWidth,
                                      ConfigOptionWrapper wrapper, int listIndex, PreviewScreen owner,
                                      WidgetListConfigOptionsBase<?, ?> list, ActionRow row) {
            super(x, y, width, height, maxLabelWidth, configWidth, wrapper, listIndex, owner, list);

            addLabel(x, y + 7, maxLabelWidth, 8, 0xFFFFFFFF, row.label());
            int nextX = x + maxLabelWidth + 10;
            for (Action action : row.actions()) {
                ButtonGeneric button = new ButtonGeneric(nextX, y, -1, 20, action.label());
                addButton(button, (clicked, mouseButton) -> owner.act(action.run()));
                nextX += button.getWidth() + 2;
            }

            int valueWidth = Math.max(20, x + width - nextX - 4);
            String value = textRenderer.plainSubstrByWidth(row.value(), valueWidth);
            addLabel(nextX + 2, y + 7, valueWidth, 8, 0xFFAAAAAA, value);
        }

        //? if <=1.21.5 {
        /*@Override
        public void render(int mouseX, int mouseY, boolean selected, DrawContext context) {
            drawSubWidgets(mouseX, mouseY, context);
        }
        *///?} else if <=1.21.10 {
        /*@Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean selected) {
            drawSubWidgets(context, mouseX, mouseY);
        }
        *///?} else {
        @Override
        public void render(fi.dy.masa.malilib.render.GuiContext context, int mouseX, int mouseY, boolean selected) {
            drawSubWidgets(context, mouseX, mouseY);
        }
        //?}
    }
}
