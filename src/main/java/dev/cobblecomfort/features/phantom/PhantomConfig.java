package dev.cobblecomfort.features.phantom;

import dev.cobblecomfort.core.FeatureConfig;

public class PhantomConfig extends FeatureConfig {
	/** The HUD element with the countdown, shown only shortly before and while phantoms can spawn. */
	public boolean hudElement = true;
	/** A popup with a sound when phantoms can start to spawn. */
	public boolean warning = true;
}
