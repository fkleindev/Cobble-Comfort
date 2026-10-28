package dev.cobblecomfort.features.fullbright;

import dev.cobblecomfort.core.FeatureConfig;
import dev.cobblecomfort.features.hud.anim.Easing;

public class FullbrightConfig extends FeatureConfig {
	public FullbrightMethod method = FullbrightMethod.AUTO;
	public float strength = 1f;

	public boolean smoothFade = true;
	public float fadeDuration = 0.5f;
	public Easing easing = Easing.EASE_IN_OUT_SINE;

	public boolean showHud = true;
	public boolean toggleMessage = true;
	public boolean rememberState = true;

	/** Whether fullbright is switched on; only persisted when {@link #rememberState} is on. */
	public boolean active = false;
}
