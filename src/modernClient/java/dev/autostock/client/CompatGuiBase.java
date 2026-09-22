package dev.autostock.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.gui.Font;

/** Keeps MaLiLib's rendering context migration out of every screen implementation. */
abstract class CompatGuiBase extends GuiBase {
    protected final Font textRenderer = font;
    protected void drawScreenBackgroundCompat(GuiContext context, int mouseX, int mouseY) { }
    protected void drawContentsCompat(GuiContext context, int mouseX, int mouseY, float delta) { }

    //? if <=1.21.10 {
    /*@Override
    protected final void drawScreenBackground(DrawContext context, int mouseX, int mouseY) {
        drawScreenBackgroundCompat(context, mouseX, mouseY);
    }

    @Override
    protected final void drawContents(DrawContext context, int mouseX, int mouseY, float delta) {
        drawContentsCompat(context, mouseX, mouseY, delta);
    }
    *///?} else {
    @Override
    protected final void drawScreenBackground(fi.dy.masa.malilib.render.GuiContext context, int mouseX, int mouseY) {
        drawScreenBackgroundCompat(context, mouseX, mouseY);
    }

    @Override
    protected final void drawContents(fi.dy.masa.malilib.render.GuiContext context, int mouseX, int mouseY, float delta) {
        drawContentsCompat(context, mouseX, mouseY, delta);
    }
    //?}
}
