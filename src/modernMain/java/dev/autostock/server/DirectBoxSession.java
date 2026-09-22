package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Server-thread-only fallback. Only a box physically held in the fake's inventory can be edited. */
final class DirectBoxSession implements BoxSession {
    private final ServerPlayer player;
    private final int slot;
    private final ItemStack outer;
    private boolean closed;
    DirectBoxSession(ServerPlayer player, int slot) {
        if (!(player instanceof EntityPlayerMPFake) || slot < 0 || slot >= 36)
            throw new IllegalArgumentException("仅支持假人背包中的潜影盒");
        this.player = player; this.slot = slot; outer = player.getInventory().getItem(slot);
        ready();
    }
    private void ready() {
        if (!MinecraftCompat.server(player).isSameThread() || closed) throw new IllegalArgumentException("装盒会话不在服务端线程或已关闭");
        if (!player.isAlive() || player.getHealth() <= player.getMaxHealth() * .5F)
            throw new IllegalArgumentException("低血量停止，保留物品");
        if (player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty())
            throw new IllegalArgumentException("容器或光标尚未处理，保留现场");
        if (player.getInventory().getItem(slot) != outer || !InventoryItems.isBox(outer) || outer.getCount() != 1
                || outer.has(DataComponents.CONTAINER_LOOT)) throw new IllegalArgumentException("外层盒身份或状态无效");
    }
    @Override public void move(int playerSlot, int boxSlot, int count, boolean intoBox) {
        ready();
        if (playerSlot < 0 || playerSlot >= 36 || playerSlot == slot || boxSlot < 0 || boxSlot >= 27 || count <= 0 || count > 99)
            throw new IllegalArgumentException("槽位或数量无效");
        var contents = NonNullList.withSize(27, ItemStack.EMPTY);
        outer.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(contents);
        var bag = player.getInventory().getItem(playerSlot);
        var boxed = contents.get(boxSlot);
        var source = intoBox ? bag : boxed;
        var destination = intoBox ? boxed : bag;
        if (source.isEmpty()) throw new IllegalArgumentException("来源已空");
        BuildingMaterials.requireSupported(source.getItem());
        if (source.getCount() < count || !source.getItem().canFitInsideContainerItems()
                || (!destination.isEmpty() && !ItemStack.isSameItemSameComponents(source, destination))
                || count > Math.min(64, source.getMaxStackSize()) - destination.getCount())
            throw new IllegalArgumentException("数量、组件兼容性或容量不满足；未转移");
        var remainder = source.copyWithCount(source.getCount() - count);
        var combined = source.copyWithCount(destination.getCount() + count);
        var nextBag = intoBox ? remainder : combined;
        contents.set(boxSlot, intoBox ? combined : remainder);
        var nextContents = ItemContainerContents.fromItems(contents);
        // Both replacements are computed before mutation. Never reconstruct items from a journal.
        outer.set(DataComponents.CONTAINER, nextContents);
        player.getInventory().setItem(playerSlot, nextBag);
        player.getInventory().setChanged();
        if (player.getInventory().getItem(slot) != outer || !ItemStack.matches(nextBag, player.getInventory().getItem(playerSlot))
                || !nextContents.equals(outer.get(DataComponents.CONTAINER)))
            throw new IllegalArgumentException("转移后核对失败，保留现场，不补发或回滚");
        player.inventoryMenu.broadcastChanges();
    }
    @Override public void closeVerified() { ready(); closed = true; }
    @Override public String backend() { return "服务端直接装盒"; }
}
