package dev.cobblecomfort.features.inventoryfx;

import dev.cobblecomfort.core.FeatureConfig;
import dev.cobblecomfort.features.rarity.RarityLevel;

public class InventoryFxConfig extends FeatureConfig {
	// Carried item
	public float followLag = 0.35f;
	public boolean tilt = true;
	public float tiltStrength = 1f;
	public boolean pickupPop = true;
	public float popStrength = 0.35f;

	// Slots
	public boolean dropSquash = true;
	public float squashStrength = 0.3f;
	public boolean slotFlash = true;
	public boolean spreadPop = true;

	// Shortcuts (shift-click, number keys, Q, double-click)
	public boolean shortcutFx = true;
	public boolean flyingItems = true;
	public float flyDuration = 0.25f;
	public int flyArc = 10;

	// Particles
	public boolean particles = true;
	public ParticleStyle particleStyle = ParticleStyle.BOTH;
	public RarityLevel sparksFrom = RarityLevel.UNCOMMON;
	public boolean moveParticles = true;
	public float density = 1f;
	public boolean pickupBurst = true;
	public boolean dropBurst = true;
	public int burstSize = 12;
	public float gravity = 1f;
	public float particleLifetime = 0.8f;
	public int maxParticles = 300;

	// Sound
	public boolean sound = false;
	public float soundVolume = 0.3f;
}
