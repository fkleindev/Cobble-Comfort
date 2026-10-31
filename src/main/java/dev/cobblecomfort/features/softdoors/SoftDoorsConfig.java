package dev.cobblecomfort.features.softdoors;

import dev.cobblecomfort.core.FeatureConfig;

public class SoftDoorsConfig extends FeatureConfig {
	// Which blocks
	/** Wooden, iron and copper doors. */
	public boolean doors = true;
	/** Wooden, iron and copper trapdoors. */
	public boolean trapdoors = true;
	public boolean gates = true;

	// Feel
	/** How quickly they swing: 0.5 takes about a quarter of a second. */
	public float speed = 0.5f;
	/** How far they swing past and wobble back: 0 stops softly, 1 bounces a few times. */
	public float bounce = 0.5f;
}
