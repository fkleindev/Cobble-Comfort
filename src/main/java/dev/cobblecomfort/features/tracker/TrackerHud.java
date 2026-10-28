package dev.cobblecomfort.features.tracker;

import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.infohud.InfoHudFeature;
import dev.cobblecomfort.features.inventoryfx.ScreenParticles;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** The progress HUD: one row per goal with icon, counts and a progress bar. */
public final class TrackerHud implements HudElement {
	private static final int ROW_HEIGHT = 18;
	private static final int TITLE_HEIGHT = 11;
	private static final int WIDTH_WITH_NAMES = 150;
	private static final int WIDTH_WITHOUT_NAMES = 96;
	private static final float SHINE_SECONDS = 0.8f;
	private static final float BUMP_SECONDS = 0.35f;
	private static final float PRESENCE_SECONDS = 0.25f;
	private static final int[] CONFETTI = {0xFFFF5555, 0xFFFFFF55, 0xFF55FF55, 0xFF55FFFF, 0xFF5555FF, 0xFFFF55FF};

	private final TrackerFeature feature;
	private final Map<String, RowState> rows = new HashMap<>();
	private final ScreenParticles confetti = new ScreenParticles();
	private final Random random = new Random();
	private long lastFrameMs;
	private float lastWidth;
	private float lastHeight;

	private static final class RowState {
		float shown = -1f;
		int lastCount = -1;
		long bumpMs;
		long shineMs;
		float presence;
		float doneBlend;
		/** Set by {@link #onCompleted}; consumed when the row is next drawn, which knows its position. */
		boolean confettiPending;
	}

	TrackerHud(TrackerFeature feature) {
		this.feature = feature;
	}

	void reset() {
		rows.clear();
		confetti.clear();
	}

	void onCompleted(TrackerData.Goal goal) {
		RowState row = rows.computeIfAbsent(goal.item, k -> new RowState());
		row.shineMs = System.currentTimeMillis();
		row.confettiPending = true;
		TrackerConfig cfg = feature.config();
		if (cfg.toast) {
			Item item = item(goal.item);
			Minecraft.getInstance().gui.toastManager().addToast(new TrackerToast(new ItemStack(item),
					Component.translatable("cobblecomfort.tracker.toast.title"),
					Component.translatable("cobblecomfort.tracker.toast.text", goal.target, new ItemStack(item).getHoverName())));
		}
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
	}

	// ---------------------------------------------------------------- layout

	private int width() {
		return feature.config().showNames ? WIDTH_WITH_NAMES : WIDTH_WITHOUT_NAMES;
	}

	public Rect defaultBounds(float screenWidth, float screenHeight) {
		float w = lastWidth > 0 ? lastWidth : width() + feature.config().padding * 2;
		float h = lastHeight > 0 ? lastHeight : TITLE_HEIGHT + ROW_HEIGHT * 2 + feature.config().padding * 2;
		// Vertically centred, but below the info HUD (top left by default) if they would overlap.
		float y = Math.max(screenHeight / 2f - h / 2f, InfoHudFeature.INSTANCE.defaultBottom() + 4);
		return new Rect(4, Math.min(y, screenHeight - h - 4), w, h);
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		long now = System.currentTimeMillis();
		float dt = lastFrameMs == 0 ? 0f : Math.min((now - lastFrameMs) / 1000f, 0.1f);
		lastFrameMs = now;
		TrackerConfig cfg = feature.config();
		if (!feature.isEnabled()) {
			lastWidth = lastHeight = 0;
			return;
		}

		List<TrackerData.Goal> goals = new ArrayList<>(feature.goals());
		boolean sample = goals.isEmpty() && HudEditorScreen.isOpen();
		if (sample) {
			goals = List.of(new TrackerData.Goal("minecraft:oak_log", 64), new TrackerData.Goal("minecraft:diamond", 9));
		}
		if (goals.isEmpty() && cfg.hideWhenEmpty) {
			lastWidth = lastHeight = 0;
			confetti.update(dt, 1f);
			return;
		}
		int shownRows = Math.min(goals.size(), cfg.maxRows);
		int pad = cfg.padding;
		int width = width() + pad * 2;
		int height = (cfg.showTitle ? TITLE_HEIGHT : 0) + Math.max(1, shownRows) * ROW_HEIGHT + pad * 2 - 2;
		lastWidth = width;
		lastHeight = height;

		Rect bounds = defaultBounds(g.guiWidth(), g.guiHeight());
		int x0 = Math.round(bounds.x());
		int y0 = Math.round(bounds.y());
		if (cfg.background) {
			RenderUtil.roundedFill(g, x0, y0, x0 + width, y0 + height, cfg.cornerRadius, cfg.backgroundColor);
		}
		Font font = Minecraft.getInstance().font;
		int y = y0 + pad;
		if (cfg.showTitle) {
			int done = 0;
			for (TrackerData.Goal goal : goals) {
				if (!sample && feature.progress(goal) >= goal.target) {
					done++;
				}
			}
			g.text(font, Component.translatable("cobblecomfort.tracker.hud.title", done, goals.size()),
					x0 + pad, y, cfg.textColor, true);
			y += TITLE_HEIGHT;
		}
		if (goals.isEmpty()) {
			g.text(font, Component.translatable("cobblecomfort.tracker.hud.empty"), x0 + pad, y + 4, 0xFFAAAAAA, true);
		}
		for (int i = 0; i < shownRows; i++) {
			TrackerData.Goal goal = goals.get(i);
			int count = sample ? goal.target / 2 : feature.progress(goal);
			int nearby = sample ? 0 : feature.nearbyCount(goal.item);
			drawRow(g, font, cfg, goal, count, nearby, x0 + pad, y, width - pad * 2, now, dt);
			y += ROW_HEIGHT;
		}
		if (goals.size() > shownRows) {
			g.text(font, "+" + (goals.size() - shownRows), x0 + width - pad - font.width("+9"), y0 + pad, 0xFFAAAAAA, true);
		}

		confetti.update(dt, 1f);
		confetti.draw(g);
	}

