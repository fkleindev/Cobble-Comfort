package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.smoothgui.ScrollTicker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves gliding scroll areas on before they are drawn. */
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetScrollMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void cobblecomfort$tickScroll(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if ((Object) this instanceof ScrollTicker ticker) {
			ticker.cobblecomfort$tickScroll();
		}
	}
}
