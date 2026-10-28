package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.smoothgui.SmoothGuiFeature;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth GUI: notices screen changes and wraps the screen render in the open animation. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	@Inject(method = "setScreen", at = @At("HEAD"))
	private void cobblecomfort$screenChange(Screen screen, CallbackInfo ci) {
		SmoothGuiFeature.INSTANCE.onScreenChange(((Gui) (Object) this).screen(), screen);
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private void cobblecomfort$animateScreen(Screen screen, GuiGraphicsExtractor g, int mouseX, int mouseY, float delta,
			Operation<Void> original) {
		SmoothGuiFeature.INSTANCE.begin(g, screen);
		try {
			original.call(screen, g, mouseX, mouseY, delta);
		} finally {
			SmoothGuiFeature.INSTANCE.end(g);
		}
	}
}
