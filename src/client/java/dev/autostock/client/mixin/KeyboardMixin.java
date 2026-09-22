package dev.autostock.client.mixin;

import dev.autostock.client.ClientKeys;
import net.minecraft.client.Keyboard;
//? if >1.21.8 {
/*import net.minecraft.client.input.KeyInput;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Keyboard.class, priority = 1100)
public abstract class KeyboardMixin {
    //? if <=1.21.8 {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void autostock$key(long window, int key, int scanCode, int action, int mods, CallbackInfo ci) {
        if (ClientKeys.onKey(window, key, action, mods)) ci.cancel();
    }
    //?} else {
    /*@Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void autostock$key(long window, int ignored, KeyInput input, CallbackInfo ci) {
        if (ClientKeys.onKey(window, input.key(), ignored, input.modifiers())) ci.cancel();
    }
    *///?}
}
