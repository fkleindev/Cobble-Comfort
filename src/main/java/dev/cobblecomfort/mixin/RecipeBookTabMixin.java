package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.smoothgui.SmoothScroll;
import dev.cobblecomfort.features.smoothgui.WidgetAnim;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The selected recipe book tab slides out instead of jumping. */
@Mixin(RecipeBookTabButton.class)
public abstract class RecipeBookTabMixin {
	/** Vanilla moves the selected tab this far to the left. */
	@Unique
	private static final float SHIFT = 2f;

	@Shadow
	private boolean selected;

	@Unique
	private float cobblecomfort$lift = -1f;
	@Unique
	private long cobblecomfort$nanos;

	@Inject(method = "extractContents", at = @At("HEAD"))
	private void cobblecomfort$beginSlide(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		long now = System.nanoTime();
		float seconds = SmoothScroll.elapsed(cobblecomfort$nanos, now);
		cobblecomfort$nanos = now;
		float target = selected ? 1f : 0f;
		cobblecomfort$lift = WidgetAnim.enabled(WidgetAnim.Kind.TABS) ? WidgetAnim.step(cobblecomfort$lift, target, seconds) : target;
		g.pose().pushMatrix();
		// Undo vanilla's jump and move by the gliding amount instead.
		g.pose().translate((selected ? SHIFT : 0f) - SHIFT * cobblecomfort$lift, 0f);
	}

	@Inject(method = "extractContents", at = @At("RETURN"))
	private void cobblecomfort$endSlide(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		g.pose().popMatrix();
	}
}
