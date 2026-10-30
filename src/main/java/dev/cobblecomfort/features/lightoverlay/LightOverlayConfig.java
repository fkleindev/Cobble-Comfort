package dev.cobblecomfort.features.lightoverlay;

import dev.cobblecomfort.core.FeatureConfig;

public class LightOverlayConfig extends FeatureConfig {
	/** How far around the player blocks are checked, horizontally and vertically. */
	public int range = 16;
	/** Yellow crosses where monsters only spawn at night. */
	public boolean showNight = true;

	/** The block light as a number on every block a monster could stand on, near the player. */
	public boolean numbers = false;
	public int numberRange = 8;

	public boolean fade = true;
	public boolean toggleMessage = true;
	public boolean rememberState = false;

	/** Whether the overlay is switched on; only persisted when {@link #rememberState} is on. */
	public boolean active = false;
}
