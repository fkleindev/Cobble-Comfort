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
	/** Water dripping off wet players and companions, and from leaves and roof edges after the rain. */
	public boolean drips = true;
	/** Dust where players and mobs land after a fall. */
	public boolean landingDust = true;

	// Fireflies
	/** Fireflies on warm nights in swamps, meadows, flower forests and cherry groves. */
	public boolean fireflies = true;
	/** How many fireflies at most around you. */
	public int fireflyCount = 24;

	// Butterflies and dragonflies
	/** Butterflies by day wherever flowers grow under the open sky (tropical ones in the jungle). */
	public boolean butterflies = true;
	/** How many butterflies at most around you. */
	public int butterflyCount = 12;
	/** Dragonflies by day over water. */
	public boolean dragonflies = true;
	/** How many dragonflies at most around you. */
	public int dragonflyCount = 6;

	// Birds
	/** Birds flying over now and then, the kind depending on the biome. */
	public boolean birds = true;
	/** How often flocks come by, in percent. */
	public int birdAmount = 100;
	/** Their quiet calls. */
	public boolean birdSounds = true;
	/** Owls and bats at night. */
	public boolean birdsAtNight = true;

	// Snow
	/** Snow settles on the head and shoulders of players standing in snowfall, and falls off when they jump. */
	public boolean snowCover = true;
}
