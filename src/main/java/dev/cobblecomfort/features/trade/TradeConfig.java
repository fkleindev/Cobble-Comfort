package dev.cobblecomfort.features.trade;

import dev.cobblecomfort.core.FeatureConfig;

public class TradeConfig extends FeatureConfig {
	// What is remembered
	public boolean wanderingTraders = true;
	public boolean forgetGone = true;
	public boolean naming = true;

	// Hints
	public boolean tooltipHint = true;
	public int tooltipLines = 3;

	// Map
	public boolean mapMarkers = true;
}
