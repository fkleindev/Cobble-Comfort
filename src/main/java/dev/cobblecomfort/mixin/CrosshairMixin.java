package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.cobblecomfort.features.crosshair.CrosshairFeature;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hit marker and attack cooldown ring around the crosshair. */
@Mixin(Hud.class)
public abstract class CrosshairMixin {
	@Shadow
	@Final
	private static Identifier CROSSHAIR_SPRITE;
	@Shadow
	@Final
	private static Identifier CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE;
	@Shadow
	@Final
	private static Identifier CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE;

	@Inject(method = "extractCrosshair", at = @At("HEAD"))
	private void cobblecomfort$beginCrosshair(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		CrosshairFeature.INSTANCE.beginCrosshair();
	}

	@WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void cobblecomfort$crosshairSprite(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int width,
			int height, Operation<Void> original) {
		CrosshairFeature crosshair = CrosshairFeature.INSTANCE;
		if (sprite.equals(CROSSHAIR_SPRITE)) {
			crosshair.crosshairDrawn();
		} else if (sprite.equals(CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE) && crosshair.replaceIndicator(true)
				|| sprite.equals(CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE) && crosshair.replaceIndicator(false)) {
			return;
		}
		original.call(g, pipeline, sprite, x, y, width, height);
	}

	/** The filling part of vanilla's indicator. */
	@WrapOperation(method = "extractCrosshair", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"))
	private void cobblecomfort$indicatorProgress(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int textureWidth,
			int textureHeight, int u, int v, int x, int y, int width, int height, Operation<Void> original) {
		if (!CrosshairFeature.INSTANCE.replaceIndicator(false)) {
			original.call(g, pipeline, sprite, textureWidth, textureHeight, u, v, x, y, width, height);
		}
	}

	@Inject(method = "extractCrosshair", at = @At("RETURN"))
	private void cobblecomfort$endCrosshair(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		CrosshairFeature.INSTANCE.endCrosshair(g);
	}
}
