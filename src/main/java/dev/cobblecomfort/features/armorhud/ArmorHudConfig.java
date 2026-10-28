package dev.cobblecomfort.features.armorhud;

import dev.cobblecomfort.core.FeatureConfig;

public class ArmorHudConfig extends FeatureConfig {
	// Content
	public boolean showArrows = true;
	public boolean showEmptySlots = false;

	// Layout
	public ArmorLayout layout = ArmorLayout.HORIZONTAL;
	public int spacing = 18;

	// Durability
	public DurabilityDisplay durabilityDisplay = DurabilityDisplay.PERCENT;
	public float textScale = 0.75f;
	public boolean colorByDurability = true;
	public int textColor = 0xFFFFFFFF;
	public boolean textShadow = true;

	// Warning
	public float warnBelow = 0.1f;
	public boolean warnBlink = true;
	public int warnColor = 0xFFFF5555;
	public boolean warnSound = false;

	// Animation
	public boolean animateEquip = true;
	public float equipDuration = 0.25f;
	public boolean shakeOnDamage = true;
	public boolean smoothMovement = true;
}
