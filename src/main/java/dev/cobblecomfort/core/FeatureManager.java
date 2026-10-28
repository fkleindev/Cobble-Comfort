package dev.cobblecomfort.core;

import dev.cobblecomfort.core.config.ConfigManager;
import dev.cobblecomfort.core.config.GeneralConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FeatureManager {
	private static final List<Feature<?>> FEATURES = new ArrayList<>();
	private static GeneralConfig general;

	private FeatureManager() {
	}

	public static void register(Feature<?> feature) {
		FEATURES.add(feature);
	}

	public static void init() {
		general = ConfigManager.load("general", GeneralConfig.class, GeneralConfig::new);
		for (Feature<?> feature : FEATURES) {
			feature.load();
			feature.init();
		}
	}

	public static List<Feature<?>> features() {
		return Collections.unmodifiableList(FEATURES);
	}

	public static GeneralConfig general() {
		return general;
	}

	public static void saveAll() {
		ConfigManager.save("general", general);
		FEATURES.forEach(Feature::save);
	}
}
