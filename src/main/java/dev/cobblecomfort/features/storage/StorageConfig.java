package dev.cobblecomfort.features.storage;

import dev.cobblecomfort.core.FeatureConfig;

public class StorageConfig extends FeatureConfig {
	// What is remembered
	public boolean chests = true;
	public boolean enderChest = true;
	public boolean utilityBlocks = true;
	public boolean entities = true;
	public boolean forgetBroken = true;

	// Search
	public boolean inventoryHint = true;
	public boolean countShulkerContents = true;
	public boolean enderTooltip = true;
	public boolean showAge = true;
	public int staleDays = 7;
	public boolean naming = true;

	// Finding
	public int highlightSeconds = 30;
	public boolean outline = true;
	public int outlineColor = 0xFFFFD24A;
	public boolean compassArrow = true;
	public boolean mapMarkers = true;
	public boolean mapAllContainers = false;

	// Look info
	public boolean lookInfo = true;
	public int lookInfoRows = 2;
}
