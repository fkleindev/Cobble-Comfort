package dev.cobblecomfort.features.atmosphere;

import dev.cobblecomfort.core.FeatureConfig;

public class AtmosphereConfig extends FeatureConfig {
	// Breath
	/** Small clouds of breath in the cold: snowy biomes, high up and in snowfall. */
	public boolean breath = true;
	public boolean breathMobs = true;
	/** Your own breath, seen in front of you in first person too. */
	public boolean ownBreath = true;

	// Water
	/** Rings and splashes where players and mobs wade through shallow water. */
	public boolean splashes = true;

	// Fireflies
	/** Fireflies on warm nights in swamps, meadows, flower forests and cherry groves. */
	public boolean fireflies = true;
	/** How many fireflies at most around you. */
	public int fireflyCount = 24;

	// Snow
	/** Snow settles on the head and shoulders of players standing in snowfall, and falls off when they jump. */
	public boolean snowCover = true;
}
