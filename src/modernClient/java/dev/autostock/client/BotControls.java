package dev.autostock.client;
import net.minecraft.client.Minecraft;
final class BotControls {
 static void send(String action,ClientDraft draft){
  var client=Minecraft.getInstance();if(client.getConnection()==null)throw new IllegalArgumentException("未连接服务器");
  if(!java.util.Set.of("spawn","recall","delete").contains(action))throw new IllegalArgumentException("无效假人操作");
  ClientSettings.get().sync();client.getConnection().sendCommand("autostock-"+action);
 }
}
