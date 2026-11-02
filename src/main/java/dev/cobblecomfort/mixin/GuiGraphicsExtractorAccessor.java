package dev.cobblecomfort.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The GUI render state, for submitting picture-in-picture elements of our own (the 3D map). */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {
	@Accessor("guiRenderState")
	GuiRenderState cobblecomfort$guiRenderState();
}
