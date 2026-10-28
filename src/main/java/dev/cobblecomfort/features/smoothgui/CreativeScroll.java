package dev.cobblecomfort.features.smoothgui;

import dev.cobblecomfort.features.hud.anim.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Smooth scrolling in the creative inventory: the grid jumps a row per wheel step, so for a moment it is drawn shifted
 * back to where it was and slides into place, with the rows that come into view drawn at the edge.
 */
public final class CreativeScroll {
	private static final int ROW = 18;
	private static final int ROWS = 5;
	private static final int COLUMNS = 9;
	private static final float MAX_OFFSET = 4 * ROW;
	private static final float TAB_SECONDS = 0.18f;
	private static final float TAB_RISE = 3f;

	/** How far the grid is drawn below where it is, in pixels. */
	private static float offset;
	private static long nanos;
	private static int gridX, gridY;
	private static boolean active;
	private static long tabNanos;
	private static boolean fading;
	private static float savedFade = 1f;

	private CreativeScroll() {
	}

	/** A new tab was picked: its button rises and the grid fades in. */
	public static void tabChanged() {
		tabNanos = System.nanoTime();
		offset = 0;
	}

	private static float tabProgress() {
		if (tabNanos == 0 || !WidgetAnim.enabled(WidgetAnim.Kind.TABS)) {
			return 1f;
		}
		return Math.min(1f, (System.nanoTime() - tabNanos) / 1e9f / TAB_SECONDS);
	}

	/** How far the selected tab is still drawn towards the panel (top tabs down, bottom tabs up). */
	public static float tabOffset(boolean top) {
		float rest = TAB_RISE * (1f - Easing.EASE_OUT_CUBIC.apply(tabProgress()));
		return top ? rest : -rest;
	}

	/** Start of the slots: fades the grid in after a tab change. */
	public static void beginFade() {
		float t = tabProgress();
		fading = t < 1f;
		if (fading) {
			savedFade = ScreenFade.push(Easing.EASE_OUT_CUBIC.apply(t));
		}
	}

	public static void endFade() {
		if (fading) {
			ScreenFade.restore(savedFade);
			fading = false;
		}
	}

	/** The wheel moved the grid by {@code rows}. */
	public static void scrolled(int rows) {
		if (rows != 0 && SmoothScroll.enabled(SmoothScroll.Kind.CREATIVE)) {
			offset = Math.clamp(offset + rows * ROW, -MAX_OFFSET, MAX_OFFSET);
		}
	}

	/** Start of the slots: moves the slide on and finds the grid. */
	public static void beginSlots(List<Slot> slots, Container grid) {
		long now = System.nanoTime();
		float seconds = SmoothScroll.elapsed(nanos, now);
		nanos = now;
		offset -= (float) (offset * SmoothScroll.approach(seconds));
		if (Math.abs(offset) < 0.3f || !SmoothScroll.enabled(SmoothScroll.Kind.CREATIVE)) {
			offset = 0;
		}
		active = false;
		if (offset == 0) {
			return;
		}
		for (Slot slot : slots) {
			if (slot.container == grid && slot.getContainerSlot() == 0) {
				gridX = slot.x;
				gridY = slot.y;
				active = true;
				return;
			}
		}
	}

	/** Draws a slot, shifted and cut off at the grid's edge when it belongs to the sliding grid. */
	public static void slot(GuiGraphicsExtractor g, Slot slot, Container grid, Runnable draw) {
		if (!active || slot.container != grid) {
			draw.run();
			return;
		}
		clip(g);
		g.pose().pushMatrix();
		g.pose().translate(0, offset);
		draw.run();
		g.pose().popMatrix();
		g.disableScissor();
	}

	private static void clip(GuiGraphicsExtractor g) {
		g.enableScissor(gridX - 1, gridY - 1, gridX + COLUMNS * ROW - 1, gridY + ROWS * ROW - 1);
	}

	/** After the slots: the rows sliding in at the edge the grid moved away from. */
	public static void endSlots(GuiGraphicsExtractor g, NonNullList<ItemStack> items, int topRow) {
		if (!active) {
			return;
		}
		clip(g);
		int extra = (int) Math.ceil(Math.abs(offset) / ROW);
		for (int k = 1; k <= extra; k++) {
			int row = offset > 0 ? topRow - k : topRow + ROWS - 1 + k;
			float y = offset > 0 ? gridY + offset - k * ROW : gridY + (ROWS - 1 + k) * ROW + offset;
			for (int column = 0; column < COLUMNS; column++) {
				int index = row * COLUMNS + column;
				if (row < 0 || index >= items.size() || items.get(index).isEmpty()) {
					continue;
				}
				int x = gridX + column * ROW;
				g.pose().pushMatrix();
				g.pose().translate(0, y - gridY);
				g.item(items.get(index), x, gridY);
				g.itemDecorations(Minecraft.getInstance().font, items.get(index), x, gridY);
				g.pose().popMatrix();
			}
		}
		g.disableScissor();
	}
}
