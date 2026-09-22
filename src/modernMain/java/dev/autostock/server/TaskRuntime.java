package dev.autostock.server;

import carpet.patches.EntityPlayerMPFake;
import dev.autostock.core.PlanDraft;
import dev.autostock.core.RegionRole;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** One server-thread state machine. Every transfer reads physical stacks again at its destination. */
final class TaskRuntime {
    final EntityPlayerMPFake player;
    final UUID task;
    final PlanDraft draft;
    private final TaskJournal journal;
    private WarehouseIndex warehouse;
    private boolean indexed,bindingsVerified;
    private TaskJournal.State state = TaskJournal.State.RUNNING;
    private String detail = "重新扫描世界实际状态";
    private SourceBoxFlow.Origin origin;
    private SafePath search;
    private FakeWalker walker;
    private java.util.function.Consumer<WarehouseIndex.Container> action;
    private Map<BlockPos,WarehouseIndex.Container> destinations = Map.of();
    private WarehouseIndex.Container target;
    private BlockPos materialTarget;
    private Map<String,Long> delivered = Map.of();
    private String currentMaterial="";private long elapsed;
    private int waitTicks,transferDelay,pathRetries,retryDelay;private boolean monitoring,waitingForStock;private WarehouseIndex monitor,retryIndex;

