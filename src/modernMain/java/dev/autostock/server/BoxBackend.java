package dev.autostock.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

final class BoxBackend {
    static BoxSession open(ServerPlayer player, int slot, boolean preferQuick) {
        //? if <=1.21 {
        /*return new DirectBoxSession(player, slot);
        *///?} else {
        if (!preferQuick || !FabricLoader.getInstance().isModLoaded("quickshulker")) return new DirectBoxSession(player, slot);
        var inventory = player.getInventory();
        var before = java.util.stream.IntStream.range(0, inventory.getContainerSize()).mapToObj(i -> inventory.getItem(i).copy()).toList();
        var identity = slot >= 0 && slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
        var handler = player.containerMenu;
        var cursor = handler.getCarried().copy();
        try { return QuickShulkerSession.open(player, slot); }
        catch (RuntimeException | LinkageError error) {
            boolean unchanged = player.containerMenu == handler && handler == player.inventoryMenu
                    && ItemStack.matches(cursor, handler.getCarried()) && cursor.isEmpty()
                    && slot >= 0 && slot < inventory.getContainerSize() && inventory.getItem(slot) == identity;
            for (int i = 0; i < before.size() && unchanged; i++) unchanged = ItemStack.matches(before.get(i), inventory.getItem(i));
            if (!unchanged) throw new IllegalArgumentException("QuickShulker 会话改变现场，禁止回退，请人工检查", error);
            dev.autostock.AutoStock.LOG.info("QuickShulker unavailable; using direct inventory backend: {}", error.getMessage());
            return new DirectBoxSession(player, slot);
        }
        //?}
    }
}
