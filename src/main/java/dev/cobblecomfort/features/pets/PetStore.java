package dev.cobblecomfort.features.pets;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.config.ConfigManager;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Remembered pets of the current world or server, saved as JSON in config/cobblecomfort/pets/. */
public final class PetStore {
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "Cobble Comfort pet writer");
		thread.setDaemon(true);
		return thread;
	});

	/** Saved form. */
	private static final class Data {
		List<PetEntry> pets = new ArrayList<>();
		/** The pet hanging as a plush charm on the back: its UUID, "" to pick one automatically, or "none". */
		String charm = "";
		/** The pet worn as a plush backpack: its UUID, or "" for none. */
		String pack = "";
	}

	private Data data = new Data();
	private String key;
	private boolean dirty;
	/** Bumped on every change, so searches can cache their results. */
	private int version;

	public void load(String worldKey) {
		key = worldKey;
		data = new Data();
		dirty = false;
		version++;
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			Data loaded = ConfigManager.GSON.fromJson(reader, Data.class);
			if (loaded != null && loaded.pets != null) {
				data = loaded;
			}
		} catch (Exception e) {
			CobbleComfortClient.LOGGER.error("Could not read pet memory {}", file, e);
		}
		data.pets.removeIf(pet -> pet == null || pet.uuid == null || pet.type == null || pet.dimension == null);
		for (PetEntry pet : data.pets) {
			if (pet.name == null) {
				pet.name = "";
			}
			if (pet.deathMessage == null) {
				pet.deathMessage = "";
			}
		}
	}

	public void unload() {
		save();
		key = null;
		data = new Data();
		version++;
	}

	public boolean loaded() {
		return key != null;
	}

	public int version() {
		return version;
	}

	public List<PetEntry> all() {
		return data.pets;
	}

	public PetEntry byUuid(String uuid) {
		for (PetEntry pet : data.pets) {
			if (pet.uuid.equals(uuid)) {
				return pet;
			}
		}
		return null;
	}

	public String charm() {
		return data.charm == null ? "" : data.charm;
	}

	public void setCharm(String charm) {
		data.charm = charm;
		changed();
	}

	public String pack() {
		return data.pack == null ? "" : data.pack;
	}

	public void setPack(String pack) {
		data.pack = pack;
		changed();
	}

	public void put(PetEntry pet) {
		if (!data.pets.contains(pet)) {
			data.pets.add(pet);
		}
		changed();
	}

	public void remove(PetEntry pet) {
		if (data.pets.remove(pet)) {
			changed();
		}
	}

	public void changed() {
		dirty = true;
		version++;
	}

	private Path file() {
		return ConfigManager.dir().resolve("pets").resolve((key == null ? "unknown" : key) + ".json");
	}

	/** Writes the file in the background if anything changed. */
	public void save() {
		if (key == null || !dirty) {
			return;
		}
		dirty = false;
		String json = ConfigManager.GSON.toJson(data);
		Path file = file();
		WRITER.execute(() -> {
			try {
				Files.createDirectories(file.getParent());
				Path temp = file.resolveSibling(file.getFileName() + ".tmp");
				Files.writeString(temp, json, StandardCharsets.UTF_8);
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				CobbleComfortClient.LOGGER.error("Could not write pet memory {}", file, e);
			}
		});
	}
}
