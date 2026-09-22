package dev.autostock.client.mixin;

import fi.dy.masa.litematica.gui.GuiMaterialList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=GuiMaterialList.class,remap=false)
public abstract class MaterialListButtonMixin {
    @Inject(method="getBrowserHeight",at=@At("RETURN"),cancellable=true)
    private void autostock$room(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> info){info.setReturnValue(Math.max(20,info.getReturnValue()-14));}
    @Inject(method="initGui",at=@At("TAIL"))
    private void autostock$button(CallbackInfo info){
        dev.autostock.client.LitematicaResult.addStockButton((GuiMaterialList)(Object)this);
    }
}

