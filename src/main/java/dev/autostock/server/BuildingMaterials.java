package dev.autostock.server;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

public final class BuildingMaterials {
    private BuildingMaterials() { }
    public static boolean supported(Item item) {
        return item instanceof BlockItem block && !(block.getBlock() instanceof ShulkerBoxBlock);
    }
    public static void requireSupported(Item item) {
        if (!supported(item)) throw new IllegalArgumentException("暂不支持：" + Registries.ITEM.getId(item) + "；首版仅支持普通建筑方块材料");
    }
}
