package dev.cobblecomfort.features.inventoryfx;

import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.mixin.ContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Workstation animations: furnace flames and arrows move smoothly instead of in pixel steps and a finished item glints;
 * the anvil, grindstone and smithing table show their result with a clonk and sparks and the anvil's XP cost counts up;
 * trading screens slide new offers into the list and fill the level bar smoothly.
 */
public final class StationFx {
	private static final float GLINT_MS = 650f;
	private static final float COST_MS = 450f;
	private static final float SLIDE_MS = 520f;
	private static final long SLIDE_STAGGER_MS = 120;
	private static final int SLIDE_DISTANCE = 100;
	/** The trading screen's offer list, relative to the GUI. */
	private static final int LIST_LEFT = 4;
	private static final int LIST_TOP = 17;
	private static final int LIST_WIDTH = 96;
	private static final int LIST_HEIGHT = 140;
	private static final int FURNACE_RESULT = 2;
	private static final int GRINDSTONE_RESULT = 2;

	/**
	 * A progress value the server only updates once per tick, followed smoothly: it keeps moving at its last rate for
	 * a moment between updates, and jumps when the value jumps (a new item, new fuel).
	 */
	private static final class Smoothed {
		float shown = -1f;
		float last;
		float rate;
		long changedNanos;
		long frameNanos;

		float update(float raw) {
			long now = System.nanoTime();
			float dt = frameNanos == 0 ? 0f : Math.min((now - frameNanos) / 1e9f, 0.1f);
			frameNanos = now;
			if (shown < 0f || Math.abs(raw - last) > 0.15f) {
				shown = last = raw;
				rate = 0f;
				changedNanos = now;
				return raw;
			}
			if (raw != last) {
				float since = (now - changedNanos) / 1e9f;
				rate = since > 0.01f && since < 0.5f ? (raw - last) / since : 0f;
				last = raw;
				changedNanos = now;
			}
			float ahead = Math.min((now - changedNanos) / 1e9f, 0.1f);
			float target = Math.clamp(last + rate * ahead, 0f, 1f);
			shown += (target - shown) * (1f - (float) Math.exp(-25f * dt));
			return shown;
		}
	}

	private final InventoryFxFeature feature;
	private Object screen;
	private final Smoothed lit = new Smoothed();
	private final Smoothed burn = new Smoothed();
	private float litShown;
	private float burnShown;
	private int resultCount = -1;
	private long glintAt;
	private ItemStack result = ItemStack.EMPTY;
	private int costFrom;
	private int costTarget = -1;
	private int costShown;
	private long costStart;
	private int knownOffers = -1;
	private final Map<Integer, Long> slideAt = new HashMap<>();
	private int barLevel;
	private float barXp = -1f;
	private long barNanos;

	StationFx(InventoryFxFeature feature) {
		this.feature = feature;
	}

	private boolean stations() {
		return feature.isEnabled() && feature.config().stations;
	}

	private boolean trading() {
		return feature.isEnabled() && feature.config().trading;
	}

