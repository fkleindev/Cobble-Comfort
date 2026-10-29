package dev.cobblecomfort.features.map.death;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.config.ConfigManager;
import dev.cobblecomfort.features.furnace.FurnaceFeature;
import dev.cobblecomfort.features.map.MapConfig;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import dev.cobblecomfort.features.map.waypoint.WaypointStore;
import dev.cobblecomfort.features.tracker.TrackerToast;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Remembers what the player dropped when dying, ticks the despawn timer down and checks items off as they are picked
 * up again. Dropped items despawn after 5 minutes, but only while their chunk ticks, so there are two timers: one that
 * only runs while the player is near (the likely case) and one that always runs (if something kept the chunk loaded).
 * Saved per world in config/cobblecomfort/deaths/WORLD.json.
 */
public final class DeathInventory {
	/** How long after the kill packet its snapshot still belongs to the death waypoint being set. */
	private static final long PENDING_MS = 5000;
	/** Ticks after dying before checking whether the inventory was kept (keepInventory). */
	private static final int KEEP_CHECK_TICKS = 20;
	private static final int CHECK_TICKS = 20;
	private static final int SAVE_TICKS = 600;
	/** Pickups this far from the death point (blocks) count; items scatter and float away. */
	private static final double PICKUP_RANGE = 64;
	/** Within this horizontal distance the dropped items must be visible to the client. */
	private static final double SEE_RANGE = 24;
	private static final double SEARCH_RANGE = 40;
	/** Seeing none of the items for this long while close means they are gone. */
	private static final int GONE_TICKS = 100;
	private static final int COLOR_RECOVERED = 10;
	private static final int COLOR_LOST = 8;
	public static final int RUNNING_COLOR = 0xFFFFC94D;
	public static final int DONE_COLOR = 0xFF6EE06E;
	public static final int WARN_COLOR = 0xFFFF8A4D;

	private static final class Data {
		List<DeathRecord> deaths = new ArrayList<>();
	}

	private final WaypointStore waypoints;
	private String key;
	private DynamicOps<JsonElement> ops;
	private Data data = new Data();
	private boolean dirty;
	private List<ItemStack> pending;
	private long pendingAt;
	private int ticks;

	public DeathInventory(WaypointStore waypoints) {
		this.waypoints = waypoints;
	}

