package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.cobblecomfort.features.smoothgui.SmoothScroll;
import dev.cobblecomfort.features.smoothgui.WidgetAnim;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** The highlight of vanilla buttons fades in and out instead of switching. */
@Mixin(AbstractButton.class)
public abstract class ButtonHoverMixin {
	@Shadow
	@Final
	private static WidgetSprites SPRITES;

	@Unique
	private float cobblecomfort$hover = -1f;
	@Unique
	private long cobblecomfort$nanos;

	@WrapOperation(method = "extractDefaultSprite", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"))
	private void cobblecomfort$fadeHover(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int width,
			int height, int color, Operation<Void> original) {
		boolean highlighted = sprite.equals(SPRITES.enabledFocused());
		long now = System.nanoTime();
		float seconds = SmoothScroll.elapsed(cobblecomfort$nanos, now);
		cobblecomfort$nanos = now;
		if (!WidgetAnim.enabled(WidgetAnim.Kind.BUTTONS) || !highlighted && !sprite.equals(SPRITES.enabled())) {
			cobblecomfort$hover = highlighted ? 1f : 0f;
			original.call(g, pipeline, sprite, x, y, width, height, color);
			return;
		}
		cobblecomfort$hover = WidgetAnim.step(cobblecomfort$hover, highlighted ? 1f : 0f, seconds);
		original.call(g, pipeline, SPRITES.enabled(), x, y, width, height, color);
		if (cobblecomfort$hover > 0.01f) {
			original.call(g, pipeline, SPRITES.enabledFocused(), x, y, width, height, ARGB.multiplyAlpha(color, cobblecomfort$hover));
		}
	}
}
