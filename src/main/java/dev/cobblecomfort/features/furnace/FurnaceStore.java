package dev.cobblecomfort.features.furnace;

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

/** The remembered furnaces of one world or server, in config/cobblecomfort/furnaces/WORLD.json. */
public final class FurnaceStore {
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "Cobble Comfort furnace writer");
		thread.setDaemon(true);
		return thread;
	});

	private static final class Data {
		List<FurnaceEntry> furnaces = new ArrayList<>();
	}

	private String key;
	private DynamicOps<JsonElement> ops;
	private Data data = new Data();
	private boolean dirty;

	public void load(String worldKey, HolderLookup.Provider registries) {
		key = worldKey;
		ops = registries.createSerializationContext(JsonOps.INSTANCE);
		data = new Data();
		dirty = false;
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			Data loaded = ConfigManager.GSON.fromJson(reader, Data.class);
			if (loaded != null && loaded.furnaces != null) {
				data = loaded;
			}
		} catch (Exception e) {
			CobbleComfortClient.LOGGER.error("Could not read furnace memory {}", file, e);
		}
		data.furnaces.removeIf(entry -> entry == null || entry.kind == null || entry.dimension == null);
		for (FurnaceEntry entry : data.furnaces) {
			entry.stack = entry.icon == null ? ItemStack.EMPTY : ItemStack.CODEC.parse(ops, entry.icon).result().orElse(ItemStack.EMPTY);
			if (entry.reason == null) {
				entry.reason = FurnaceEntry.Reason.DONE;
			}
		}
	}

	public void unload() {
		save();
		key = null;
		data = new Data();
	}

	public boolean loaded() {
		return key != null;
	}

	public List<FurnaceEntry> all() {
		return data.furnaces;
	}

	public FurnaceEntry at(String dimension, BlockPos pos) {
		for (FurnaceEntry entry : data.furnaces) {
			if (entry.at(dimension, pos)) {
				return entry;
			}
		}
		return null;
	}

	public FurnaceEntry getOrCreate(String dimension, BlockPos pos) {
		FurnaceEntry entry = at(dimension, pos);
		if (entry == null) {
			entry = new FurnaceEntry();
			entry.dimension = dimension;
			entry.x = pos.getX();
			entry.y = pos.getY();
			entry.z = pos.getZ();
			data.furnaces.add(entry);
		}
		return entry;
	}

	/** Adds an entry removed earlier (e.g. while the furnace was empty) back. */
	public void add(FurnaceEntry entry) {
		if (!data.furnaces.contains(entry)) {
			data.furnaces.add(entry);
			changed();
		}
	}

	public void remove(FurnaceEntry entry) {
		if (data.furnaces.remove(entry)) {
			changed();
		}
	}

	public void setIcon(FurnaceEntry entry, ItemStack stack) {
		entry.stack = stack.copyWithCount(1);
		entry.icon = stack.isEmpty() || ops == null ? null : ItemStack.CODEC.encodeStart(ops, entry.stack).result().orElse(null);
	}

	public void changed() {
		dirty = true;
	}

	private Path file() {
		return ConfigManager.dir().resolve("furnaces").resolve((key == null ? "unknown" : key) + ".json");
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
				CobbleComfortClient.LOGGER.error("Could not write furnace memory {}", file, e);
			}
		});
	}
}