	private void drawRow(GuiGraphicsExtractor g, Font font, TrackerConfig cfg, TrackerData.Goal goal, int count,
			int nearby, int x, int y, int width, long now, float dt) {
		RowState row = rows.computeIfAbsent(goal.item, k -> new RowState());
		if (row.lastCount >= 0 && count != row.lastCount && cfg.bumpOnChange) {
			row.bumpMs = now;
		}
		row.lastCount = count;
		if (row.shown < 0 || !cfg.smoothCount) {
			row.shown = count;
		} else {
			row.shown += (count - row.shown) * (1f - (float) Math.exp(-10f * dt));
			if (Math.abs(count - row.shown) < 0.5f) {
				row.shown = count;
			}
		}
		row.presence = cfg.slideIn ? Math.min(1f, row.presence + dt / PRESENCE_SECONDS) : 1f;
		boolean done = count >= goal.target;
		row.doneBlend = Math.clamp(row.doneBlend + (done ? dt : -dt) * 4f, 0f, 1f);

		float presence = Easing.EASE_OUT_CUBIC.apply(row.presence);
		float slide = (1f - presence) * -20f;
		float bumpAge = (now - row.bumpMs) / 1000f;
		float bump = bumpAge < BUMP_SECONDS ? 1f + 0.15f * (1f - bumpAge / BUMP_SECONDS) : 1f;

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(slide, 0);
		Item item = item(goal.item);
		ItemStack stack = new ItemStack(item);
		if (presence > 0.5f) {
			g.item(stack, x, y);
		}
		int textX = x + 20;
		int textColor = RenderUtil.withAlpha(RenderUtil.lerpColor(cfg.textColor, cfg.doneColor, row.doneBlend), presence);
		String amount = (done ? "✔ " : "") + Math.round(row.shown) + "/" + goal.target;
		int amountWidth = font.width(amount);
		int right = x + width;
		if (cfg.showNames) {
			String name = font.plainSubstrByWidth(stack.getHoverName().getString(), right - textX - amountWidth - 6);
			g.text(font, name, textX, y + 1, textColor, true);
		}

		// Count text, bumped around its centre when it changes.
		float cx = right - amountWidth / 2f;
		float cy = y + 5f;
		pose.pushMatrix();
		pose.translate(cx, cy);
		pose.scale(bump);
		pose.translate(-cx, -cy);
		g.text(font, amount, right - amountWidth, y + 1, textColor, true);
		pose.popMatrix();

		if (nearby > 0) {
			// With names the chest count goes at the end of the bar line, otherwise left of the amount.
			String chest = "+" + nearby;
			int chestX = cfg.showNames ? right - font.width(chest) : right - amountWidth - font.width(chest) - 4;
			int chestY = cfg.showNames ? y + 9 : y + 1;
			g.text(font, chest, chestX, chestY, RenderUtil.withAlpha(cfg.nearbyColor, presence), true);
		}

		if (cfg.showBar) {
			int barY = y + 12;
			int barRight = nearby > 0 && cfg.showNames ? right - font.width("+" + nearby) - 3 : right;
			int barWidth = barRight - textX;
			float fraction = Math.clamp(row.shown / Math.max(1, goal.target), 0f, 1f);
			g.fill(textX, barY, barRight, barY + 2, RenderUtil.withAlpha(0x80000000, presence));
			int barColor = RenderUtil.lerpColor(cfg.barColor, cfg.doneColor, row.doneBlend);
			g.fill(textX, barY, textX + Math.round(barWidth * fraction), barY + 2, RenderUtil.withAlpha(barColor, presence));
		}

		float shineAge = (now - row.shineMs) / 1000f;
		if (cfg.completionShine && row.shineMs > 0 && shineAge < SHINE_SECONDS) {
			drawShine(g, x, y, width, shineAge / SHINE_SECONDS);
		}
		pose.popMatrix();

		if (row.confettiPending) {
			row.confettiPending = false;
			if (cfg.completionConfetti) {
				for (int i = 0; i < 24; i++) {
					confetti.spawn(x + random.nextFloat() * width, y + 6, 0f, -90f, 70f,
							CONFETTI[random.nextInt(CONFETTI.length)], random.nextBoolean(), 1.2f, 200);
				}
			}
		}
	}

	/** A bright diagonal band sweeping once across the row. */
	private static void drawShine(GuiGraphicsExtractor g, int x, int y, int width, float progress) {
		int band = 12;
		int pos = Math.round(-band + progress * (width + band * 2));
		for (int row = 0; row < ROW_HEIGHT - 2; row++) {
			int start = Math.clamp(pos - row / 2, 0, width);
			int end = Math.clamp(pos - row / 2 + band, 0, width);
			if (end > start) {
				g.fill(x + start, y + row, x + end, y + row + 1, 0x50FFFFFF);
			}
		}
	}
}
