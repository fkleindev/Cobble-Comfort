package dev.cobblecomfort.features.smoothgui;

/**
 * A scroll position that glides to where the mouse wheel sent it. One per list; {@link #update} once per frame before
 * drawing, and draw and hit-test with {@link #value}.
 */
public final class SmoothScroll {
	/** How fast scrolling catches up, per second; about a seventh of a second to get there. */
	private static final double RATE = 16.0;

	public enum Kind {
		LISTS, CHAT, CREATIVE, RECIPES, MOD_SCREENS
	}

	private final Kind kind;
	private double value;
	private double target;
	private long nanos;

	public SmoothScroll(Kind kind) {
		this.kind = kind;
	}

	/** Whether scrolling of this kind is smooth right now. */
	public static boolean enabled(Kind kind) {
		SmoothGuiFeature feature = SmoothGuiFeature.INSTANCE;
		if (!feature.isEnabled()) {
			return false;
		}
		SmoothGuiConfig c = feature.config();
		return switch (kind) {
			case LISTS -> c.scrollLists;
			case CHAT -> c.scrollChat;
			case CREATIVE -> c.scrollCreative;
			case RECIPES -> c.recipePages;
			case MOD_SCREENS -> c.scrollModScreens;
		};
	}

	/** Share of the remaining way covered in {@code seconds}. */
	public static double approach(double seconds) {
		return 1.0 - Math.exp(-seconds * RATE * SmoothGuiFeature.INSTANCE.config().scrollSpeed);
	}

	/** Seconds since {@code last} (0 the first time), at most a twentieth. */
	public static float elapsed(long last, long now) {
		return last == 0 ? 0f : Math.min((now - last) / 1e9f, 0.05f);
	}

	public double value() {
		return value;
	}

	/** Jumps there at once (a reset, a new list). */
	public void set(double position) {
		value = position;
		target = position;
	}

	public void scroll(double delta) {
		target += delta;
		if (!enabled(kind)) {
			value = target;
		}
	}

	/** Keeps both within {@code 0..max} and moves on towards the target. */
	public double update(double max) {
		max = Math.max(0, max);
		target = Math.clamp(target, 0, max);
		long now = System.nanoTime();
		float seconds = elapsed(nanos, now);
		nanos = now;
		if (enabled(kind)) {
			value += (target - value) * approach(seconds);
			if (Math.abs(target - value) < 0.5) {
				value = target;
			}
		} else {
			value = target;
		}
		value = Math.clamp(value, 0, max);
		return value;
	}
}
