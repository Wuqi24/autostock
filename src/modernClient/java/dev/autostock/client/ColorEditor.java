package dev.autostock.client;

import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.gui.GuiColorEditorHSV;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class ColorEditor {
    private ColorEditor() {}

    static void open(Screen parent, int initial, IntConsumer onChanged) {
        ClientMinecraftCompat.setScreen(Minecraft.getInstance(), create(parent, initial, onChanged));
    }

    static GuiColorEditorHSV create(Screen parent, int initial, IntConsumer onChanged) {
        var value = new ConfigColor("autostockColor", format(initial));
        value.setIntegerValue(initial);
        value.setValueChangeCallback(config -> onChanged.accept(config.getIntegerValue()));
        return new GuiColorEditorHSV(value, null, parent);
    }

    static String format(int color) {
        return String.format("#%08X", color);
    }

    static Integer parse(String text) {
        String value = text == null ? "" : text.trim().replaceFirst("^#", "");
        if (value.matches("[0-9a-fA-F]{6}")) return 0xFF000000 | Integer.parseInt(value, 16);
        if (value.matches("[0-9a-fA-F]{8}")) return (int) Long.parseLong(value, 16);
        return null;
    }
}
