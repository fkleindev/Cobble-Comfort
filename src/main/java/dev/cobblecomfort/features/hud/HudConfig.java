package dev.cobblecomfort.features.hud;

import dev.cobblecomfort.core.FeatureConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class HudConfig extends FeatureConfig {
	public Map<String, HudElementConfig> elements = new LinkedHashMap<>();

	// Editor
	public boolean snapping = true;
	public int snapThreshold = 4;
	public int gridSize = 0;

	public HudElementConfig element(HudElementDef def) {
		return elements.computeIfAbsent(def.key(), k -> defaultsFor(def));
	}

	public static HudElementConfig defaultsFor(HudElementDef def) {
		HudElementConfig config = new HudElementConfig();
		config.anchor = def.naturalAnchor();
		return config;
	}
}
