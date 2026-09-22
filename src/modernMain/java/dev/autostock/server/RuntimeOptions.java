package dev.autostock.server;
import dev.autostock.core.RunOptions;
public final class RuntimeOptions {
 private static final java.util.Map<java.util.UUID,RunOptions> values=new java.util.HashMap<>();
 private static final java.util.Map<java.util.UUID,java.util.UUID> owners=new java.util.HashMap<>();
 public static RunOptions get(java.util.UUID id){return values.getOrDefault(owners.getOrDefault(id,id),RunOptions.defaults());}
 public static boolean quick(java.util.UUID fake,boolean fallback){var owner=owners.getOrDefault(fake,fake);return values.containsKey(owner)?values.get(owner).quickShulker():fallback;}
 public static void set(java.util.UUID owner,RunOptions options){values.put(owner,options);}
 public static void attach(java.util.UUID fake,java.util.UUID owner){owners.put(fake,owner);}
 public static void register(){
  net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(dev.autostock.net.OptionsRequest.ID,dev.autostock.net.OptionsRequest.CODEC);
  net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(dev.autostock.net.OptionsRequest.ID,(request,context)->{var p=context.player();if(!MinecraftCompat.hasPermission(p,2)&&!MinecraftCompat.isHost(context.server(),p))return;try{var options=new com.google.gson.Gson().fromJson(request.json(),RunOptions.class);if(options!=null)values.put(p.getUUID(),options);}catch(RuntimeException e){dev.autostock.AutoStock.LOG.warn("Rejected execution options: {}",e.getMessage());}});
  net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->{values.clear();owners.clear();});
 }
}
