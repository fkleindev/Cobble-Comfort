package dev.cobblecomfort.features.effects;

import dev.cobblecomfort.core.FeatureConfig;

public class EffectHudConfig extends FeatureConfig {
	// Layout
	public EffectStyle style = EffectStyle.LIST;
	public int listWidth = 120;
	public int perRow = 8;
	public boolean separateGroups = true;
	public EffectSort sort = EffectSort.LONGEST_FIRST;
	public boolean hideIconless = true;

	// Look
	public boolean background = true;
	public int backgroundColor = 0xA0101010;
	public boolean categoryMark = true;
	public int beneficialColor = 0xFF55FF55;
	public int harmfulColor = 0xFFFF5555;
	public boolean effectColorBars = true;
	public boolean showLevel = true;

	// Warning
	public boolean warn = true;
	public int warnSeconds = 10;
	public boolean warnSound = false;
	public float warnVolume = 0.5f;

	// Animation
	public boolean animations = true;
	public float animSpeed = 1f;

	// Inventory
	public boolean inventory = true;
	public boolean inventoryTooltip = true;
}
