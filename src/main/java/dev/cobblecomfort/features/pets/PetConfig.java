package dev.cobblecomfort.features.pets;

import dev.cobblecomfort.core.FeatureConfig;

public class PetConfig extends FeatureConfig {
	// What is remembered
	/** Wolves, cats and parrots tamed by the player. */
	public boolean companions = true;
	/** Horses, donkeys, mules, llamas and camels the player rode or opened. */
	public boolean mounts = true;
	/** Nautiluses tamed by the player and happy ghasts the player rode. */
	public boolean others = true;

	// Death
	public boolean deathToast = true;

	// Map
	public boolean mapMarkers = true;
}
