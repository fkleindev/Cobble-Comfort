package dev.cobblecomfort.features.trade;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.config.ConfigManager;
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

/** Remembered traders of the current world or server, saved as JSON in config/cobblecomfort/trades/. */
public final class TradeStore {
	private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "Cobble Comfort trade writer");
		thread.setDaemon(true);
		return thread;
	});

	/** Saved form. */
	private static final class Data {
		List<TradeMerchant> merchants = new ArrayList<>();
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
			if (loaded != null && loaded.merchants != null) {
				data = loaded;
			}
		} catch (Exception e) {
			CobbleComfortClient.LOGGER.error("Could not read trade memory {}", file, e);
		}
		for (TradeMerchant merchant : data.merchants) {
			if (merchant.name == null) {
				merchant.name = "";
			}
			if (merchant.offers == null) {
				merchant.offers = new ArrayList<>();
			}
			for (TradeOffer offer : merchant.offers) {
				offer.costAStack = parse(offer.costA);
				offer.costBStack = parse(offer.costB);
				offer.resultStack = parse(offer.result);
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

	public List<TradeMerchant> all() {
		return data.merchants;
	}

	public TradeMerchant byUuid(String uuid) {
		for (TradeMerchant merchant : data.merchants) {
			if (merchant.uuid.equals(uuid)) {
				return merchant;
			}
		}
		return null;
	}

	public void put(TradeMerchant merchant) {
		if (!data.merchants.contains(merchant)) {
			data.merchants.add(merchant);
		}
		changed();
	}

	public void remove(TradeMerchant merchant) {
		if (data.merchants.remove(merchant)) {
			changed();
		}
	}

	/** Makes an offer from stacks, with their saved forms. */
	public TradeOffer offer(ItemStack costA, ItemStack costB, ItemStack result, int uses, int maxUses) {
		TradeOffer offer = new TradeOffer();
		offer.costAStack = costA.copy();
		offer.costBStack = costB.copy();
		offer.resultStack = result.copy();
		offer.costA = encode(costA);
		offer.costB = costB.isEmpty() ? null : encode(costB);
		offer.result = encode(result);
		offer.uses = uses;
		offer.maxUses = maxUses;
		return offer;
	}

	private JsonElement encode(ItemStack stack) {
		return ops == null || stack.isEmpty() ? null : ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
	}

	private ItemStack parse(JsonElement json) {
		return json == null || ops == null ? ItemStack.EMPTY : ItemStack.CODEC.parse(ops, json).result().orElse(ItemStack.EMPTY);
	}

	public void changed() {
		dirty = true;
		version++;
	}

	private Path file() {
		return ConfigManager.dir().resolve("trades").resolve((key == null ? "unknown" : key) + ".json");
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
				CobbleComfortClient.LOGGER.error("Could not write trade memory {}", file, e);
			}
		});
	}
}
