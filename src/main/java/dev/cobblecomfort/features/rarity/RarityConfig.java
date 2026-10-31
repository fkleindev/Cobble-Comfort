package dev.cobblecomfort.features.rarity;

import dev.cobblecomfort.core.FeatureConfig;

public class RarityConfig extends FeatureConfig {
	// Where
	public boolean inInventories = true;
	public boolean inHotbar = true;
	public boolean inArmorHud = true;
	public boolean inCreative = true;
	public boolean tooltipFrame = true;
	public RarityLevel minRarity = RarityLevel.UNCOMMON;

	// Look
	public IndicatorStyle style = IndicatorStyle.GLOW;
	public float intensity = 0.8f;
	public int thickness = 1;
	public int cornerSize = 5;
	public int commonColor = 0xFFFFFFFF;
	public int uncommonColor = 0xFFFFFF55;
	public int rareColor = 0xFF55FFFF;
	public int epicColor = 0xFFFF55FF;

	// Animation
	public boolean pulse = true;
	public RarityLevel pulseFrom = RarityLevel.RARE;
	public float pulseSpeed = 0.8f;
	public float pulseStrength = 0.4f;
	public boolean shimmer = true;
	public RarityLevel shimmerFrom = RarityLevel.EPIC;
	public float shimmerInterval = 3f;
	public boolean hoverHighlight = true;
	public boolean rainbowEpic = false;
	public float rainbowSpeed = 0.25f;
}
