package dev.cobblecomfort.features.effects;

import dev.cobblecomfort.features.hud.render.RenderUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import org.joml.Matrix3x2fStack;

/** Draws one effect in each of the three styles. All colours are multiplied by {@code alpha}. */
final class EffectRenderer {
	static final int ROW_HEIGHT = 20;
	static final int CELL_WIDTH = 24;
	static final int RING_HEIGHT = 32;
	static final int COMPACT_HEIGHT = 38;
	static final int GAP = 2;

	private static final int ICON = 18;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int TIME = 0xFFAAAAAA;
	private static final int TIME_AMBIENT = 0xFF55FFFF;
	private static final int BAR_BACKGROUND = 0x60000000;
	private static final int RING_BACKGROUND = 0x80000000;
	private static final int NEUTRAL = 0xFFAAAAAA;
	private static final float RING_OUTER = 11f;
	private static final float RING_INNER = 9f;
	private static final float SHIMMER_SECONDS = 2f;

	private EffectRenderer() {
	}

	static int height(EffectStyle style) {
		return switch (style) {
			case LIST -> ROW_HEIGHT;
			case RINGS -> RING_HEIGHT;
			case COMPACT -> COMPACT_HEIGHT;
		};
	}

	static int width(EffectStyle style, EffectHudConfig config) {
		return style == EffectStyle.LIST ? config.listWidth : CELL_WIDTH;
	}

	static void draw(GuiGraphicsExtractor g, Font font, EffectStyle style, EffectHudConfig config, int x, int y, int width,
			MobEffectInstance effect, float progress, float alpha) {
		switch (style) {
			case LIST -> drawRow(g, font, config, x, y, width, effect, progress, alpha);
			case RINGS -> drawRing(g, font, config, x, y, effect, progress, alpha);
			case COMPACT -> drawCompact(g, font, config, x, y, effect, alpha);
		}
	}

	private static void drawRow(GuiGraphicsExtractor g, Font font, EffectHudConfig config, int x, int y, int w,
			MobEffectInstance effect, float progress, float alpha) {
		if (config.background) {
			RenderUtil.roundedFill(g, x, y, x + w, y + ROW_HEIGHT, 3, RenderUtil.withAlpha(config.backgroundColor, alpha));
		}
		if (config.categoryMark) {
			g.fill(x + 1, y + 3, x + 3, y + ROW_HEIGHT - 3, RenderUtil.withAlpha(categoryColor(effect, config), alpha));
		}
		icon(g, effect, x + 4, y + 1, alpha);
		String time = time(effect);
		int timeWidth = font.width(time);
		int textX = x + 25;
		String name = font.plainSubstrByWidth(name(effect, config).getString(), w - 25 - timeWidth - 8);
		g.text(font, name, textX, y + 2, RenderUtil.withAlpha(TEXT, alpha), true);
		g.text(font, time, x + w - 4 - timeWidth, y + 2, RenderUtil.withAlpha(effect.isAmbient() ? TIME_AMBIENT : TIME, alpha), true);
		int barX0 = textX;
		int barX1 = x + w - 4;
		int barY = y + 13;
		g.fill(barX0, barY, barX1, barY + 3, RenderUtil.withAlpha(BAR_BACKGROUND, alpha));
		int filled = Math.round((barX1 - barX0) * Math.clamp(progress, 0f, 1f));
		int color = barColor(effect, config);
		g.fill(barX0, barY, barX0 + filled, barY + 2, RenderUtil.withAlpha(color, alpha));
		g.fill(barX0, barY + 2, barX0 + filled, barY + 3, RenderUtil.withAlpha(RenderUtil.lerpColor(color, 0xFF000000, 0.4f), alpha));
		if (effect.isInfiniteDuration() || effect.isAmbient()) {
			float t = (System.currentTimeMillis() % (long) (SHIMMER_SECONDS * 1000)) / (SHIMMER_SECONDS * 1000f);
			int sx = barX0 + Math.round((barX1 - barX0 + 8) * t) - 8;
			g.fill(Math.max(barX0, sx), barY, Math.min(barX1, sx + 6), barY + 2, RenderUtil.withAlpha(0x80FFFFFF, alpha));
		}
	}

