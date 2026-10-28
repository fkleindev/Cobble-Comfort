package dev.cobblecomfort.features.smoothgui;

import dev.cobblecomfort.features.hud.anim.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.List;

/** Recipe book pages slide in from the side they were turned to and fade in. */
public final class RecipePageSlide {
	private static final float SECONDS = 0.2f;
	private static final float DISTANCE = 24f;

	private RecipePageSlide() {
	}

	/** 0 to 1 through the slide that started at {@code startNanos}. */
	public static float progress(long startNanos) {
		float t = (System.nanoTime() - startNanos) / 1e9f / (SECONDS / Math.max(0.05f, SmoothGuiFeature.INSTANCE.config().scrollSpeed));
		return Math.clamp(t, 0f, 1f);
	}

	/** Draws a recipe button shifted and faded, cut off at the edge of the page's buttons. */
	public static void button(GuiGraphicsExtractor g, List<? extends AbstractWidget> buttons, int direction, float t, Runnable draw) {
		if (direction == 0 || t >= 1f || !SmoothScroll.enabled(SmoothScroll.Kind.RECIPES) || buttons.isEmpty()) {
			draw.run();
			return;
		}
		int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
		for (AbstractWidget button : buttons) {
			left = Math.min(left, button.getX());
			top = Math.min(top, button.getY());
			right = Math.max(right, button.getRight());
			bottom = Math.max(bottom, button.getBottom());
		}
		float eased = Easing.EASE_OUT_CUBIC.apply(t);
		float saved = ScreenFade.get();
		ScreenFade.set(saved * eased);
		g.enableScissor(left, top, right, bottom);
		g.pose().pushMatrix();
		g.pose().translate(direction * DISTANCE * (1f - eased), 0);
		draw.run();
		g.pose().popMatrix();
		g.disableScissor();
		ScreenFade.set(saved);
	}
}
