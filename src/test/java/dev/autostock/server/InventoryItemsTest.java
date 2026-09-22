package dev.autostock.server;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ContainerLootComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class InventoryItemsTest {
    @BeforeAll static void bootstrap() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }
    @Test void componentKeysMatchCompatibilityAndAreImmutable() {
        var a = new ItemStack(Items.STONE, 32);
        var b = new ItemStack(Items.STONE, 64);
        var key = InventoryItems.key(a);
        assertEquals(key, InventoryItems.key(b));
        a.set(DataComponentTypes.CUSTOM_NAME, Text.literal("保留名称"));
        assertNotEquals(InventoryItems.key(a), InventoryItems.key(b));
        assertEquals(key, InventoryItems.key(b));
    }
    @Test void emptyNamedBoxStillCountsAsEmpty() {
        var box = new ItemStack(Items.BLUE_SHULKER_BOX);
        box.set(DataComponentTypes.CUSTOM_NAME, Text.literal("空盒"));
        assertTrue(InventoryItems.isBox(box));
        assertFalse(InventoryItems.contents(box).iterator().hasNext());
    }
    @Test void expandsOnlyOneLevelAndLeavesItemsUntouched() {
        var inner = new ItemStack(Items.SHULKER_BOX);
        inner.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(new ItemStack(Items.STONE, 20))));
        var outer = new ItemStack(Items.SHULKER_BOX);
        outer.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(inner)));
        var iterator = InventoryItems.contents(outer).iterator();
        assertTrue(InventoryItems.isBox(iterator.next()));
        assertFalse(iterator.hasNext());
        assertEquals(20, InventoryItems.contents(inner).iterator().next().getCount());
    }
    @Test void unresolvedLootIsUnknownRatherThanEmpty() {
        var box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponentTypes.CONTAINER_LOOT, new ContainerLootComponent(
                RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of("minecraft", "chests/simple_dungeon")), 1L));
        assertThrows(IllegalArgumentException.class, () -> InventoryItems.contents(box));
    }
    @Test void readsActualLimitsForNormalPearlAndTool() {
        assertEquals(64, InventoryItems.key(new ItemStack(Items.STONE)).maxCount());
        assertEquals(16, InventoryItems.key(new ItemStack(Items.ENDER_PEARL)).maxCount());
        assertEquals(1, InventoryItems.key(new ItemStack(Items.DIAMOND_PICKAXE)).maxCount());
    }
}
