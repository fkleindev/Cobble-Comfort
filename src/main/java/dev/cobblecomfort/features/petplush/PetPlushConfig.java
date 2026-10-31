package dev.cobblecomfort.features.petplush;

import dev.cobblecomfort.core.FeatureConfig;

public class PetPlushConfig extends FeatureConfig {
	/** Your own plush (third person, mirrors). */
	public boolean showOwn = true;
	/** Plushes of other players with the mod. */
	public boolean showOthers = true;
	/** Tell the server (Cobble Comfort Sync) which plush you have, so others see it. */
	public boolean share = true;
	/** Without the server plugin: give other players a plush of their own wolf or cat when it is nearby. */
	public boolean nearbyPets = true;

	/** How long the plush keeps swinging: 0 settles almost at once, 1 swings for a long time. */
	public float swing = 0.5f;
	/** Squeezed when its player is hit, harder the more damage it was. */
	public boolean hurtSquish = true;
}
