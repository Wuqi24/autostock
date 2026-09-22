package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.collection.DefaultedList;

/** Server-thread-only fallback. Only a box physically held in the fake's inventory can be edited. */
final class DirectBoxSession implements BoxSession {
    private final ServerPlayerEntity player;
    private final int slot;
    private final ItemStack outer;
    private boolean closed;
    DirectBoxSession(ServerPlayerEntity player, int slot) {
        if (!(player instanceof EntityPlayerMPFake) || slot < 0 || slot >= 36)
            throw new IllegalArgumentException("仅支持假人背包中的潜影盒");
        this.player = player; this.slot = slot; outer = player.getInventory().getStack(slot);
        ready();
    }
    private void ready() {
        if (!MinecraftCompat.server(player).isOnThread() || closed) throw new IllegalArgumentException("装盒会话不在服务端线程或已关闭");
        if (!player.isAlive() || player.getHealth() <= player.getMaxHealth() * .5F)
            throw new IllegalArgumentException("低血量停止，保留物品");
        if (player.currentScreenHandler != player.playerScreenHandler || !player.currentScreenHandler.getCursorStack().isEmpty())
            throw new IllegalArgumentException("容器或光标尚未处理，保留现场");
        if (player.getInventory().getStack(slot) != outer || !InventoryItems.isBox(outer) || outer.getCount() != 1
                || outer.contains(DataComponentTypes.CONTAINER_LOOT)) throw new IllegalArgumentException("外层盒身份或状态无效");
    }
    @Override public void move(int playerSlot, int boxSlot, int count, boolean intoBox) {
        ready();
        if (playerSlot < 0 || playerSlot >= 36 || playerSlot == slot || boxSlot < 0 || boxSlot >= 27 || count <= 0 || count > 99)
            throw new IllegalArgumentException("槽位或数量无效");
        var contents = DefaultedList.ofSize(27, ItemStack.EMPTY);
        outer.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).copyTo(contents);
        var bag = player.getInventory().getStack(playerSlot);
        var boxed = contents.get(boxSlot);
        var source = intoBox ? bag : boxed;
        var destination = intoBox ? boxed : bag;
        if (source.isEmpty()) throw new IllegalArgumentException("来源已空");
        BuildingMaterials.requireSupported(source.getItem());
        if (source.getCount() < count || !source.getItem().canBeNested()
                || (!destination.isEmpty() && !ItemStack.areItemsAndComponentsEqual(source, destination))
                || count > Math.min(64, source.getMaxCount()) - destination.getCount())
            throw new IllegalArgumentException("数量、组件兼容性或容量不满足；未转移");
        var remainder = source.copyWithCount(source.getCount() - count);
        var combined = source.copyWithCount(destination.getCount() + count);
        var nextBag = intoBox ? remainder : combined;
        contents.set(boxSlot, intoBox ? combined : remainder);
        var nextContents = ContainerComponent.fromStacks(contents);
        // Both replacements are computed before mutation. Never reconstruct items from a journal.
        outer.set(DataComponentTypes.CONTAINER, nextContents);
        player.getInventory().setStack(playerSlot, nextBag);
        player.getInventory().markDirty();
        if (player.getInventory().getStack(slot) != outer || !ItemStack.areEqual(nextBag, player.getInventory().getStack(playerSlot))
                || !nextContents.equals(outer.get(DataComponentTypes.CONTAINER)))
            throw new IllegalArgumentException("转移后核对失败，保留现场，不补发或回滚");
        player.playerScreenHandler.sendContentUpdates();
    }
    @Override public void closeVerified() { ready(); closed = true; }
    @Override public String backend() { return "服务端直接装盒"; }
}
