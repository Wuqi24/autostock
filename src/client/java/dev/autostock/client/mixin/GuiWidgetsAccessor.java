package dev.autostock.client.mixin;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;
@Mixin(value=GuiBase.class,remap=false)
public interface GuiWidgetsAccessor {
    @Accessor("widgets") List<WidgetBase> autostock$widgets();
}
