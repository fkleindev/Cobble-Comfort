package dev.cobblecomfort.features.plantsway;

import dev.cobblecomfort.core.FeatureConfig;

public class PlantSwayConfig extends FeatureConfig {
	// Which plants
	/** Short and tall grass, ferns, bushes and dry grass. */
	public boolean grass = true;
	/** Flowers, saplings, mushrooms, berry bushes and other small plants. */
	public boolean flowers = true;
	/** Wheat, carrots, potatoes, beetroots, stems and nether wart. */
	public boolean crops = true;
	/** Plants in flower pots wobble as you brush past. */
	public boolean pots = true;
	/** Lily pads dip under whoever stands on them and rock in the wake of boats and swimmers. */
	public boolean lilyPads = true;
	/** Leaves shake when something runs or jumps into them. */
	public boolean leaves = true;
	/** Shaken leaves drop a few falling leaves. */
	public boolean fallingLeaves = true;

	// Who
	public boolean players = true;
	public boolean mobs = true;
	/** Boats and projectiles rock lily pads and shake leaves. */
	public boolean other = true;

	// Feel
	/** How far plants bend away. */
	public float strength = 0.7f;
	/** How long they swing back and forth before settling: 0 settles at once, 1 swings for a long time. */
	public float swing = 0.5f;
}
