package dev.cobblecomfort.features.tracker;

import dev.cobblecomfort.core.FeatureConfig;

public class TrackerConfig extends FeatureConfig {
	// Counting
	public boolean countShulkers = true;
	public boolean countBundles = true;
	public boolean nearbyChests = true;
	public int nearbyRadius = 32;
	public boolean nearbyCountsForGoal = false;
	public int defaultQuantity = 64;

	// HUD
	public boolean hideWhenEmpty = true;
	public boolean showTitle = true;
	public boolean showNames = true;
	public boolean showBar = true;
	public int maxRows = 8;
	public int textColor = 0xFFFFFFFF;
	public int nearbyColor = 0xFFE0A050;
	public int barColor = 0xFF55AAFF;
	public int doneColor = 0xFF55FF55;
	public boolean background = true;
	public int backgroundColor = 0x90000000;
	public int cornerRadius = 3;
	public int padding = 4;

	// Animation & feedback
	public boolean smoothCount = true;
	public boolean bumpOnChange = true;
	public boolean slideIn = true;
	public boolean completionShine = true;
	public boolean completionConfetti = true;
	public boolean toast = true;
}
