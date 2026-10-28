package dev.cobblecomfort.features.furnace;

import dev.cobblecomfort.core.FeatureConfig;

public class FurnaceConfig extends FeatureConfig {
	public boolean brewingStands = true;

	// Notifications
	public boolean toast = true;
	public boolean sound = true;

	// Markers
	public boolean worldMarkers = true;
	public int markerDistance = 32;
	public boolean mapMarkers = true;
	public boolean minimapMarkers = true;
}
