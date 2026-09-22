package dev.autostock.server;

import dev.autostock.core.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import java.util.*;

public class InventoryBehaviorGameTest {
    private void floor(TestContext c){for(int x=1;x<15;x++)for(int z=1;z<15;z++)c.setBlockState(new BlockPos(x,0,z),Blocks.STONE);}
    private String key(BlockPos p){return p.getX()+","+p.getY()+","+p.getZ()+":minecraft:barrel;";}

    @GameTest(structure="autostock-test:navigation",maxTicks=800)
    public void arrivalStoresItemsAndKeepsEmptyBoxes(TestContext c) throws Exception {
        floor(c);var world=c.getWorld();var owner=UUID.randomUUID();
        c.setBlockState(new BlockPos(10,1,3),Blocks.BARREL);
        var pos=c.getAbsolutePos(new BlockPos(10,1,3));var output=(BarrelBlockEntity)world.getBlockEntity(pos);
        new OutputBindings(world.getServer(),owner).commit(world.getRegistryKey().getValue().toString(),Set.of(key(pos)));
        var player=TaskFakePlayers.login(world.getServer(),world,owner,Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3,1,3))));
        player.getInventory().setStack(0,new ItemStack(Items.DIAMOND_PICKAXE));
        player.getInventory().setStack(4,new ItemStack(Items.SHULKER_BOX));
        var source=new ItemStack(Items.BLUE_SHULKER_BOX);source.set(DataComponentTypes.CONTAINER,ContainerComponent.fromStacks(List.of(new ItemStack(Items.STONE,16))));
        player.getInventory().setStack(9,source.copy());player.getInventory().setStack(12,new ItemStack(Items.APPLE,3));
        var cleanup=new ArrivalCleanup(player,owner);
        for(int tick=1;tick<780;tick++)c.runAtTick(tick,()->{
            cleanup.tick();if(!cleanup.done())return;
            c.assertTrue(cleanup.failure()==null,Text.literal(Objects.toString(cleanup.failure(),"")));
            c.assertTrue(player.getInventory().getStack(4).isOf(Items.SHULKER_BOX)&&java.util.stream.IntStream.range(0,36).filter(i->i!=4).allMatch(i->player.getInventory().getStack(i).isEmpty()),Text.literal("空盒未保留或上线物品未归置"));
            c.assertTrue(output.getStack(0).isOf(Items.DIAMOND_PICKAXE)&&ItemStack.areEqual(output.getStack(1),source)&&output.getStack(2).isOf(Items.APPLE)&&output.getStack(2).getCount()==3,Text.literal("归置物品不守恒"));
            TaskStop.logout(player,"归置测试完成");c.complete();
        });
    }

    @GameTest(structure="autostock-test:navigation",maxTicks=450)
    public void pickupDropsOnlyNewQuantityAwayFromRoute(TestContext c){
        floor(c);var world=c.getWorld();var player=TaskFakePlayers.login(world.getServer(),world,UUID.randomUUID(),Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3,1,3))));
        player.getInventory().setStack(15,new ItemStack(Items.STONE,5));
        var walker=new FakeWalker(player,c.getAbsolutePos(new BlockPos(11,1,3)),false);boolean[] picked={false};
        for(int tick=1;tick<430;tick++){final int at=tick;c.runAtTick(tick,()->{
            if(at==10){var item=new ItemEntity(world,player.getX(),player.getY(),player.getZ(),new ItemStack(Items.STONE,3));item.setPickupDelay(0);world.spawnEntity(item);item.onPlayerCollision(player);c.assertTrue(player.getInventory().getStack(15).getCount()==8,Text.literal("没有实际拾取测试物品"));picked[0]=true;}
            walker.tick();c.assertTrue(walker.state()!=FakeWalker.State.FAILED,Text.literal(Objects.toString(walker.failure(),"")));
            if(walker.state()!=FakeWalker.State.ARRIVED)return;
            var items=world.getEntitiesByClass(ItemEntity.class,new Box(Vec3d.of(c.getAbsolutePos(new BlockPos(0,0,0))),Vec3d.of(c.getAbsolutePos(new BlockPos(16,5,16)))),e->e.getStack().isOf(Items.STONE));
            c.assertTrue(picked[0]&&player.getInventory().getStack(15).getCount()==5,Text.literal("误丢原有物品或未丢新增数量"));
            c.assertTrue(items.stream().mapToInt(e->e.getStack().getCount()).sum()==3&&items.stream().allMatch(e->Math.abs(e.getBlockPos().getZ()-c.getAbsolutePos(new BlockPos(3,1,3)).getZ())>1),Text.literal("物品仍落在路径上或数量错误"));
            TaskStop.logout(player,"拾取测试完成");c.complete();
        });}
    }

    @GameTest(structure="autostock-test:navigation",maxTicks=1100)
    public void blockedRouteRetriesFiveTimesBeforeStopping(TestContext c) throws Exception { blocked(c,false); }
    @GameTest(structure="autostock-test:navigation",maxTicks=1100)
    public void routeRefreshFindsNewlyOpenedPassage(TestContext c) throws Exception { blocked(c,true); }
    private void blocked(TestContext c,boolean reopen) throws Exception {
        floor(c);var world=c.getWorld();var owner=UUID.randomUUID();var roles=new EnumMap<RegionRole,List<RegionBox>>(RegionRole.class);
        var points=Map.of(RegionRole.MATERIAL,new BlockPos(7,1,7),RegionRole.EMPTY_BOX,new BlockPos(2,1,3),RegionRole.OUTPUT,new BlockPos(12,1,3));
        for(var e:points.entrySet()){c.setBlockState(e.getValue(),Blocks.BARREL);var p=c.getAbsolutePos(e.getValue());roles.put(e.getKey(),List.of(new RegionBox(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ())));}
        for(int x=6;x<=8;x++)for(int z=6;z<=8;z++)if(x!=7||z!=7)for(int y=1;y<=3;y++)c.setBlockState(new BlockPos(x,y,z),Blocks.STONE);
        ((BarrelBlockEntity)world.getBlockEntity(c.getAbsolutePos(points.get(RegionRole.MATERIAL)))).setStack(0,new ItemStack(Items.STONE,1));
        var player=TaskFakePlayers.login(world.getServer(),world,owner,Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(3,1,3))));player.getInventory().setStack(0,new ItemStack(Items.SHULKER_BOX));
        var draft=new PlanDraft(PlanDraft.PROTOCOL,world.getRegistryKey().getValue().toString(),"重试测试",roles,Map.of("minecraft:stone",1L),1,false);
        new OutputBindings(world.getServer(),owner).commit(draft.dimension(),Set.of(key(c.getAbsolutePos(points.get(RegionRole.OUTPUT)))));
        var runtime=new TaskRuntime(player,UUID.randomUUID(),draft,new TaskJournal(world.getServer().getSavePath(WorldSavePath.ROOT),owner),null);boolean[] fifth={false},opened={false};
        for(int tick=1;tick<1080;tick++)c.runAtTick(tick,()->{
            runtime.tick();if(runtime.detail().contains("5/5"))fifth[0]=true;
            if(reopen&&!opened[0]&&runtime.detail().contains("2/5")){for(int y=1;y<=3;y++)c.setBlockState(new BlockPos(7,y,6),Blocks.AIR);opened[0]=true;}
            if(runtime.state()==TaskJournal.State.ERROR){c.assertTrue(!reopen&&fifth[0]&&runtime.detail().contains("5 次"),Text.literal(runtime.detail()));TaskStop.logout(player,"重试测试完成");c.complete();}
            if(runtime.state()==TaskJournal.State.COMPLETED){c.assertTrue(reopen&&opened[0]&&runtime.delivered().getOrDefault("minecraft:stone",0L)==1,Text.literal("没有在刷新后完成交付"));c.complete();}
        });
    }
}
