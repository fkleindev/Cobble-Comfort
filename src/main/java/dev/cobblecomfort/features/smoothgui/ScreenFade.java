package dev.cobblecomfort.features.smoothgui;

/**
 * Global alpha for everything a screen draws while it fades in. 1 = no effect.
 * Applied by the GUI draw hooks; items can't be faded, so they are skipped while mostly transparent.
 */
public final class ScreenFade {
	private static final float ITEM_THRESHOLD = 0.5f;
	private static float alpha = 1f;

	private ScreenFade() {
	}

	static void set(float value) {
		alpha = Math.clamp(value, 0f, 1f);
	}

	static float get() {
		return alpha;
	}

	public static int modulate(int argb) {
		if (alpha >= 1f) {
			return argb;
		}
		int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static boolean suppressItems() {
		return alpha < ITEM_THRESHOLD;
	}
}
