package dev.autostock.server;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RecoveryGameTest {
    @GameTest public void sourceReceiptRestoresOnlyOriginalContainer(TestContext context) {
        var player = QuickShulkerGameTest.fake(context); var local = new BlockPos(1,1,1);
        context.setBlockState(local,Blocks.BARREL); var pos = context.getAbsolutePos(local);
        player.setPosition(Vec3d.ofCenter(pos).add(0,-.5,-1));
        var barrel = (BarrelBlockEntity)context.getWorld().getBlockEntity(pos);
        var box = new ItemStack(Items.BLUE_SHULKER_BOX);
        box.set(DataComponentTypes.CONTAINER,ContainerComponent.fromStacks(List.of(new ItemStack(Items.STONE,30))));
        barrel.setStack(5,box);
        var first = SourceBoxFlow.take(player,pos,5,9); var origin = first.origin();
        SourceBoxFlow.restore(player,origin,9).finish();
        context.assertTrue(barrel.getStack(5).isOf(Items.BLUE_SHULKER_BOX),Text.literal("原容器未正常恢复归还"));
        var second = SourceBoxFlow.take(player,pos,5,9); var replaced = second.origin();
        context.setBlockState(local,Blocks.AIR); context.setBlockState(local,Blocks.BARREL);
        boolean rejected = false;
        try { SourceBoxFlow.restore(player,replaced,9); } catch (IllegalArgumentException expected) { rejected = true; }
        context.assertTrue(rejected && player.getInventory().getStack(9).isOf(Items.BLUE_SHULKER_BOX)
                && ((BarrelBlockEntity)context.getWorld().getBlockEntity(pos)).isEmpty(),Text.literal("错误归还到替换容器或丢失源盒"));
        context.complete();
    }
    @GameTest(structure="autostock-test:navigation",maxTicks=100)
    public void fullBagCursorSurvivesRealLogoutAndLogin(TestContext context) {
        var owner = UUID.randomUUID(); var world = context.getWorld();
        for (int x=1;x<15;x++) for(int z=1;z<15;z++) context.setBlockState(new BlockPos(x,0,z),Blocks.STONE);
        var spawn = Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(3,1,3)));
        var player = TaskFakePlayers.login(world.getServer(),world,owner,spawn);
        for(int i=0;i<36;i++) player.getInventory().setStack(i,new ItemStack(Items.DIRT,64));
        var cursor = new ItemStack(Items.STONE,17); cursor.set(DataComponentTypes.CUSTOM_NAME,Text.literal("保留材料"));
        player.currentScreenHandler.setCursorStack(cursor); player.setHealth(9);
        TaskStop.logout(player,"低血量隔离测试");
        context.runAtTick(5,() -> {
            context.assertTrue(world.getServer().getPlayerManager().getPlayer(TaskFakePlayers.id(owner)) == null,Text.literal("未实际下线"));
            var restored = TaskFakePlayers.login(world.getServer(),world,owner,spawn.add(8,0,0));
            context.assertTrue(restored.getHealth() == 9 && restored.getPos().squaredDistanceTo(spawn.add(8,0,0)) < 1,Text.literal("上线未使用玩家新坐标或未保留血量"));
            context.assertTrue(ItemStack.areEqual(cursor,TaskAttachments.retained(restored)),Text.literal("真实玩家存档未保存光标物品及组件"));
            context.assertTrue(!TaskAttachments.restoreCursor(restored),Text.literal("背包满时覆盖物品"));
            context.assertTrue(TransitInventory.read(restored,Set.of("minecraft:stone")).get("minecraft:stone") == 17,Text.literal("保留光标未计入实际在途"));
            restored.getInventory().setStack(12,ItemStack.EMPTY);
            context.assertTrue(TaskAttachments.restoreCursor(restored) && ItemStack.areEqual(cursor,restored.getInventory().getStack(12))
                    && TaskAttachments.retained(restored).isEmpty(),Text.literal("腾空后恢复失败"));
            TaskAttachments.restoreCursor(restored);
            context.assertTrue(TransitInventory.read(restored,Set.of("minecraft:stone")).get("minecraft:stone") == 17,Text.literal("重复恢复导致复制"));
            TaskStop.logout(restored,"测试结束"); context.complete();
        });
    }
}

