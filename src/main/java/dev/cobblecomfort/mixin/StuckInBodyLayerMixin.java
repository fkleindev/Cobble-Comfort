package dev.cobblecomfort.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.features.stuckarrows.StuckArrowsFeature;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets arrows and bee stingers stuck in a player wobble (see {@link StuckArrowsFeature}). */
@Mixin(StuckInBodyLayer.class)
public abstract class StuckInBodyLayerMixin {
	@Unique
	private StuckArrowsFeature.Wobble cobblecomfort$wobble;
	@Unique
	private int cobblecomfort$index;

	@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
			at = @At("HEAD"))
	private void cobblecomfort$begin(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
		cobblecomfort$wobble = StuckArrowsFeature.INSTANCE.wobble(state, (Object) this instanceof BeeStingerLayer);
		cobblecomfort$index = 0;
	}

	@Inject(method = "submitStuckItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;III)V"))
	private void cobblecomfort$wobble(PoseStack pose, SubmitNodeCollector collector, int light, float x, float y, float z, int outline, CallbackInfo ci) {
		if (cobblecomfort$wobble != null) {
			StuckArrowsFeature.tilt(pose, cobblecomfort$wobble, cobblecomfort$index++);
		}
	}
}
