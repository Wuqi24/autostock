package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import carpet.patches.NetHandlerPlayServerFake;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import java.util.List;
import java.util.UUID;

/** Real world and Carpet fake class, with Carpet's packet sink and no account login. */
public class QuickShulkerGameTest implements CustomTestMethodInvoker {
    @GameTest public void transitUsesWorldContentsAndCursorWithoutCountingOpenBoxTwice(TestContext context) {
        var player = fake(context); var box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(new ItemStack(Items.STONE, 30))));
        player.getInventory().setStack(0, box); player.getInventory().setStack(1, new ItemStack(Items.STONE, 12));
        var session = QuickShulkerSession.open(player, 0);
        player.currentScreenHandler.setCursorStack(new ItemStack(Items.STONE, 5));
        var totals = TransitInventory.read(player, java.util.Set.of("minecraft:stone"));
        context.assertTrue(totals.get("minecraft:stone") == 47, Text.literal("在途重复统计或丢失光标材料"));
        player.currentScreenHandler.setCursorStack(ItemStack.EMPTY); session.closeVerified();
        player.getInventory().setStack(1, ItemStack.EMPTY);
        context.assertTrue(TransitInventory.read(player, java.util.Set.of("minecraft:stone")).get("minecraft:stone") == 30,
                Text.literal("库存变化后仍依据旧记录计算")); context.complete();
    }
    @GameTest public void borrowedSourceMustLeaveContainerBeforePackingAndReturnsToSameSlot(TestContext context) {
        var player = fake(context);
        var relative = new net.minecraft.util.math.BlockPos(1, 1, 1);
        context.setBlockState(relative, net.minecraft.block.Blocks.BARREL);
        var pos = context.getAbsolutePos(relative);
        player.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos).add(0, -0.5, -1));
        var barrel = (net.minecraft.block.entity.BarrelBlockEntity) context.getWorld().getBlockEntity(pos);
        var source = new ItemStack(Items.BLUE_SHULKER_BOX);
        source.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(new ItemStack(Items.STONE, 30))));
        barrel.setStack(5, source); player.getInventory().setStack(0, new ItemStack(Items.SHULKER_BOX));
        var flow = SourceBoxFlow.take(player, pos, 5, 9);
        context.assertTrue(barrel.getStack(5).isEmpty() && InventoryItems.isBox(player.getInventory().getStack(9)), Text.literal("未先领取源盒"));
        flow.pack(0, 0, 0, 10, 12, false); flow.finish();
        var contents = new net.kyrptonaught.quickshulker.api.ItemStackInventory(barrel.getStack(5), 27);
        context.assertTrue(contents.getStack(0).getCount() == 18 && player.getInventory().getStack(9).isEmpty(), Text.literal("源盒没有归还原槽位"));
        var work = new net.kyrptonaught.quickshulker.api.ItemStackInventory(player.getInventory().getStack(0), 27);
        context.assertTrue(work.getStack(0).getCount() == 12 && player.getInventory().getStack(10).isEmpty(), Text.literal("工作盒材料不符"));
        context.complete();
    }
    @GameTest public void exhaustedSourceBecomesHotbarEmptyBox(TestContext context) {
        var player = fake(context); var relative = new net.minecraft.util.math.BlockPos(1,1,1);
        context.setBlockState(relative, net.minecraft.block.Blocks.BARREL); var pos = context.getAbsolutePos(relative);
        player.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos).add(0,-0.5,-1));
        var barrel = (net.minecraft.block.entity.BarrelBlockEntity) context.getWorld().getBlockEntity(pos);
        var source = new ItemStack(Items.BLUE_SHULKER_BOX);
        source.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(new ItemStack(Items.STONE, 12))));
        barrel.setStack(5, source); player.getInventory().setStack(0, new ItemStack(Items.SHULKER_BOX));
        var flow = SourceBoxFlow.take(player, pos, 5, 9); flow.pack(0, 0, 0, 10, 12, true); flow.finish();
        context.assertTrue(barrel.getStack(5).isEmpty() && player.getInventory().getStack(9).isEmpty()
                && player.getInventory().getStack(1).isOf(Items.BLUE_SHULKER_BOX)
                && !InventoryItems.contents(player.getInventory().getStack(1)).iterator().hasNext(), Text.literal("空源盒未进入快捷栏"));
        context.complete();
    }
    @GameTest public void occupiedOriginSlotKeepsSourceBoxInBag(TestContext context) {
        var player = fake(context); var relative = new net.minecraft.util.math.BlockPos(1,1,1);
        context.setBlockState(relative, net.minecraft.block.Blocks.BARREL); var pos = context.getAbsolutePos(relative);
        player.setPosition(net.minecraft.util.math.Vec3d.ofCenter(pos).add(0,-0.5,-1));
        var barrel = (net.minecraft.block.entity.BarrelBlockEntity) context.getWorld().getBlockEntity(pos);
        var source = new ItemStack(Items.BLUE_SHULKER_BOX);
        source.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(new ItemStack(Items.STONE, 12))));
        barrel.setStack(5, source); var flow = SourceBoxFlow.take(player, pos, 5, 9);
        barrel.setStack(5, new ItemStack(Items.DIRT, 64)); boolean rejected = false;
        try { flow.finish(); } catch (IllegalArgumentException expected) { rejected = true; }
        context.assertTrue(rejected && InventoryItems.isBox(player.getInventory().getStack(9)) && barrel.getStack(5).getCount() == 64
                && barrel.getStack(5).isOf(Items.DIRT), Text.literal("覆盖来源槽位或丢失源盒")); context.complete();
    }
    @GameTest public void directBackendPreservesComponentsAndQuantity(TestContext context) {
        var player = fake(context); var box = new ItemStack(Items.BLUE_SHULKER_BOX);
        box.set(DataComponentTypes.CUSTOM_NAME, Text.literal("保底工作盒"));
        var material = new ItemStack(Items.STONE, 60); material.set(DataComponentTypes.CUSTOM_NAME, Text.literal("命名材料"));
        player.getInventory().setStack(0, box); player.getInventory().setStack(1, material);
        var session = BoxBackend.open(player, 0, false);
        session.move(1, 0, 17, true); session.move(2, 0, 7, false); session.closeVerified();
        var contents = new net.kyrptonaught.quickshulker.api.ItemStackInventory(box, 27);
        context.assertTrue(contents.getStack(0).getCount() == 10 && player.getInventory().getStack(1).getCount() == 43
                && player.getInventory().getStack(2).getCount() == 7, Text.literal("直接后端数量不守恒"));
        context.assertTrue(Text.literal("命名材料").equals(contents.getStack(0).get(DataComponentTypes.CUSTOM_NAME))
                && Text.literal("保底工作盒").equals(box.get(DataComponentTypes.CUSTOM_NAME)), Text.literal("直接后端组件丢失"));
        context.complete();
    }
    @GameTest public void disabledQuickFallsBackBeforeMutation(TestContext context) {
        var player = fake(context); var box = new ItemStack(Items.SHULKER_BOX); player.getInventory().setStack(0, box);
        boolean previous = net.kyrptonaught.quickshulker.QuickShulkerMod.getConfig().quickShulkerBox;
        try {
            net.kyrptonaught.quickshulker.QuickShulkerMod.getConfig().quickShulkerBox = false;
            var session = BoxBackend.open(player, 0, true);
            context.assertTrue(session instanceof DirectBoxSession, Text.literal("Quick 不可用没有回退"));
            session.closeVerified();
        } finally { net.kyrptonaught.quickshulker.QuickShulkerMod.getConfig().quickShulkerBox = previous; }
        context.complete();
    }
    @GameTest public void directBackendRejectsIncompatibleAndLowHealthWithoutMutation(TestContext context) {
        var player = fake(context); var box = new ItemStack(Items.SHULKER_BOX);
        var named = new ItemStack(Items.STONE, 10); named.set(DataComponentTypes.CUSTOM_NAME, Text.literal("不同"));
        box.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(named)));
        player.getInventory().setStack(0, box); player.getInventory().setStack(1, new ItemStack(Items.STONE, 20));
        var before = box.copy(); var session = BoxBackend.open(player, 0, false); int rejected = 0;
        try { session.move(1, 0, 1, true); } catch (IllegalArgumentException expected) { rejected++; }
        player.setHealth(player.getMaxHealth() * .5F);
        try { session.move(1, 1, 1, true); } catch (IllegalArgumentException expected) { rejected++; }
        context.assertTrue(rejected == 2 && ItemStack.areEqual(before, box) && player.getInventory().getStack(1).getCount() == 20,
                Text.literal("拒绝转移后现场改变"));
        context.complete();
    }
    @GameTest public void specialItemsRejectedBeforePacking(TestContext context) {
        int rejected = 0;
        for (var item : List.of(Items.SHULKER_BOX, Items.POTION, Items.ENCHANTED_BOOK))
            try { BuildingMaterials.requireSupported(item); } catch (IllegalArgumentException expected) { rejected++; }
        BuildingMaterials.requireSupported(Items.STONE);
        context.assertTrue(rejected == 3, Text.literal("特殊物品规则失效")); context.complete();
    }
    @Override public void invokeTestMethod(TestContext context, java.lang.reflect.Method method) throws ReflectiveOperationException {
        try { method.invoke(this, context); }
        catch (java.lang.reflect.InvocationTargetException error) {
            dev.autostock.AutoStock.LOG.error("Integration failure: " + method.getName(), error.getCause());
            throw error;
        }
    }
    static EntityPlayerMPFake fake(TestContext context) {
        var world = context.getWorld();
        var server = world.getServer();
        var profile = new GameProfile(UUID.randomUUID(), "AutoStockTest");
        var player = EntityPlayerMPFake.respawnFake(server, world, profile, SyncedClientOptions.createDefault());
        player.networkHandler = new NetHandlerPlayServerFake(server, new ClientConnection(NetworkSide.SERVERBOUND),
                player, ConnectedClientData.createDefault(profile, false));
        player.setHealth(player.getMaxHealth());
        return player;
    }

    @GameTest public void opensAndClosesNamedBoxWithoutChanges(TestContext context) {
        var player = fake(context);
        var box = new ItemStack(Items.BLUE_SHULKER_BOX);
        box.set(DataComponentTypes.CUSTOM_NAME, Text.literal("源盒 A"));
        box.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(new ItemStack(Items.STONE, 17))));
        player.getInventory().setStack(0, box);
        var before = box.copy();
        var session = QuickShulkerSession.open(player, 0);
        context.assertTrue(player.currentScreenHandler != player.playerScreenHandler, Text.literal("未打开容器"));
        session.closeVerified();
        context.assertTrue(ItemStack.areEqual(before, box), Text.literal("开关盒改变了物品数据"));
        context.complete();
    }

    @GameTest public void lowHealthDoesNotOpenOrChangeBox(TestContext context) {
        var player = fake(context);
        var box = new ItemStack(Items.SHULKER_BOX);
        player.getInventory().setStack(0, box);
        var before = box.copy();
        player.setHealth(player.getMaxHealth() * 0.5F);
        boolean rejected = false;
        try { QuickShulkerSession.open(player, 0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        context.assertTrue(rejected, Text.literal("低血量没有阻止开盒"));
        context.assertTrue(player.currentScreenHandler == player.playerScreenHandler && ItemStack.areEqual(before, box),
                Text.literal("低血量时修改了现场"));
        context.complete();
    }

    @GameTest public void replacedOuterBoxIsRejectedEvenIfIdentical(TestContext context) {
        var player = fake(context);
        var original = new ItemStack(Items.SHULKER_BOX);
        player.getInventory().setStack(0, original);
        var session = QuickShulkerSession.open(player, 0);
        player.getInventory().setStack(0, original.copy());
        boolean rejected = false;
        try { session.verify(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        context.assertTrue(rejected, Text.literal("同类盒替换未被识别"));
        player.getInventory().setStack(0, original);
        session.closeVerified();
        context.complete();
    }

    private int inventorySlot(EntityPlayerMPFake player, int index) {
        for (int i = 0; i < player.currentScreenHandler.slots.size(); i++) {
            var slot = player.currentScreenHandler.getSlot(i);
            if (slot.inventory == player.getInventory() && slot.getIndex() == index) return i;
        }
        throw new IllegalArgumentException("找不到背包槽位");
    }

    @GameTest public void splitTransferAndTakeBackPreserveCountsAndNames(TestContext context) {
        var player = fake(context);
        var box = new ItemStack(Items.BLUE_SHULKER_BOX);
        box.set(DataComponentTypes.CUSTOM_NAME, Text.literal("成品盒"));
        var material = new ItemStack(Items.STONE, 60);
        material.set(DataComponentTypes.CUSTOM_NAME, Text.literal("材料"));
        player.getInventory().setStack(0, box); player.getInventory().setStack(1, material);
        var session = QuickShulkerSession.open(player, 0);
        session.transfer(inventorySlot(player, 1), 0, 17);
        session.transfer(0, inventorySlot(player, 2), 7);
        session.closeVerified();
        var contents = new net.kyrptonaught.quickshulker.api.ItemStackInventory(box, 27);
        context.assertTrue(contents.getStack(0).getCount() == 10 && player.getInventory().getStack(1).getCount() == 43
                && player.getInventory().getStack(2).getCount() == 7, Text.literal("拆分转移数量不守恒"));
        context.assertTrue(Text.literal("材料").equals(contents.getStack(0).get(DataComponentTypes.CUSTOM_NAME))
                && Text.literal("成品盒").equals(box.get(DataComponentTypes.CUSTOM_NAME)), Text.literal("名称丢失"));
        context.complete();
    }

    @GameTest public void incompatibleDestinationLeavesAllItemsUntouched(TestContext context) {
        var player = fake(context); var box = new ItemStack(Items.SHULKER_BOX);
        var named = new ItemStack(Items.STONE, 10); named.set(DataComponentTypes.CUSTOM_NAME, Text.literal("不同组件"));
        box.set(DataComponentTypes.CONTAINER, ContainerComponent.fromStacks(List.of(named)));
        player.getInventory().setStack(0, box); player.getInventory().setStack(1, new ItemStack(Items.STONE, 20));
        var before = box.copy(); var session = QuickShulkerSession.open(player, 0);
        boolean rejected = false;
        try { session.transfer(inventorySlot(player, 1), 0, 1); } catch (IllegalArgumentException expected) { rejected = true; }
        session.closeVerified();
        context.assertTrue(rejected && ItemStack.areEqual(before, box) && player.getInventory().getStack(1).getCount() == 20,
                Text.literal("不兼容堆叠被转移"));
        context.complete();
    }

    @GameTest public void activeOuterBoxCannotBeMoved(TestContext context) {
        var player = fake(context); var box = new ItemStack(Items.SHULKER_BOX);
        player.getInventory().setStack(0, box); var session = QuickShulkerSession.open(player, 0);
        boolean rejected = false;
        try { session.transfer(inventorySlot(player, 0), 0, 1); } catch (IllegalArgumentException expected) { rejected = true; }
        context.assertTrue(rejected && player.getInventory().getStack(0) == box, Text.literal("外层盒被移动"));
        session.closeVerified(); context.complete();
    }

    @GameTest public void lowHealthAfterOpeningPreservesMaterials(TestContext context) {
        var player = fake(context); var box = new ItemStack(Items.SHULKER_BOX);
        player.getInventory().setStack(0, box); player.getInventory().setStack(1, new ItemStack(Items.STONE, 20));
        var session = QuickShulkerSession.open(player, 0); player.setHealth(player.getMaxHealth() * 0.5F);
        boolean rejected = false;
        try { session.transfer(inventorySlot(player, 1), 0, 5); } catch (IllegalArgumentException expected) { rejected = true; }
        context.assertTrue(rejected && player.getInventory().getStack(1).getCount() == 20
                && player.currentScreenHandler.getCursorStack().isEmpty(), Text.literal("低血量后仍发生取料"));
        session.closeVerified(); context.complete();
    }
}