	public void load(String worldKey, HolderLookup.Provider registries, boolean singleplayer) {
		key = worldKey;
		ops = registries.createSerializationContext(JsonOps.INSTANCE);
		data = new Data();
		dirty = false;
		pending = null;
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			Data loaded = ConfigManager.GSON.fromJson(reader, Data.class);
			if (loaded != null && loaded.deaths != null) {
				data = loaded;
			}
		} catch (Exception e) {
			CobbleComfortClient.LOGGER.error("Could not read death inventories {}", file, e);
		}
		data.deaths.removeIf(record -> record == null || record.items == null || record.dimension == null);
		long now = System.currentTimeMillis();
		for (DeathRecord record : data.deaths) {
			record.stacks = new ArrayList<>();
			for (JsonElement item : record.items) {
				record.stacks.add(ItemStack.CODEC.parse(ops, item).result().orElse(ItemStack.EMPTY));
			}
			if (record.recovered == null) {
				record.recovered = new int[record.stacks.size()];
			}
			if (record.state == null) {
				record.state = DeathRecord.State.ACTIVE;
			}
			record.checkTicks = -1;
			// A server keeps ticking while we are away; a singleplayer world does not.
			if (!singleplayer && record.state == DeathRecord.State.ACTIVE) {
				record.worstRemaining -= (int) Math.min(Integer.MAX_VALUE, Math.max(0, now - record.seen) / 50);
			}
		}
	}

	public void unload() {
		save();
		key = null;
		data = new Data();
		pending = null;
	}

	public boolean loaded() {
		return key != null;
	}

	/** The record belonging to a death waypoint, or null. */
	public DeathRecord of(Waypoint waypoint) {
		if (!waypoint.death) {
			return null;
		}
		for (DeathRecord record : data.deaths) {
			if (record.waypoint == waypoint.created) {
				return record;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- dying

	/** The server announced the player's death; the inventory is still intact at this point. */
	public void onKilled(LocalPlayer player) {
		pending = snapshot(player);
		pendingAt = System.currentTimeMillis();
	}

	/** Links what was dropped to the death waypoint just set. */
	public void attach(Waypoint waypoint, LocalPlayer player) {
		if (!loaded() || !MapFeature.INSTANCE.config().deathInventory) {
			return;
		}
		List<ItemStack> stacks = pending != null && System.currentTimeMillis() - pendingAt < PENDING_MS ? pending : snapshot(player);
		pending = null;
		DeathRecord record = new DeathRecord();
		for (ItemStack stack : stacks) {
			JsonElement json = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
			if (json != null) {
				record.items.add(json);
				record.stacks.add(stack);
			}
		}
		if (record.stacks.isEmpty()) {
			return;
		}
		record.waypoint = waypoint.created;
		record.dimension = waypoint.dimension;
		record.x = waypoint.x;
		record.y = (int) Math.floor(waypoint.yOr(player.getY()));
		record.z = waypoint.z;
		record.recovered = new int[record.stacks.size()];
		data.deaths.add(record);
		dirty = true;
	}

	/** Everything that drops: the whole inventory except items with Curse of Vanishing. */
	private static List<ItemStack> snapshot(LocalPlayer player) {
		List<ItemStack> list = new ArrayList<>();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && !EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				list.add(stack.copy());
			}
		}
		return list;
	}

	/** With keepInventory the items stay in the inventory instead of dropping. */
	private static boolean kept(LocalPlayer player, DeathRecord record) {
		Inventory inventory = player.getInventory();
		int found = 0;
		for (ItemStack lost : record.stacks) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (ItemStack.isSameItemSameComponents(inventory.getItem(i), lost)) {
					found++;
					break;
				}
			}
		}
		return found * 2 >= record.stacks.size();
	}

	// ---------------------------------------------------------------- picking up

	/** The local player picked up {@code amount} of an item; checks it off at the nearest death point. */
	public void onPickup(Minecraft mc, ItemStack stack, int amount) {
		LocalPlayer player = mc.player;
		if (!loaded() || player == null || mc.level == null) {
			return;
		}
		String dim = MapFeature.dimensionId(mc.level);
		DeathRecord nearest = null;
		double best = PICKUP_RANGE * PICKUP_RANGE;
		for (DeathRecord record : data.deaths) {
			double distance = player.distanceToSqr(record.x + 0.5, record.y, record.z + 0.5);
			if (record.state == DeathRecord.State.ACTIVE && record.dimension.equals(dim) && distance < best) {
				best = distance;
				nearest = record;
			}
		}
		if (nearest == null) {
			return;
		}
		int left = amount;
		for (int i = 0; i < nearest.stacks.size() && left > 0; i++) {
			if (ItemStack.isSameItemSameComponents(nearest.stacks.get(i), stack)) {
				int take = Math.min(left, nearest.remaining(i));
				nearest.recovered[i] += take;
				left -= take;
			}
		}
		if (left < amount) {
			dirty = true;
			if (nearest.allRecovered()) {
				finish(mc, nearest, DeathRecord.State.RECOVERED);
			}
		}
	}

	// ---------------------------------------------------------------- time passing

	public void tick(Minecraft mc, MapConfig config) {
		LocalPlayer player = mc.player;
		if (!loaded() || player == null || mc.level == null) {
			return;
		}
		ticks++;
		boolean check = ticks % CHECK_TICKS == 0;
		if (check && data.deaths.removeIf(record -> !hasWaypoint(record))) {
			dirty = true;
		}
		String dim = MapFeature.dimensionId(mc.level);
		for (DeathRecord record : new ArrayList<>(data.deaths)) {
			if (record.checkTicks >= 0 && ++record.checkTicks >= KEEP_CHECK_TICKS) {
				record.checkTicks = -1;
				if (kept(player, record)) {
					data.deaths.remove(record);
					dirty = true;
					continue;
				}
			}
			if (record.state != DeathRecord.State.ACTIVE || mc.isPaused()) {
				continue;
			}
			record.worstRemaining--;
			record.seen = System.currentTimeMillis();
			if (MapFeature.simulated(mc, record.dimension, record.x, record.z)) {
				record.loadedRemaining--;
				int warnAt = config.deathWarnSeconds * 20;
				if (!record.warned && config.deathWarning && warnAt > 0 && record.loadedRemaining <= warnAt) {
					record.warned = true;
					notify(mc, record, Component.translatable("cobblecomfort.death.toast.soon"),
							Component.translatable("cobblecomfort.death.toast.soon_text", FurnaceFeature.time(record.loadedRemaining)));
				}
				if (record.loadedRemaining <= 0) {
					finish(mc, record, DeathRecord.State.EXPIRED);
					continue;
				}
			}
			if (check && record.checkTicks < 0 && record.dimension.equals(dim) && !player.isDeadOrDying()) {
				checkPresent(mc, player, record);
			}
		}
		if (ticks % SAVE_TICKS == 0) {
			save();
		}
	}

	/** Close to the death point, the client sees the dropped items; none of them left means they are gone. */
	private void checkPresent(Minecraft mc, LocalPlayer player, DeathRecord record) {
		double dx = player.getX() - (record.x + 0.5);
		double dz = player.getZ() - (record.z + 0.5);
		if (dx * dx + dz * dz > SEE_RANGE * SEE_RANGE) {
			record.missingTicks = 0;
			return;
		}
		AABB area = new AABB(record.x - SEARCH_RANGE, record.y - SEARCH_RANGE, record.z - SEARCH_RANGE,
				record.x + SEARCH_RANGE, record.y + SEARCH_RANGE, record.z + SEARCH_RANGE);
		boolean any = !mc.level.getEntitiesOfClass(ItemEntity.class, area, item -> matches(record, item.getItem())).isEmpty();
		record.missingTicks = any ? 0 : record.missingTicks + CHECK_TICKS;
		if (record.missingTicks >= GONE_TICKS) {
			finish(mc, record, DeathRecord.State.GONE);
		}
	}

	private static boolean matches(DeathRecord record, ItemStack stack) {
		for (int i = 0; i < record.stacks.size(); i++) {
			if (record.remaining(i) > 0 && ItemStack.isSameItem(record.stacks.get(i), stack)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasWaypoint(DeathRecord record) {
		return waypoint(record) != null;
	}

	private Waypoint waypoint(DeathRecord record) {
		for (Waypoint waypoint : waypoints.all()) {
			if (waypoint.death && waypoint.created == record.waypoint) {
				return waypoint;
			}
		}
		return null;
	}

	/** Ends a record and colours its waypoint: green when everything is back, grey when the items are lost. */
	private void finish(Minecraft mc, DeathRecord record, DeathRecord.State state) {
		record.state = state;
		dirty = true;
		Waypoint waypoint = waypoint(record);
		if (waypoint != null) {
			waypoint.color = state == DeathRecord.State.RECOVERED ? COLOR_RECOVERED : COLOR_LOST;
			waypoints.save();
		}
		if (state == DeathRecord.State.RECOVERED) {
			notify(mc, record, Component.translatable("cobblecomfort.death.toast.recovered"),
					Component.translatable("cobblecomfort.death.progress", record.stacks.size(), record.stacks.size()));
		} else if (MapFeature.INSTANCE.config().deathWarning) {
			int left = 0;
			for (int i = 0; i < record.stacks.size(); i++) {
				left += record.remaining(i) > 0 ? 1 : 0;
			}
			notify(mc, record, Component.translatable("cobblecomfort.death.toast." + (state == DeathRecord.State.EXPIRED ? "expired" : "gone")),
					Component.translatable("cobblecomfort.death.toast.lost_text", left));
		}
		save();
	}

	private static void notify(Minecraft mc, DeathRecord record, Component title, Component text) {
		mc.gui.toastManager().addToast(new TrackerToast(icon(record), title, text));
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, 0.7f));
	}

	/** The most valuable item still out there, for toasts. */
	private static ItemStack icon(DeathRecord record) {
		for (int i : sorted(record)) {
			if (record.remaining(i) > 0) {
				return record.stacks.get(i);
			}
		}
		return new ItemStack(Items.SKELETON_SKULL);
	}

	// ---------------------------------------------------------------- texts

	/** Indices of the stacks with the valuable ones first, otherwise in inventory order. */
	public static List<Integer> sorted(DeathRecord record) {
		List<Integer> valuable = new ArrayList<>();
		List<Integer> rest = new ArrayList<>();
		for (int i = 0; i < record.stacks.size(); i++) {
			(valuable(record.stacks.get(i)) ? valuable : rest).add(i);
		}
		valuable.addAll(rest);
		return valuable;
	}

	/** Enchanted or rare items, filled containers, totems, elytras and diamond or netherite things. */
	public static boolean valuable(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.getRarity() != Rarity.COMMON || stack.isEnchanted() || stack.has(DataComponents.CONTAINER)
				|| stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.ELYTRA)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("netherite") || path.contains("diamond");
	}

	/** "▶ 3:12 until despawn" (⏸ while the chunk is not loaded), or how it ended. */
	public static Component status(DeathRecord record) {
		return switch (record.state) {
			case ACTIVE -> {
				boolean running = MapFeature.simulated(Minecraft.getInstance(), record.dimension, record.x, record.z);
				yield Component.translatable("cobblecomfort.death.status." + (running ? "running" : "paused"),
						FurnaceFeature.time(Math.max(0, record.loadedRemaining))).withColor(RUNNING_COLOR);
			}
			case RECOVERED -> Component.translatable("cobblecomfort.death.status.recovered").withColor(DONE_COLOR);
			case EXPIRED -> Component.translatable("cobblecomfort.death.status.expired").withColor(WARN_COLOR);
			case GONE -> Component.translatable("cobblecomfort.death.status.gone").withColor(WARN_COLOR);
		};
	}

	/** The worst case, if the chunk might have stayed loaded; null when it says nothing new. */
	public static Component worstCase(DeathRecord record) {
		if (record.state != DeathRecord.State.ACTIVE || record.loadedRemaining - record.worstRemaining < 20) {
			return null;
		}
		return (record.worstRemaining > 0
				? Component.translatable("cobblecomfort.death.worst", FurnaceFeature.time(record.worstRemaining))
				: Component.translatable("cobblecomfort.death.worst_over")).withStyle(ChatFormatting.GRAY);
	}

	/** "18/27 stacks back". */
	public static Component progress(DeathRecord record) {
		return Component.translatable("cobblecomfort.death.progress", record.recoveredStacks(), record.stacks.size())
				.withStyle(ChatFormatting.GRAY);
	}

	// ---------------------------------------------------------------- saving

	private Path file() {
		return ConfigManager.dir().resolve("deaths").resolve((key == null ? "unknown" : key) + ".json");
	}

	public void save() {
		if (key == null || !dirty) {
			return;
		}
		dirty = false;
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				ConfigManager.GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.error("Could not write death inventories {}", file, e);
		}
	}
}