	void init() {
		ScreenEvents.AFTER_INIT.register((mc, s, width, height) -> {
			if (s instanceof AbstractFurnaceScreen<?> || s instanceof ItemCombinerScreen<?> || s instanceof GrindstoneScreen
					|| s instanceof MerchantScreen) {
				AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) s;
				ScreenEvents.afterExtract(s).register((s2, g, mouseX, mouseY, delta) -> afterFrame(container, g));
			}
		});
	}

	/** A new screen starts without animations from the last one. */
	private void check(Object current) {
		if (current != screen) {
			screen = current;
			lit.shown = burn.shown = -1f;
			resultCount = -1;
			glintAt = 0;
			result = ItemStack.EMPTY;
			costTarget = -1;
			knownOffers = -1;
			slideAt.clear();
			barXp = -1f;
		}
	}

	// ---------------------------------------------------------------- furnace

	public float litProgress(Object screen, float raw) {
		check(screen);
		litShown = stations() ? lit.update(raw) : raw;
		return litShown;
	}

	public float burnProgress(Object screen, float raw) {
		check(screen);
		burnShown = stations() ? burn.update(raw) : raw;
		return burnShown;
	}

	/**
	 * Draws the flame (rising from the bottom, 14x14) or arrow (growing to the right, 24x16) with the smooth progress:
	 * whole pixels as usual and the next pixel row or column half see-through by how far it is reached.
	 */
	public boolean drawProgress(GuiGraphicsExtractor g, boolean flame, ProgressBlit blit, int x, int y) {
		if (!stations()) {
			return false;
		}
		if (flame) {
			float exact = litShown * 13f + 1f;
			int full = (int) Math.floor(exact);
			float part = exact - full;
			if (litShown > 0f) {
				blit.draw(0, 14 - full, x, y + 14 - full, 14, full, 0xFFFFFFFF);
			}
			if (litShown > 0f && part > 0.01f && full < 14) {
				blit.draw(0, 13 - full, x, y + 13 - full, 14, 1, RenderUtil.withAlpha(0xFFFFFFFF, part));
			}
		} else {
			float exact = burnShown * 24f;
			int full = (int) Math.floor(exact);
			float part = exact - full;
			if (full > 0) {
				blit.draw(0, 0, x, y, full, 16, 0xFFFFFFFF);
			}
			if (part > 0.01f && full < 24) {
				blit.draw(full, 0, x + full, y, 1, 16, RenderUtil.withAlpha(0xFFFFFFFF, part));
			}
		}
		return true;
	}

	/** Draws part of a progress sprite: texture offset, screen place, size and colour. */
	@FunctionalInterface
	public interface ProgressBlit {
		void draw(int u, int v, int x, int y, int w, int h, int color);
	}

	// ---------------------------------------------------------------- anvil

	/** The XP cost shown: counts up to a new cost (from 0 when a result has just appeared). */
	public int anvilCost(Object screen, int cost) {
		check(screen);
		if (!stations()) {
			return cost;
		}
		long now = System.currentTimeMillis();
		if (cost != costTarget) {
			costFrom = costTarget < 0 ? 0 : costShown;
			costTarget = cost;
			costStart = now;
		}
		float t = Math.min(1f, (now - costStart) / COST_MS);
		float eased = 1f - (1f - t) * (1f - t) * (1f - t);
		costShown = Math.round(costFrom + (costTarget - costFrom) * eased);
		return costShown;
	}

	// ---------------------------------------------------------------- trading

	/** Notices offers that came in while the screen is open (a level up), so they slide in. */
	public void merchantFrame(Object screen, int offers) {
		check(screen);
		if (!trading()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (knownOffers > 0 && offers > knownOffers) {
			for (int i = knownOffers; i < offers; i++) {
				slideAt.put(i, now + (i - knownOffers) * SLIDE_STAGGER_MS);
			}
		}
		if (offers > 0) {
			knownOffers = offers;
		}
	}

	/** How far an offer's row is still pushed to the left as it slides in (0 once in place). */
	public float slideOffset(int offer) {
		Long start = slideAt.get(offer);
		if (start == null) {
			return 0f;
		}
		float t = (System.currentTimeMillis() - start) / SLIDE_MS;
		if (t >= 1f) {
			slideAt.remove(offer);
			return 0f;
		}
		t = Math.max(0f, t);
		// Ease out with a slight overshoot.
		float eased = 1f + 1.6f * (float) Math.pow(t - 1f, 3) + 0.6f * (float) Math.pow(t - 1f, 2);
		return -(1f - eased) * SLIDE_DISTANCE;
	}

	/** Moves what follows by {@code offset}, cut to the offer list so it slides out from under its edge. */
	public void beginSlide(GuiGraphicsExtractor g, ContainerScreenAccessor gui, float offset) {
		int left = gui.cobblecomfort$leftPos() + LIST_LEFT;
		int top = gui.cobblecomfort$topPos() + LIST_TOP;
		g.enableScissor(left, top, left + LIST_WIDTH, top + LIST_HEIGHT);
		g.pose().pushMatrix();
		g.pose().translate(offset, 0);
	}

	public void endSlide(GuiGraphicsExtractor g) {
		g.pose().popMatrix();
		g.disableScissor();
	}

	/** The level the trader's bar shows: the old one until its bar has filled up after a level up. */
	public int barLevel(MerchantMenu menu, int level) {
		if (!trading()) {
			return level;
		}
		int xp = menu.getTraderXp();
		long now = System.nanoTime();
		float dt = barNanos == 0 ? 0f : Math.min((now - barNanos) / 1e9f, 0.1f);
		barNanos = now;
		if (barXp < 0f || level < barLevel) {
			barLevel = level;
			barXp = xp;
		}
		boolean levelling = level > barLevel && VillagerData.canLevelUp(barLevel);
		float target = levelling ? VillagerData.getMaxXpPerLevel(barLevel) : xp;
		barXp += (target - barXp) * (1f - (float) Math.exp(-5f * dt));
		if (Math.abs(target - barXp) < 0.3f) {
			barXp = target;
			if (levelling) {
				// Full: on to the new level, starting empty.
				barLevel++;
				barXp = VillagerData.getMinXpPerLevel(barLevel);
			}
		}
		if (level > barLevel && !VillagerData.canLevelUp(barLevel)) {
			barLevel = level;
		}
		return barLevel;
	}

	/** The trader XP the bar shows, gliding (whole points; the bar is 102 pixels wide). */
	public int barXp(int xp) {
		return trading() && barXp >= 0f ? Math.round(barXp) : xp;
	}

	// ---------------------------------------------------------------- results

	private void afterFrame(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
		check(s);
		if (!stations()) {
			return;
		}
		ContainerScreenAccessor gui = (ContainerScreenAccessor) s;
		if (s instanceof AbstractFurnaceScreen<?>) {
			furnaceResult(s, g, gui);
		} else if (!(s instanceof MerchantScreen)) {
			int index = s.getMenu() instanceof ItemCombinerMenu combiner ? combiner.getResultSlot() : GRINDSTONE_RESULT;
			combinerResult(s.getMenu().slots.get(index));
		}
	}

	/** A finished item pops and a glint sweeps over the output slot. */
	private void furnaceResult(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, ContainerScreenAccessor gui) {
		Slot slot = s.getMenu().slots.get(FURNACE_RESULT);
		int count = slot.getItem().getCount();
		long now = System.currentTimeMillis();
		if (resultCount >= 0 && count > resultCount) {
			glintAt = now;
			feature.popSlots(List.of(slot), 0);
		}
		resultCount = count;
		float t = (now - glintAt) / GLINT_MS;
		if (glintAt == 0 || t >= 1f || slot.getItem().isEmpty()) {
			return;
		}
		int x = gui.cobblecomfort$leftPos() + slot.x;
		int y = gui.cobblecomfort$topPos() + slot.y;
		g.nextStratum();
		g.enableScissor(x, y, x + 16, y + 16);
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x - 10 + 36 * t, y + 8);
		pose.rotate((float) Math.toRadians(25));
		float alpha = (float) Math.sin(Math.PI * t);
		g.fill(-2, -14, 2, 14, RenderUtil.withAlpha(0xFFFFFFFF, 0.75f * alpha));
		g.fill(3, -14, 4, 14, RenderUtil.withAlpha(0xFFFFFFFF, 0.4f * alpha));
		pose.popMatrix();
		g.disableScissor();
		g.fill(x, y, x + 16, y + 16, RenderUtil.withAlpha(0xFFFFF4C0, 0.25f * (1f - t)));
	}

	/** A result appearing in the anvil, grindstone or smithing table: a clonk wobble with sparks. */
	private void combinerResult(Slot slot) {
		ItemStack now = slot.getItem();
		boolean appeared = !now.isEmpty() && (result.isEmpty() || !ItemStack.isSameItem(now, result));
		if (appeared && resultCount >= 0) {
			feature.clonk(slot);
		}
		resultCount = 0;
		result = now.copy();
		if (now.isEmpty()) {
			costTarget = -1;
		}
	}
}
