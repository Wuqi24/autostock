package dev.autostock.server;

import dev.autostock.net.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.util.*;

/** Own frozen task only; one running/paused warehouse task per server avoids shared-container races. */
public final class TaskService {
    static final Set<UUID> deletedOwners=new HashSet<>();
    private final Map<UUID,TaskRuntime> tasks=new HashMap<>();
    private final Map<UUID,Integer> lastRequest=new HashMap<>();
    private final Map<UUID,UUID> pendingDeletes=new HashMap<>();
    private record PendingArrival(ArrivalCleanup cleanup, TaskRequest resume) { }
    private final Map<UUID,PendingArrival> arrivals=new HashMap<>();
    public void register() {
        registerManagement();
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->
            dispatcher.register(net.minecraft.commands.Commands.literal("autostock-manual").executes(context->{
                var owner=context.getSource().getPlayerOrException();var server=MinecraftCompat.server(owner);
                try {
                    if(!MinecraftCompat.hasPermission(owner,2)&&!MinecraftCompat.isHost(server,owner))throw new IllegalArgumentException("需要管理员权限或单人存档所有者身份");
                    if(pendingDeletes.containsKey(owner.getUUID()))throw new IllegalArgumentException("假人删除尚未完成");
                    var runtime=tasks.get(owner.getUUID());
                    if(runtime!=null&&runtime.state()==TaskJournal.State.RUNNING)throw new IllegalArgumentException("请先暂停或取消任务，再手动处理假人");
                    var record=new TaskJournal(server.getWorldPath(LevelResource.ROOT),owner.getUUID()).load();
                    if(record==null&&server.getPlayerList().getPlayer(TaskFakePlayers.id(owner.getUUID()))==null&&!java.nio.file.Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("playerdata/"+TaskFakePlayers.id(owner.getUUID())+".dat")))throw new IllegalArgumentException("没有本人的假人存档");
                    var fake=TaskFakePlayers.login(server,MinecraftCompat.world(owner),owner.getUUID(),MinecraftCompat.position(owner));
                    tasks.remove(owner.getUUID());TaskStop.pause(fake);
                    boolean restored=TaskAttachments.restoreCursor(fake);
                    owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("假人 "+fake.getName().getString()+" 已上线供手动处理，自动执行未启动；位置 "+fake.blockPosition().toShortString()
                            +(restored?"":"；背包满，请腾空后再次执行本命令恢复保留光标物品")));
                    return 1;
                } catch(Exception error) {context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(Objects.toString(error.getMessage(),"手动恢复失败")));return 0;}
            })));
        ServerPlayNetworking.registerGlobalReceiver(TaskRequest.ID,(request,context)->handle(context.player(),request));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var entry:List.copyOf(arrivals.entrySet())){
                var owner=server.getPlayerList().getPlayer(entry.getKey());var pending=entry.getValue();
                if(owner==null){pending.cleanup.stop();TaskStop.logout(pending.cleanup.player,"所有者离线，归置停止");arrivals.remove(entry.getKey());continue;}
                pending.cleanup.tick();
                if(!pending.cleanup.done())continue;
                arrivals.remove(entry.getKey());
                if(pending.cleanup.failure()!=null){
                    owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("假人："+pending.cleanup.failure()));
                    if(pending.resume!=null)ServerPlayNetworking.send(owner,new TaskStatus(pending.resume.taskId(),"ERROR",pending.cleanup.failure(),0,0,0,null,List.of()));
                }else if(pending.resume!=null){lastRequest.remove(entry.getKey());handle(owner,pending.resume);}
                else owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("假人上线背包已存入备货区，空盒保留"));
            }
            for(var entry:List.copyOf(tasks.entrySet())) {
                var runtime=entry.getValue();var before=runtime.state();runtime.tick();
                if(server.getTickCount()%10==0 || before!=runtime.state()) {
                    var owner=server.getPlayerList().getPlayer(entry.getKey());if(owner!=null)send(owner,runtime);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            var runtime=tasks.remove(handler.player.getUUID());lastRequest.remove(handler.player.getUUID());
            if(runtime!=null && server.getPlayerList().getPlayer(runtime.player.getUUID())!=null)
                runtime.stop(TaskJournal.State.PAUSED,"任务所有者离线，保存进度并保留物品",true);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{
            for(var runtime:tasks.values())if(server.getPlayerList().getPlayer(runtime.player.getUUID())!=null)
                runtime.stop(TaskJournal.State.PAUSED,"服务器关闭，物品保留；重新登录后手动恢复",false);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{tasks.clear();lastRequest.clear();arrivals.clear();});
    }
    private void registerManagement(){
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            for(String action:List.of("spawn","recall","delete"))dispatcher.register(net.minecraft.commands.Commands.literal("autostock-"+action).executes(context->{
                var owner=context.getSource().getPlayerOrException();var server=MinecraftCompat.server(owner);
                try{
                    if(!MinecraftCompat.hasPermission(owner,2)&&!MinecraftCompat.isHost(server,owner))throw new IllegalArgumentException("需要管理员权限或单人存档所有者身份");
                    if(pendingDeletes.containsKey(owner.getUUID()))throw new IllegalArgumentException("假人删除正在完成，请稍后");
                    var current=tasks.get(owner.getUUID());
                    var entity=server.getPlayerList().getPlayer(TaskFakePlayers.id(owner.getUUID()));
                    if(action.equals("spawn")){
                        if(entity!=null){owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("本人假人已在线，无需重复放置"));return 1;}
                        if(current!=null&&(current.state()==TaskJournal.State.RUNNING||current.state()==TaskJournal.State.PAUSED))throw new IllegalArgumentException("已有任务，请通过任务恢复或取消后再放置假人");
                        var spawned=TaskFakePlayers.login(server,MinecraftCompat.world(owner),owner.getUUID(),MinecraftCompat.position(owner));
                        if(ArrivalCleanup.needed(spawned))arrivals.put(owner.getUUID(),new PendingArrival(new ArrivalCleanup(spawned,owner.getUUID()),null));
                        owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("假人已放置；离线上线使用玩家当前坐标，自动备货未启动"));return 1;
                    }
                    if(action.equals("recall")){
                        var arrival=arrivals.remove(owner.getUUID());if(arrival!=null)arrival.cleanup.stop();
                        if(entity==null){owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("本人假人已离线"));return 1;}
                        if(current!=null){current.stop(TaskJournal.State.CANCELLED,"玩家召回假人，物品保留",false);send(owner,current);}
                        TaskStop.logout((carpet.patches.EntityPlayerMPFake)entity,"玩家召回假人，物品保留");
                        owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("已召回本人的专用假人，物品保留"));return 1;
                    }
                    if(current!=null&&(current.state()==TaskJournal.State.RUNNING||current.state()==TaskJournal.State.PAUSED))throw new IllegalArgumentException("请先取消或召回假人，再删除");
                    var dataFile=server.getWorldPath(LevelResource.ROOT).resolve("playerdata/"+TaskFakePlayers.id(owner.getUUID())+".dat");
                    if(entity==null&&!java.nio.file.Files.exists(dataFile))throw new IllegalArgumentException("本人假人不存在");
                    var fake=TaskFakePlayers.login(server,MinecraftCompat.world(owner),owner.getUUID(),MinecraftCompat.position(owner));
                    if(!fake.getInventory().isEmpty()||!fake.containerMenu.getCarried().isEmpty()||!TaskAttachments.retained(fake).isEmpty()||!fake.getEnderChestInventory().isEmpty())throw new IllegalArgumentException("假人携带物品，未删除；已上线供玩家整理背包和末影箱");
                    tasks.remove(owner.getUUID());TaskStop.logout(fake,"删除空假人");pendingDeletes.put(owner.getUUID(),fake.getUUID());return 1;
                }catch(Exception error){context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(Objects.toString(error.getMessage(),"假人操作失败")));return 0;}
            }));
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var entry:List.copyOf(pendingDeletes.entrySet())){
                if(server.getPlayerList().getPlayer(entry.getValue())!=null)continue;
                pendingDeletes.remove(entry.getKey());var owner=server.getPlayerList().getPlayer(entry.getKey());
                try{
                    var root=server.getWorldPath(LevelResource.ROOT);var base=root.resolve("playerdata");
                    java.nio.file.Files.deleteIfExists(base.resolve(entry.getValue()+".dat"));java.nio.file.Files.deleteIfExists(base.resolve(entry.getValue()+".dat_old"));
                    java.nio.file.Files.deleteIfExists(root.resolve("autostock/tasks/"+entry.getKey()+".json"));
                    deletedOwners.add(entry.getKey());
                    if(owner!=null)owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("空假人已删除；冻结需求文件保留"));
                }catch(java.io.IOException error){if(owner!=null)owner.sendSystemMessage(net.minecraft.network.chat.Component.literal("假人已下线，但清理存档失败；请查看日志"));dev.autostock.AutoStock.LOG.error("Cannot remove empty fake save",error);}
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{pendingDeletes.clear();deletedOwners.clear();});
    }
    private void handle(ServerPlayer owner,TaskRequest request) {
        var server=MinecraftCompat.server(owner);
        try {
            if(pendingDeletes.containsKey(owner.getUUID()))throw new IllegalArgumentException("假人删除尚未完成");
            if(!MinecraftCompat.hasPermission(owner,2)&&!MinecraftCompat.isHost(server,owner))throw new IllegalArgumentException("启动任务需要服务器管理员权限或单人存档所有者身份");
            var arriving=arrivals.get(owner.getUUID());
            if(arriving!=null){
                if(request.cancel()){arrivals.remove(owner.getUUID());arriving.cleanup.stop();TaskStop.logout(arriving.cleanup.player,"取消上线归置，物品保留");ServerPlayNetworking.send(owner,new TaskStatus(request.taskId(),"CANCELLED","已取消上线归置，物品保留",0,0,0,null,List.of()));return;}
                if(arriving.resume==null)arrivals.put(owner.getUUID(),new PendingArrival(arriving.cleanup,request));
                ServerPlayNetworking.send(owner,new TaskStatus(request.taskId(),"PREPARING",arriving.cleanup.detail(),0,0,0,null,List.of()));return;
            }
            var previous=lastRequest.put(owner.getUUID(),server.getTickCount());
            if(previous!=null && server.getTickCount()-previous<5)throw new IllegalArgumentException("操作过快，请稍后重试");
            var current=tasks.get(owner.getUUID());
            if(current!=null && current.task.equals(request.taskId())) {
                if(request.cancel()) { current.stop(TaskJournal.State.CANCELLED,"任务取消：已保存并下线，物品全部保留",true);send(owner,current);return; }
                if(current.state()==TaskJournal.State.RUNNING) { current.stop(TaskJournal.State.PAUSED,"原地暂停，物品保留",false);send(owner,current);return; }
            }
            if(request.cancel())throw new IllegalArgumentException("当前没有这个正在执行的任务");
            for(var entry:tasks.entrySet())if(!entry.getKey().equals(owner.getUUID()) && (entry.getValue().state()==TaskJournal.State.RUNNING||entry.getValue().state()==TaskJournal.State.PAUSED))
                throw new IllegalArgumentException("服务器已有备货任务，请等待完成或取消");
            if(current!=null && !current.task.equals(request.taskId()) && current.state()==TaskJournal.State.RUNNING)
                throw new IllegalArgumentException("请先取消当前任务");
            var root=server.getWorldPath(LevelResource.ROOT);var journal=new TaskJournal(root,owner.getUUID());var record=journal.load();
            var draft=new FrozenTasks(root).load(owner.getUUID(),request.taskId());
            if(!draft.dimension().equals(MinecraftCompat.world(owner).dimension().identifier().toString()))throw new IllegalArgumentException("请在任务原维度启动");
            boolean wasOffline=server.getPlayerList().getPlayer(TaskFakePlayers.id(owner.getUUID()))==null;
            var fake=TaskFakePlayers.login(server,MinecraftCompat.world(owner),owner.getUUID(),MinecraftCompat.position(owner));
            if(fake.getHealth()<=fake.getMaxHealth()*.5F) { TaskStop.logout(fake,"血量不足，请玩家先处理");throw new IllegalArgumentException("假人血量不足，不恢复执行"); }
            if(!TaskAttachments.restoreCursor(fake))throw new IllegalArgumentException("请腾空假人背包以恢复光标物品");
            if((wasOffline||record==null||!record.task().equals(request.taskId()))&&ArrivalCleanup.needed(fake)){
                arrivals.put(owner.getUUID(),new PendingArrival(new ArrivalCleanup(fake,owner.getUUID()),request));
                ServerPlayNetworking.send(owner,new TaskStatus(request.taskId(),"PREPARING","上线背包归置中，完成后继续任务",0,0,draft.totalItems(),null,List.of()));return;
            }
            var runtime=new TaskRuntime(fake,request.taskId(),draft,journal,record!=null&&record.task().equals(request.taskId())?record.source():null);
            tasks.put(owner.getUUID(),runtime);send(owner,runtime);
        } catch(Exception error) {
            String message=Objects.toString(error.getMessage(),"任务操作失败");if(message.length()>500)message=message.substring(0,500);
            ServerPlayNetworking.send(owner,new TaskStatus(request.taskId(),"REJECTED",message,0,0,0,null,List.of()));
            dev.autostock.AutoStock.LOG.warn("Task request rejected: {}",message);
        }
    }
    private void send(ServerPlayer owner,TaskRuntime runtime) {
        if(!ServerPlayNetworking.canSend(owner,TaskStatus.ID))return;
        long w=runtime.delivered().values().stream().mapToLong(Long::longValue).sum();
        long t;
        try {
            t=TransitInventory.read(runtime.player,runtime.draft.demand().keySet()).values().stream().mapToLong(Long::longValue).sum(); }
        catch(RuntimeException unknown) { t=-1; }
        ServerPlayNetworking.send(owner,new TaskStatus(runtime.task,runtime.state().name(),runtime.detail(),w,t,runtime.draft.totalItems(),runtime.target(),
                runtime.path().stream().limit(256).map(n->new BlockPos(n.x(),(int)Math.floor(n.y()),n.z())).toList(),runtime.bindingsVerified(),runtime.currentMaterial(),runtime.materialRemaining(),runtime.eta()));
    }
}



