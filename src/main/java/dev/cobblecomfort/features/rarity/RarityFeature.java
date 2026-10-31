package dev.cobblecomfort.features.rarity;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.awt.Color;

/** Colours item slots and tooltip frames by item rarity. */
public final class RarityFeature extends Feature<RarityConfig> {
	public static final RarityFeature INSTANCE = new RarityFeature();

	private static final int SLOT = 16;
	private static final int GLOW_LAYERS = 8;
	private static final float GLOW_LAYER_ALPHA = 0.16f;
	private static final float FILL_ALPHA = 0.6f;
	private static final float SHIMMER_SWEEP_SECONDS = 0.6f;
	private static final int SHIMMER_WIDTH = 4;
	private static final float SHIMMER_ALPHA = 0.45f;
	private static final float HOVER_BOOST = 1.6f;

	/** Where a slot is drawn; each place can be switched off separately. */
	public enum Place {
		INVENTORY, CREATIVE, HOTBAR, ARMOR_HUD
	}

	private RarityFeature() {
		super("rarity", RarityConfig.class, RarityConfig::new);
	}

	@Override
	public void init() {
	}

	@Override
	public ConfigCategory buildCategory() {
		return RarityConfigUi.buildCategory(this);
	}

	private boolean enabledIn(Place place) {
		if (!isEnabled()) {
			return false;
		}
		return switch (place) {
			case INVENTORY -> config.inInventories;
			case CREATIVE -> config.inCreative;
			case HOTBAR -> config.inHotbar;
			case ARMOR_HUD -> config.inArmorHud;
		};
	}

	/** Rarity of the stack if it should get an indicator, otherwise null. */
	private RarityLevel levelOf(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		RarityLevel level = RarityLevel.of(stack.getRarity());
		return level.atLeast(config.minRarity) ? level : null;
	}

	/** The configured rarity colour of a stack, independent of whether the indicator is enabled. */
	public int rarityColor(ItemStack stack) {
		return colorOf(RarityLevel.of(stack.getRarity()), 0, 0);
	}

	private int colorOf(RarityLevel level, int x, int y) {
		if (level == RarityLevel.EPIC && config.rainbowEpic) {
			float hue = (System.currentTimeMillis() / 1000f * config.rainbowSpeed + (x + y) / 100f) % 1f;
			return Color.HSBtoRGB(hue, 0.6f, 1f) | 0xFF000000;
		}
		return switch (level) {
			case COMMON -> config.commonColor;
			case UNCOMMON -> config.uncommonColor;
			case RARE -> config.rareColor;
			case EPIC -> config.epicColor;
		};
	}

	/** Draws the indicator behind the item of a 16x16 slot at (x, y). */
	public void drawSlot(GuiGraphicsExtractor g, ItemStack stack, int x, int y, boolean hovered, Place place) {
		if (!enabledIn(place)) {
			return;
		}
		RarityLevel level = levelOf(stack);
		if (level == null) {
			return;
		}
		int color = colorOf(level, x, y);
		float alpha = config.intensity;
		if (config.pulse && level.atLeast(config.pulseFrom)) {
			double wave = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1000.0 * Math.PI * 2 * config.pulseSpeed);
			alpha *= 1f - config.pulseStrength * (float) wave;
		}
		int thickness = config.thickness;
		if (hovered && config.hoverHighlight) {
			alpha = Math.min(1f, alpha * HOVER_BOOST);
			thickness++;
		}

		switch (config.style) {
			case GLOW -> {
				int layer = RenderUtil.withAlpha(color, alpha * GLOW_LAYER_ALPHA);
				for (int i = 0; i < GLOW_LAYERS; i++) {
					g.fill(x + i, y + i, x + SLOT - i, y + SLOT - i, layer);
				}
			}
			case FILL -> g.fill(x, y, x + SLOT, y + SLOT, RenderUtil.withAlpha(color, alpha * FILL_ALPHA));
			case FRAME -> {
				int c = RenderUtil.withAlpha(color, alpha);
				int t = Math.min(thickness, SLOT / 2);
				g.fill(x, y, x + SLOT, y + t, c);
				g.fill(x, y + SLOT - t, x + SLOT, y + SLOT, c);
				g.fill(x, y + t, x + t, y + SLOT - t, c);
				g.fill(x + SLOT - t, y + t, x + SLOT, y + SLOT - t, c);
			}
			case CORNER -> {
				int c = RenderUtil.withAlpha(color, alpha);
				int size = Math.min(SLOT, config.cornerSize + (hovered && config.hoverHighlight ? 1 : 0));
				for (int i = 0; i < size; i++) {
					g.fill(x, y + i, x + size - i, y + i + 1, c);
				}
			}
			case UNDERLINE -> g.fill(x, y + SLOT - Math.min(thickness, SLOT), x + SLOT, y + SLOT,
					RenderUtil.withAlpha(color, alpha));
		}

		if (config.shimmer && level.atLeast(config.shimmerFrom)) {
			drawShimmer(g, x, y, alpha);
		}
	}

	/** A light band sweeping diagonally across the slot every few seconds; slots are slightly staggered. */
	private void drawShimmer(GuiGraphicsExtractor g, int x, int y, float alpha) {
		float interval = Math.max(config.shimmerInterval, SHIMMER_SWEEP_SECONDS);
		float t = (System.currentTimeMillis() / 1000f + (x + y) * 0.004f) % interval;
		if (t >= SHIMMER_SWEEP_SECONDS) {
			return;
		}
		float progress = t / SHIMMER_SWEEP_SECONDS;
		int diagonal = Math.round(-SHIMMER_WIDTH + progress * (SLOT * 2 + SHIMMER_WIDTH));
		int color = RenderUtil.withAlpha(0xFFFFFFFF, SHIMMER_ALPHA * Math.min(1f, alpha * 1.5f));
		for (int row = 0; row < SLOT; row++) {
			int start = Math.clamp(diagonal - row, 0, SLOT);
			int end = Math.clamp(diagonal - row + SHIMMER_WIDTH, 0, SLOT);
			if (end > start) {
				g.fill(x + start, y + row, x + end, y + row + 1, color);
			}
		}
	}

	/**
	 * Replaces the vanilla tooltip frame with one in the rarity colour. Returns false if the vanilla
	 * frame should be drawn instead.
	 */
	public boolean drawTooltipFrame(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int width, int height) {
		if (!isEnabled() || !config.tooltipFrame) {
			return false;
		}
		RarityLevel level = levelOf(stack);
		if (level == null) {
			return false;
		}
		int top = colorOf(level, x, y) | 0xFF000000;
		int bottom = RenderUtil.lerpColor(top, 0xFF000000, 0.5f);
		// The frame sprite is nine-sliced with a 9 px transparent margin around a 1 px line.
		RenderUtil.roundedOutline(g, x + 9, y + 9, x + width - 9, y + height - 9, 0, top, bottom);
		return true;
	}
}
