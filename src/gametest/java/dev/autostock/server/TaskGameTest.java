package dev.autostock.server;

import dev.autostock.core.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.*;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import java.util.*;

public class TaskGameTest {
    @GameTest(structure="autostock-test:navigation",maxTicks=1000)
    public void completeTaskMovesPacksReturnsAndDelivers(TestContext context) throws Exception { complete(context,true); }
    @GameTest(structure="autostock-test:navigation",maxTicks=1000)
    public void completeTaskWithDirectBackend(TestContext context) throws Exception { complete(context,false); }
    @GameTest(structure="autostock-test:navigation",maxTicks=1000)
    public void cancelledBorrowedSourceResumesFromActualPlayerdata(TestContext context) throws Exception { complete(context,true,true); }
    private void complete(TestContext context,boolean quick) throws Exception {
        complete(context,quick,false);
    }
    @GameTest(structure="autostock-test:navigation",maxTicks=1800)
    public void loopDetectsWithdrawnStockAndRefillsWithoutDuplicating(TestContext context) throws Exception {complete(context,false,false,true);}
    private void complete(TestContext context,boolean quick,boolean recover) throws Exception {complete(context,quick,recover,false);}
    private void complete(TestContext context,boolean quick,boolean recover,boolean loop) throws Exception {complete(context,quick,recover,loop,false);}
    private void complete(TestContext context,boolean quick,boolean recover,boolean loop,boolean dodge) throws Exception {
        var world=context.getWorld();
        for(int x=1;x<15;x++)for(int z=1;z<15;z++)context.setBlockState(new BlockPos(x,0,z),Blocks.STONE);
        var roles=new EnumMap<RegionRole,List<RegionBox>>(RegionRole.class);
        var points=Map.of(RegionRole.MATERIAL,new BlockPos(3,1,6),RegionRole.EMPTY_BOX,new BlockPos(8,1,3),RegionRole.OUTPUT,new BlockPos(12,1,9));
        for(var entry:points.entrySet()) {
            context.setBlockState(entry.getValue(),Blocks.BARREL); var pos=context.getAbsolutePos(entry.getValue());
            roles.put(entry.getKey(),List.of(new RegionBox(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ())));
        }
        var source=(BarrelBlockEntity)world.getBlockEntity(context.getAbsolutePos(points.get(RegionRole.MATERIAL)));
        var empty=(BarrelBlockEntity)world.getBlockEntity(context.getAbsolutePos(points.get(RegionRole.EMPTY_BOX)));
        var output=(BarrelBlockEntity)world.getBlockEntity(context.getAbsolutePos(points.get(RegionRole.OUTPUT)));
        var box=new ItemStack(Items.BLUE_SHULKER_BOX);
        box.set(DataComponentTypes.CUSTOM_NAME,Text.literal("来源盒 A"));
        box.set(DataComponentTypes.CONTAINER,ContainerComponent.fromStacks(List.of(new ItemStack(Items.STONE,30))));
        source.setStack(5,box);source.setStack(7,new ItemStack(Items.DIRT,32));empty.setStack(3,new ItemStack(Items.SHULKER_BOX));
        var owner=UUID.randomUUID();var player=TaskFakePlayers.login(world.getServer(),world,owner,Vec3d.ofBottomCenter(context.getAbsolutePos(new BlockPos(3,1,3))));
        var draft=new PlanDraft(PlanDraft.PROTOCOL,world.getRegistryKey().getValue().toString(),"完整备货测试",roles,Map.of("minecraft:stone",12L,"minecraft:dirt",16L),1,quick);
        var outputPos=output.getPos();new OutputBindings(world.getServer(),owner).commit(draft.dimension(),Set.of(outputPos.getX()+","+outputPos.getY()+","+outputPos.getZ()+":minecraft:barrel;"));
        var journal=new TaskJournal(world.getServer().getSavePath(WorldSavePath.ROOT),owner);
        if(loop)RuntimeOptions.set(owner,new RunOptions(64,3,150,true,false,false,true,true,false));
        if(dodge){RuntimeOptions.set(owner,new RunOptions(64,1,100,true,true,false,false,false,false));var mob=context.spawnEntity(net.minecraft.entity.EntityType.ZOMBIE,new BlockPos(2,1,3));mob.setAiDisabled(true);mob.setInvulnerable(true);}
        boolean[] evaded={false};
        var holder=new TaskRuntime[]{new TaskRuntime(player,UUID.randomUUID(),draft,journal,null)};
        int[] recovery={0,0};int[] rounds={0};
        for(int tick=1;tick<(loop?1750:dodge?1350:950);tick++) { final int currentTick=tick;context.runAtTick(tick,()->{
            if(recovery[0]==1) {
                if(currentTick<recovery[1])return;
                try {
                    context.assertTrue(world.getServer().getPlayerManager().getPlayer(player.getUuid())==null,Text.literal("取消后假人未下线"));
                    var restored=TaskFakePlayers.login(world.getServer(),world,owner,player.getPos());var entry=journal.load();
                    context.assertTrue(entry.state()==TaskJournal.State.CANCELLED && entry.source()!=null && InventoryItems.isBox(restored.getInventory().getStack(9)),Text.literal("来源元数据或实际源盒未保存"));
                    holder[0]=new TaskRuntime(restored,entry.task(),draft,journal,entry.source());recovery[0]=2;
                } catch(Exception error) {throw new AssertionError("取消恢复失败",error);}
            }
            var runtime=holder[0];
            runtime.tick();if(runtime.detail().startsWith("闪避怪物"))evaded[0]=true;
            if(recover && recovery[0]==0 && InventoryItems.isBox(runtime.player.getInventory().getStack(9))) {
                runtime.stop(TaskJournal.State.CANCELLED,"取消恢复隔离测试",true);recovery[0]=1;recovery[1]=currentTick+5;return;
            }
            if(runtime.state()==TaskJournal.State.ERROR || runtime.state()==TaskJournal.State.EMERGENCY) { TaskStop.logout(player,"测试失败");throw new AssertionError(runtime.detail()+" @ "+player.getPos()); }
            if(loop&&runtime.detail().startsWith("循环补货")){
                var count=new HashMap<String,Long>();for(int i=0;i<output.size();i++)if(InventoryItems.isBox(output.getStack(i)))for(var material:InventoryItems.contents(output.getStack(i)))count.merge(net.minecraft.registry.Registries.ITEM.getId(material.getItem()).toString(),(long)material.getCount(),Long::sum);
                if(rounds[0]==0){context.assertTrue(count.equals(draft.demand()),Text.literal("首轮备货数量错误"));output.clear();empty.setStack(3,new ItemStack(Items.SHULKER_BOX));rounds[0]=1;}
                else if(count.equals(draft.demand())){context.assertTrue(InventoryItems.contents(source.getStack(5)).iterator().next().getCount()==6&&source.getStack(7).isEmpty(),Text.literal("循环补货重复取料或物品不守恒"));runtime.stop(TaskJournal.State.COMPLETED,"循环补货测试完成",true);context.complete();return;}
                return;
            }
            if(runtime.state()==TaskJournal.State.COMPLETED) {
                context.assertTrue(!dodge||evaded[0],Text.literal("未执行怪物闪避"));
                var counts=new HashMap<String,Long>();
                for(int slot=0;slot<output.size();slot++)if(InventoryItems.isBox(output.getStack(slot)))for(var item:InventoryItems.contents(output.getStack(slot)))
                    counts.merge(net.minecraft.registry.Registries.ITEM.getId(item.getItem()).toString(),(long)item.getCount(),Long::sum);
                context.assertTrue(counts.equals(draft.demand()),Text.literal("交付数量与冻结需求不一致："+counts));
                context.assertTrue(source.getStack(5).isOf(Items.BLUE_SHULKER_BOX)&&Text.literal("来源盒 A").equals(source.getStack(5).get(DataComponentTypes.CUSTOM_NAME))
                        && InventoryItems.contents(source.getStack(5)).iterator().next().getCount()==18 && source.getStack(7).getCount()==16,Text.literal("源盒未归还原槽或材料不守恒"));
                context.assertTrue(runtime.player.getInventory().isEmpty(),Text.literal("完成后仍有未交付任务材料"));
                context.assertTrue(!recover || recovery[0]==2,Text.literal("未实际触发取消恢复"));
                context.complete();
            }
        }); }
    }
}

