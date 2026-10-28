package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.zoom.ZoomFeature;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	/** Scrolling while zoomed changes the zoom instead of the hotbar slot. */
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (ZoomFeature.INSTANCE.onScroll(yOffset)) {
			ci.cancel();
		}
	}

	/** Local 11 is the final sensitivity multiplier (sensitivity curve * 8) applied to mouse movement. */
	@ModifyVariable(method = "turnPlayer", at = @At("STORE"), index = 11)
	private double cobblecomfort$zoomSensitivity(double multiplier) {
		return multiplier * ZoomFeature.INSTANCE.sensitivityMultiplier();
	}
}
