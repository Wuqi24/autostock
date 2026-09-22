package dev.autostock.client;

//? if <=1.21.8 {
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
//?}
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public final class ContainerOverlay {
    private static ClientDraft active;

    static void register(ClientDraft draft) {
        active = draft;
        //? if <=1.21.8 {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> render(context.matrixStack(), context.consumers(), context.camera().getPos()));
        //?}
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d camera) {
            var draft = active;
            if (draft == null) return;
            var report = draft.report();
            var world = MinecraftClient.getInstance().world;
            if (world == null || matrices == null || consumers == null) return;
            var buffer = consumers.getBuffer(
                    //? if <=1.21.10 {
                    RenderLayer.getLines()
                    //?} else {
                    /*net.minecraft.client.render.RenderLayers.lines()
                    *///?}
            );
            if (ClientSettings.get().showRegions) {
                for (var entry : draft.regions().entrySet()) for (var box : entry.getValue()) {
                    int color = ClientSettings.get().colors[4];
                    RenderCompat.drawBox(matrices, buffer, box.minX() - camera.x, box.minY() - camera.y, box.minZ() - camera.z,
                            box.maxX() + 1 - camera.x, box.maxY() + 1 - camera.y, box.maxZ() + 1 - camera.z,
                            ((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F, .65F);
                    color=RegionSetupScreen.color(entry.getKey());
                    // Short corner accents make region extents readable without filling the world.
                    for(int cx:new int[]{box.minX(),box.maxX()+1})for(int cy:new int[]{box.minY(),box.maxY()+1})for(int cz:new int[]{box.minZ(),box.maxZ()+1}) {
                        var corner=new org.joml.Vector3f((float)(cx-camera.x),(float)(cy-camera.y),(float)(cz-camera.z));
                        RenderCompat.drawVector(matrices,buffer,corner,new net.minecraft.util.math.Vec3d(cx==box.minX()?.28:-.28,0,0),color);
                        RenderCompat.drawVector(matrices,buffer,corner,new net.minecraft.util.math.Vec3d(0,cy==box.minY()?.28:-.28,0),color);
                        RenderCompat.drawVector(matrices,buffer,corner,new net.minecraft.util.math.Vec3d(0,0,cz==box.minZ()?.28:-.28),color);
                    }
                }
                for (var a : draft.regions().getOrDefault(dev.autostock.core.RegionRole.EMPTY_BOX, java.util.List.of()))
                    for (var b : draft.regions().getOrDefault(dev.autostock.core.RegionRole.OUTPUT, java.util.List.of())) if (a.overlaps(b)) {
                        RenderCompat.drawBox(matrices, buffer,
                                Math.max(a.minX(), b.minX()) - camera.x - .01, Math.max(a.minY(), b.minY()) - camera.y - .01, Math.max(a.minZ(), b.minZ()) - camera.z - .01,
                                Math.min(a.maxX(), b.maxX()) + 1 - camera.x + .01, Math.min(a.maxY(), b.maxY()) + 1 - camera.y + .01, Math.min(a.maxZ(), b.maxZ()) + 1 - camera.z + .01,
                                .9F, .2F, .6F, System.currentTimeMillis() / 650 % 2 == 0 ? 1F : .65F);
                        float x1 = (float) (Math.max(a.minX(), b.minX()) - camera.x), y1 = (float) (Math.max(a.minY(), b.minY()) - camera.y), z1 = (float) (Math.max(a.minZ(), b.minZ()) - camera.z - .012);
                        float x2 = (float) (Math.min(a.maxX(), b.maxX()) + 1 - camera.x), y2 = (float) (Math.min(a.maxY(), b.maxY()) + 1 - camera.y);
                        RenderCompat.drawVector(matrices, buffer, new org.joml.Vector3f(x1,y1,z1), new net.minecraft.util.math.Vec3d(x2-x1,y2-y1,0), 0xFFE63399);
                        RenderCompat.drawVector(matrices, buffer, new org.joml.Vector3f(x1,y2,z1), new net.minecraft.util.math.Vec3d(x2-x1,y1-y2,0), 0xFFE63399);
                    }
            }
            var task=draft.taskStatus();
            if(task!=null && task.state().equals("RUNNING")) {
                if(ClientSettings.get().showPaths)for(int i=1;i<task.path().size();i++) {
                    var a=net.minecraft.util.math.Vec3d.ofBottomCenter(task.path().get(i-1)).add(0,.08,0);
                    var b=net.minecraft.util.math.Vec3d.ofBottomCenter(task.path().get(i)).add(0,.08,0);
                    RenderCompat.drawVector(matrices,buffer,new org.joml.Vector3f((float)(a.x-camera.x),(float)(a.y-camera.y),(float)(a.z-camera.z)),b.subtract(a),ClientSettings.get().colors[3]);
                    if(i%4==1) {
                        var direction=b.subtract(a).normalize();var side=new net.minecraft.util.math.Vec3d(-direction.z,0,direction.x).multiply(.13);var tip=a.lerp(b,.7);
                        var point=new org.joml.Vector3f((float)(tip.x-camera.x),(float)(tip.y-camera.y),(float)(tip.z-camera.z));
                        RenderCompat.drawVector(matrices,buffer,point,direction.multiply(-.23).add(side),ClientSettings.get().colors[3]);
                        RenderCompat.drawVector(matrices,buffer,point,direction.multiply(-.23).subtract(side),ClientSettings.get().colors[3]);
                    }
                }
                if(ClientSettings.get().showTarget && task.target()!=null) {
                    var p=task.target();double x=p.getX()-camera.x,y=p.getY()-camera.y,z=p.getZ()-camera.z;
                    RenderCompat.drawBox(matrices,buffer,x-.015,y-.015,z-.015,x+1.015,y+1.015,z+1.015,1F,.75F,.2F,1F);
                }
                if(ClientSettings.get().showPoints && !task.path().isEmpty()) {
                    var p=task.path().getLast();double x=p.getX()+.5-camera.x,y=p.getY()+.05-camera.y,z=p.getZ()+.5-camera.z;
                    RenderCompat.drawBox(matrices,buffer,x-.15,y,z-.15,x+.15,y+.1,z+.15,.3F,1F,.4F,1F);
                }
            }
            if (!ClientSettings.get().showContainers) return;
            for (var mark : draft.failureMarks()) {
                double x = mark.x() - camera.x + .5, y = mark.y() - camera.y + 1.1, z = mark.z() - camera.z + .5;
                if (x * x + y * y + z * z < 128 * 128) RenderCompat.drawBox(matrices, buffer,
                        x - .08, y - .08, z - .08, x + .08, y + .08, z + .08, .94F, .75F, .25F, 1F);
            }
            if (report == null || !world.getRegistryKey().getValue().toString().equals(report.dimension())) return;
            for (var mark : report.marks()) {
                double x = mark.x() - camera.x, y = mark.y() - camera.y, z = mark.z() - camera.z;
                if (x * x + y * y + z * z > 128 * 128) continue;
                float r = mark.role() == dev.autostock.core.RegionRole.EMPTY_BOX ? 0.75F : 0.25F;
                float g = switch (mark.role()) { case MATERIAL -> 0.55F; case EMPTY_BOX -> 0.35F; case OUTPUT -> 0.95F; };
                if(ClientSettings.get().highlightShortage&&mark.role()==dev.autostock.core.RegionRole.MATERIAL&&mark.shortage()){r=((ClientSettings.get().colors[5]>>16)&255)/255F;g=((ClientSettings.get().colors[5]>>8)&255)/255F;}
                float b = mark.role() == dev.autostock.core.RegionRole.OUTPUT ? 0.45F : 1F;
                if(ClientSettings.get().highlightShortage&&mark.shortage())b=(ClientSettings.get().colors[5]&255)/255F;
                RenderCompat.drawBox(matrices, buffer, x - 0.002, y - 0.002, z - 0.002,
                        x + 1.002, y + 1.002, z + 1.002, r, g, b, 1F);
            }
    }
}


