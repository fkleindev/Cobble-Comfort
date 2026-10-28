package dev.cobblecomfort.features.hud;

import com.google.gson.reflect.TypeToken;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.config.ConfigManager;
import dev.cobblecomfort.features.hud.anim.ShowAnimation;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Built-in layouts and user layouts saved as JSON in config/cobblecomfort/hud_presets/. */
public final class HudPresets {
	private static final Type LAYOUT_TYPE = new TypeToken<LinkedHashMap<String, HudElementConfig>>() {
	}.getType();

	public enum BuiltIn {
		VANILLA(cfg -> {
		}),
		MINIMAL(HudPresets::minimal),
		CLEAN(HudPresets::clean);

		private final Consumer<Map<String, HudElementConfig>> customizer;

		BuiltIn(Consumer<Map<String, HudElementConfig>> customizer) {
			this.customizer = customizer;
		}

		public Map<String, HudElementConfig> create() {
			Map<String, HudElementConfig> layout = defaults();
			customizer.accept(layout);
			return layout;
		}
	}

	private HudPresets() {
	}

	public static Path dir() {
		return ConfigManager.dir().resolve("hud_presets");
	}

	private static Map<String, HudElementConfig> defaults() {
		Map<String, HudElementConfig> layout = new LinkedHashMap<>();
		for (HudElementDef def : HudElements.all()) {
			layout.put(def.key(), HudConfig.defaultsFor(def));
		}
		return layout;
	}

	/** Status bars hide when nothing changes and slide back in when they do. */
	private static void minimal(Map<String, HudElementConfig> layout) {
		for (HudElementDef def : List.of(HudElements.HEALTH, HudElements.ARMOR, HudElements.FOOD,
				HudElements.AIR, HudElements.INFO_BAR, HudElements.EXPERIENCE_LEVEL, HudElements.HOTBAR)) {
			HudElementConfig cfg = layout.get(def.key());
			cfg.autoHide = true;
			cfg.autoHideDelay = def == HudElements.HOTBAR ? 3f : 5f;
			cfg.animation = ShowAnimation.FADE_SLIDE;
		}
		layout.get(HudElements.HEALTH.key()).pulseWhenLow = true;
		layout.get(HudElements.FOOD.key()).pulseWhenLow = true;
	}

	/** Translucent backgrounds behind info panels, softer effects and scoreboard. */
	private static void clean(Map<String, HudElementConfig> layout) {
		for (HudElementDef def : List.of(HudElements.SCOREBOARD, HudElements.MOB_EFFECTS, HudElements.HELD_ITEM_TOOLTIP,
				HudElements.OVERLAY_MESSAGE, HudElements.SUBTITLES)) {
			HudElementConfig cfg = layout.get(def.key());
			cfg.background = true;
			cfg.backgroundRadius = 3;
			cfg.backgroundPadding = 3;
			cfg.backgroundColor = 0x66000000;
		}
		layout.get(HudElements.SCOREBOARD.key()).opacity = 0.85f;
		layout.get(HudElements.MOB_EFFECTS.key()).opacity = 0.9f;
		layout.get(HudElements.HELD_ITEM_TOOLTIP.key()).textShadow = TextShadow.ON;
		layout.get(HudElements.HEALTH.key()).pulseWhenLow = true;
	}

	public static List<String> userPresets() {
		List<String> names = new ArrayList<>();
		if (!Files.isDirectory(dir())) {
			return names;
		}
		try (Stream<Path> files = Files.list(dir())) {
			files.map(p -> p.getFileName().toString())
					.filter(n -> n.endsWith(".json"))
					.map(n -> n.substring(0, n.length() - 5))
					.sorted()
					.forEach(names::add);
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.error("Could not list HUD presets", e);
		}
		return names;
	}

	/** Strips characters that are not safe in file names. Returns an empty string if nothing is left. */
	public static String sanitize(String name) {
		return name.trim().replaceAll("[^A-Za-z0-9 _\\-]", "").trim();
	}

	public static boolean save(String name, Map<String, HudElementConfig> layout) {
		String safe = sanitize(name);
		if (safe.isEmpty()) {
			return false;
		}
		try {
			Files.createDirectories(dir());
			try (Writer writer = Files.newBufferedWriter(dir().resolve(safe + ".json"))) {
				ConfigManager.GSON.toJson(layout, LAYOUT_TYPE, writer);
			}
			return true;
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.error("Could not save HUD preset {}", safe, e);
			return false;
		}
	}

	public static Map<String, HudElementConfig> load(String name) {
		Path file = dir().resolve(sanitize(name) + ".json");
		try (Reader reader = Files.newBufferedReader(file)) {
			Map<String, HudElementConfig> layout = ConfigManager.GSON.fromJson(reader, LAYOUT_TYPE);
			return layout != null ? layout : null;
		} catch (Exception e) {
			CobbleComfortClient.LOGGER.error("Could not load HUD preset {}", file, e);
			return null;
		}
	}

	public static Map<String, HudElementConfig> copy(Map<String, HudElementConfig> layout) {
		return ConfigManager.GSON.fromJson(ConfigManager.GSON.toJsonTree(layout, LAYOUT_TYPE), LAYOUT_TYPE);
	}
}
