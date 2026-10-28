package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.zoom.ZoomFeature;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
	/** Zoom narrows the world FOV; the hand keeps its own FOV. Runs once per frame. */
	@Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
	private void cobblecomfort$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		float zoom = ZoomFeature.INSTANCE.update();
		if (zoom != 1f) {
			cir.setReturnValue(cir.getReturnValueF() / zoom);
		}
	}
}
