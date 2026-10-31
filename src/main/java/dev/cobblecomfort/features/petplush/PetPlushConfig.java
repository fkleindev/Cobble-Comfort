package dev.cobblecomfort.features.petplush;

import dev.cobblecomfort.core.FeatureConfig;

public class PetPlushConfig extends FeatureConfig {
	/** Plushes of other players with the mod. */
	public boolean showOthers = true;
	/** How long the plush keeps swinging: 0 settles almost at once, 1 swings for a long time. */
	public float swing = 0.5f;
	/** Squeezed when its player is hit, harder the more damage it was. */
	public boolean hurtSquish = true;
}
