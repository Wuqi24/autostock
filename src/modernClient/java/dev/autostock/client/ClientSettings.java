package dev.autostock.client;

import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;

final class ClientSettings {
    static final int[] DEFAULT_COLORS={0xFF6C9EFF,0xFFA78BFA,0xFF4ADE80,0xFFF09040,0xFFFFFFFF,0xFFF87171};
    enum Action {
        CONFIG("配置界面", GLFW.GLFW_KEY_P, 0), REGION("区域窗口", GLFW.GLFW_KEY_N, 0),
        SAVE("保存选区", GLFW.GLFW_KEY_R, 0), CYCLE("切换区域（滚轮）", -1, GLFW.GLFW_MOD_ALT),
        TOGGLE("启动 / 暂停", GLFW.GLFW_KEY_R, GLFW.GLFW_MOD_CONTROL),
        CANCEL("取消任务", GLFW.GLFW_KEY_R, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT),
        BOUNDARY("区域边界", GLFW.GLFW_KEY_1, GLFW.GLFW_MOD_CONTROL),
        CONTAINERS("容器标记", GLFW.GLFW_KEY_2, GLFW.GLFW_MOD_CONTROL),
        PATH("计划路径", GLFW.GLFW_KEY_3, GLFW.GLFW_MOD_CONTROL),
        TARGET("当前目标", GLFW.GLFW_KEY_4, GLFW.GLFW_MOD_CONTROL),
        POINTS("交互点", GLFW.GLFW_KEY_5, GLFW.GLFW_MOD_CONTROL);
        final String label; final int key, mods;
        Action(String label, int key, int mods) { this.label = label; this.key = key; this.mods = mods; }
    }
    record Binding(int key,int mods,java.util.List<Integer> keys) {
        Binding(int key,int mods){this(key,mods,null);}
        java.util.List<Integer> codes(){
            if(keys!=null)return keys;
            var result=new java.util.ArrayList<Integer>();
            if((mods&2)!=0)result.add(GLFW.GLFW_KEY_LEFT_CONTROL);
            if((mods&4)!=0)result.add(GLFW.GLFW_KEY_LEFT_ALT);
            if((mods&1)!=0)result.add(GLFW.GLFW_KEY_LEFT_SHIFT);
            if(key!=-2)result.add(key);return result;
        }
        String label(){return codes().isEmpty()?"NONE":codes().stream().map(code->code==-1?"滚轮":fi.dy.masa.malilib.hotkeys.KeybindMulti.getStorageStringForKeyCode(code)).collect(java.util.stream.Collectors.joining(" + "));}
        boolean matches(int code,int modifiers){
            if(keys==null)return key!=-2&&key==code&&mods==(modifiers&7);
            if(keys.isEmpty()||!keys.contains(code)||mods!=(modifiers&7))return false;
            var window=net.minecraft.client.Minecraft.getInstance().getWindow();
            long handle=window.handle();
            for(int value:keys)if(value!=code&&value!=-1&&!(value<0?GLFW.glfwGetMouseButton(handle,value+100)==GLFW.GLFW_PRESS:
                    //? if <=1.21.8 {
                    /*net.minecraft.client.util.InputUtil.isKeyPressed(handle,value)
                    *///?} else {
                    com.mojang.blaze3d.platform.InputConstants.isKeyDown(window,value)
                    //?}
            ))return false;
            return true;
        }
        boolean valid(Action action){
            if(keys==null)return mods>=0&&mods<=7&&key>=-1&&key<=GLFW.GLFW_KEY_LAST&&(action==Action.CYCLE)==(key==-1);
            return keys.size()<=16&&keys.stream().distinct().count()==keys.size()&&keys.stream().allMatch(k->k!=null&&(k>=32&&k<=GLFW.GLFW_KEY_LAST||k>=-100&&k<=-93||k==-1&&action==Action.CYCLE))&&(keys.isEmpty()||action!=Action.CYCLE||keys.contains(-1));
        }
    }
    String completionMessage="狗修金撒码，材料准备好了";
    String notificationPrefix="[自动备货] ";
    boolean notificationSound=false;
    boolean quickShulker = true;
    boolean notifyComplete=true,notifyShortage=true,autoPath=true,dodgeMobs=false,silent=false,loop=false,autoTrigger=false,highlightShortage=true,detailVisible=true,recallVisible=true;
    int scanRange=32,pickupInterval=10,moveSpeed=100;
    int[] hudOpacity={62,62},hudBg={0xFF14161E,0xFF14161E},hudText={0xFFD8D8DC,0xFFD8D8DC},hudAccent={0xFF7BA4F4,0xFF4ADE80};
    int regionPanelWidth = 180;
    float hudScale = .65F; // Legacy configuration migration.
    float[] hudScales;
    boolean showRegionHud=true,showTaskHud=true;
    float[] hudPositions={-1,-1,-1,-1};
    int[] colors=DEFAULT_COLORS.clone();
    boolean showRegions = true, showContainers = true, showPaths = true, showTarget = true, showPoints = true;
    Map<Action, Binding> bindings = new EnumMap<>(Action.class);
    private static ClientSettings instance;
    static String snapshot(){return new GsonBuilder().create().toJson(get());}
    static void restore(String snapshot){var restored=new GsonBuilder().create().fromJson(snapshot,ClientSettings.class);validateImport(restored);restored.save();instance=restored;}
    static void resetDefaults(){var defaults=new ClientSettings();defaults.hudScales=new float[]{.65F,.65F};for(var action:Action.values())defaults.bindings.put(action,new Binding(action.key,action.mods));defaults.save();instance=defaults;}
    static ClientSettings get() {
        if (instance == null) {
            try { instance = new GsonBuilder().create().fromJson(Files.readString(path()), ClientSettings.class); }
            catch (Exception ignored) { }
            if (instance == null) instance = new ClientSettings();
            if(!Float.isFinite(instance.hudScale)||instance.hudScale<.5F||instance.hudScale>1F)instance.hudScale=.65F;
            if(instance.hudScales==null||instance.hudScales.length!=2)instance.hudScales=new float[]{instance.hudScale,instance.hudScale};
            for(int i=0;i<2;i++)if(!Float.isFinite(instance.hudScales[i])||instance.hudScales[i]<.5F||instance.hudScales[i]>1.5F)instance.hudScales[i]=.65F;
            if(instance.completionMessage==null||instance.completionMessage.isBlank()||instance.completionMessage.length()>128)instance.completionMessage="狗修金撒码，材料准备好了";
            if(instance.notificationPrefix==null||instance.notificationPrefix.length()>32)instance.notificationPrefix="[自动备货] ";
            if(instance.hudPositions==null||instance.hudPositions.length!=4)instance.hudPositions=new float[]{-1,-1,-1,-1};
            for(int i=0;i<4;i++)if(!Float.isFinite(instance.hudPositions[i])||instance.hudPositions[i]<-1||instance.hudPositions[i]>1)instance.hudPositions[i]=-1;
            if(instance.colors==null||instance.colors.length!=6)instance.colors=DEFAULT_COLORS.clone();
            if(instance.hudOpacity==null||instance.hudOpacity.length!=2)instance.hudOpacity=new int[]{62,62};
            for(int i=0;i<2;i++)instance.hudOpacity[i]=Math.max(0,Math.min(100,instance.hudOpacity[i]));
            if(instance.hudBg==null||instance.hudBg.length!=2)instance.hudBg=new int[]{0xFF14161E,0xFF14161E};
            if(instance.hudText==null||instance.hudText.length!=2)instance.hudText=new int[]{0xFFD8D8DC,0xFFD8D8DC};
            if(instance.hudAccent==null||instance.hudAccent.length!=2)instance.hudAccent=new int[]{0xFF7BA4F4,0xFF4ADE80};
            instance.scanRange=Math.max(8,Math.min(64,instance.scanRange));instance.pickupInterval=Math.max(1,Math.min(1200,instance.pickupInterval));instance.moveSpeed=Math.max(50,Math.min(200,instance.moveSpeed));
            instance.dodgeMobs=false;
            if(instance.regionPanelWidth<160||instance.regionPanelWidth>240)instance.regionPanelWidth=180;
            if (instance.bindings == null) instance.bindings = new EnumMap<>(Action.class);
            for (var action : Action.values()) {
                var binding = instance.bindings.get(action);
                if (binding == null || !binding.valid(action))
                    instance.bindings.put(action, new Binding(action.key, action.mods));
            }
        }
        return instance;
    }
    Binding binding(Action action) { return bindings.get(action); }
    void bind(Action action,int key,int mods){
        if((action==Action.CYCLE)!=(key==-1))throw new IllegalArgumentException("切换区域使用修饰键 + 滚轮；其他操作使用键盘");
        setBinding(action,new Binding(key,mods&7));
    }
    void bindKeys(Action action,java.util.List<Integer> values){
        var keys=new java.util.ArrayList<>(new java.util.LinkedHashSet<>(values));
        if(action==Action.CYCLE&&!keys.isEmpty()&&!keys.contains(-1))keys.add(-1);
        int mods=0;for(int k:keys){if(k==340||k==344)mods|=1;if(k==341||k==345)mods|=2;if(k==342||k==346)mods|=4;}
        var binding=new Binding(keys.isEmpty()?-2:keys.getLast(),mods,java.util.List.copyOf(keys));
        if(!binding.valid(action))throw new IllegalArgumentException("按键组合无效或超过 16 键");
        setBinding(action,binding);
    }
    private void setBinding(Action action,Binding binding){
        for(var other:Action.values())if(other!=action&&!binding.codes().isEmpty()&&new java.util.HashSet<>(binding.codes()).equals(new java.util.HashSet<>(bindings.get(other).codes())))throw new IllegalArgumentException("该组合已用于："+other.label);
        bindings.put(action,binding);save();
    }
    static java.nio.file.Path transferPath(){return FabricLoader.getInstance().getConfigDir().resolve("autostock-transfer.json");}
    static void exportConfig(){
        try{Files.createDirectories(transferPath().getParent());Files.writeString(transferPath(),new GsonBuilder().setPrettyPrinting().create().toJson(get()));}
        catch(java.io.IOException error){throw new IllegalArgumentException("配置导出失败",error);}
    }
    static void importConfig(){
        final ClientSettings candidate;
        try{
            if(Files.size(transferPath())>65536)throw new IllegalArgumentException("配置文件超过 64 KiB");
            candidate=new GsonBuilder().create().fromJson(Files.readString(transferPath()),ClientSettings.class);
        }catch(java.io.IOException|com.google.gson.JsonParseException error){throw new IllegalArgumentException("无法读取配置，请检查 config/autostock-transfer.json",error);}
        validateImport(candidate);
        candidate.save();instance=candidate;
    }
    static void validateImport(ClientSettings candidate){
        if(candidate==null||!Float.isFinite(candidate.hudScale)||candidate.hudScale<.5F||candidate.hudScale>1F)throw new IllegalArgumentException("HUD 比例须为 0.5 至 1");
        if(candidate.hudScales==null)candidate.hudScales=new float[]{candidate.hudScale,candidate.hudScale};
        if(candidate.hudScales.length!=2)throw new IllegalArgumentException("HUD 缩放格式错误");
        for(float scale:candidate.hudScales)if(!Float.isFinite(scale)||scale<.5F||scale>1.5F)throw new IllegalArgumentException("HUD 缩放须为 0.5 至 1.5");
        new dev.autostock.core.RunOptions(candidate.scanRange,candidate.pickupInterval,candidate.moveSpeed,candidate.autoPath,candidate.dodgeMobs,candidate.silent,candidate.loop,candidate.autoTrigger);
        if(candidate.completionMessage==null||candidate.completionMessage.isBlank()||candidate.completionMessage.length()>128)throw new IllegalArgumentException("完成通知须为 1 至 128 字");
        if(candidate.notificationPrefix==null||candidate.notificationPrefix.length()>32)throw new IllegalArgumentException("通知前缀最多 32 字");
        for(int[] pair:new int[][]{candidate.hudOpacity,candidate.hudBg,candidate.hudText,candidate.hudAccent})if(pair==null||pair.length!=2)throw new IllegalArgumentException("HUD 外观格式错误");
        for(int opacity:candidate.hudOpacity)if(opacity<0||opacity>100)throw new IllegalArgumentException("透明度须为 0–100");
        if(candidate.colors==null||candidate.colors.length!=6||candidate.hudPositions==null||candidate.hudPositions.length!=4)throw new IllegalArgumentException("颜色或悬浮窗位置格式错误");
        for(float value:candidate.hudPositions)if(!Float.isFinite(value)||value< -1||value>1)throw new IllegalArgumentException("悬浮窗位置无效");
        if(candidate.bindings==null)throw new IllegalArgumentException("缺少按键设置");
        var seen=new java.util.HashSet<java.util.Set<Integer>>();
        for(var action:Action.values()){
            var binding=candidate.bindings.get(action);
            if(binding==null||!binding.valid(action)||!binding.codes().isEmpty()&&!seen.add(new java.util.HashSet<>(binding.codes())))throw new IllegalArgumentException("按键设置不完整、冲突或格式错误");
        }
    }
    void sync(){if(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.autostock.net.OptionsRequest.ID))net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.autostock.net.OptionsRequest(new com.google.gson.Gson().toJson(new dev.autostock.core.RunOptions(scanRange,pickupInterval,moveSpeed,autoPath,dodgeMobs,silent,loop,autoTrigger,quickShulker))));}
    void save() {
        try { Files.createDirectories(path().getParent()); var temporary=Files.createTempFile(path().getParent(),"autostock-settings-",".tmp");
            try {Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(this));
                try {Files.move(temporary,path(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
                catch(java.nio.file.AtomicMoveNotSupportedException unsupported){Files.move(temporary,path(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            }finally {Files.deleteIfExists(temporary);} sync(); }
        catch (java.io.IOException error) { throw new IllegalArgumentException("无法保存按键设置", error); }
    }
    private static java.nio.file.Path path() { return FabricLoader.getInstance().getConfigDir().resolve("autostock-client.json"); }
}




