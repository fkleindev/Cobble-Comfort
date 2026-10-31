package dev.cobblecomfort.mixin;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.cobblecomfort.features.fullbright.FullbrightFeature;
import dev.cobblecomfort.features.namecharm.NameCharmFeature;
import dev.cobblecomfort.features.zoom.ZoomFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$hideHandWhileZooming(CameraRenderState camera, PlayerRenderState player,
			GpuTextureView target, CallbackInfo ci) {
		if (ZoomFeature.INSTANCE.hideHand()) {
			ci.cancel();
		}
	}

	/** After the zoom check: if that cancels, the hands aren't drawn and the flag isn't set. */
	@Inject(method = "renderItemInHand", at = @At("HEAD"))
	private void cobblecomfort$enterHands(CameraRenderState camera, PlayerRenderState player, GpuTextureView target,
			CallbackInfo ci) {
		NameCharmFeature.INSTANCE.setFirstPersonPass(true);
	}

	@Inject(method = "renderItemInHand", at = @At("RETURN"))
	private void cobblecomfort$leaveHands(CameraRenderState camera, PlayerRenderState player, GpuTextureView target,
			CallbackInfo ci) {
		NameCharmFeature.INSTANCE.setFirstPersonPass(false);
	}

	/** Strength and fade of the faked night vision (the fake effect itself never runs out). */
	@Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true)
	private static void cobblecomfort$fullbrightNightVision(LivingEntity entity, float partialTick,
			CallbackInfoReturnable<Float> cir) {
		if (entity == Minecraft.getInstance().player
				&& FullbrightFeature.INSTANCE.fakeNightVision() != null
				&& !entity.getActiveEffectsMap().containsKey(MobEffects.NIGHT_VISION)) {
			cir.setReturnValue(FullbrightFeature.INSTANCE.intensity());
		}
	}
}
