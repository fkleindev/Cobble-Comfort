package dev.cobblecomfort.features.shulker;

import dev.cobblecomfort.core.FeatureConfig;

public class ShulkerPreviewConfig extends FeatureConfig {
	// Tooltip
	public boolean tooltip = true;
	public boolean compactByDefault = true;
	public PreviewKey fullKey = PreviewKey.SHIFT;
	public boolean keyHint = true;
	public boolean sortByCount = true;
	public int compactColumns = 9;

	// Look
	public boolean tint = true;
	public float tintStrength = 0.45f;
	public boolean rarityGlow = true;
	public boolean popIn = true;
	public float popDuration = 0.18f;
	public int staggerMs = 12;

	// HUD
	public boolean hud = true;
	public boolean hudOffhand = true;
	public int hudColumns = 9;

	// Fill bar
	public boolean fillBar = true;
	public boolean fillBarInHotbar = true;
	public FillMode fillMode = FillMode.SLOTS;
	public int fillColor = 0xFF55AAFF;
	public int fullColor = 0xFFFFAA00;
}
