//? if >1.21 {
package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import net.fabricmc.loader.api.FabricLoader;
import net.kyrptonaught.quickshulker.QuickShulkerMod;
import net.kyrptonaught.quickshulker.api.ItemInventoryContainer;
import net.kyrptonaught.quickshulker.api.Util;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import java.util.List;

/** Guarded QuickShulker session. Transfers use vanilla slot clicks and preserve uncertain state. */
final class QuickShulkerSession implements BoxSession {
    private final ServerPlayer player;
    private final int inventorySlot;
    private final ItemStack original;
    private final ShulkerBoxMenu handler;

    static QuickShulkerSession open(ServerPlayer player, int inventorySlot) {
        var version = FabricLoader.getInstance().getModContainer("quickshulker")
                .orElseThrow(() -> new IllegalArgumentException("服务端未安装 QuickShulker"))
                .getMetadata().getVersion().getFriendlyString();
        if (!version.startsWith("3.1.0")) throw new IllegalArgumentException("此适配器仅支持 QuickShulker 3.1.0 系列");
        if (!(player instanceof EntityPlayerMPFake)) throw new IllegalArgumentException("目前只允许 Carpet 假人，真人模式未启用");
        if (inventorySlot < 0 || inventorySlot >= 36) throw new IllegalArgumentException("背包槽位应为 0–35");
        if (!player.isAlive() || player.getHealth() <= player.getMaxHealth() * 0.5F) {
            throw new IllegalArgumentException("假人血量不满足开盒条件，保留物品等待处理");
        }
        if (player.containerMenu != player.inventoryMenu
                || !player.containerMenu.getCarried().isEmpty()) {
            throw new IllegalArgumentException("假人仍有打开的容器或光标物品，请先处理现场");
        }
        var stack = player.getInventory().getItem(inventorySlot);
        if (!InventoryItems.isBox(stack) || stack.getCount() != 1) throw new IllegalArgumentException("该槽位必须恰好有一个潜影盒");
        if (stack.has(DataComponents.CONTAINER_LOOT)) throw new IllegalArgumentException("请先手动解析盒内战利品");
        if (!QuickShulkerMod.getConfig().quickShulkerBox || !Util.isOpenableItem(stack)) {
            throw new IllegalArgumentException("QuickShulker 的潜影盒开盒功能未启用");
        }
        Util.openItem(player, 0, inventorySlot);
        if (!(player.containerMenu instanceof ShulkerBoxMenu opened)
                || ((ItemInventoryContainer) opened).getUsedSlotInPlayerInv() != inventorySlot) {
            throw new IllegalArgumentException("QuickShulker 未建立预期会话，已保留现场，请检查假人");
        }
        var session = new QuickShulkerSession(player, inventorySlot, stack, opened);
        session.verify();
        return session;
    }

    private QuickShulkerSession(ServerPlayer player, int slot, ItemStack stack, ShulkerBoxMenu handler) {
        this.player = player; this.inventorySlot = slot; this.original = stack; this.handler = handler;
    }

    void verify() {
        // Upstream checks item type; also require the very same outer stack and session here.
        if (player.containerMenu != handler || player.getInventory().getItem(inventorySlot) != original
                || original.getCount() != 1 || ((ItemInventoryContainer) handler).getUsedSlotInPlayerInv() != inventorySlot) {
            throw new IllegalArgumentException("开盒会话或外层盒身份改变，停止并保留现场");
        }
    }

    @Override public void closeVerified() {
        verify();
        if (!handler.getCarried().isEmpty()) throw new IllegalArgumentException("光标仍持有物品，保留会话等待处理");
        List<ItemStack> expected = java.util.stream.IntStream.range(0, 27)
                .mapToObj(i -> handler.getSlot(i).getItem().copy()).toList();
        player.closeContainer();
        if (player.containerMenu != player.inventoryMenu || player.getInventory().getItem(inventorySlot) != original) {
            throw new IllegalArgumentException("关闭后会话或盒身份异常，请人工检查；不会补发物品");
        }
        var actual = NonNullList.withSize(27, ItemStack.EMPTY);
        original.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(actual);
        for (int i = 0; i < 27; i++) if (!ItemStack.matches(expected.get(i), actual.get(i))) {
            throw new IllegalArgumentException("关闭后盒内容核对失败，请人工检查；不会补发物品");
        }
    }

