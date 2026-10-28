package dev.cobblecomfort.features.pickup;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.MapFeature;
import dev.cobblecomfort.features.rarity.RarityFeature;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A short list at the screen edge of items just picked up ("+32 Cobblestone"). Pickups of the same item are merged
 * into one row while it is still shown.
 */
public final class PickupLogFeature extends Feature<PickupLogConfig> {
	public static final PickupLogFeature INSTANCE = new PickupLogFeature();
	public static final Identifier LAYER_ID = CobbleComfortClient.id("pickup_log");

	private static final int WIDTH = 150;
	private static final int ROW = 14;
	private static final int ICON = 12;
	private static final int PAD = 3;
	private static final int MAX_NAME_WIDTH = 96;
	private static final float SLIDE_IN = 0.25f;
	private static final float FADE_OUT = 0.5f;
	private static final float POP = 0.2f;
	private static final float SLIDE_DISTANCE = 30f;

	private static final class Entry {
		final ItemStack stack;
		int amount;
		long created;
		long updated;
		/** Animated row position, counted from the bottom. */
		float row = -1f;

		Entry(ItemStack stack, int amount, long now) {
			this.stack = stack;
			this.amount = amount;
			this.created = now;
			this.updated = now;
		}
	}

	/** Oldest first; the newest row is drawn at the bottom. */
	private final List<Entry> entries = new ArrayList<>();
	private long lastFrameMs;

	private PickupLogFeature() {
		super("pickup_log", PickupLogConfig.class, PickupLogConfig::new);
	}

