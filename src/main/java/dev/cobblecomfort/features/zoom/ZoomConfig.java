package dev.cobblecomfort.features.zoom;

import dev.cobblecomfort.core.FeatureConfig;
import dev.cobblecomfort.features.hud.anim.Easing;

public class ZoomConfig extends FeatureConfig {
	// Behaviour
	public boolean toggleMode = false;
	public float zoomFactor = 4f;
	public float minZoom = 1.5f;
	public float maxZoom = 50f;
	public boolean scrollZoom = true;
	public float scrollStep = 0.2f;
	public boolean resetOnRelease = true;

	// Transition
	public boolean smoothTransition = true;
	public float transitionSpeed = 10f;

	// Style
	public ZoomStyle style = ZoomStyle.NORMAL;
	public float barHeight = 0.12f;
	public int barColor = 0xFF000000;
	public float barDuration = 0.35f;
	public Easing barEasing = Easing.EASE_OUT_CUBIC;
	public boolean spyglassOverlay = false;

	// Extras
	public boolean adjustSensitivity = true;
	public float sensitivityStrength = 1f;
	public boolean smoothCamera = false;
	public boolean hideHand = true;
	public boolean hideHud = false;
	public boolean showZoomLevel = true;
	public float zoomLevelDuration = 1.5f;
}
