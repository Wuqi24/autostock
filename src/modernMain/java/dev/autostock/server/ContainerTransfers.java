package dev.autostock.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** Verified physical transfer through a vanilla container handler; no inventory creation or rollback. */
final class ContainerTransfers {
    static int playerSlot(ServerPlayer player, AbstractContainerMenu handler, int inventorySlot) {
        for (int i = 0; i < handler.slots.size(); i++) {
            var slot = handler.getSlot(i);
            if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) return i;
        }
        throw new IllegalArgumentException("背包槽位不在当前容器中");
    }
    static void move(ServerPlayer player, AbstractContainerMenu handler, int source, int destination, int count) {
        if (!(player instanceof carpet.patches.EntityPlayerMPFake) || !MinecraftCompat.server(player).isSameThread())
            throw new IllegalArgumentException("仅允许服务端假人转移");
        if (player.containerMenu != handler || !handler.stillValid(player) || !handler.getCarried().isEmpty()
                || !player.isAlive() || player.getHealth() <= player.getMaxHealth() * .5F) throw new IllegalArgumentException("转移会话、光标或健康状态无效");
        if (source < 0 || source >= handler.slots.size() || destination < 0 || destination >= handler.slots.size() || source == destination
                || count <= 0 || count > 99) throw new IllegalArgumentException("容器转移参数无效");
        var from = handler.getSlot(source); var to = handler.getSlot(destination);
        if ((from.container == player.getInventory()) == (to.container == player.getInventory())) throw new IllegalArgumentException("转移必须跨背包与容器");
        var item = from.getItem().copy(); var existing = to.getItem().copy();
        if (item.isEmpty() || count > item.getCount() || !from.mayPickup(player) || !to.mayPlace(item)
                || (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(item,existing))
                || count > to.getMaxStackSize(item) - existing.getCount()) throw new IllegalArgumentException("来源、目标容量或组件兼容性发生变化");
        var before = handler.slots.stream().map(slot -> slot.getItem().copy()).toList();
        handler.clicked(source,0,ContainerInput.PICKUP,player);
        if (count == item.getCount()) handler.clicked(destination,0,ContainerInput.PICKUP,player);
        else for (int i = 0; i < count; i++) handler.clicked(destination,1,ContainerInput.PICKUP,player);
        if (!handler.getCarried().isEmpty()) handler.clicked(source,0,ContainerInput.PICKUP,player);
        if (!handler.getCarried().isEmpty()) throw new IllegalArgumentException("光标未清空，停止并保留现场");
        for (int i = 0; i < before.size(); i++) {
            var expected = i == source ? item.copyWithCount(item.getCount() - count)
                    : i == destination ? item.copyWithCount(existing.getCount() + count) : before.get(i);
            if (!ItemStack.matches(expected,handler.getSlot(i).getItem())) throw new IllegalArgumentException("容器转移后核对失败，禁止补发物品");
        }
    }
}
