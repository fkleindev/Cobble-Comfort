package dev.cobblecomfort.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.cobblecomfort.CobbleComfortClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Reads and writes one JSON file per feature in config/cobblecomfort/. */
public final class ConfigManager {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private ConfigManager() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve(CobbleComfortClient.MOD_ID);
	}

	public static <T> T load(String name, Class<T> type, Supplier<T> defaults) {
		Path file = dir().resolve(name + ".json");
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				T value = GSON.fromJson(reader, type);
				if (value != null) {
					return value;
				}
			} catch (Exception e) {
				CobbleComfortClient.LOGGER.error("Could not read config {}, using defaults", file, e);
			}
		}
		T value = defaults.get();
		save(name, value);
		return value;
	}

	public static void save(String name, Object value) {
		Path file = dir().resolve(name + ".json");
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(value, writer);
			}
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.error("Could not write config {}", file, e);
		}
	}

	/** Deep copy through JSON; used for presets and resets. */
	public static <T> T copy(T value, Class<T> type) {
		return GSON.fromJson(GSON.toJsonTree(value), type);
	}
}
