package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.smoothgui.SmoothGuiFeature;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Smooth GUI: the menu background blur ramps up while a screen opens. */
@Mixin(Options.class)
public abstract class OptionsMixin {
	@Inject(method = "getMenuBackgroundBlurriness", at = @At("RETURN"), cancellable = true)
	private void cobblecomfort$fadeBlur(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(SmoothGuiFeature.INSTANCE.blurriness(cir.getReturnValueI()));
	}
}
