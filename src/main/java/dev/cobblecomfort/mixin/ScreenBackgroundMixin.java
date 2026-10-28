package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.smoothgui.SmoothGuiFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth GUI: the full-screen background is not scaled with the screen and fades in on its own. */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	@Inject(method = {"extractTransparentBackground", "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"},
			at = @At("HEAD"))
	private void cobblecomfort$beginBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
		SmoothGuiFeature.INSTANCE.beginBackground(g);
	}

	@Inject(method = {"extractTransparentBackground", "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"},
			at = @At("RETURN"))
	private void cobblecomfort$endBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
		SmoothGuiFeature.INSTANCE.endBackground(g);
	}
}
