package dev.cobblecomfort.features.enchantdesc;

import dev.cobblecomfort.core.FeatureConfig;

public class EnchantDescConfig extends FeatureConfig {
	// When
	public DisplayMode displayMode = DisplayMode.SHIFT;
	public boolean shiftHint = true;

	// Content
	public boolean showDescription = true;
	public boolean showMaxLevel = true;
	public boolean maxLevelAlways = true;
	public boolean highlightMaxLevel = true;
	public boolean showApplicable = true;
	public boolean showConflicts = true;
	public int maxListed = 5;
	public boolean markCurses = true;

	// Look
	public int indent = 2;
	public int wrapWidth = 200;
	public int descriptionColor = 0xFF8C8C8C;
	public int labelColor = 0xFF6E7F99;
	public int extraColor = 0xFF9AA5B5;
	public int maxLevelColor = 0xFFFFAA00;
	public int curseColor = 0xFFFF5555;
	public int hintColor = 0xFF606060;
}
