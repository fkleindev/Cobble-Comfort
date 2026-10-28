package dev.cobblecomfort.features.hudanim;

import dev.cobblecomfort.core.FeatureConfig;

public class HudAnimConfig extends FeatureConfig {
	// Hearts
	public boolean healthTrail = true;
	public boolean healPop = true;

	// Experience
	public boolean smoothXp = true;
	public boolean levelPulse = true;
	public boolean xpGain = true;

	// Food, armour and air
	public boolean food = true;
	public boolean armor = true;
	public boolean air = true;

	// Boss bars
	public boolean bossSmooth = true;
	public boolean bossTrail = true;

	// Timing
	/** How long the lost part of a bar stays before it runs down, in seconds. */
	public float trailDelay = 0.4f;
	/** Multiplies the speed of all animations. */
	public float speed = 1f;
	public float popStrength = 0.35f;
}
