package dev.autostock.client.mixin;
import net.minecraft.client.Mouse;
//? if >1.21.8 {
/*import net.minecraft.client.input.MouseInput;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Mouse.class,priority=1100)
public abstract class MouseMixin {
    //? if <=1.21.8 {
    @Inject(method="onMouseButton",at=@At("HEAD"),cancellable=true)
    private void autostock$button(long window,int button,int action,int mods,CallbackInfo ci){
        if(dev.autostock.client.ClientKeys.onKey(window,button-100,action,mods))ci.cancel();
    }
    //?} else {
    /*@Inject(method="onMouseButton",at=@At("HEAD"),cancellable=true)
    private void autostock$button(long window,MouseInput input,int action,CallbackInfo ci){
        if(dev.autostock.client.ClientKeys.onKey(window,input.button()-100,action,input.modifiers()))ci.cancel();
    }
    *///?}
    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void autostock$scroll(long window,double horizontal,double vertical,CallbackInfo ci) {
        if(window==net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle()&&dev.autostock.client.RegionOverlayInput.scroll(vertical))ci.cancel();
    }
}
