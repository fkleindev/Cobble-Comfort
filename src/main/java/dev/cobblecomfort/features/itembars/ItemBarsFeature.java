package dev.cobblecomfort.features.itembars;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Item decorations in the hotbar and all inventories: the durability bar glides down and blinks shortly before the item
 * breaks, and cooldowns (ender pearl, shield, wind charge, ...) close a ring around the slot instead of the white
 * overlay, with a flash when they are over.
 */
public final class ItemBarsFeature extends Feature<ItemBarsConfig> {
	public static final ItemBarsFeature INSTANCE = new ItemBarsFeature();

	private static final int BAR_WIDTH = 13;
	private static final float BAR_SPEED = 12f;
	private static final float BLINK_HZ = 2.5f;
	private static final int WARN_COLOR = 0xFF3030;
	private static final int SLOT = 18;
	private static final int RING_TRACK = 0x70000000;
	private static final float FLASH_SECONDS = 0.35f;
	private static final long FORGET_MS = 2000;

	/** A durability bar, by where it is drawn: slots keep their position while their stack is replaced. */
	private static final class Bar {
		Item item;
		float shown;
		long seen;
	}

	private final Map<Long, Bar> bars = new HashMap<>();
	private final Map<Identifier, Long> cooling = new HashMap<>();
	private final Map<Identifier, Long> finished = new HashMap<>();
	private long barNanos;
	private float barStep;
	private long lastCleanup;

	private ItemBarsFeature() {
		super("item_bars", ItemBarsConfig.class, ItemBarsConfig::new);
	}

	@Override
	public void init() {
	}

	@Override
	public ConfigCategory buildCategory() {
		return ItemBarsConfigUi.buildCategory(this);
	}

	private void cleanup(long now) {
		if (now - lastCleanup < FORGET_MS) {
			return;
		}
		lastCleanup = now;
		bars.values().removeIf(b -> now - b.seen > FORGET_MS);
		cooling.values().removeIf(t -> now - t > FORGET_MS);
		finished.values().removeIf(t -> now - t > FORGET_MS);
	}

	// ---------------------------------------------------------------- durability

	/** Draws the durability bar in place of vanilla's; returns false to leave it to vanilla. */
	public boolean drawBar(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
		if (!isEnabled() || !config.smoothDurability && !config.breakWarning) {
			return false;
		}
		if (!stack.isBarVisible()) {
			return true;
		}
		long now = System.currentTimeMillis();
		cleanup(now);
		int target = stack.getBarWidth();
		float width = target;
		if (config.smoothDurability) {
			long nanos = System.nanoTime();
			// One step per frame, shared by all bars of the frame.
			if (nanos - barNanos > 1_000_000L) {
				barStep = barNanos == 0 ? 0f : Math.min((nanos - barNanos) / 1e9f, 0.05f);
				barNanos = nanos;
			}
			long key = ((long) x << 32) | (y & 0xFFFFFFFFL);
			Bar bar = bars.get(key);
			if (bar == null || bar.item != stack.getItem() || now - bar.seen > FORGET_MS) {
				bar = new Bar();
				bar.item = stack.getItem();
				bar.shown = target;
				bars.put(key, bar);
			}
			bar.shown += (target - bar.shown) * (1f - (float) Math.exp(-barStep * BAR_SPEED));
			if (Math.abs(target - bar.shown) < 0.05f) {
				bar.shown = target;
			}
			bar.seen = now;
			width = bar.shown;
		}
		int left = x + 2;
		int top = y + 13;
		g.fill(RenderPipelines.GUI, left, top, left + BAR_WIDTH, top + 2, 0xFF000000);
		int color = ARGB.opaque(stack.getBarColor());
		if (config.breakWarning && nearlyBroken(stack)) {
			float blink = 0.5f + 0.5f * (float) Math.sin(now / 1000.0 * Math.PI * 2 * BLINK_HZ);
			g.fill(RenderPipelines.GUI, left, top, left + BAR_WIDTH, top + 1, ARGB.color(Math.round(200 * blink), WARN_COLOR));
			color = ARGB.srgbLerp(blink, color, 0xFFFFFFFF);
		}
		int filled = Math.round(width);
		if (filled > 0) {
			g.fill(RenderPipelines.GUI, left, top, left + filled, top + 1, color);
		}
		return true;
	}

	private boolean nearlyBroken(ItemStack stack) {
		if (!stack.isDamageableItem()) {
			return false;
		}
		int max = stack.getMaxDamage();
		int left = max - stack.getDamageValue();
		return max > 0 && left <= Math.max(1, Math.round(max * config.warnBelow));
	}

	// ---------------------------------------------------------------- cooldown

	/** Draws the cooldown in place of vanilla's overlay; returns false to leave it to vanilla. */
	public boolean drawCooldown(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
		if (!isEnabled() || !config.cooldownRing) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return true;
		}
		long now = System.currentTimeMillis();
		cleanup(now);
		ItemCooldowns cooldowns = player.getCooldowns();
		float percent = cooldowns.getCooldownPercent(stack, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
		Identifier group = null;
		if (percent > 0f || !cooling.isEmpty() || !finished.isEmpty()) {
			group = cooldowns.getCooldownGroup(stack);
		}
		if (percent > 0f) {
			cooling.put(group, now);
			int ring = 4 * (SLOT - 1);
			ring(g, x - 1, y - 1, 0, ring, RING_TRACK);
			ring(g, x - 1, y - 1, 0, Math.round(ring * (1f - percent)), config.ringColor);
			return true;
		}
		if (group != null && cooling.remove(group) != null && config.cooldownFlash) {
			finished.put(group, now);
		}
		Long end = group == null ? null : finished.get(group);
		float t = end == null ? 1f : (now - end) / (FLASH_SECONDS * 1000f);
		if (t < 1f) {
			float fade = 1f - t;
			g.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, RenderUtil.withAlpha(0xFFFFFFFF, 0.5f * fade));
			ring(g, x - 1, y - 1, 0, 4 * (SLOT - 1), RenderUtil.withAlpha(config.ringColor, fade));
		}
		return true;
	}

	/**
	 * Fills the cells {@code from} to {@code to} of the 1 px ring around an 18 px slot, counted clockwise from the top
	 * centre.
	 */
	private static void ring(GuiGraphicsExtractor g, int x0, int y0, int from, int to, int color) {
		int side = SLOT - 1;
		int cells = 4 * side;
		for (int j = from; j < to; j++) {
			int i = (j + SLOT / 2) % cells;
			int px;
			int py;
			if (i < side) {
				px = x0 + i;
				py = y0;
			} else if (i < 2 * side) {
				px = x0 + side;
				py = y0 + i - side;
			} else if (i < 3 * side) {
				px = x0 + side - (i - 2 * side);
				py = y0 + side;
			} else {
				px = x0;
				py = y0 + side - (i - 3 * side);
			}
			g.fill(RenderPipelines.GUI, px, py, px + 1, py + 1, color);
		}
	}
}
