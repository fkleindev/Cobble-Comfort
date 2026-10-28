package dev.cobblecomfort.features.smoothgui;

import dev.cobblecomfort.core.FeatureConfig;

public class SmoothGuiConfig extends FeatureConfig {
	// Where
	public boolean containers = true;
	public boolean menus = true;
	public boolean chat = true;
	public boolean onlyFromGame = true;

	// Animation
	public float duration = 0.12f;
	public float startScale = 0.92f;
	public boolean bounce = true;
	public boolean fade = true;
	public int chatSlide = 10;

	// Extras
	public boolean staggerSlots = true;
	public boolean backgroundFade = true;
	public float backgroundDuration = 0.15f;

	// Tooltips
	public boolean tooltipFade = true;
	public boolean tooltipFollow = true;
	public boolean tooltipMorph = true;

	// Widgets
	public boolean buttonHover = true;
	public boolean sliderGlide = true;
	public boolean checkboxAnim = true;
	public boolean tabTransitions = true;

	// Scrolling
	public boolean scrollLists = true;
	public boolean scrollChat = true;
	public boolean scrollCreative = true;
	public boolean recipePages = true;
	public boolean scrollModScreens = true;
	public float scrollSpeed = 1f;
}
