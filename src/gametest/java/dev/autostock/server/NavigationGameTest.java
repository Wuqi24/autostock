package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.minecraft.block.Blocks;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class NavigationGameTest implements CustomTestMethodInvoker {
    public void invokeTestMethod(TestContext context, java.lang.reflect.Method method) throws ReflectiveOperationException {
        try { method.invoke(this,context); } catch (java.lang.reflect.InvocationTargetException error) {
            dev.autostock.AutoStock.LOG.error("Navigation integration failure: " + method.getName(),error.getCause()); throw error;
        }
    }
    private EntityPlayerMPFake floor(TestContext context) {
        for (int x = 1; x < 15; x++) for (int z = 1; z < 15; z++) context.setBlockState(new BlockPos(x,0,z),Blocks.STONE);
        var player = QuickShulkerGameTest.fake(context); var pos = context.getAbsolutePos(new BlockPos(3,1,3));
        player.setPosition(Vec3d.ofBottomCenter(pos)); player.setOnGround(true); return player;
    }
    private SafePath.Route route(EntityPlayerMPFake player, BlockPos target) {
        var search = new SafePath(player,target,false);
        for (int i = 0; i < 100; i++) { var result = search.advance(128); if (result != null) return result; }
        throw new AssertionError("路径搜索未在预算内结束");
    }
    @GameTest(structure="autostock-test:navigation") public void flatAndOneBlockJumpRoutes(TestContext context) {
        var player = floor(context); var goal = context.getAbsolutePos(new BlockPos(9,1,3));
        var flat = route(player,goal); context.assertTrue(flat.found(),Text.literal("平地路径失败：" + flat.failure()));
        context.setBlockState(new BlockPos(9,1,3),Blocks.STONE);
        var step = route(player,goal.up()); context.assertTrue(step.found(),Text.literal("一格台阶路径失败：" + step.failure())); context.complete();
    }
    @GameTest(structure="autostock-test:navigation") public void slabsAndStairsUseCollisionHeights(TestContext context) {
        var player = floor(context); context.setBlockState(new BlockPos(7,1,3),Blocks.STONE_SLAB);
        context.setBlockState(new BlockPos(8,1,3),Blocks.STONE_STAIRS);
        var goal = context.getAbsolutePos(new BlockPos(8,2,3)); var result = route(player,goal);
        context.assertTrue(result.found(),Text.literal("半砖与楼梯路径失败：" + result.failure())); context.complete();
    }
    @GameTest(structure="autostock-test:navigation") public void deepDropIsRejected(TestContext context) {
        var player = floor(context); var top = context.getAbsolutePos(new BlockPos(3,3,3));
        context.setBlockState(new BlockPos(3,1,3),Blocks.STONE); context.setBlockState(new BlockPos(3,2,3),Blocks.STONE);
        player.setPosition(Vec3d.ofBottomCenter(top));
        var result = route(player,context.getAbsolutePos(new BlockPos(9,1,3)));
        context.assertTrue(!result.found(),Text.literal("允许了两格落差")); context.complete();
    }
    @GameTest(structure="autostock-test:navigation") public void waterAndLadderTargetsRejected(TestContext context) {
        var player = floor(context); var goal = new BlockPos(9,1,3);
        context.setBlockState(goal,Blocks.WATER); context.assertTrue(!route(player,context.getAbsolutePos(goal)).found(),Text.literal("进入水目标"));
        context.setBlockState(goal,Blocks.LADDER); context.assertTrue(!route(player,context.getAbsolutePos(goal)).found(),Text.literal("进入梯子目标")); context.complete();
    }
    @GameTest(structure="autostock-test:navigation",maxTicks=250) public void fakeWalksUsingCarpetInput(TestContext context) {
        var fixture = floor(context);
        walk(context,fixture,context.getAbsolutePos(new BlockPos(9,1,3)));
    }
    @GameTest(structure="autostock-test:navigation",maxTicks=250) public void fakeJumpsOneBlockUsingCarpetInput(TestContext context) {
        var fixture = floor(context); context.setBlockState(new BlockPos(9,1,3),Blocks.STONE);
        walk(context,fixture,context.getAbsolutePos(new BlockPos(9,2,3)));
    }
    @GameTest(structure="autostock-test:navigation",maxTicks=250) public void fakeOpensWoodenDoorOnRoute(TestContext context) {
        var fixture = floor(context);
        for (int z = 1; z < 15; z++) for (int y = 1; y <= 3; y++) context.setBlockState(new BlockPos(6,y,z),Blocks.STONE);
        var lower = Blocks.OAK_DOOR.getDefaultState().with(net.minecraft.block.DoorBlock.FACING,net.minecraft.util.math.Direction.WEST);
        context.setBlockState(new BlockPos(6,1,3),lower);
        context.setBlockState(new BlockPos(6,2,3),lower.with(net.minecraft.block.DoorBlock.HALF,net.minecraft.block.enums.DoubleBlockHalf.UPPER));
        walk(context,fixture,context.getAbsolutePos(new BlockPos(9,1,3)), () -> context.assertTrue(
                context.getWorld().getBlockState(context.getAbsolutePos(new BlockPos(6,1,3))).get(net.minecraft.block.DoorBlock.OPEN),Text.literal("假人绕行，未实际开门")));
    }
    @GameTest(structure="autostock-test:navigation",maxTicks=250) public void fakeOpensHeadHeightTrapdoor(TestContext context) {
        var fixture = floor(context);
        for (int z = 1; z < 15; z++) for (int y = 1; y <= 3; y++) context.setBlockState(new BlockPos(6,y,z),Blocks.STONE);
        context.setBlockState(new BlockPos(6,1,3),Blocks.AIR);
        context.setBlockState(new BlockPos(6,2,3),Blocks.OAK_TRAPDOOR.getDefaultState().with(net.minecraft.block.TrapdoorBlock.FACING,net.minecraft.util.math.Direction.NORTH));
        walk(context,fixture,context.getAbsolutePos(new BlockPos(9,1,3)), () -> context.assertTrue(
                context.getWorld().getBlockState(context.getAbsolutePos(new BlockPos(6,2,3))).get(net.minecraft.block.TrapdoorBlock.OPEN),Text.literal("假人绕行，未实际打开活板门")));
    }
    private void walk(TestContext context, EntityPlayerMPFake fixture, BlockPos goal) {
        walk(context,fixture,goal,() -> { });
    }
    private void walk(TestContext context, EntityPlayerMPFake fixture, BlockPos goal, Runnable arrived) {
        var player = TaskFakePlayers.login(context.getWorld().getServer(),context.getWorld(),java.util.UUID.randomUUID(),fixture.getPos());
        var walker = new FakeWalker(player,goal,false);
        for (int tick = 1; tick < 200; tick++) context.runAtTick(tick, () -> {
            walker.tick();
            if (walker.state() == FakeWalker.State.FAILED) { player.kill(Text.literal("测试结束")); throw new AssertionError(walker.failure() + "；位置 " + player.getPos()); }
            if (walker.state() == FakeWalker.State.ARRIVED) { context.assertTrue(player.getPos().squaredDistanceTo(Vec3d.ofBottomCenter(goal)) < .3,Text.literal("假人没有实际走到终点")); player.kill(Text.literal("测试结束")); arrived.run(); context.complete(); }
        });
    }
}
