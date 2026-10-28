package dev.cobblecomfort.features.hud.render;

/** Counts HUD frames so elements spread over several layers can tell when a new frame starts. */
public final class HudFrame {
	private static int id;

	private HudFrame() {
	}

	public static void next() {
		id++;
	}

	public static int id() {
		return id;
	}
}
