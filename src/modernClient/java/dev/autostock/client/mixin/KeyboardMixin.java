package dev.autostock.client.mixin;

import dev.autostock.client.ClientKeys;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyboardHandler.class, priority = 1100)
public abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void autostock$key(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (ClientKeys.onKey(window, event.key(), action, event.modifiers())) ci.cancel();
    }
}
