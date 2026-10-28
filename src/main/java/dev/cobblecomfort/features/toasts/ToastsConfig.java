package dev.cobblecomfort.features.toasts;

import dev.cobblecomfort.core.FeatureConfig;

public class ToastsConfig extends FeatureConfig {
	// Toasts
	public boolean smoothToasts = true;
	public boolean stackToasts = true;
	/** Multiplies how long toasts stay, on top of vanilla's notification time. */
	public float toastTime = 0.6f;
	public boolean hideRecipeToasts = false;

	// Player list
	public boolean tabFade = true;
	public boolean pingNumbers = true;
	public boolean tabSort = true;
	public boolean headFrames = true;
}
