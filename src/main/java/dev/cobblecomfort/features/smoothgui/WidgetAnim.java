package dev.cobblecomfort.features.smoothgui;

/** Small eased transitions for vanilla widgets: button hover, slider handles, checkboxes and tabs. */
public final class WidgetAnim {
	/** About a tenth of a second to get there. */
	private static final float RATE = 22f;

	public enum Kind {
		BUTTONS, SLIDERS, CHECKBOXES, TABS
	}

	private WidgetAnim() {
	}

	public static boolean enabled(Kind kind) {
		SmoothGuiFeature feature = SmoothGuiFeature.INSTANCE;
		if (!feature.isEnabled()) {
			return false;
		}
		SmoothGuiConfig c = feature.config();
		return switch (kind) {
			case BUTTONS -> c.buttonHover;
			case SLIDERS -> c.sliderGlide;
			case CHECKBOXES -> c.checkboxAnim;
			case TABS -> c.tabTransitions;
		};
	}

	/** Moves {@code value} towards {@code target} for {@code seconds}; a negative value starts at the target. */
	public static float step(float value, float target, float seconds) {
		if (value < 0f) {
			return target;
		}
		float next = value + (target - value) * (1f - (float) Math.exp(-seconds * RATE));
		return Math.abs(target - next) < 0.01f ? target : next;
	}

	public static double step(double value, double target, float seconds) {
		if (Double.isNaN(value)) {
			return target;
		}
		double next = value + (target - value) * (1.0 - Math.exp(-seconds * RATE));
		return Math.abs(target - next) < 0.001 ? target : next;
	}
}
