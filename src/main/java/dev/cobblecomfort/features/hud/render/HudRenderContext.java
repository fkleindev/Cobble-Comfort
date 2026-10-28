package dev.cobblecomfort.features.hud.render;

import dev.cobblecomfort.features.hud.TextShadow;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

/**
 * Per-element render settings consulted by {@code GuiGraphicsExtractorMixin} while a wrapped HUD
 * element draws. Only touched on the render thread, and only between {@link #begin} and {@link #end}.
 */
public final class HudRenderContext {
	private static boolean active;
	private static float alpha;
	private static float tintR, tintG, tintB;
	private static TextShadow shadow;
	private static boolean suppressItems;
	private static ElementState capture;
	private static final Matrix3x2f INVERSE = new Matrix3x2f();
	private static final Matrix3x2f TMP = new Matrix3x2f();
	private static final Vector2f POINT = new Vector2f();

	private HudRenderContext() {
	}

	/**
	 * @param poseBefore the pose before the element's own transform was applied
	 * @param poseAfter  the pose after it; captured coordinates are mapped back through
	 *                   poseBefore * inverse(poseAfter) so they are independent of our transform
	 */
	public static void begin(ElementState state, Matrix3x2fc poseBefore, Matrix3x2fc poseAfter,
			float alpha, int tint, TextShadow shadow, boolean suppressItems) {
		active = true;
		capture = state;
		HudRenderContext.alpha = alpha;
		tintR = ((tint >> 16) & 0xFF) / 255f;
		tintG = ((tint >> 8) & 0xFF) / 255f;
		tintB = (tint & 0xFF) / 255f;
		HudRenderContext.shadow = shadow;
		HudRenderContext.suppressItems = suppressItems;
		poseAfter.invert(INVERSE);
		poseBefore.mul(INVERSE, INVERSE);
	}

	public static void end() {
		active = false;
		capture = null;
	}

	public static boolean active() {
		return active;
	}

	public static boolean suppressItems() {
		return active && suppressItems;
	}

	public static int modulate(int argb) {
		if (!active) {
			return argb;
		}
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		int r = Math.round(((argb >> 16) & 0xFF) * tintR);
		int g = Math.round(((argb >> 8) & 0xFF) * tintG);
		int b = Math.round((argb & 0xFF) * tintB);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static boolean shadow(boolean vanilla) {
		if (!active) {
			return vanilla;
		}
		return switch (shadow) {
			case VANILLA -> vanilla;
			case ON -> true;
			case OFF -> false;
		};
	}

	/** Records a drawn rectangle (in the current pose) for bounds and change detection. */
	public static void capture(Matrix3x2fc pose, float x0, float y0, float x1, float y1, long contentHash) {
		if (!active) {
			return;
		}
		INVERSE.mul(pose, TMP);
		TMP.transformPosition(x0, y0, POINT);
		float ax = POINT.x, ay = POINT.y;
		TMP.transformPosition(x1, y1, POINT);
		capture.extend(ax, ay, POINT.x, POINT.y);
		capture.mix(Float.floatToIntBits(x0) * 31L + Float.floatToIntBits(y0));
		capture.mix(Float.floatToIntBits(x1) * 31L + Float.floatToIntBits(y1));
		capture.mix(contentHash);
	}
}
