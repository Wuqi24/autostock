package dev.autostock.server;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

/** Verified physical transfer through a vanilla container handler; no inventory creation or rollback. */
final class ContainerTransfers {
    static int playerSlot(ServerPlayerEntity player, ScreenHandler handler, int inventorySlot) {
        for (int i = 0; i < handler.slots.size(); i++) {
            var slot = handler.getSlot(i);
            if (slot.inventory == player.getInventory() && slot.getIndex() == inventorySlot) return i;
        }
        throw new IllegalArgumentException("背包槽位不在当前容器中");
    }
    static void move(ServerPlayerEntity player, ScreenHandler handler, int source, int destination, int count) {
        if (!(player instanceof carpet.patches.EntityPlayerMPFake) || !MinecraftCompat.server(player).isOnThread())
            throw new IllegalArgumentException("仅允许服务端假人转移");
        if (player.currentScreenHandler != handler || !handler.canUse(player) || !handler.getCursorStack().isEmpty()
                || !player.isAlive() || player.getHealth() <= player.getMaxHealth() * .5F) throw new IllegalArgumentException("转移会话、光标或健康状态无效");
        if (source < 0 || source >= handler.slots.size() || destination < 0 || destination >= handler.slots.size() || source == destination
                || count <= 0 || count > 99) throw new IllegalArgumentException("容器转移参数无效");
        var from = handler.getSlot(source); var to = handler.getSlot(destination);
        if ((from.inventory == player.getInventory()) == (to.inventory == player.getInventory())) throw new IllegalArgumentException("转移必须跨背包与容器");
        var item = from.getStack().copy(); var existing = to.getStack().copy();
        if (item.isEmpty() || count > item.getCount() || !from.canTakeItems(player) || !to.canInsert(item)
                || (!existing.isEmpty() && !ItemStack.areItemsAndComponentsEqual(item,existing))
                || count > to.getMaxItemCount(item) - existing.getCount()) throw new IllegalArgumentException("来源、目标容量或组件兼容性发生变化");
        var before = handler.slots.stream().map(slot -> slot.getStack().copy()).toList();
        handler.onSlotClick(source,0,SlotActionType.PICKUP,player);
        if (count == item.getCount()) handler.onSlotClick(destination,0,SlotActionType.PICKUP,player);
        else for (int i = 0; i < count; i++) handler.onSlotClick(destination,1,SlotActionType.PICKUP,player);
        if (!handler.getCursorStack().isEmpty()) handler.onSlotClick(source,0,SlotActionType.PICKUP,player);
        if (!handler.getCursorStack().isEmpty()) throw new IllegalArgumentException("光标未清空，停止并保留现场");
        for (int i = 0; i < before.size(); i++) {
            var expected = i == source ? item.copyWithCount(item.getCount() - count)
                    : i == destination ? item.copyWithCount(existing.getCount() + count) : before.get(i);
            if (!ItemStack.areEqual(expected,handler.getSlot(i).getStack())) throw new IllegalArgumentException("容器转移后核对失败，禁止补发物品");
        }
    }
}
