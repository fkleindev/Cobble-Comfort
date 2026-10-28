package dev.cobblecomfort.features.preview;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.config.ConfigManager;
import dev.cobblecomfort.features.tracker.TrackerStorage;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Where each map is centred, stored per world or server. The server never tells the client a map's centre, so it is
 * worked out from the player's own marker while the map is carried (see {@link ItemPreviewFeature}).
 */
final class MapCenters {
	/** A map's centre block and the dimension it shows. */
	record Center(int x, int z, String dimension) {
	}

	/** Saved form; a class so Gson can fill it. */
	private static final class Data {
		Map<Integer, Center> maps = new HashMap<>();
	}

	private Data data = new Data();
	private String key;

	void load() {
		key = TrackerStorage.currentWorldKey();
		data = new Data();
		Path file = file();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				Data loaded = ConfigManager.GSON.fromJson(reader, Data.class);
				if (loaded != null && loaded.maps != null) {
					data = loaded;
				}
			} catch (Exception e) {
				CobbleComfortClient.LOGGER.error("Could not read map centres {}", file, e);
			}
		}
	}

	void unload() {
		key = null;
		data = new Data();
	}

	Center get(int mapId) {
		return data.maps.get(mapId);
	}

	void put(int mapId, Center center) {
		if (key != null && !center.equals(data.maps.put(mapId, center))) {
			save();
		}
	}

	private Path file() {
		return ConfigManager.dir().resolve("map_centers").resolve((key == null ? "unknown" : key) + ".json");
	}

	private void save() {
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				ConfigManager.GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.error("Could not write map centres {}", file, e);
		}
	}
}
