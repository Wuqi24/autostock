package dev.autostock.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

final class BoxBackend {
    static BoxSession open(ServerPlayerEntity player, int slot, boolean preferQuick) {
        //? if <=1.21 {
        /*return new DirectBoxSession(player, slot);
        *///?} else {
        if (!preferQuick || !FabricLoader.getInstance().isModLoaded("quickshulker")) return new DirectBoxSession(player, slot);
        var inventory = player.getInventory();
        var before = java.util.stream.IntStream.range(0, inventory.size()).mapToObj(i -> inventory.getStack(i).copy()).toList();
        var identity = slot >= 0 && slot < inventory.size() ? inventory.getStack(slot) : ItemStack.EMPTY;
        var handler = player.currentScreenHandler;
        var cursor = handler.getCursorStack().copy();
        try { return QuickShulkerSession.open(player, slot); }
        catch (RuntimeException | LinkageError error) {
            boolean unchanged = player.currentScreenHandler == handler && handler == player.playerScreenHandler
                    && ItemStack.areEqual(cursor, handler.getCursorStack()) && cursor.isEmpty()
                    && slot >= 0 && slot < inventory.size() && inventory.getStack(slot) == identity;
            for (int i = 0; i < before.size() && unchanged; i++) unchanged = ItemStack.areEqual(before.get(i), inventory.getStack(i));
            if (!unchanged) throw new IllegalArgumentException("QuickShulker 会话改变现场，禁止回退，请人工检查", error);
            dev.autostock.AutoStock.LOG.info("QuickShulker unavailable; using direct inventory backend: {}", error.getMessage());
            return new DirectBoxSession(player, slot);
        }
        //?}
    }
}
