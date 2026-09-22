package dev.autostock.client.mixin;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=MouseHandler.class,priority=1100)
public abstract class MouseMixin {
    @Inject(method="onButton",at=@At("HEAD"),cancellable=true)
    private void autostock$button(long window,MouseButtonInfo buttonInfo,int action,CallbackInfo ci){
        if(dev.autostock.client.ClientKeys.onKey(window,buttonInfo.button()-100,action,buttonInfo.modifiers()))ci.cancel();
    }
    @Inject(method="onScroll",at=@At("HEAD"),cancellable=true)
    private void autostock$scroll(long window,double horizontal,double vertical,CallbackInfo ci) {
        if(window==net.minecraft.client.Minecraft.getInstance().getWindow().handle()&&dev.autostock.client.RegionOverlayInput.scroll(vertical))ci.cancel();
    }
}
