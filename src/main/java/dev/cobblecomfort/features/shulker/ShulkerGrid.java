package dev.cobblecomfort.features.shulker;

import dev.cobblecomfort.features.hud.anim.Easing;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.rarity.RarityFeature;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;

/** Draws a container-style item grid tinted in the shulker box's colour, with optional pop-in animation. */
final class ShulkerGrid {
	static final int CELL = 18;
	static final int PAD = 4;
	private static final int HALF_ITEM = 8;
	private static final int OUTLINE = 0xFF1E1E1E;
	private static final int BACKGROUND = 0xFFC6C6C6;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int SLOT_LIGHT = 0xFFFFFFFF;

	/** One cell; {@code text} replaces the stack's count text (null = vanilla count). */
	record Cell(ItemStack stack, String text) {
	}

	private ShulkerGrid() {
	}

	static int width(int columns) {
		return columns * CELL + 2 * PAD;
	}

	static int height(int rows) {
		return rows * CELL + 2 * PAD;
	}

	/**
	 * @param tint        ARGB colour the grid is tinted with
	 * @param strength    how strongly (0 = plain vanilla grey)
	 * @param animStartMs start of the pop-in, or 0 for none
	 */
	static void draw(GuiGraphicsExtractor g, Font font, int x, int y, List<Cell> cells, int columns, int rows,
			int tint, float strength, long animStartMs, ShulkerPreviewConfig config) {
		int w = width(columns);
		int h = height(rows);
		RenderUtil.roundedFill(g, x, y, x + w, y + h, 3, OUTLINE);
		RenderUtil.roundedFill(g, x + 1, y + 1, x + w - 1, y + h - 1, 2, RenderUtil.lerpColor(BACKGROUND, tint, strength));
		int slot = RenderUtil.lerpColor(SLOT, tint, strength);
		int shadow = RenderUtil.lerpColor(SLOT_SHADOW, tint, strength * 0.5f);
		int light = RenderUtil.lerpColor(SLOT_LIGHT, tint, strength * 0.5f);
		long now = System.currentTimeMillis();
		for (int i = 0; i < columns * rows; i++) {
			int cx = x + PAD + (i % columns) * CELL;
			int cy = y + PAD + (i / columns) * CELL;
			g.fill(cx, cy, cx + CELL, cy + CELL, light);
			g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, shadow);
			g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, slot);
			if (i >= cells.size() || cells.get(i).stack().isEmpty()) {
				continue;
			}
			float scale = 1f;
			if (animStartMs > 0) {
				float t = (now - animStartMs - (long) i * config.staggerMs) / (Math.max(0.05f, config.popDuration) * 1000f);
				if (t <= 0f) {
					continue;
				}
				scale = t >= 1f ? 1f : Easing.EASE_OUT_BACK.apply(t);
			}
			drawItem(g, font, cells.get(i), cx + 1, cy + 1, scale, config.rarityGlow);
		}
	}

	private static void drawItem(GuiGraphicsExtractor g, Font font, Cell cell, int ix, int iy, float scale, boolean rarity) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		if (scale != 1f) {
			pose.translate(ix + HALF_ITEM, iy + HALF_ITEM);
			pose.scale(Math.max(0.01f, scale));
			pose.translate(-ix - HALF_ITEM, -iy - HALF_ITEM);
		}
		if (rarity) {
			RarityFeature.INSTANCE.drawSlot(g, cell.stack(), ix, iy, false, RarityFeature.Place.INVENTORY);
		}
		g.item(cell.stack(), ix, iy);
		g.itemDecorations(font, cell.stack(), ix, iy, cell.text());
		pose.popMatrix();
	}

	/** Short count text: 1-999 as is, then 1.2k style. */
	static String shortCount(int count) {
		if (count < 1000) {
			return String.valueOf(count);
		}
		float k = count / 1000f;
		return (k < 10 ? String.format(java.util.Locale.ROOT, "%.1f", k) : String.valueOf(Math.round(k))) + "k";
	}
}
