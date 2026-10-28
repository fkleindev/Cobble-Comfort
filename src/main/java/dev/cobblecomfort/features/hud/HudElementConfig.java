package dev.cobblecomfort.features.hud;

import dev.cobblecomfort.features.hud.anim.Easing;
import dev.cobblecomfort.features.hud.anim.ShowAnimation;

/** Persisted settings of a single HUD element. */
public class HudElementConfig {
	// Visibility
	public boolean visible = true;
	public float opacity = 1f;

	// Position & size. Without a custom position the element stays where vanilla puts it.
	public boolean customPosition = false;
	public Anchor anchor = Anchor.BOTTOM_CENTER;
	public float offsetX = 0f;
	public float offsetY = 0f;
	public float scale = 1f;

	// Style
	public int tint = 0xFFFFFFFF;
	public TextShadow textShadow = TextShadow.VANILLA;
	public boolean background = false;
	public int backgroundColor = 0x80000000;
	public int backgroundPadding = 2;
	public int backgroundRadius = 2;

	// Animation
	public boolean autoHide = false;
	public float autoHideDelay = 4f;
	public ShowAnimation animation = ShowAnimation.FADE_SLIDE;
	public Easing easing = Easing.EASE_OUT_CUBIC;
	public float animationDuration = 0.35f;
	public boolean pulseWhenLow = false;
}
