package dev.autostock.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Rendering calls that changed between early and later 1.21 releases. */
final class RenderCompat {
    private RenderCompat() { }

    static void drawBox(PoseStack matrices, VertexConsumer vertices,
                        double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                        float red, float green, float blue, float alpha) {
        //? if <=1.21.1 {
        /*WorldRenderer.drawBox(matrices, vertices, minX, minY, minZ, maxX, maxY, maxZ,
                red, green, blue, alpha);
        *///?} else if <=1.21.10 {
        /*VertexRendering.drawBox(
                //? if <=1.21.8 {
                /^matrices,
                ^///?} else {
                matrices.peek(),
                //?}
                vertices, minX, minY, minZ, maxX, maxY, maxZ,
                red, green, blue, alpha);
        *///?} else {
        drawBoxLines(matrices.last(), vertices, minX, minY, minZ, maxX, maxY, maxZ,
                red, green, blue, alpha);
        //?}
    }

    static void drawVector(PoseStack matrices, VertexConsumer vertices, Vector3f start, Vec3 vector, int color) {
        //? if <=1.21.1 {
        /*var entry = matrices.peek();
        float length = (float) vector.length();
        float normalX = length == 0 ? 0 : (float) (vector.x / length);
        float normalY = length == 0 ? 1 : (float) (vector.y / length);
        float normalZ = length == 0 ? 0 : (float) (vector.z / length);
        vertices.vertex(entry, start).color(color).normal(entry, normalX, normalY, normalZ);
        vertices.vertex(entry, start.x + (float) vector.x, start.y + (float) vector.y,
                start.z + (float) vector.z).color(color).normal(entry, normalX, normalY, normalZ);
        *///?} else if <=1.21.10 {
        /*VertexRendering.drawVector(matrices, vertices, start, vector, color);
        *///?} else {
        line(matrices.last(), vertices, start.x, start.y, start.z,
                start.x + vector.x, start.y + vector.y, start.z + vector.z,
                ((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F,
                (color & 255) / 255F, ((color >>> 24) & 255) / 255F);
        //?}
    }

    private static void drawBoxLines(PoseStack.Pose entry, VertexConsumer vertices,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ,
                                     float red, float green, float blue, float alpha) {
        line(entry, vertices, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        line(entry, vertices, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        line(entry, vertices, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        line(entry, vertices, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);
        line(entry, vertices, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(entry, vertices, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(entry, vertices, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        line(entry, vertices, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
        line(entry, vertices, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        line(entry, vertices, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        line(entry, vertices, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        line(entry, vertices, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    private static void line(PoseStack.Pose entry, VertexConsumer vertices,
                             double x1, double y1, double z1, double x2, double y2, double z2,
                             float red, float green, float blue, float alpha) {
        float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = length == 0 ? 0 : dx / length;
        float ny = length == 0 ? 1 : dy / length;
        float nz = length == 0 ? 0 : dz / length;
        vertices.addVertex(entry, (float) x1, (float) y1, (float) z1).setColor(red, green, blue, alpha).setNormal(entry, nx, ny, nz);
        vertices.addVertex(entry, (float) x2, (float) y2, (float) z2).setColor(red, green, blue, alpha).setNormal(entry, nx, ny, nz);
    }
}