    void transfer(int sourceIndex, int destinationIndex, int count) {
        actionReady();
        if (sourceIndex < 0 || destinationIndex < 0 || sourceIndex >= handler.slots.size()
                || destinationIndex >= handler.slots.size() || sourceIndex == destinationIndex || count <= 0 || count > 99) {
            throw new IllegalArgumentException("转移槽位或数量无效");
        }
        var source = handler.getSlot(sourceIndex);
        var destination = handler.getSlot(destinationIndex);
        if (isOuterSlot(sourceIndex) || isOuterSlot(destinationIndex)) throw new IllegalArgumentException("不能移动正在打开的外层盒");
        // One endpoint must be in the box and the other in the fake player's inventory.
        if ((sourceIndex < 27) == (destinationIndex < 27)) throw new IllegalArgumentException("仅支持背包与当前盒之间转移");
        var item = source.getItem().copy();
        if (!item.isEmpty()) BuildingMaterials.requireSupported(item.getItem());
        var existing = destination.getItem().copy();
        if (item.isEmpty() || count > item.getCount() || !source.mayPickup(player) || !destination.mayPlace(item)
                || (!existing.isEmpty() && !ItemStack.isSameItemSameComponents(item, existing))
                || count > destination.getMaxStackSize(item) - existing.getCount()) {
            throw new IllegalArgumentException("来源数量、堆叠兼容性或目标容量不足，未执行转移");
        }
        var before = handler.slots.stream().map(slot -> slot.getItem().copy()).toList();
        var outerBefore = original.copy();
        outerBefore.remove(DataComponents.CONTAINER);
        click(sourceIndex, 0);
        if (count == item.getCount()) click(destinationIndex, 0);
        else for (int i = 0; i < count; i++) click(destinationIndex, 1);
        if (!handler.getCarried().isEmpty()) click(sourceIndex, 0);
        verify();
        if (!handler.getCarried().isEmpty()) throw new IllegalArgumentException("转移后光标未清空，保留现场等待处理");
        for (int i = 0; i < before.size(); i++) {
            if (isOuterSlot(i)) continue;
            var expected = before.get(i);
            if (i == sourceIndex) expected = item.copyWithCount(item.getCount() - count);
            if (i == destinationIndex) expected = item.copyWithCount(existing.getCount() + count);
            if (!ItemStack.matches(expected, handler.getSlot(i).getItem())) {
                throw new IllegalArgumentException("转移后槽位核对不符，保留现场；不会自动补发或回滚");
            }
        }
        var outerAfter = original.copy();
        outerAfter.remove(DataComponents.CONTAINER);
        if (!ItemStack.matches(outerBefore, outerAfter)) throw new IllegalArgumentException("外层盒属性异常，停止并保留现场");
    }

    private boolean isOuterSlot(int index) {
        var slot = handler.getSlot(index);
        return slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot;
    }

    @Override public String backend() { return "QuickShulker"; }
    @Override public void move(int playerSlot, int boxSlot, int count, boolean intoBox) {
        if (playerSlot < 0 || playerSlot >= 36 || boxSlot < 0 || boxSlot >= 27) throw new IllegalArgumentException("槽位无效");
        for (int i = 27; i < handler.slots.size(); i++) {
            var slot = handler.getSlot(i);
            if (slot.container == player.getInventory() && slot.getContainerSlot() == playerSlot) {
                transfer(intoBox ? i : boxSlot, intoBox ? boxSlot : i, count); return;
            }
        }
        throw new IllegalArgumentException("背包槽位不可用");
    }

    private void actionReady() {
        verify();
        if (!player.isAlive() || player.getHealth() <= player.getMaxHealth() * 0.5F) {
            throw new IllegalArgumentException("低血量停止动作，保留全部物品等待玩家处理");
        }
        if (!handler.getCarried().isEmpty()) throw new IllegalArgumentException("光标有物品，先处理现场");
    }

    private void click(int index, int button) {
        verify();
        if (!player.isAlive() || player.getHealth() <= player.getMaxHealth() * 0.5F) {
            throw new IllegalArgumentException("转移中触发紧急停止，保留当前光标与物品");
        }
        handler.clicked(index, button, ContainerInput.PICKUP, player);
    }
}
//?}
