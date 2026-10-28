package dev.cobblecomfort.features.food;

import dev.cobblecomfort.core.FeatureConfig;

public class FoodHudConfig extends FeatureConfig {
	// Food bar
	public boolean saturationOverlay = true;
	public boolean eatPreview = true;
	public boolean exhaustion = true;
	public boolean estimateExhaustion = true;
	public boolean numbers = true;
	public boolean hungerWarning = true;
	public int warnBelow = 6;

	// Hearts
	public boolean healPreview = true;

	// Items
	public boolean tooltipValues = true;
	public boolean bestFood = true;

	// Colours
	public int exhaustionColor = 0x60FFFFFF;
	public int warningColor = 0xFFFF3030;
	public int bestFoodColor = 0xFF55FF55;
}
