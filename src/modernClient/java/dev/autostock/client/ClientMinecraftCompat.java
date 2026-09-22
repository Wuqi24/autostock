package dev.autostock.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Isolates the 26.2 move of screen ownership from Minecraft to Gui. */
final class ClientMinecraftCompat {
    private static final java.lang.reflect.Method GUI_SCREEN = method(net.minecraft.client.gui.Gui.class, "screen");
    private static final java.lang.reflect.Method GUI_SET_SCREEN = method(net.minecraft.client.gui.Gui.class, "setScreen", Screen.class);
    private static final java.lang.reflect.Method MINECRAFT_SET_SCREEN = method(Minecraft.class, "setScreen", Screen.class);
    private static final java.lang.reflect.Field MINECRAFT_SCREEN = field(Minecraft.class, "screen");

    private ClientMinecraftCompat() { }

    static Screen screen(Minecraft client) {
        try {
            return GUI_SCREEN != null ? (Screen)GUI_SCREEN.invoke(client.gui) : (Screen)MINECRAFT_SCREEN.get(client);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot read the current screen", error);
        }
    }

    static void setScreen(Minecraft client, Screen screen) {
        try {
            if (GUI_SET_SCREEN != null) GUI_SET_SCREEN.invoke(client.gui, screen);
            else MINECRAFT_SET_SCREEN.invoke(client, screen);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot change the current screen", error);
        }
    }

    private static java.lang.reflect.Method method(Class<?> owner, String name, Class<?>... parameters) {
        try { return owner.getMethod(name, parameters); }
        catch (NoSuchMethodException ignored) { return null; }
    }

    private static java.lang.reflect.Field field(Class<?> owner, String name) {
        try { return owner.getField(name); }
        catch (NoSuchFieldException ignored) { return null; }
    }
}
