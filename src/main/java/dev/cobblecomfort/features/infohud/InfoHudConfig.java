package dev.cobblecomfort.features.infohud;

import dev.cobblecomfort.core.FeatureConfig;

import java.util.ArrayList;
import java.util.List;

public class InfoHudConfig extends FeatureConfig {
	// Content: the shown lines in their order
	public List<InfoEntry> entries = new ArrayList<>(List.of(InfoEntry.COORDS, InfoEntry.DIRECTION, InfoEntry.BIOME,
			InfoEntry.LIGHT, InfoEntry.TIME, InfoEntry.WEATHER, InfoEntry.FPS, InfoEntry.PING, InfoEntry.REST));
	public boolean otherDimensionCoords = true;
	public boolean directionDegrees = false;
	public boolean sleepHint = true;
	public boolean colorValues = true;

	// Look
	public InfoStyle style = InfoStyle.PANEL;
	public boolean icons = true;
	public int backgroundColor = 0xB0100010;
	public int labelColor = 0xFFAAAAAA;
	public int valueColor = 0xFFFFFFFF;

	// Behaviour
	public boolean hideWithDebug = true;
	public boolean animate = true;
}
