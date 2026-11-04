package dev.cobblecomfort.features.footprints;

import dev.cobblecomfort.core.FeatureConfig;

public class FootprintsConfig extends FeatureConfig {
	// Ground
	/** Snow layers and snow blocks. */
	public boolean snow = true;
	/** Sand, red sand and suspicious sand. */
	public boolean sand = true;
	/** Mud, soul sand and soul soil. */
	public boolean mud = true;
	/** Coming out of the water, feet leave wet prints on any ground for a few steps, which dry quickly. */
	public boolean wet = true;

	// Who
	public boolean players = true;
	public boolean mobs = true;

	// Look
	/** How long a print stays before it has faded away, in seconds. */
	public int duration = 20;
	/** How dark the prints are. */
	public float opacity = 0.5f;
	/** Rain and snowfall fill prints in sooner. */
	public boolean weather = true;
}
