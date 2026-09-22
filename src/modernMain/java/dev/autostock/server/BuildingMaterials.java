package dev.autostock.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public final class BuildingMaterials {
    private BuildingMaterials() { }
    public static boolean supported(Item item) {
        return item instanceof BlockItem block && !(block.getBlock() instanceof ShulkerBoxBlock);
    }
    public static void requireSupported(Item item) {
        if (!supported(item)) throw new IllegalArgumentException("暂不支持：" + BuiltInRegistries.ITEM.getKey(item) + "；首版仅支持普通建筑方块材料");
    }
}
