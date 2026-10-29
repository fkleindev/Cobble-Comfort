package dev.cobblecomfort.features.furnace;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.tracker.TrackerStorage;
import dev.cobblecomfort.features.tracker.TrackerToast;
import dev.cobblecomfort.mixin.AbstractFurnaceMenuAccessor;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Remembers furnaces and brewing stands the player looked into and estimates when they are done. The estimate only
 * counts time while the block's chunk is simulated (the player is near it), because furnaces stand still otherwise.
 * When done: a toast and sound; meanwhile a label above the block and markers on the map.
 */
public final class FurnaceFeature extends Feature<FurnaceConfig> {
	public static final FurnaceFeature INSTANCE = new FurnaceFeature();

	private static final int SNAPSHOT_TICKS = 10;
	private static final int CHECK_TICKS = 20;
	private static final int SAVE_TICKS = 600;
	/** A furnace that went out this long after the last look, while it should still be cooking, has stopped. */
	private static final long OUT_GRACE_MS = 3000;
	private static final float STATUS_SCALE = 0.3f;
	private static final float ITEM_SCALE = 0.22f;
	private static final int RUNNING_COLOR = 0xFFFFC94D;
	private static final int DONE_COLOR = 0xFF6EE06E;
	private static final int WARN_COLOR = 0xFFFF8A4D;
	/** Burn ticks of common fuels, for a furnace whose last fuel is unknown (not lit when looked into). */
	private static final Map<net.minecraft.world.item.Item, Integer> FUEL = Map.of(Items.COAL, 1600, Items.CHARCOAL, 1600,
			Items.COAL_BLOCK, 16000, Items.LAVA_BUCKET, 20000, Items.BLAZE_ROD, 2400, Items.DRIED_KELP_BLOCK, 4000,
			Items.STICK, 100, Items.BAMBOO, 50);
	private static final int DEFAULT_FUEL = 300;

	private final FurnaceStore store = new FurnaceStore();
	private AbstractContainerScreen<?> openScreen;
	private FurnaceEntry openEntry;
	private int ticks;

	private FurnaceFeature() {
		super("furnace", FurnaceConfig.class, FurnaceConfig::new);
	}