	private static void drawRing(GuiGraphicsExtractor g, Font font, EffectHudConfig config, int x, int y,
			MobEffectInstance effect, float progress, float alpha) {
		float cx = x + CELL_WIDTH / 2f;
		float cy = y + RING_OUTER + 1;
		if (config.background) {
			RenderUtil.roundedFill(g, x, y, x + CELL_WIDTH, y + RING_HEIGHT, 3, RenderUtil.withAlpha(config.backgroundColor, alpha));
		}
		int color = RenderUtil.withAlpha(barColor(effect, config), alpha);
		int empty = RenderUtil.withAlpha(RING_BACKGROUND, alpha);
		float p = effect.isInfiniteDuration() || effect.isAmbient() ? 1f : Math.clamp(progress, 0f, 1f);
		int r = (int) Math.ceil(RING_OUTER);
		for (int dy = -r; dy < r; dy++) {
			for (int dx = -r; dx < r; dx++) {
				double px = dx + 0.5;
				double py = dy + 0.5;
				double d = Math.sqrt(px * px + py * py);
				if (d > RING_OUTER || d < RING_INNER) {
					continue;
				}
				double angle = Math.atan2(px, -py);
				if (angle < 0) {
					angle += Math.PI * 2;
				}
				int pxl = (int) Math.floor(cx + dx);
				int pyl = (int) Math.floor(cy + dy);
				g.fill(pxl, pyl, pxl + 1, pyl + 1, angle / (Math.PI * 2) <= p ? color : empty);
			}
		}
		icon(g, effect, Math.round(cx) - ICON / 2, Math.round(cy) - ICON / 2, alpha);
		if (config.showLevel && effect.getAmplifier() > 0) {
			small(g, font, level(effect.getAmplifier()), x + CELL_WIDTH - 2, y + 1, alpha, true);
		}
		String time = time(effect);
		g.centeredText(font, time, x + CELL_WIDTH / 2, y + RING_HEIGHT - 9,
				RenderUtil.withAlpha(effect.isAmbient() ? TIME_AMBIENT : TEXT, alpha));
	}

	private static void drawCompact(GuiGraphicsExtractor g, Font font, EffectHudConfig config, int x, int y,
			MobEffectInstance effect, float alpha) {
		if (config.background) {
			RenderUtil.roundedFill(g, x, y, x + CELL_WIDTH, y + COMPACT_HEIGHT, 3, RenderUtil.withAlpha(config.backgroundColor, alpha));
		}
		if (config.categoryMark) {
			g.fill(x + 3, y + 1, x + CELL_WIDTH - 3, y + 2, RenderUtil.withAlpha(categoryColor(effect, config), alpha));
		}
		icon(g, effect, x + 3, y + 2, alpha);
		g.centeredText(font, time(effect), x + CELL_WIDTH / 2, y + 21,
				RenderUtil.withAlpha(effect.isAmbient() ? TIME_AMBIENT : TEXT, alpha));
		if (config.showLevel && effect.getAmplifier() > 0) {
			g.centeredText(font, level(effect.getAmplifier()), x + CELL_WIDTH / 2, y + 29, RenderUtil.withAlpha(TIME, alpha));
		}
	}

	private static void icon(GuiGraphicsExtractor g, MobEffectInstance effect, int x, int y, float alpha) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect.getEffect()), x, y, ICON, ICON,
				RenderUtil.withAlpha(0xFFFFFFFF, alpha));
	}

	/** Small right-aligned (or left-aligned) text at 75 % size. */
	private static void small(GuiGraphicsExtractor g, Font font, String text, int x, int y, float alpha, boolean rightAligned) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(0.75f);
		g.text(font, text, rightAligned ? -font.width(text) : 0, 0, RenderUtil.withAlpha(TEXT, alpha), true);
		pose.popMatrix();
	}

	// ---------------------------------------------------------------- text and colours

	static Component name(MobEffectInstance effect, EffectHudConfig config) {
		MutableComponent name = effect.getEffect().value().getDisplayName().copy();
		if (config.showLevel && effect.getAmplifier() > 0) {
			name.append(" " + level(effect.getAmplifier()));
		}
		return name;
	}

	static String level(int amplifier) {
		int level = amplifier + 1;
		return level <= 10 ? Component.translatable("enchantment.level." + level).getString() : String.valueOf(level);
	}

	static String time(MobEffectInstance effect) {
		if (effect.isInfiniteDuration()) {
			return "∞";
		}
		int seconds = Math.max(0, effect.getDuration() / 20);
		if (seconds >= 3600) {
			return String.format("%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
		}
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	static int categoryColor(MobEffectInstance effect, EffectHudConfig config) {
		MobEffectCategory category = effect.getEffect().value().getCategory();
		return switch (category) {
			case BENEFICIAL -> config.beneficialColor;
			case HARMFUL -> config.harmfulColor;
			case NEUTRAL -> NEUTRAL;
		};
	}

	static int barColor(MobEffectInstance effect, EffectHudConfig config) {
		MobEffect type = effect.getEffect().value();
		return config.effectColorBars ? 0xFF000000 | type.getColor() : categoryColor(effect, config);
	}

	static ChatFormatting categoryFormatting(MobEffectInstance effect) {
		return switch (effect.getEffect().value().getCategory()) {
			case BENEFICIAL -> ChatFormatting.GREEN;
			case HARMFUL -> ChatFormatting.RED;
			case NEUTRAL -> ChatFormatting.GRAY;
		};
	}
}
