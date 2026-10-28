package dev.cobblecomfort.features.crosshair;

import dev.cobblecomfort.core.FeatureConfig;

public class CrosshairConfig extends FeatureConfig {
	// Hit marker
	public boolean hitMarker = true;
	public boolean critColor = true;
	public boolean killMarker = true;
	public int hitColor = 0xFFFFFFFF;
	public int critMarkerColor = 0xFFFFD84A;
	public int killColor = 0xFFFF4040;

	// Attack cooldown
	public boolean attackRing = true;
	public int ringColor = 0xFFFFFFFF;
}
