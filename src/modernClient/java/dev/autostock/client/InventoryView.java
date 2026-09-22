package dev.autostock.client;

import dev.autostock.net.BotInventory;
import dev.autostock.net.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import java.util.UUID;

final class InventoryView {
    static UUID session;
    static BotInventory snapshot;
    static long lastReceived;
    static void tick(Minecraft client){
        boolean visible=ClientMinecraftCompat.screen(client) instanceof BotInventoryScreen||ClientMinecraftCompat.screen(client) instanceof PreviewScreen p&&p.watchesBot()||ClientMinecraftCompat.screen(client) instanceof PreviewDialog dialog&&dialog.getParent() instanceof PreviewScreen parent&&parent.watchesBot();
        if(visible&&session==null&&client.getConnection()!=null&&ClientPlayNetworking.canSend(InventoryWatch.ID)){
            session=UUID.randomUUID();snapshot=null;lastReceived=System.currentTimeMillis();ClientPlayNetworking.send(new InventoryWatch(session,true));
        }else if(!visible&&session!=null){if(client.getConnection()!=null&&ClientPlayNetworking.canSend(InventoryWatch.ID))ClientPlayNetworking.send(new InventoryWatch(session,false));clear();}
    }
    static void receive(BotInventory value){
        if(session==null||!session.equals(value.session()))return;
        if(snapshot!=null&&value.revision()<snapshot.revision())return;
        lastReceived=System.currentTimeMillis();
        boolean changed=snapshot==null||value.revision()>snapshot.revision();if(changed)snapshot=value;
        var screen=ClientMinecraftCompat.screen(Minecraft.getInstance());
        if(changed){
            if(screen instanceof PreviewScreen p&&p.watchesBot())p.refreshInventory();
            else if(screen instanceof BotInventoryScreen detail)detail.refreshInventory();
        }
    }
    static void clear(){session=null;snapshot=null;lastReceived=0;}
    static String status(){
        if(session==null)return "服务器未提供背包同步";
        if(snapshot==null)return System.currentTimeMillis()-lastReceived>5000?"同步中断":"等待背包同步…";
        if(snapshot.state().equals("OFFLINE"))return "离线 · 最终快照";
        if(snapshot.state().equals("MISSING"))return "假人不存在或尚未上线";
        if(System.currentTimeMillis()-lastReceived>5000)return "同步中断 · 显示最后快照";
        var world=Minecraft.getInstance().level;
        if(world!=null&&!world.dimension().identifier().toString().equals(snapshot.dimension()))return "假人不在当前维度";
        return "在线 · 实时同步";
    }
}

