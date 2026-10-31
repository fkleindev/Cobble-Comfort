package dev.cobblecomfort.features.belt;

import dev.cobblecomfort.core.FeatureConfig;

public class BeltConfig extends FeatureConfig {
	/** Your own belt (third person, mirrors). */
	public boolean showOwn = true;
	/** The belts of other players with the mod (needs the Cobble Comfort Sync plugin on the server). */
	public boolean showOthers = true;
	/** Tell the server (Cobble Comfort Sync) what hangs on your belt, so others see it. */
	public boolean share = true;

	// What
	/** Bundles from the hotbar and inventory. */
	public boolean bundles = true;
	/** A sword (or spear, mace or axe) from the hotbar on the left hip. */
	public boolean melee = true;
	/** A bow or crossbow from the hotbar on the right hip, with a quiver of arrows. */
	public boolean ranged = true;
	/** How many bundles hang on a belt at most. */
	public int count = BeltBundle.MAX;

	/** How long they keep swinging: 0 settles almost at once, 1 swings for a long time. */
	public float swing = 0.5f;
}