	@Override
	public void init() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LAYER_ID, (g, delta) -> render(g));
	}

	@Override
	public ConfigCategory buildCategory() {
		return PickupLogConfigUi.buildCategory(this);
	}

	/** Called when the server reports that the local player picked up {@code amount} of this item. */
	public void onPickup(ItemStack stack, int amount) {
		if (!isEnabled() || stack.isEmpty() || amount <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if (ItemStack.isSameItemSameComponents(entry.stack, stack) && alive(entry, now)) {
				entry.amount += amount;
				entry.updated = now;
				entries.remove(i);
				entries.add(entry);
				return;
			}
		}
		entries.add(new Entry(stack.copyWithCount(1), amount, now));
		while (entries.size() > config.maxEntries) {
			entries.removeFirst();
		}
	}

	private boolean alive(Entry entry, long now) {
		return now - entry.updated < config.displaySeconds * 1000f;
	}

	public Rect defaultBounds(float width, float height) {
		int h = Math.max(1, config.maxEntries) * ROW;
		// Vertically centred, but below the minimap (top right by default) if they would overlap.
		float y = height / 2f - h / 2f;
		if (MapFeature.INSTANCE.topRightReserved() > 0) {
			Rect map = MapFeature.INSTANCE.defaultBounds(width, height);
			y = Math.max(y, map.y() + map.h() + 6);
		}
		return new Rect(width - 4 - WIDTH, Math.min(y, height - h - 4), WIDTH, h);
	}

	// ---------------------------------------------------------------- rendering

	private void render(GuiGraphicsExtractor g) {
		long now = System.currentTimeMillis();
		float dt = lastFrameMs == 0 ? 0f : Math.min((now - lastFrameMs) / 1000f, 0.1f);
		lastFrameMs = now;
		if (!isEnabled()) {
			entries.clear();
			return;
		}
		entries.removeIf(entry -> now - entry.updated > (config.displaySeconds + FADE_OUT) * 1000f);
		List<Entry> shown = entries;
		boolean sample = shown.isEmpty() && HudEditorScreen.isOpen();
		if (sample) {
			shown = List.of(new Entry(new ItemStack(Items.COBBLESTONE), 32, 0), new Entry(new ItemStack(Items.DIAMOND), 1, 0),
					new Entry(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 2, 0));
		}
		if (shown.isEmpty()) {
			return;
		}
		Rect bounds = defaultBounds(g.guiWidth(), g.guiHeight());
		int right = Math.round(bounds.x() + bounds.w());
		float bottom = bounds.y() + bounds.h();
		Font font = Minecraft.getInstance().font;
		float follow = config.animations ? 1f - (float) Math.exp(-14f * dt) : 1f;
		for (int i = 0; i < shown.size(); i++) {
			Entry entry = shown.get(i);
			float target = shown.size() - 1 - i;
			entry.row = entry.row < 0 || sample ? target : entry.row + (target - entry.row) * follow;
			drawRow(g, font, entry, right, bottom - (entry.row + 1) * ROW, sample ? 0f : (now - entry.created) / 1000f,
					sample ? 0f : (now - entry.updated) / 1000f);
		}
	}

	private void drawRow(GuiGraphicsExtractor g, Font font, Entry entry, int right, float top, float age, float sinceUpdate) {
		float alpha = 1f;
		float slide = 0f;
		float scale = 1f;
		float fadeAt = config.displaySeconds;
		if (sinceUpdate > fadeAt) {
			float t = Math.min(1f, (sinceUpdate - fadeAt) / FADE_OUT);
			alpha = 1f - t;
			slide = config.animations ? SLIDE_DISTANCE * Easing.EASE_OUT_CUBIC.apply(t) * 0.5f : 0f;
		}
		if (config.animations) {
			if (age < SLIDE_IN) {
				float e = Easing.EASE_OUT_CUBIC.apply(age / SLIDE_IN);
				slide += SLIDE_DISTANCE * (1f - e);
				alpha *= e;
			}
			if (sinceUpdate < POP && age > POP) {
				float t = 1f - sinceUpdate / POP;
				scale = 1f + 0.15f * t * t;
			}
		}
		if (alpha <= 0.02f) {
			return;
		}

		String name = entry.stack.getHoverName().getString();
		if (font.width(name) > MAX_NAME_WIDTH) {
			name = font.plainSubstrByWidth(name, MAX_NAME_WIDTH - font.width("…")) + "…";
		}
		String amount = "+" + entry.amount + " ";
		String total = config.showTotal ? " (" + total(entry.stack) + ")" : "";
		int iconSpace = config.showIcon ? ICON + 3 : 0;
		int width = PAD + font.width(amount) + font.width(name) + font.width(total) + iconSpace + PAD;

		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(right + slide, top + ROW / 2f);
		pose.scale(scale);
		int x0 = -width;
		int y0 = -ROW / 2 + 1;
		if (config.background) {
			RenderUtil.roundedFill(g, x0, y0, 0, y0 + ROW - 2, 2, RenderUtil.withAlpha(0xFF000000, config.backgroundOpacity * alpha));
		}
		int textY = y0 + (ROW - 2 - font.lineHeight) / 2 + 1;
		int x = x0 + PAD;
		g.text(font, amount, x, textY, RenderUtil.withAlpha(0xFF7CFC7C, alpha), true);
		x += font.width(amount);
		int nameColor = config.rarityColors ? RarityFeature.INSTANCE.rarityColor(entry.stack) : 0xFFFFFFFF;
		g.text(font, name, x, textY, RenderUtil.withAlpha(nameColor, alpha), true);
		x += font.width(name);
		if (!total.isEmpty()) {
			g.text(font, total, x, textY, RenderUtil.withAlpha(0xFFA0A0A0, alpha), true);
		}
		if (config.showIcon && alpha > 0.4f) {
			// Items cannot be faded, so the icon disappears once the row is mostly transparent.
			pose.pushMatrix();
			pose.translate(-PAD - ICON, y0 + (ROW - 2 - ICON) / 2f);
			pose.scale(ICON / 16f);
			g.item(entry.stack, 0, 0);
			pose.popMatrix();
		}
		pose.popMatrix();
	}

	/** How many of this item the player now carries. */
	private static int total(ItemStack stack) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return 0;
		}
		Inventory inventory = player.getInventory();
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack slot = inventory.getItem(i);
			if (ItemStack.isSameItemSameComponents(slot, stack)) {
				count += slot.getCount();
			}
		}
		return count;
	}
}
