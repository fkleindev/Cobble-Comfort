package dev.cobblecomfort.features.tracker;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Resource goals per world with a progress HUD, counting the inventory and remembered nearby chests. */
public final class TrackerFeature extends Feature<TrackerConfig> {
	public static final TrackerFeature INSTANCE = new TrackerFeature();
	public static final Identifier LAYER_ID = CobbleComfortClient.id("tracker");
	private static final int RECOUNT_TICKS = 5;
	/** Max distance between the player and a container that was just opened, to attribute the screen to it. */
	private static final double MAX_OPEN_DISTANCE_SQ = 8 * 8;

	private final TrackerHud hud = new TrackerHud(this);
	private KeyMapping key;
	private String worldKey;
	private TrackerData data;
	private Map<String, Integer> inventoryCounts = Map.of();
	private Map<String, Integer> nearbyCounts = Map.of();
	private int tickCounter;
	private BlockPos lookedAtContainer;
	private BlockPos openContainer;
	private Screen lastScreen;
	private Consumer<TrackerData.Goal> completionListener = goal -> {
	};

	private TrackerFeature() {
		super("tracker", TrackerConfig.class, TrackerConfig::new);
	}

	@Override
	public void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.cobblecomfort.tracker", InputConstants.KEY_K, CobbleComfortClient.keyCategory()));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> loadWorld());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> unloadWorld());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LAYER_ID, hud);
		completionListener = hud::onCompleted;
	}

	@Override
	public ConfigCategory buildCategory() {
		return TrackerConfigUi.buildCategory(this);
	}

	public TrackerHud hud() {
		return hud;
	}

	// ---------------------------------------------------------------- data access

	public List<TrackerData.Goal> goals() {
		return data != null ? data.goals : List.of();
	}

	public boolean hasWorld() {
		return data != null;
	}

	public int inventoryCount(String item) {
		return inventoryCounts.getOrDefault(item, 0);
	}

	public int nearbyCount(String item) {
		return config.nearbyChests ? nearbyCounts.getOrDefault(item, 0) : 0;
	}

	/** Amount that counts towards the goal. */
	public int progress(TrackerData.Goal goal) {
		return inventoryCount(goal.item) + (config.nearbyCountsForGoal ? nearbyCount(goal.item) : 0);
	}

	public void addGoal(Item item, int amount) {
		if (data == null || amount <= 0) {
			return;
		}
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		TrackerData.Goal goal = data.goal(id);
		if (goal != null) {
			goal.target += amount;
			goal.celebrated = false;
		} else {
			data.goals.add(new TrackerData.Goal(id, amount));
		}
		save();
	}

	public void setTarget(TrackerData.Goal goal, int target) {
		goal.target = Math.max(1, target);
		goal.celebrated = progress(goal) >= goal.target;
		save();
	}

	public void removeGoal(TrackerData.Goal goal) {
		if (data != null) {
			data.goals.remove(goal);
			save();
		}
	}

	public void removeCompleted() {
		if (data != null) {
			data.goals.removeIf(goal -> progress(goal) >= goal.target);
			save();
		}
	}

	public void save() {
		if (data != null && worldKey != null) {
			TrackerStorage.save(worldKey, data);
		}
	}

	private void loadWorld() {
		worldKey = TrackerStorage.currentWorldKey();
		data = TrackerStorage.load(worldKey);
		inventoryCounts = Map.of();
		nearbyCounts = Map.of();
		hud.reset();
	}

	private void unloadWorld() {
		save();
		data = null;
		worldKey = null;
		openContainer = null;
		lookedAtContainer = null;
	}

	// ---------------------------------------------------------------- tick

	private void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (!isEnabled() || player == null || mc.level == null) {
			return;
		}
		if (data == null) {
			loadWorld();
		}
		while (key.consumeClick()) {
			mc.gui.setScreen(new TrackerScreen(this, mc.gui.screen()));
		}

		Screen screen = mc.gui.screen();
		if (screen == null) {
			lookedAtContainer = containerUnderCrosshair(mc);
		}
		if (screen != lastScreen) {
			if (lastScreen != null && openContainer != null) {
				save();
			}
			openContainer = isContainerScreen(screen) ? lookedAtContainer : null;
			lastScreen = screen;
		}
		if (config.nearbyChests && openContainer != null && screen instanceof AbstractContainerScreen<?> containerScreen) {
			rememberContainer(mc, openContainer, containerScreen);
		}

		if (++tickCounter % RECOUNT_TICKS == 0) {
			recount(mc, player);
		}
	}

	private static boolean isContainerScreen(Screen screen) {
		return screen instanceof AbstractContainerScreen<?>
				&& !(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen);
	}

	private static BlockPos containerUnderCrosshair(Minecraft mc) {
		HitResult hit = mc.hitResult;
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			if (mc.level.getBlockEntity(pos) instanceof BaseContainerBlockEntity
					&& Vec3.atCenterOf(pos).distanceToSqr(mc.player.getEyePosition()) < MAX_OPEN_DISTANCE_SQ) {
				return pos.immutable();
			}
		}
		return null;
	}

	private void rememberContainer(Minecraft mc, BlockPos pos, AbstractContainerScreen<?> screen) {
		Map<String, Integer> items = new HashMap<>();
		Inventory playerInventory = mc.player.getInventory();
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container != playerInventory) {
				ItemCounter.add(items, slot.getItem(), config, 0);
			}
		}
		String dimension = mc.level.dimension().identifier().toString();
		String key = TrackerData.containerKey(dimension, pos.getX(), pos.getY(), pos.getZ());
		TrackerData.RememberedContainer container = data.containers.computeIfAbsent(key, k -> {
			TrackerData.RememberedContainer c = new TrackerData.RememberedContainer();
			c.dimension = dimension;
			c.x = pos.getX();
			c.y = pos.getY();
			c.z = pos.getZ();
			return c;
		});
		container.items = items;
	}

	private void recount(Minecraft mc, LocalPlayer player) {
		inventoryCounts = ItemCounter.countInventory(player.getInventory(), config);

		Map<String, Integer> nearby = new HashMap<>();
		if (config.nearbyChests) {
			String dimension = mc.level.dimension().identifier().toString();
			double radiusSq = (double) config.nearbyRadius * config.nearbyRadius;
			Iterator<TrackerData.RememberedContainer> it = data.containers.values().iterator();
			while (it.hasNext()) {
				TrackerData.RememberedContainer c = it.next();
				if (!c.dimension.equals(dimension)) {
					continue;
				}
				BlockPos pos = new BlockPos(c.x, c.y, c.z);
				// Forget containers that were broken (only checkable while their chunk is loaded).
				if (mc.level.isLoaded(pos) && !(mc.level.getBlockEntity(pos) instanceof BaseContainerBlockEntity)) {
					it.remove();
					continue;
				}
				if (Vec3.atCenterOf(pos).distanceToSqr(player.position()) <= radiusSq) {
					c.items.forEach((item, count) -> nearby.merge(item, count, Integer::sum));
				}
			}
		}
		nearbyCounts = nearby;

		boolean changed = false;
		for (TrackerData.Goal goal : data.goals) {
			boolean done = progress(goal) >= goal.target;
			if (done && !goal.celebrated) {
				goal.celebrated = true;
				changed = true;
				completionListener.accept(goal);
			} else if (!done && goal.celebrated) {
				goal.celebrated = false;
				changed = true;
			}
		}
		if (changed) {
			save();
		}
	}
}
