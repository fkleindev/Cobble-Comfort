package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.cobblecomfort.features.smoothgui.SmoothScroll;
import dev.cobblecomfort.features.smoothgui.WidgetAnim;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** The check mark of vanilla checkboxes fades in and out. */
@Mixin(Checkbox.class)
public abstract class CheckboxAnimMixin {
	@Shadow
	@Final
	private static Identifier CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE;
	@Shadow
	@Final
	private static Identifier CHECKBOX_SELECTED_SPRITE;
	@Shadow
	@Final
	private static Identifier CHECKBOX_HIGHLIGHTED_SPRITE;
	@Shadow
	@Final
	private static Identifier CHECKBOX_SPRITE;
	@Shadow
	private boolean selected;

	@Unique
	private float cobblecomfort$check = -1f;
	@Unique
	private long cobblecomfort$nanos;

	@WrapOperation(method = "extractContents", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"))
	private void cobblecomfort$fadeCheck(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int width,
			int height, int color, Operation<Void> original) {
		long now = System.nanoTime();
		float seconds = SmoothScroll.elapsed(cobblecomfort$nanos, now);
		cobblecomfort$nanos = now;
		if (!WidgetAnim.enabled(WidgetAnim.Kind.CHECKBOXES)) {
			cobblecomfort$check = selected ? 1f : 0f;
			original.call(g, pipeline, sprite, x, y, width, height, color);
			return;
		}
		boolean focused = sprite.equals(CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE) || sprite.equals(CHECKBOX_HIGHLIGHTED_SPRITE);
		cobblecomfort$check = WidgetAnim.step(cobblecomfort$check, selected ? 1f : 0f, seconds);
		original.call(g, pipeline, focused ? CHECKBOX_HIGHLIGHTED_SPRITE : CHECKBOX_SPRITE, x, y, width, height, color);
		if (cobblecomfort$check > 0.01f) {
			original.call(g, pipeline, focused ? CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE : CHECKBOX_SELECTED_SPRITE, x, y, width, height,
					ARGB.multiplyAlpha(color, cobblecomfort$check));
		}
	}
}
