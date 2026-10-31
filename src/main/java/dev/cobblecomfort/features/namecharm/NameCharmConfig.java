package dev.cobblecomfort.features.namecharm;

import dev.cobblecomfort.core.FeatureConfig;

public class NameCharmConfig extends FeatureConfig {
	/** Your own weapon in first person (also in Punchy's arm). */
	public boolean firstPerson = true;
	/** Held by you in third person, by other players and by mobs. */
	public boolean thirdPerson = true;
	/** Dropped, in item frames and on armour stands. */
	public boolean inWorld = true;

	/** How long the charm keeps swinging: 0 settles almost at once, 1 swings for a long time. */
	public float swing = 0.5f;
}
