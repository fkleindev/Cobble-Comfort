package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.plush.PlushFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Punchy draws first-person items in its own outstretched arm, resolving them with the third-person transform. While
 * it does, the totem plush is turned upright. Skipped when Punchy is missing or changes this method.
 */
@Pseudo
@Mixin(targets = "punchy.client.render.PunchyArmRenderer")
public abstract class PunchyArmRendererMixin {
	@Inject(method = "submitVanillaItem", at = @At("HEAD"), require = 0)
	private static void cobblecomfort$enterArm(CallbackInfoReturnable<?> cir) {
		PlushFeature.INSTANCE.setInPunchyArm(true);
	}

	@Inject(method = "submitVanillaItem", at = @At("RETURN"), require = 0)
	private static void cobblecomfort$leaveArm(CallbackInfoReturnable<?> cir) {
		PlushFeature.INSTANCE.setInPunchyArm(false);
	}
}
