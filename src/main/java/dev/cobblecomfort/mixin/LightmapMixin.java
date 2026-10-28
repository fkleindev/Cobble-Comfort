package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.fullbright.FullbrightFeature;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
	@Shadow
	private boolean needsUpdate;

	/** Recalculate the lightmap every frame while fullbright is on or fading. */
	@Inject(method = "extract", at = @At("HEAD"))
	private void cobblecomfort$forceUpdate(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		if (FullbrightFeature.INSTANCE.needsLightmapUpdate()) {
			needsUpdate = true;
		}
	}

	/** The lightmap's night vision term brightens every light level without touching sky or fog colours. */
	@Inject(method = "extract", at = @At("RETURN"))
	private void cobblecomfort$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		float intensity = FullbrightFeature.INSTANCE.updateIntensity();
		if (intensity > 0f && !FullbrightFeature.INSTANCE.usesNightVision()) {
			state.nightVisionEffectIntensity = Math.max(state.nightVisionEffectIntensity, intensity);
		}
	}
}
