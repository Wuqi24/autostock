package dev.autostock.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class ClientKeys {
    private static ClientDraft draft;
    static int modifiers() {
        long window = Minecraft.getInstance().getWindow().handle();
        return (pressed(window, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT) ? 1 : 0)
                | (pressed(window, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL) ? 2 : 0)
                | (pressed(window, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT) ? 4 : 0);
    }
    private static boolean pressed(long window,int left,int right){return GLFW.glfwGetKey(window,left)==GLFW.GLFW_PRESS||GLFW.glfwGetKey(window,right)==GLFW.GLFW_PRESS;}
    static void register(ClientDraft value) { draft = value; }
    /** Consume configured shortcuts before vanilla, whose P key also opens social interactions. */
    public static boolean onKey(long window, int key, int event, int modifiers) {
        var client = Minecraft.getInstance();
        if (draft == null || client.level == null || window != client.getWindow().handle()) return false;
        if(key<0&&ClientMinecraftCompat.screen(client)!=null)return false;
        if(ClientMinecraftCompat.screen(client) instanceof PreviewScreen screen&&screen.typing())return false;
        boolean materialList = ClientMinecraftCompat.screen(client) instanceof fi.dy.masa.litematica.gui.GuiMaterialList;
        if (ClientMinecraftCompat.screen(client) != null && !(ClientMinecraftCompat.screen(client) instanceof DraftScreen)
                && !(ClientMinecraftCompat.screen(client) instanceof RegionSetupScreen) && !materialList) return false;
        var settings = ClientSettings.get();
        modifiers |= modifiers();
        if(RegionSetupScreen.active()&&ClientMinecraftCompat.screen(client)==null&&settings.binding(ClientSettings.Action.SAVE).matches(key,modifiers)) {
            if(event==GLFW.GLFW_PRESS)RegionSetupScreen.saveOverlay();return true;
        }
        for (var action : ClientSettings.Action.values()) {
            if (action == ClientSettings.Action.SAVE || action == ClientSettings.Action.CYCLE
                    || materialList && action != ClientSettings.Action.CONFIG || !settings.binding(action).matches(key, modifiers)) continue;
            if (event != GLFW.GLFW_PRESS) return event == GLFW.GLFW_REPEAT;
            switch (action) {
                case CONFIG -> ClientMinecraftCompat.setScreen(client, ClientMinecraftCompat.screen(client) instanceof DraftScreen ? null : new DraftScreen(draft));
                case REGION -> {if(ClientMinecraftCompat.screen(client)!=null)RegionSetupScreen.open(draft);else RegionSetupScreen.toggle(draft);}
                case TOGGLE -> draft.perform(draft::taskToggle);
                case CANCEL -> draft.perform(draft::taskCancel);
                case BOUNDARY -> { settings.showRegions = !settings.showRegions; draft.perform(settings::save); }
                case CONTAINERS -> { settings.showContainers = !settings.showContainers; draft.perform(settings::save); }
                case PATH -> { settings.showPaths = !settings.showPaths; draft.perform(settings::save); }
                case TARGET -> { settings.showTarget = !settings.showTarget; draft.perform(settings::save); }
                case POINTS -> { settings.showPoints = !settings.showPoints; draft.perform(settings::save); }
                default -> { }
            }
            return true;
        }
        return false;
    }
}
