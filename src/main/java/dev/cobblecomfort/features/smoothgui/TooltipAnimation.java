package dev.cobblecomfort.features.smoothgui;

import dev.cobblecomfort.features.hud.anim.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * Tooltips fade in while sliding up a little, follow the mouse with a bit of inertia, and glide to the size and place
 * of the next tooltip instead of popping up anew.
 */
public final class TooltipAnimation {
	/** A pause longer than this between two tooltips makes the next one appear anew. */
	private static final long GAP_NANOS = 120_000_000L;
	private static final float APPEAR_SECONDS = 0.14f;
	private static final float SLIDE = 4f;
	private static final float FOLLOW_SPEED = 28f;
	private static final float MORPH_SPEED = 22f;
	/** The background reaches this far beyond the content. */
	private static final int CLIP_MARGIN = 4;

	private static long lastNanos;
	private static long appearNanos;
	private static float x, y, width, height;
	private static int targetWidth, targetHeight;
	private static float step;
	private static boolean fresh;
	private static float savedAlpha = 1f;
	private static boolean clipping;

	private TooltipAnimation() {
	}

	private static SmoothGuiConfig config() {
		return SmoothGuiFeature.INSTANCE.config();
	}

	private static boolean active() {
		SmoothGuiConfig c = config();
		return SmoothGuiFeature.INSTANCE.isEnabled() && (c.tooltipFade || c.tooltipFollow || c.tooltipMorph);
	}

	/** Start of a tooltip: whether it appears anew, and its fade. */
	public static void begin() {
		savedAlpha = ScreenFade.get();
		if (!active()) {
			return;
		}
		long nanos = System.nanoTime();
		fresh = lastNanos == 0 || nanos - lastNanos > GAP_NANOS;
		step = fresh ? 0f : Math.min((nanos - lastNanos) / 1e9f, 0.05f);
		if (fresh) {
			appearNanos = nanos;
		}
		lastNanos = nanos;
		if (config().tooltipFade) {
			ScreenFade.set(savedAlpha * Easing.EASE_OUT_CUBIC.apply(appear(nanos)));
		}
	}

	private static float appear(long nanos) {
		return Math.min(1f, (nanos - appearNanos) / 1e9f / APPEAR_SECONDS);
	}

	/** Where the tooltip is drawn, given where vanilla would put it and how large it is. */
	public static Vector2ic position(Vector2ic target, int w, int h) {
		if (!active()) {
			return target;
		}
		SmoothGuiConfig c = config();
		boolean morphing = c.tooltipMorph && !fresh && (Math.abs(width - w) > 0.5f || Math.abs(height - h) > 0.5f
				|| w != targetWidth || h != targetHeight);
		if (fresh) {
			x = target.x();
			y = target.y();
			width = w;
			height = h;
		} else {
			if (c.tooltipFollow || morphing) {
				float t = 1f - (float) Math.exp(-step * (c.tooltipFollow ? FOLLOW_SPEED : MORPH_SPEED));
				x += (target.x() - x) * t;
				y += (target.y() - y) * t;
			} else {
				x = target.x();
				y = target.y();
			}
			if (c.tooltipMorph) {
				float t = 1f - (float) Math.exp(-step * MORPH_SPEED);
				width += (w - width) * t;
				height += (h - height) * t;
			} else {
				width = w;
				height = h;
			}
		}
		if (Math.abs(x - target.x()) < 0.5f && Math.abs(y - target.y()) < 0.5f) {
			x = target.x();
			y = target.y();
		}
		if (Math.abs(width - w) < 0.5f && Math.abs(height - h) < 0.5f) {
			width = w;
			height = h;
		}
		targetWidth = w;
		targetHeight = h;
		float slide = c.tooltipFade ? SLIDE * (1f - Easing.EASE_OUT_CUBIC.apply(appear(System.nanoTime()))) : 0f;
		return new Vector2i(Math.round(x), Math.round(y + slide));
	}

	/** The background's size while it glides to the new tooltip's. */
	public static int width(int w) {
		return active() ? Math.round(width) : w;
	}

	public static int height(int h) {
		return active() ? Math.round(height) : h;
	}

	/** After the background: while it is still smaller than the content, the content is cut off at its edge. */
	public static void clip(GuiGraphicsExtractor g, int bx, int by, int w, int h) {
		int aw = width(w);
		int ah = height(h);
		if (active() && (aw < w || ah < h)) {
			g.enableScissor(bx - CLIP_MARGIN, by - CLIP_MARGIN, bx + aw + CLIP_MARGIN, by + ah + CLIP_MARGIN);
			clipping = true;
		}
	}

	public static void end(GuiGraphicsExtractor g) {
		if (clipping) {
			g.disableScissor();
			clipping = false;
		}
		ScreenFade.set(savedAlpha);
	}
}
