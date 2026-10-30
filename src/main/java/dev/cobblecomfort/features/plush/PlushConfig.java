package dev.cobblecomfort.features.plush;

import dev.cobblecomfort.core.FeatureConfig;

public class PlushConfig extends FeatureConfig {
	/** Held by players, in first and third person. */
	public boolean inHand = true;
	/** Inventory, hotbar and every other screen. */
	public boolean inGui = true;
	/** The big totem in the middle of the screen when one saves you. */
	public boolean activation = true;
	/** Dropped, in item frames, on armour stands and held by mobs. */
	public boolean inWorld = true;

	public boolean wobble = true;
	public boolean equipSquish = true;
	public boolean activationSquish = true;
}
