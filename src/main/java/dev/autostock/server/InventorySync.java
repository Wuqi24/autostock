package dev.autostock.server;

import dev.autostock.net.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** The requesting player may observe only their reserved fake. No inventory mutation. */
public final class InventorySync {
    private record Watch(UUID session) {}
    private final Map<UUID,Watch> watches=new HashMap<>();
    private final Map<UUID,BotInventory> latest=new HashMap<>();
    public void register(){
        ServerPlayNetworking.registerGlobalReceiver(InventoryWatch.ID,(request,context)->{
            var owner=context.player();
            if(!request.active()){var watch=watches.get(owner.getUuid());if(watch!=null&&watch.session.equals(request.session()))watches.remove(owner.getUuid());return;}
            watches.put(owner.getUuid(),new Watch(request.session()));
            publish(owner,MinecraftCompat.server(owner).getPlayerManager().getPlayer(TaskFakePlayers.id(owner.getUuid())),true,false);
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var id:List.copyOf(watches.keySet())){var owner=server.getPlayerManager().getPlayer(id);if(owner==null){watches.remove(id);continue;}
                publish(owner,server.getPlayerManager().getPlayer(TaskFakePlayers.id(id)),server.getTicks()%10==0,false);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            // Capture the real final inventory before the fake entity disappears.
            for(var owner:server.getPlayerManager().getPlayerList())if(TaskFakePlayers.id(owner.getUuid()).equals(handler.player.getUuid())){
                publish(owner,handler.player,true,true);break;
            }
            watches.remove(handler.player.getUuid());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{watches.clear();latest.clear();});
    }
    private void publish(ServerPlayerEntity owner,ServerPlayerEntity bot,boolean periodic,boolean offline){
        var watch=watches.get(owner.getUuid());if(watch==null)return;
        var old=latest.get(owner.getUuid());
        if(bot==null&&TaskService.deletedOwners.contains(owner.getUuid())) {
            var slots=java.util.Collections.nCopies(36,ItemStack.EMPTY);var categories=java.util.Collections.nCopies(36,0);
            send(owner,watch,new BotInventory(watch.session,TaskFakePlayers.id(owner.getUuid()),old==null?0:old.revision()+(old.state().equals("MISSING")?0:1),"MISSING","",BlockPos.ORIGIN,0,slots,categories));return;
        }
        if(bot==null&&old!=null){
            if(!periodic&&!old.state().equals("ONLINE"))return;
            send(owner,watch,new BotInventory(watch.session,old.bot(),old.revision()+(old.state().equals("ONLINE")?1:0),old.state().equals("MISSING")?"MISSING":"OFFLINE",old.dimension(),old.position(),old.health(),old.slots(),old.categories()));return;
        }
        var slots=new ArrayList<ItemStack>();var categories=new ArrayList<Integer>();
        for(int i=0;i<36;i++){
            var stack=bot==null?ItemStack.EMPTY:bot.getInventory().getStack(i).copy();slots.add(stack);
            categories.add(stack.isEmpty()?0:InventoryItems.isBox(stack)?(!stack.contains(net.minecraft.component.DataComponentTypes.CONTAINER_LOOT)&&!stack.getOrDefault(net.minecraft.component.DataComponentTypes.CONTAINER,net.minecraft.component.type.ContainerComponent.DEFAULT).iterateNonEmpty().iterator().hasNext()?2:3):1);
        }
        String state=bot==null?"MISSING":offline?"OFFLINE":"ONLINE";
        boolean changed=old==null||!old.state().equals(state)||bot!=null&&(old.health()!=bot.getHealth()||(periodic&&!old.position().equals(bot.getBlockPos()))||!old.dimension().equals(MinecraftCompat.world(bot).getRegistryKey().getValue().toString()));
        if(!changed)for(int i=0;i<36;i++)if(!ItemStack.areEqual(old.slots().get(i),slots.get(i))){changed=true;break;}
        if(!changed&&!periodic)return;
        var value=new BotInventory(watch.session,TaskFakePlayers.id(owner.getUuid()),old==null?0:old.revision()+(changed?1:0),state,bot==null?"":MinecraftCompat.world(bot).getRegistryKey().getValue().toString(),bot==null?BlockPos.ORIGIN:bot.getBlockPos(),bot==null?0:bot.getHealth(),slots,categories);
        send(owner,watch,value);
    }
    private void send(ServerPlayerEntity owner,Watch watch,BotInventory value){
        var previous=latest.put(owner.getUuid(),value);
        // Offline/missing snapshots are sent once per subscription, without a heartbeat.
        if(previous!=null&&previous.session().equals(watch.session)&&previous.state().equals(value.state())&&!value.state().equals("ONLINE")&&previous.revision()==value.revision())return;
        if(ServerPlayNetworking.canSend(owner,BotInventory.ID))ServerPlayNetworking.send(owner,value);
    }
}



