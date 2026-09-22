package dev.autostock.server;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** Shared snapshot rules, also tested against actual Minecraft item components. */
public final class InventoryItems {
    public record Key(Item item, DataComponentMap components, int maxCount) { }
    private InventoryItems() { }
    public static boolean isBox(ItemStack stack) {
        return !stack.isEmpty() && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }
    public static Iterable<ItemStack> contents(ItemStack stack) {
        if (stack.has(DataComponents.CONTAINER_LOOT)) {
            throw new IllegalArgumentException("潜影盒中存在未生成的战利品，需先手动打开");
        }
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
    }
    public static Key key(ItemStack stack) {
        return new Key(stack.getItem(), DataComponentMap.builder().addAll(stack.getComponents()).build(), stack.getMaxStackSize());
    }
}