	@Override
	public void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) ->
				store.load(TrackerStorage.currentWorldKey(), handler.registryAccess()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
			store.unload();
			openScreen = null;
			openEntry = null;
		}));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?> container
					&& (container.getMenu() instanceof AbstractFurnaceMenu || container.getMenu() instanceof BrewingStandMenu)) {
				onOpen(mc, container);
			}
		});
	}

	@Override
	public ConfigCategory buildCategory() {
		return FurnaceConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- looking into furnaces

	private static FurnaceEntry.Kind kind(Block block) {
		if (block instanceof BlastFurnaceBlock) {
			return FurnaceEntry.Kind.BLAST_FURNACE;
		}
		if (block instanceof SmokerBlock) {
			return FurnaceEntry.Kind.SMOKER;
		}
		if (block instanceof AbstractFurnaceBlock) {
			return FurnaceEntry.Kind.FURNACE;
		}
		return block instanceof BrewingStandBlock ? FurnaceEntry.Kind.BREWING_STAND : null;
	}

	private void onOpen(Minecraft mc, AbstractContainerScreen<?> screen) {
		if (!isEnabled() || !store.loaded() || mc.level == null || screen == openScreen) {
			return;
		}
		// The screen opens right after the click, so the crosshair is still on the block.
		HitResult hit = mc.hitResult;
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = blockHit.getBlockPos().immutable();
		FurnaceEntry.Kind kind = kind(mc.level.getBlockState(pos).getBlock());
		boolean brewing = screen.getMenu() instanceof BrewingStandMenu;
		if (kind == null || (kind == FurnaceEntry.Kind.BREWING_STAND) != brewing || brewing && !config.brewingStands) {
			return;
		}
		String dim = MapFeature.dimensionId(mc.level);
		openScreen = screen;
		openEntry = store.getOrCreate(dim, pos);
		openEntry.kind = kind;
		ScreenEvents.remove(screen).register(s -> {
			if (openScreen == screen) {
				snapshot(screen.getMenu());
				store.save();
				openScreen = null;
				openEntry = null;
			}
		});
	}

	/** Reads the open furnace and recomputes when it will be done. */
	private void snapshot(AbstractContainerMenu menu) {
		FurnaceEntry entry = openEntry;
		if (entry == null) {
			return;
		}
		entry.seen = System.currentTimeMillis();
		boolean keep = menu instanceof AbstractFurnaceMenu furnace ? estimateFurnace(entry, furnace) : estimateBrewing(entry, (BrewingStandMenu) menu);
		// Stays the open entry even while empty, so filling it while the screen is open still gets it remembered.
		if (!keep) {
			store.remove(entry);
			return;
		}
		store.add(entry);
		// The player is looking at it right now, so a finished state needs no notification.
		if (entry.done) {
			entry.notified = true;
		}
		store.changed();
	}

	private boolean estimateFurnace(FurnaceEntry entry, AbstractFurnaceMenu menu) {
		ContainerData data = ((AbstractFurnaceMenuAccessor) menu).cobblecomfort$data();
		int litRemaining = data.get(0);
		int litTotal = data.get(1);
		int timer = data.get(2);
		int cookTotal = data.get(3) > 0 ? data.get(3) : entry.kind.cookTime;
		ItemStack input = menu.getSlot(0).getItem();
		ItemStack fuel = menu.getSlot(1).getItem();
		ItemStack output = menu.getSlot(2).getItem();
		int capacity = output.isEmpty() ? 64 : output.getMaxStackSize() - output.getCount();
		int toCook = Math.min(input.getCount(), capacity);
		if (toCook <= 0) {
			// Nothing to cook: remember it only while results are waiting.
			if (output.isEmpty()) {
				return false;
			}
			setState(entry, output, true, output.getCount(), 0, input.isEmpty() ? FurnaceEntry.Reason.DONE : FurnaceEntry.Reason.FULL);
			return true;
		}
		int perFuel = litTotal > 0 ? litTotal : fuelTicks(fuel, entry.kind);
		long fuelTicks = litRemaining + (long) fuel.getCount() * perFuel;
		int first = Math.max(1, cookTotal - timer);
		long need = first + (long) (toCook - 1) * cookTotal;
		ItemStack shown = output.isEmpty() ? input : output;
		int base = output.isEmpty() ? 0 : output.getCount();
		if (fuelTicks <= 0) {
			setState(entry, shown, !output.isEmpty(), output.isEmpty() ? input.getCount() : base, 0, FurnaceEntry.Reason.FUEL);
		} else if (fuelTicks >= need) {
			FurnaceEntry.Reason reason = toCook < input.getCount() ? FurnaceEntry.Reason.FULL : FurnaceEntry.Reason.DONE;
			setState(entry, shown, !output.isEmpty(), base + toCook, (int) Math.min(Integer.MAX_VALUE, need), reason);
		} else {
			int cooked = fuelTicks < first ? 0 : 1 + (int) ((fuelTicks - first) / cookTotal);
			setState(entry, shown, !output.isEmpty(), base + cooked, (int) fuelTicks, FurnaceEntry.Reason.FUEL);
		}
		return true;
	}

	private boolean estimateBrewing(FurnaceEntry entry, BrewingStandMenu menu) {
		ItemStack bottle = ItemStack.EMPTY;
		int bottles = 0;
		for (int i = 0; i < 3; i++) {
			ItemStack stack = menu.getSlot(i).getItem();
			if (!stack.isEmpty()) {
				bottles++;
				if (bottle.isEmpty()) {
					bottle = stack;
				}
			}
		}
		int brewing = menu.getBrewingTicks();
		if (brewing <= 0 || bottles == 0) {
			return false;
		}
		setState(entry, bottle, false, bottles, brewing, FurnaceEntry.Reason.DONE);
		entry.total = Math.max(brewing, menu.getTotalBrewingTicks());
		ItemStack ingredient = menu.getSlot(3).getItem();
		entry.ingredient = ingredient.isEmpty() ? "" : ingredient.getHoverName().getString();
		return true;
	}

	private void setState(FurnaceEntry entry, ItemStack shown, boolean result, int count, int remaining, FurnaceEntry.Reason reason) {
		store.setIcon(entry, shown);
		entry.result = result;
		entry.count = count;
		entry.remaining = remaining;
		entry.total = remaining;
		entry.reason = reason;
		entry.done = remaining <= 0;
		entry.notified = entry.done;
	}

	private static int fuelTicks(ItemStack fuel, FurnaceEntry.Kind kind) {
		if (fuel.isEmpty()) {
			return 0;
		}
		Integer known = FUEL.get(fuel.getItem());
		int ticks = known != null ? known : fuel.is(ItemTags.LOGS) || fuel.is(ItemTags.PLANKS) ? 300 : DEFAULT_FUEL;
		return kind.halvesFuel() ? ticks / 2 : ticks;
	}

	// ---------------------------------------------------------------- time passing

	private void tick(Minecraft mc) {
		if (!isEnabled() || !store.loaded() || mc.player == null || mc.level == null) {
			return;
		}
		ticks++;
		if (openEntry != null && mc.gui.screen() == openScreen && ticks % SNAPSHOT_TICKS == 0) {
			snapshot(openScreen.getMenu());
		}
		ClientLevel level = mc.level;
		String dim = MapFeature.dimensionId(level);
		boolean check = ticks % CHECK_TICKS == 0;
		for (FurnaceEntry entry : new ArrayList<>(store.all())) {
			if (!entry.dimension.equals(dim) || !level.isLoaded(entry.pos())) {
				continue;
			}
			if (check && !stillThere(level, entry)) {
				store.remove(entry);
				continue;
			}
			if (!entry.done && simulated(mc, entry) && !mc.isPaused() && entry != openEntry) {
				if (--entry.remaining <= 0) {
					finish(mc, entry);
				}
			}
		}
		if (ticks % SAVE_TICKS == 0) {
			store.save();
		}
		drawLabels(mc, mc.player, dim);
	}

	/** Forgets broken furnaces; a furnace that went out early has stopped. */
	private boolean stillThere(ClientLevel level, FurnaceEntry entry) {
		BlockState state = level.getBlockState(entry.pos());
		if (kind(state.getBlock()) != entry.kind) {
			return false;
		}
		if (!entry.done && entry != openEntry && state.getBlock() instanceof AbstractFurnaceBlock
				&& !state.getValue(AbstractFurnaceBlock.LIT) && System.currentTimeMillis() - entry.seen > OUT_GRACE_MS) {
			entry.reason = FurnaceEntry.Reason.STOPPED;
			finish(Minecraft.getInstance(), entry);
		}
		return true;
	}

	/** Whether the server ticks this block now: same dimension and within the simulation distance. */
	private static boolean simulated(Minecraft mc, FurnaceEntry entry) {
		return MapFeature.simulated(mc, entry.dimension, entry.x, entry.z);
	}

	private void finish(Minecraft mc, FurnaceEntry entry) {
		entry.remaining = 0;
		entry.done = true;
		store.changed();
		if (entry.notified) {
			return;
		}
		entry.notified = true;
		if (config.toast) {
			mc.gui.toastManager().addToast(new TrackerToast(entry.stack().isEmpty() ? new ItemStack(Items.FURNACE) : entry.stack(),
					title(entry), amountText(entry)));
		}
		if (config.sound) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f, 0.7f));
		}
	}

	// ---------------------------------------------------------------- texts

	private static Component title(FurnaceEntry entry) {
		String block = entry.kind == FurnaceEntry.Kind.BREWING_STAND ? "brewing" : "furnace";
		String state = switch (entry.reason) {
			case DONE -> "done";
			case FUEL -> "fuel";
			case FULL -> "full";
			case STOPPED -> "stopped";
		};
		return Component.translatable("cobblecomfort.furnace.toast." + block + "." + state);
	}

	/** "64× Iron Ingot", or "32× Raw Iron" while no result is known yet; brews as "3× Potion (Nether Wart)". */
	public static Component amountText(FurnaceEntry entry) {
		if (entry.kind == FurnaceEntry.Kind.BREWING_STAND) {
			MutableComponent text = Component.literal(entry.count + "× ").append(Component.translatable("cobblecomfort.furnace.potion"));
			return entry.ingredient == null || entry.ingredient.isEmpty() ? text : text.append(" (" + entry.ingredient + ")");
		}
		MutableComponent name = entry.stack().isEmpty() ? Component.literal("?") : entry.stack().getHoverName().copy();
		return Component.literal(entry.count + "× ").append(name);
	}

	/** Remaining time as m:ss (h:mm:ss above an hour). */
	public static String time(int ticks) {
		int seconds = (ticks + 19) / 20;
		int h = seconds / 3600;
		int m = seconds / 60 % 60;
		int s = seconds % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
	}

	/** "▶ 2:35", "⏸ 2:35" (not simulated), "✔ done", "⚠ out of fuel" ... */
	public Component status(FurnaceEntry entry) {
		if (!entry.done) {
			boolean running = simulated(Minecraft.getInstance(), entry);
			return Component.literal((running ? "▶ " : "⏸ ") + time(entry.remaining)).withColor(RUNNING_COLOR);
		}
		return switch (entry.reason) {
			case DONE -> Component.translatable("cobblecomfort.furnace.status.done").withColor(DONE_COLOR);
			case FULL -> Component.translatable("cobblecomfort.furnace.status.full").withColor(DONE_COLOR);
			case FUEL -> Component.translatable("cobblecomfort.furnace.status.fuel").withColor(WARN_COLOR);
			case STOPPED -> Component.translatable("cobblecomfort.furnace.status.stopped").withColor(WARN_COLOR);
		};
	}

	/** The colour of the ring around a map marker. */
	public static int color(FurnaceEntry entry) {
		if (!entry.done) {
			return RUNNING_COLOR;
		}
		return entry.reason == FurnaceEntry.Reason.DONE || entry.reason == FurnaceEntry.Reason.FULL ? DONE_COLOR : WARN_COLOR;
	}

	// ---------------------------------------------------------------- markers

	private void drawLabels(Minecraft mc, LocalPlayer player, String dim) {
		if (!config.worldMarkers) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		double max = config.markerDistance * (double) config.markerDistance;
		try (Gizmos.TemporaryCollection ignored = mc.collectPerTickGizmos()) {
			for (FurnaceEntry entry : store.all()) {
				Vec3 top = new Vec3(entry.x + 0.5, entry.y + 1.45, entry.z + 0.5);
				if (!entry.dimension.equals(dim) || top.distanceToSqr(eye) > max || entry == openEntry) {
					continue;
				}
				// Two short lines, so labels of furnaces standing side by side do not run into each other.
				Gizmos.billboardText(status(entry).getString(), top,
						TextGizmo.Style.forColorAndCentered(color(entry)).withScale(STATUS_SCALE));
				Gizmos.billboardText(amountText(entry).getString(), top.subtract(0, 0.25, 0),
						TextGizmo.Style.forColorAndCentered(0xFFE0E0E0).withScale(ITEM_SCALE));
			}
		}
	}

	/** Remembered furnaces in a dimension, for the map. */
	public List<FurnaceEntry> mapEntries(String dimension, boolean minimap) {
		List<FurnaceEntry> list = new ArrayList<>();
		if (!isEnabled() || !store.loaded() || !config.mapMarkers || minimap && !config.minimapMarkers) {
			return list;
		}
		for (FurnaceEntry entry : store.all()) {
			if (entry.dimension.equals(dimension)) {
				list.add(entry);
			}
		}
		return list;
	}

	/** The remembered furnace at a block, for the storage memory list. */
	public FurnaceEntry at(String dimension, BlockPos pos) {
		return isEnabled() && store.loaded() ? store.at(dimension, pos) : null;
	}

	/** The icon of a marker: the block itself. */
	public static ItemStack blockIcon(FurnaceEntry entry) {
		return new ItemStack(switch (entry.kind) {
			case FURNACE -> Items.FURNACE;
			case BLAST_FURNACE -> Items.BLAST_FURNACE;
			case SMOKER -> Items.SMOKER;
			case BREWING_STAND -> Items.BREWING_STAND;
		});
	}
}
