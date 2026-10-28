package dev.cobblecomfort.features.storage;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

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

/** Remembered containers of the current world or server, saved as JSON in config/cobblecomfort/storage/. */
public final class StorageStore {
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "Cobble Comfort storage writer");
		thread.setDaemon(true);
		return thread;
	});

	/** Saved form. */
	private static final class Data {
		List<StoredContainer> containers = new ArrayList<>();
	}

	private Data data = new Data();
	private String key;
	private DynamicOps<JsonElement> ops;
	private boolean dirty;
	/** Bumped on every change, so searches can cache their results. */
	private int version;

	public void load(String worldKey, HolderLookup.Provider registries) {
		key = worldKey;
		ops = registries.createSerializationContext(JsonOps.INSTANCE);
		data = new Data();
		dirty = false;
		version++;
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			Data loaded = ConfigManager.GSON.fromJson(reader, Data.class);
			if (loaded != null && loaded.containers != null) {
				data = loaded;
			}
		} catch (Exception e) {
			CobbleComfortClient.LOGGER.error("Could not read storage memory {}", file, e);
		}
		for (StoredContainer container : data.containers) {
			container.items = new ArrayList<>();
			if (container.stacks == null) {
				container.stacks = new ArrayList<>();
			}
			for (JsonElement json : container.stacks) {
				ItemStack.CODEC.parse(ops, json).result().ifPresent(container.items::add);
			}
			if (container.name == null) {
				container.name = "";
			}
		}
	}

	public void unload() {
		save();
		key = null;
		ops = null;
		data = new Data();
		version++;
	}

	public boolean loaded() {
		return key != null;
	}

	public int version() {
		return version;
	}

	public List<StoredContainer> all() {
		return data.containers;
	}

	public StoredContainer atBlock(String dimension, BlockPos pos) {
		for (StoredContainer container : data.containers) {
			if (container.at(dimension, pos)) {
				return container;
			}
		}
		return null;
	}

	public StoredContainer byEntity(String uuid) {
		for (StoredContainer container : data.containers) {
			if (container.kind == StoredContainer.Kind.ENTITY && uuid.equals(container.uuid)) {
				return container;
			}
		}
		return null;
	}

	public StoredContainer ender() {
		for (StoredContainer container : data.containers) {
			if (container.kind == StoredContainer.Kind.ENDER) {
				return container;
			}
		}
		return null;
	}

	/** Adds a container, replacing remembered ones it overlaps (e.g. two single chests that became a double chest). */
	public void put(StoredContainer container) {
		if (container.kind == StoredContainer.Kind.BLOCK) {
			data.containers.removeIf(c -> c != container && (c.at(container.dimension, container.pos())
					|| (container.otherPos() != null && c.at(container.dimension, container.otherPos()))));
		}
		if (!data.containers.contains(container)) {
			data.containers.add(container);
		}
		changed();
	}

	public void remove(StoredContainer container) {
		if (data.containers.remove(container)) {
			changed();
		}
	}

	/** Stores new contents of a container. */
	public void setItems(StoredContainer container, List<ItemStack> items) {
		container.items = new ArrayList<>(items);
		container.stacks = new ArrayList<>();
		if (ops != null) {
			for (ItemStack stack : items) {
				ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(container.stacks::add);
			}
		}
		container.seen = System.currentTimeMillis();
		changed();
	}

	public void changed() {
		dirty = true;
		version++;
	}

	private Path file() {
		return ConfigManager.dir().resolve("storage").resolve((key == null ? "unknown" : key) + ".json");
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
				CobbleComfortClient.LOGGER.error("Could not write storage memory {}", file, e);
			}
		});
	}
}
