//? if >1.21 {
package dev.autostock.server;

import net.kyrptonaught.quickshulker.api.ItemStackInventory;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the pinned upstream inventory implementation, not a substitute copy. */
class QuickShulkerInventoryTest {
    @BeforeAll static void bootstrap() { SharedConstants.createGameVersion(); Bootstrap.initialize(); }

    @Test void openingAndClosingSingleBoxDoesNotChangeItem() {
        var box = new ItemStack(Items.BLUE_SHULKER_BOX);
        box.set(DataComponentTypes.CUSTOM_NAME, Text.literal("来源 A"));
        box.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(new ItemStack(Items.STONE, 12))));
        var before = box.copy();
        var inventory = new ItemStackInventory(box, 27);
        inventory.onClose(null); // Single boxes do not use the player-dependent split/drop branch.
        assertTrue(ItemStack.areEqual(before, box));
    }

    @Test void mutationsWriteBackAndPreserveNamesAndOuterColor() {
        var box = new ItemStack(Items.BLUE_SHULKER_BOX);
        box.set(DataComponentTypes.CUSTOM_NAME, Text.literal("来源 A"));
        var content = new ItemStack(Items.STONE, 19);
        content.set(DataComponentTypes.CUSTOM_NAME, Text.literal("材料名称"));
        var inventory = new ItemStackInventory(box, 27);
        inventory.setStack(4, content);
        inventory.markDirty();
        var reopened = new ItemStackInventory(box, 27);
        assertTrue(ItemStack.areEqual(content, reopened.getStack(4)));
        assertEquals(Items.BLUE_SHULKER_BOX, box.getItem());
        assertEquals(Text.literal("来源 A"), box.get(DataComponentTypes.CUSTOM_NAME));
    }

    @Test void takingSourceContentsPersistsRemainderAndThenEmptyBox() {
        var box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(new ItemStack(Items.STONE, 12))));
        var inventory = new ItemStackInventory(box, 27);
        assertEquals(5, inventory.removeStack(0, 5).getCount());
        inventory.markDirty();
        assertEquals(7, new ItemStackInventory(box, 27).getStack(0).getCount());
        assertEquals(7, inventory.removeStack(0).getCount());
        inventory.markDirty();
        assertTrue(new ItemStackInventory(box, 27).isEmpty());
        assertEquals(1, box.getCount());
    }

    @Test void identicalBoxesHaveIndependentInventories() {
        var a = new ItemStack(Items.SHULKER_BOX);
        var b = a.copy();
        var inventory = new ItemStackInventory(a, 27);
        inventory.setStack(0, new ItemStack(Items.GLASS, 8));
        inventory.markDirty();
        assertEquals(8, new ItemStackInventory(a, 27).getStack(0).getCount());
        assertTrue(new ItemStackInventory(b, 27).isEmpty());
    }
}
//?}
