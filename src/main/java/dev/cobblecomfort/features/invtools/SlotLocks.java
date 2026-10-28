package dev.cobblecomfort.features.invtools;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.config.ConfigManager;
import dev.cobblecomfort.features.tracker.TrackerStorage;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

/** Locked player inventory slots (by inventory index), stored per world or server. */
final class SlotLocks {
	/** Saved form; a class so Gson can fill it. */
	private static final class Data {
		Set<Integer> slots = new TreeSet<>();
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
				if (loaded != null && loaded.slots != null) {
					data = loaded;
				}
			} catch (Exception e) {
				CobbleComfortClient.LOGGER.error("Could not read slot locks {}", file, e);
			}
		}
	}

	void unload() {
		key = null;
		data = new Data();
	}

	boolean isLocked(int inventoryIndex) {
		return data.slots.contains(inventoryIndex);
	}

	void toggle(int inventoryIndex) {
		if (!data.slots.remove(inventoryIndex)) {
			data.slots.add(inventoryIndex);
		}
		save();
	}

	private Path file() {
		return ConfigManager.dir().resolve("locks").resolve((key == null ? "unknown" : key) + ".json");
	}

	private void save() {
		if (key == null) {
			return;
		}
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				ConfigManager.GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.error("Could not write slot locks {}", file, e);
		}
	}
}
