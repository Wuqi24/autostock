package dev.autostock.server;

import net.minecraft.block.Block;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Shared snapshot rules, also tested against actual Minecraft item components. */
public final class InventoryItems {
    public record Key(Item item, ComponentMap components, int maxCount) { }
    private InventoryItems() { }
    public static boolean isBox(ItemStack stack) {
        return !stack.isEmpty() && Block.getBlockFromItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }
    public static Iterable<ItemStack> contents(ItemStack stack) {
        if (stack.contains(DataComponentTypes.CONTAINER_LOOT)) {
            throw new IllegalArgumentException("潜影盒中存在未生成的战利品，需先手动打开");
        }
        return stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT).iterateNonEmpty();
    }
    public static Key key(ItemStack stack) {
        return new Key(stack.getItem(), ComponentMap.builder().addAll(stack.getComponents()).build(), stack.getMaxCount());
    }
}