    TaskRuntime(EntityPlayerMPFake player,UUID task,PlanDraft draft,TaskJournal journal,SourceBoxFlow.Origin origin) throws IOException {
        this.player=player;RuntimeOptions.attach(player.getUUID(),journal.owner); this.task=task; this.draft=draft; this.journal=journal; this.origin=origin;
        if (!MinecraftCompat.world(player).dimension().identifier().toString().equals(draft.dimension()))
            throw new IllegalArgumentException("假人所在维度与冻结任务不符，保留原位置");
        if (!TaskAttachments.restoreCursor(player)) throw new IllegalArgumentException("请先腾空假人背包以恢复保留物品");
        if (!player.containerMenu.getCarried().isEmpty() || player.containerMenu != player.inventoryMenu)
            throw new IllegalArgumentException("假人仍有未结束会话");
        warehouse=new WarehouseIndex(MinecraftCompat.world(player),draft);
        save();
    }
    String currentMaterial(){return currentMaterial;}
    long materialRemaining(){return Math.max(0,draft.demand().getOrDefault(currentMaterial,0L)-delivered.getOrDefault(currentMaterial,0L));}
    long eta(){long done=delivered.values().stream().mapToLong(Long::longValue).sum();return done>0?Math.max(0,(draft.totalItems()-done)*elapsed/20/done):-1;}
    boolean bindingsVerified(){return bindingsVerified&&state!=TaskJournal.State.ERROR;}
    TaskJournal.State state() { return state; }
    String detail() { return detail; }
    Map<String,Long> delivered() { return delivered; }
    List<SafePath.Node> path() { return state!=TaskJournal.State.RUNNING || MinecraftCompat.server(player).getPlayerList().getPlayer(player.getUUID())!=player || walker == null ? List.of() : List.copyOf(walker.remainingPath()); }
    BlockPos target() { return state!=TaskJournal.State.RUNNING || target == null ? null : target.position(); }
    private void save() throws IOException { journal.save(new TaskJournal.Entry(1,task,state,draft.dimension(),origin,detail)); }
    void stop(TaskJournal.State next,String reason,boolean logout) {
        state=next; detail=reason.length()>440?reason.substring(0,440):reason; if (walker != null) walker.stop();walker=null;search=null;target=null;action=null;destinations=Map.of();retryIndex=null;retryDelay=0;
        TaskStop.pause(player);
        try { save(); } catch (IOException error) { detail += "；进度保存失败，物品仍在玩家存档"; dev.autostock.AutoStock.LOG.error("Task journal save failed",error); }
        if(logout) TaskStop.logout(player,detail);
    }
    void tick() {
        boolean online=MinecraftCompat.server(player).getPlayerList().getPlayer(player.getUUID())==player;
        if(online && state!=TaskJournal.State.CANCELLED && state!=TaskJournal.State.COMPLETED && state!=TaskJournal.State.EMERGENCY
                && (!player.isAlive() || player.getHealth()<=player.getMaxHealth()*.5F)) {
            stop(TaskJournal.State.EMERGENCY,"低血量：已下线并保留全部物品",true);return;
        }
        if(state!=TaskJournal.State.RUNNING) return;
        if(!online) { stop(TaskJournal.State.ERROR,"假人已离线，执行停止；恢复时重新读取玩家存档",false);return; }
        try {
            if (!player.isAlive() || player.getHealth()<=player.getMaxHealth()*.5F) { stop(TaskJournal.State.EMERGENCY,"低血量：已下线并保留全部物品",true); return; }
            var options=RuntimeOptions.get(player.getUUID());if(!monitoring)elapsed++;
            if(waitingForStock){if(waitTicks++<100)return;waitingForStock=false;waitTicks=0;warehouse=new WarehouseIndex(MinecraftCompat.world(player),draft);indexed=false;}
            if(monitoring){
                if(!options.loop()){monitoring=false;stop(TaskJournal.State.COMPLETED,"备货完成",true);return;}
                if(waitTicks++<100)return;
                if(monitor==null)monitor=new WarehouseIndex(MinecraftCompat.world(player),draft);
                if(!monitor.advance())return;
                delivered=monitor.delivered();monitor=null;waitTicks=0;
                if(!satisfied(delivered)&&options.autoTrigger()){monitoring=false;warehouse=new WarehouseIndex(MinecraftCompat.world(player),draft);indexed=false;detail="缺货时自动触发";}
                return;
            }
            if(retryDelay>0){if(--retryDelay==0)retryIndex=new WarehouseIndex(MinecraftCompat.world(player),draft);return;}
            if(retryIndex!=null){
                try{if(!retryIndex.advance())return;warehouse=retryIndex;retryIndex=null;
                    var refreshed=new LinkedHashMap<BlockPos,WarehouseIndex.Container>();for(var c:warehouse.containers)if(destinations.containsKey(c.position()))refreshed.put(c.position(),c);
                    if(refreshed.isEmpty()){retryPath("目标容器暂不可用");return;}destinations=Map.copyOf(refreshed);search=new SafePath(player,List.copyOf(destinations.keySet()),true);
                }catch(RuntimeException problem){retryIndex=null;retryPath(problem.getMessage());}return;
            }
            if (search != null) {
                var route=search.advance(128); if(route==null)return;
                search=null; if(!route.found()){retryPath(route.failure());return;}
                target=destinations.get(route.target()); walker=new FakeWalker(player,route); return;
            }
            if (walker != null) {
                walker.tick();
                if(walker.state()==FakeWalker.State.FAILED){if(walker.failure().contains("物品"))throw new IllegalArgumentException(walker.failure());retryPath(walker.failure());return;}
                if(walker.state()!=FakeWalker.State.ARRIVED)return;
                walker=null; target.verify(MinecraftCompat.world(player)); var callback=action; action=null;
                callback.accept(target); target=null;pathRetries=0; return;
            }
            if(!indexed) {
                if(!warehouse.advance())return;
                indexed=true; delivered=warehouse.delivered();
                if(warehouse.outputSlots()==0 && !satisfied(delivered))throw new IllegalArgumentException("备货区没有可用空槽位");
                validateBag();
                preflight();bindingsVerified=true;
            }
            if(transferDelay++<options.pickupInterval()-1)return;transferDelay=0;advance();
        } catch (RuntimeException | IOException error) {
            dev.autostock.AutoStock.LOG.warn("Task stopped: {}",error.getMessage());
            String issue=Objects.toString(error.getMessage(),"执行失败");if(RuntimeOptions.get(player.getUUID()).autoTrigger()&&(issue.contains("材料不足")||issue.contains("补充库存"))){waitingForStock=true;waitTicks=0;detail="缺货时自动触发 · 等待库存："+issue;TaskStop.pause(player);return;}
            stop(TaskJournal.State.ERROR,"已停止并保留物品："+issue,false);
        }
    }
    private ItemStack stack(int slot) { return player.getInventory().getItem(slot); }
    private void preflight() throws IOException {
        var bindings=new OutputBindings(MinecraftCompat.server(player),journal.owner);
        for(var c:warehouse.containers)if(c.role()==RegionRole.OUTPUT && !bindings.contains(draft.dimension(),c.key()))
            throw new IllegalArgumentException("备货容器未绑定，请重新扫描并绑定");
        var supplies=new ArrayList<dev.autostock.core.StockPlanner.Supply>();
        var variants=new HashMap<InventoryItems.Key,String>();
        java.util.function.Consumer<ItemStack> add=item->{
            if(item.isEmpty()||!draft.demand().containsKey(id(item)))return;
            var key=InventoryItems.key(item);var variant=variants.computeIfAbsent(key,k->Integer.toString(variants.size()));
            if(variants.size()>8192||supplies.size()>200000)throw new IllegalArgumentException("库存组件过多，停止预检");
            supplies.add(new dev.autostock.core.StockPlanner.Supply(id(item),variant,Math.min(64,item.getMaxStackSize()),item.getCount(),true));
        };
        for(var c:warehouse.containers)if(c.role()==RegionRole.MATERIAL)for(int i=0;i<c.inventory().getContainerSize();i++) {
            var item=c.inventory().getItem(i);if(InventoryItems.isBox(item))InventoryItems.contents(item).forEach(add);else add.accept(item);
        }
        var transit=TransitInventory.read(player,draft.demand().keySet());
        var plan=dev.autostock.core.StockPlanner.plan(draft.demand(),delivered,transit,supplies,Map.of());
        if(!plan.complete())throw new IllegalArgumentException("材料不足，请补充后恢复："+plan.rows().stream().filter(r->r.shortage()>0).map(r->r.item()+" 缺 "+r.shortage()).limit(3).toList());
        long carried=java.util.stream.IntStream.range(0,36).filter(i->i!=9 && InventoryItems.isBox(stack(i))&&!emptyBox(stack(i))).count();
        if(origin!=null||!stack(10).isEmpty())carried++;
        if(plan.boxes()+carried>warehouse.outputSlots())throw new IllegalArgumentException("备货区容量不足：预计预留 "+(plan.boxes()+carried)+" 个空槽位（恢复时包含在途盒）");
    }
    private boolean emptyBox(ItemStack box) { return InventoryItems.isBox(box) && !InventoryItems.contents(box).iterator().hasNext(); }
    private static String id(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private long need(String id,Map<String,Long> inBag) { return Math.max(0,draft.demand().getOrDefault(id,0L)-delivered.getOrDefault(id,0L)-inBag.getOrDefault(id,0L)); }
    private boolean satisfied(Map<String,Long> counts) { return draft.demand().entrySet().stream().allMatch(e->counts.getOrDefault(e.getKey(),0L)>=e.getValue()); }
    private void validateBag() {
        for(int reserved:new int[]{9,10})if(emptyBox(stack(reserved))&&(reserved!=9||origin==null)){
            int free=-1;for(int i=0;i<36;i++)if(i!=9&&i!=10&&stack(i).isEmpty()){free=i;break;}
            if(free<0)throw new IllegalArgumentException("空盒占满背包，请腾出取料临时槽；空盒全部保留");
            moveBag(reserved,free);
        }
        for(int slot=0;slot<36;slot++) {
            var stack=stack(slot); if(stack.isEmpty())continue;
            if(slot==10) { BuildingMaterials.requireSupported(stack.getItem()); continue; }
            if(!InventoryItems.isBox(stack) || stack.getCount()!=1)throw new IllegalArgumentException("假人槽位 "+slot+" 不是任务潜影盒，请手动处理");
            if(slot>=1 && slot<=8 && !emptyBox(stack))throw new IllegalArgumentException("空盒快捷栏含非空盒，请手动处理");
        }
        if(!stack(9).isEmpty() && origin==null)throw new IllegalArgumentException("源盒缺少可靠来源记录，请手动处理");
        if(origin!=null && stack(9).isEmpty()) {
            // A crash between receipt and transfer is ambiguous. Do not invent a missing source box.
            throw new IllegalArgumentException("来源记录与实际临时槽位不符，请处理现场后重新建任务");
        }
    }
    private Map<String,Long> packed() {
        var counts=new HashMap<String,Long>();
        for(int slot=0;slot<36;slot++) if(slot!=9 && InventoryItems.isBox(stack(slot)))
            for(var content:InventoryItems.contents(stack(slot)))counts.merge(id(content),(long)content.getCount(),Math::addExact);
        return counts;
    }
    private List<ItemStack> boxSlots(ItemStack box) {
        var values=new ArrayList<ItemStack>(); box.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).allItemsCopyStream().forEach(values::add);
        while(values.size()<27)values.add(ItemStack.EMPTY); return values;
    }
    private int fit(ItemStack material) {
        if(!InventoryItems.isBox(stack(0)))return -1;
        var slots=boxSlots(stack(0));
        for(int i=0;i<27;i++) if(!slots.get(i).isEmpty() && ItemStack.isSameItemSameComponents(slots.get(i),material)
                && slots.get(i).getCount()<Math.min(64,material.getMaxStackSize()))return i;
        for(int i=0;i<27;i++)if(slots.get(i).isEmpty())return i;
        return -1;
    }
    private int capacity(int boxSlot,ItemStack material) { return Math.min(64,material.getMaxStackSize())-boxSlots(stack(0)).get(boxSlot).getCount(); }
    private void moveBag(int from,int to) {
        if(!stack(to).isEmpty())throw new IllegalArgumentException("背包目标槽位已占用");
        player.getInventory().setItem(to,stack(from)); player.getInventory().setItem(from,ItemStack.EMPTY);
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
    }
    private void advance() throws IOException {
        delivered=warehouse.delivered();
        var packed=packed();
        if(origin!=null) {
            var slots=boxSlots(stack(9));
            if(stack(10).isEmpty() && InventoryItems.isBox(stack(0)))for(int i=0;i<27;i++) {
                var material=slots.get(i); if(material.isEmpty())continue;
                long required=need(id(material),packed); int into=fit(material);
                if(required>0 && into>=0) {
                    final int from=i, count=(int)Math.min(required,Math.min(material.getCount(),capacity(into,material)));
                    SourceBoxFlow.restore(player,origin,9).pack(from,0,into,10,count,RuntimeOptions.quick(player.getUUID(),draft.quickShulker()));
                    detail="从已领取源盒装入工作盒"; return;
                }
            }
            if(!stack(10).isEmpty()) { packLoose(packed); return; }
            var source=warehouse.containers.stream().filter(c->c.position().equals(origin.position())).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("原来源容器已不在材料区"));
            travel(List.of(source),c->{ SourceBoxFlow.restore(player,origin,9).finish(); origin=null; persist(); },"归还源盒或收起空源盒");return;
        }
        if(!stack(10).isEmpty()) { packLoose(packed); return; }
        int finished=finishedSlot();
        var all=new HashMap<>(delivered); packed.forEach((key,value)->all.merge(key,value,Math::addExact));
        if(satisfied(all)) {
            if(finished>=0) { deliver(finished);return; }
            if(InventoryItems.isBox(stack(0))&&!emptyBox(stack(0))) { deliver(0);return; }
            if(RuntimeOptions.get(player.getUUID()).loop()){monitoring=true;waitTicks=0;detail="循环补货 · 等待缺货";TaskStop.pause(player);save();}else stop(TaskJournal.State.COMPLETED,"备货完成，假人已下线；剩余空盒保留",true);return;
        }
        long free=java.util.stream.IntStream.range(0,36).filter(i->stack(i).isEmpty()).count();
        if(free<6 && finished>=0) { deliver(finished);return; }
        if(stack(0).isEmpty()) { acquireWorkBox();return; }
        var transit=TransitInventory.read(player,draft.demand().keySet());
        var sources=warehouse.containers.stream().filter(c->c.role()==RegionRole.MATERIAL && hasNeeded(c,transit)).toList();
        if(sources.isEmpty()) {
            if(!emptyBox(stack(0))) { sealWork();return; }
            if(finished>=0) { deliver(finished);return; }
            throw new IllegalArgumentException("材料区没有仍需取用且可装入的材料；请补充库存后恢复");
        }
        var selected=sources.stream().filter(c->c.position().equals(materialTarget)).findFirst()
                .orElseGet(()->sources.stream().min(Comparator
                        .comparingInt((WarehouseIndex.Container c)->chebyshev(player.blockPosition(),c.position()))
                        .thenComparingInt(c->c.position().getX()).thenComparingInt(c->c.position().getY())
                        .thenComparingInt(c->c.position().getZ())).orElseThrow());
        materialTarget=selected.position();
        travel(List.of(selected),c->takeMaterial(c),"前往锁定材料容器（切比雪夫距离优先）");
    }
    private boolean hasNeeded(WarehouseIndex.Container c,Map<String,Long> transit) {
        c.verify(MinecraftCompat.world(player));
        for(int slot=0;slot<c.inventory().getContainerSize();slot++) {
            var stack=c.inventory().getItem(slot);
            if(InventoryItems.isBox(stack)) { for(var material:InventoryItems.contents(stack))if(need(id(material),transit)>0 && fit(material)>=0)return true; }
            else if(!stack.isEmpty() && need(id(stack),transit)>0 && fit(stack)>=0)return true;
        }
        return false;
    }
    private void takeMaterial(WarehouseIndex.Container c) {
        detail="取料 · "+c.position().toShortString();
        delivered=warehouse.delivered(); var transit=TransitInventory.read(player,draft.demand().keySet());
        for(int slot=0;slot<c.inventory().getContainerSize();slot++) {
            var material=c.inventory().getItem(slot);
            if(InventoryItems.isBox(material)) {
                boolean needed=false; for(var content:InventoryItems.contents(material))if(need(id(content),transit)>0 && fit(content)>=0){needed=true;currentMaterial=id(content);}
                if(!needed)continue;
                var flow=SourceBoxFlow.take(player,c.position(),slot,9); origin=flow.origin(); persist();return;
            }
            long required=need(id(material),transit); int into=fit(material);
            if(material.isEmpty()||required==0||into<0)continue;
            currentMaterial=id(material);int count=(int)Math.min(required,Math.min(material.getCount(),capacity(into,material)));
            transfer(c,slot,10,count,true);return;
        }
        throw new IllegalArgumentException("到达后来源库存改变，请恢复以重新规划");
    }
    private void packLoose(Map<String,Long> packed) {
        detail="装盒 · "+stack(10).getHoverName().getString();
        var material=stack(10); int into=fit(material);
        if(into<0) { if(!stack(0).isEmpty()) { deliver(0);return; } acquireWorkBox();return; }
        int count=(int)Math.min(need(id(material),packed),Math.min(material.getCount(),capacity(into,material)));
        if(count<=0)throw new IllegalArgumentException("散料已不属于剩余需求，保留并等待玩家处理");
        var session=BoxBackend.open(player,0,RuntimeOptions.quick(player.getUUID(),draft.quickShulker()));session.move(10,into,count,true);session.closeVerified();
    }
    private int finishedSlot() { for(int i=11;i<36;i++)if(InventoryItems.isBox(stack(i))&&!emptyBox(stack(i)))return i;return -1; }
    private void sealWork() {
        for(int i=11;i<36;i++)if(stack(i).isEmpty()) { moveBag(0,i);detail="成品盒移入背包";return; }
        deliver(0);
    }
    private void acquireWorkBox() {
        detail="领取工作空盒";
        for(int i=1;i<36;i++)if(i!=9&&i!=10&&emptyBox(stack(i))) { moveBag(i,0);return; }
        var candidates=warehouse.containers.stream().filter(c->c.role()==RegionRole.EMPTY_BOX).filter(c->{
            c.verify(MinecraftCompat.world(player));for(int i=0;i<c.inventory().getContainerSize();i++)if(emptyBox(c.inventory().getItem(i)))return true;return false;
        }).toList();
        if(candidates.isEmpty())throw new IllegalArgumentException("空盒不足，请补充空盒区");
        travel(candidates,c->{for(int i=0;i<c.inventory().getContainerSize();i++)if(emptyBox(c.inventory().getItem(i))) { transfer(c,i,0,1,true);return; }
            throw new IllegalArgumentException("到达后空盒已被取走");},"领取工作空盒");
    }
    private void deliver(int slot) {
        if(!InventoryItems.isBox(stack(slot))||emptyBox(stack(slot)))throw new IllegalArgumentException("禁止向备货区交付空盒或散料");
        var destination=warehouse.containers.stream().filter(c->c.role()==RegionRole.OUTPUT).filter(c->{
            c.verify(MinecraftCompat.world(player));for(int i=0;i<c.inventory().getContainerSize();i++)if(c.inventory().getItem(i).isEmpty())return true;return false;
        }).findFirst().orElseThrow(()->new IllegalArgumentException("备货区已满，成品保留在假人背包"));
        travel(List.of(destination),c->{for(int i=0;i<c.inventory().getContainerSize();i++)if(c.inventory().getItem(i).isEmpty()) {
            transfer(c,i,slot,1,false); delivered=warehouse.delivered();return;
        } throw new IllegalArgumentException("交付时备货容器已满");},"按 X、Y、Z 顺序交付成品盒");
    }
    private void transfer(WarehouseIndex.Container c,int containerSlot,int bagSlot,int count,boolean intoBag) {
        c.verify(MinecraftCompat.world(player)); var handler=SourceBoxFlow.openOrigin(player,c.position());
        if(containerSlot<0||containerSlot>=handler.slots.size()||handler.getSlot(containerSlot).container==player.getInventory())throw new IllegalArgumentException("容器槽位结构改变");
        int playerSlot=ContainerTransfers.playerSlot(player,handler,bagSlot);
        ContainerTransfers.move(player,handler,intoBag?containerSlot:playerSlot,intoBag?playerSlot:containerSlot,count);
        player.closeContainer();
    }
    private void retryPath(String reason){
        if(walker!=null)walker.stop();walker=null;search=null;target=null;retryIndex=null;
        if(pathRetries>=5){stop(TaskJournal.State.ERROR,"寻路更新 5 次仍失败，请玩家帮助："+reason,false);return;}
        pathRetries++;retryDelay=20;detail="正在更新路径 "+pathRetries+"/5";
    }
    private void travel(List<WarehouseIndex.Container> candidates,java.util.function.Consumer<WarehouseIndex.Container> action,String detail) {
        var map=new LinkedHashMap<BlockPos,WarehouseIndex.Container>();candidates.forEach(c->map.put(c.position(),c));
        destinations=Map.copyOf(map);this.action=action;this.detail=detail;
        if(!RuntimeOptions.get(player.getUUID()).autoPath()){
            var nearby=candidates.stream().filter(c->SafePath.canInteract(player,MinecraftCompat.position(player),c.position())).findFirst().orElseThrow(()->new IllegalArgumentException("自动寻路已关闭，目标不在交互范围"));
            action.accept(nearby);this.action=null;target=null;return;
        }
        search=new SafePath(player,List.copyOf(map.keySet()),true);
    }
    private void persist() { try { save(); } catch(IOException error) { throw new IllegalArgumentException("保存来源恢复记录失败",error); } }
    private static int chebyshev(BlockPos a,BlockPos b) {
        return Math.max(Math.abs(a.getX()-b.getX()),Math.max(Math.abs(a.getY()-b.getY()),Math.abs(a.getZ()-b.getZ())));
    }
}


