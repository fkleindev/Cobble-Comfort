package dev.cobblecomfort.features.stuckarrows;

import dev.cobblecomfort.core.FeatureConfig;

public class StuckArrowsConfig extends FeatureConfig {
	// Who
	/** Arrows stuck in yourself (third person and the inventory preview). */
	public boolean own = true;
	/** Arrows stuck in other players. */
	public boolean others = true;
	/** Bee stingers wobble too. */
	public boolean stingers = true;

	// Feel
	/** How far they wobble. */
	public float strength = 0.7f;
}
