package dev.cobblecomfort.features.hanging;

import dev.cobblecomfort.core.FeatureConfig;

public class HangingConfig extends FeatureConfig {
	// What
	/** Vines hanging free, weeping vines, glow berries, hanging roots, pale hanging moss and spore blossoms. */
	public boolean plants = true;
	/** Hanging lanterns and the chains they hang from. */
	public boolean lanterns = true;
	/** Bells swing on their mount. */
	public boolean bells = true;

	// Who
	public boolean players = true;
	public boolean mobs = true;
	/** Boats and flying arrows, snowballs and other projectiles. */
	public boolean other = true;

	// Feel
	/** How far things swing when pushed. */
	public float strength = 0.7f;
	/** A light breeze that keeps hanging things around you swaying a little; 0 is still air. */
	public float wind = 0.4f;
}
