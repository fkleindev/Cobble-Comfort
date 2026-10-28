package dev.cobblecomfort.features.lookinfo;

import dev.cobblecomfort.core.FeatureConfig;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.cobblecomfort.features.hud.anim.ShowAnimation;

public class LookInfoConfig extends FeatureConfig {
	// Targets
	public boolean showBlocks = true;
	public boolean showEntities = true;
	public boolean showFluids = false;
	public boolean onlyWhenSneaking = false;
	public boolean hideInSpectator = true;

	// Content
	public boolean showIcon = true;
	public boolean showModName = true;
	public boolean showHarvestTool = true;
	public boolean showGrowth = true;
	public boolean showRedstone = true;
	public boolean showBreakProgress = true;
	public boolean showBlockStates = false;
	public HealthDisplay healthDisplay = HealthDisplay.HEARTS;
	public int maxHearts = 10;
	public boolean showArmor = true;
	public boolean showEntityDetails = true;
	public boolean showHorseStats = true;

	// Look
	public LookLayout layout = LookLayout.PANEL;
	public int backgroundColor = 0xD0100010;
	public boolean border = true;
	public int borderColorTop = 0x805000FF;
	public int borderColorBottom = 0x8028007F;
	public int cornerRadius = 3;
	public int padding = 4;
	public int nameColor = 0xFFFFFFFF;
	public int detailColor = 0xFFAAAAAA;
	public int modColor = 0xFF5555FF;
	public boolean textShadow = true;
	/** 20 keeps the panel just above the held item name, which vanilla draws 20 px above the status bars. */
	public int barGap = 20;

	// Animation
	public ShowAnimation animation = ShowAnimation.FADE_SCALE;
	public Easing easing = Easing.EASE_OUT_CUBIC;
	public float animationDuration = 0.15f;
	public float lingerTime = 0.25f;
	public boolean smoothResize = true;
}
