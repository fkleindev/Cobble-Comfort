package dev.cobblecomfort.features.enchanting;

import dev.cobblecomfort.core.FeatureConfig;

public class EnchantingConfig extends FeatureConfig {
	// Bookshelves
	public boolean shelfCounter = true;
	public boolean maxLevelInfo = true;
	public boolean worldMarkers = true;
	public boolean markValid = true;
	public boolean markBlocked = true;
	public boolean markEmpty = true;
	public int validColor = 0xFF55FF55;
	public int blockedColor = 0xFFFF5555;
	public int emptyColor = 0xFF55AAFF;

	// Chances
	public boolean oddsPanel = true;
	public boolean panelOpen = false;
	public boolean slotOdds = true;
	public int maxExtras = 4;
	public int samples = 2000;

	// Descriptions
	public boolean slotDescriptions = true;
	public boolean panelDescriptions = true;
	public boolean readableRunes = true;
}
