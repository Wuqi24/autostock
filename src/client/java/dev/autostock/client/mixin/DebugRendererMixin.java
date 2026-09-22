package dev.autostock.client.mixin;

import dev.autostock.client.ContainerOverlay;
import net.minecraft.client.render.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if >1.21.8 {
//? if <=1.21.10 {
/*import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}
//?}

@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {
    //? if >1.21.8 {
    //? if <=1.21.10 {
    /*@Inject(method = "render", at = @At("TAIL"))
    private void autostock$render(MatrixStack matrices, Frustum frustum,
                                  VertexConsumerProvider.Immediate consumers,
                                  double cameraX, double cameraY, double cameraZ,
                                  boolean late, CallbackInfo ci) {
        if (late) ContainerOverlay.render(matrices, consumers, new Vec3d(cameraX, cameraY, cameraZ));
    }
    *///?}
    //?}
}
