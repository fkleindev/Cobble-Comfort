package dev.cobblecomfort.features.itembars;

import dev.cobblecomfort.core.FeatureConfig;

public class ItemBarsConfig extends FeatureConfig {
	// Durability
	public boolean smoothDurability = true;
	public boolean breakWarning = true;
	/** Share of the durability left from which the bar blinks. */
	public float warnBelow = 0.05f;

	// Cooldown
	public boolean cooldownRing = true;
	public int ringColor = 0xFF9AD8FF;
	public boolean cooldownFlash = true;
}
