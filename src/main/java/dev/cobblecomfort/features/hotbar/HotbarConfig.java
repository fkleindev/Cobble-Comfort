package dev.cobblecomfort.features.hotbar;

import dev.cobblecomfort.core.FeatureConfig;
import dev.cobblecomfort.features.hud.anim.Easing;

public class HotbarConfig extends FeatureConfig {
	// Selected item
	public boolean scaleSelected = true;
	public float selectedScale = 1.2f;
	public float growDuration = 0.25f;
	public float bounce = 0.5f;
	public boolean lift = true;
	public int liftPixels = 2;

	// Selection frame
	public boolean smoothSelection = true;
	public float slideDuration = 0.15f;
	public Easing slideEasing = Easing.EASE_OUT_CUBIC;

	// Extras
	public SlotNumberMode slotNumbers = SlotNumberMode.WHILE_SWITCHING;
	public float numberHoldSeconds = 1f;
	public float numberScale = 0.6f;
	public int numberColor = 0xFFFFFFFF;
	public boolean dimOthers = true;
	public float dimAmount = 0.35f;
	public boolean switchParticles = true;
	public int particleCount = 6;
	/** A hotbar item that breaks swells, flashes and bursts into shards. */
	public boolean breakShatter = true;
}
