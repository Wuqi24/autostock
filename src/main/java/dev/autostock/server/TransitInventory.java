package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Reads T from physical inventory, including an interrupted click's cursor; never from receipts. */
final class TransitInventory {
    static Map<String, Long> read(ServerPlayerEntity player, Set<String> demand) {
        if (!(player instanceof EntityPlayerMPFake) || !MinecraftCompat.server(player).isOnThread())
            throw new IllegalArgumentException("只能在服务端读取任务假人实际库存");
        var result = new TreeMap<String, Long>();
        for (int slot = 0; slot < 36; slot++) add(result, demand, player.getInventory().getStack(slot));
        // The open QuickShulker handler mirrors the outer box component; do not count its 27 slots again.
        add(result, demand, player.currentScreenHandler.getCursorStack());
        add(result, demand, TaskAttachments.retained(player));
        return Map.copyOf(result);
    }
    private static void add(Map<String, Long> result, Set<String> demand, ItemStack stack) {
        if (InventoryItems.isBox(stack)) {
            if (stack.getCount() != 1) throw new IllegalArgumentException("在途盒数量异常，库存未知，停止恢复");
            for (var content : InventoryItems.contents(stack)) count(result, demand, content);
        } else count(result, demand, stack);
    }
    private static void count(Map<String, Long> result, Set<String> demand, ItemStack stack) {
        if (stack.isEmpty()) return;
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        if (demand.contains(id)) result.merge(id, (long) stack.getCount(), Math::addExact);
    }
}
