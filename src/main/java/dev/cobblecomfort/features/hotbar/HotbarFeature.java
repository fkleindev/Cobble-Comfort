package dev.cobblecomfort.features.hotbar;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.inventoryfx.ItemColors;
import dev.cobblecomfort.features.inventoryfx.ScreenParticles;
import dev.cobblecomfort.features.rarity.RarityFeature;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Smooth hotbar: a gliding selection frame, a springy enlarged selected item, slot numbers and sparks. */
public final class HotbarFeature extends Feature<HotbarConfig> {
	public static final HotbarFeature INSTANCE = new HotbarFeature();

	private static final int SLOTS = 9;
	private static final int SLOT_STEP = 20;
	private static final int HALF_ITEM = 8;
	private static final float NUMBER_FADE_SECONDS = 0.25f;
	private static final float PARTICLE_LIFETIME = 0.5f;
	private static final int MAX_PARTICLES = 60;
	private static final int SHARDS = 20;
	private static final long BREAK_MS = 140;

	private final float[] value = new float[SLOTS];
	private final float[] velocity = new float[SLOTS];
	private final ScreenParticles particles = new ScreenParticles();
	private final Random random = new Random();
	/** Items that just broke, swelling in their slot (screen centre of the slot). */
	private final List<Break> breaks = new ArrayList<>();

	private record Break(ItemStack stack, float x, float y, long startMs) {
	}

	private int selected = -1;
	private float slideFrom;
	private long slideStartMs;
	private boolean slideDone = true;
	private long lastSwitchMs;
	private long lastFrameNanos;

	private HotbarFeature() {
		super("hotbar", HotbarConfig.class, HotbarConfig::new);
	}

	@Override
	public void init() {
	}

	@Override
	public ConfigCategory buildCategory() {
		return HotbarConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- per frame

	/** Advances all animations; called once per frame at the start of the hotbar. */
	public void beginHotbar(Player player) {
		long nanos = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min((nanos - lastFrameNanos) / 1e9f, 0.05f);
		lastFrameNanos = nanos;
		if (!isEnabled() || player == null) {
			selected = -1;
			return;
		}
		int current = player.getInventory().getSelectedSlot();
		long now = System.currentTimeMillis();
		if (selected < 0) {
			selected = current;
			slideFrom = current;
			value[current] = 1f;
		} else if (current != selected) {
			slideFrom = slidePosition(now);
			slideStartMs = now;
			slideDone = false;
			selected = current;
			lastSwitchMs = now;
		}
		// Springs towards 1 for the selected slot and 0 for the rest; less damping means more bounce.
		float omega = 4f / Math.max(0.05f, config.growDuration);
		float zeta = 1f - 0.8f * Math.clamp(config.bounce, 0f, 1f);
		for (int i = 0; i < SLOTS; i++) {
			float target = i == selected ? 1f : 0f;
			float accel = omega * omega * (target - value[i]) - 2f * zeta * omega * velocity[i];
			velocity[i] += accel * dt;
			value[i] += velocity[i] * dt;
		}
		particles.update(dt, 0.4f);
	}

	/** The selection frame's position in slots, gliding from where it was to the selected slot. */
	private float slidePosition(long now) {
		if (!config.smoothSelection || config.slideDuration <= 0f) {
			return selected;
		}
		float t = Math.min(1f, (now - slideStartMs) / (config.slideDuration * 1000f));
		return slideFrom + (selected - slideFrom) * config.slideEasing.apply(t);
	}

	/** Horizontal offset in pixels of the selection frame from where vanilla draws it. */
	public float selectionOffset() {
		if (!isEnabled() || selected < 0) {
			return 0f;
		}
		return (slidePosition(System.currentTimeMillis()) - selected) * SLOT_STEP;
	}

	// ---------------------------------------------------------------- slots

	/** Transforms a hotbar slot (item, count and bar). Must be paired with {@link #endSlot}. */
	public void beginSlot(GuiGraphicsExtractor g, int slot, int x, int y) {
		g.pose().pushMatrix();
		if (!isEnabled() || slot < 0 || slot >= SLOTS) {
			return;
		}
		float v = value[slot];
		float lift = config.lift ? -config.liftPixels * v : 0f;
		float scale = config.scaleSelected ? 1f + (config.selectedScale - 1f) * v : 1f;
		Matrix3x2fStack pose = g.pose();
		pose.translate(x + HALF_ITEM, y + HALF_ITEM + lift);
		pose.scale(Math.max(0.1f, scale));
		pose.translate(-x - HALF_ITEM, -y - HALF_ITEM);
	}

	public void endSlot(GuiGraphicsExtractor g, int slot, int x, int y, ItemStack stack) {
		if (isEnabled() && config.dimOthers && slot >= 0 && slot < SLOTS && !stack.isEmpty()) {
			float dim = config.dimAmount * (1f - Math.clamp(value[slot], 0f, 1f));
			if (dim > 0.01f) {
				g.fill(x, y, x + 16, y + 16, RenderUtil.withAlpha(0xFF000000, dim));
			}
		}
		g.pose().popMatrix();
	}

	// ---------------------------------------------------------------- overlay

	/** Slot numbers and sparks, drawn after the hotbar. */
	public void endHotbar(GuiGraphicsExtractor g, Player player) {
		if (!isEnabled() || player == null || selected < 0) {
			return;
		}
		long now = System.currentTimeMillis();
		int left = g.guiWidth() / 2 - 91;
		int top = g.guiHeight() - 22;
		if (!slideDone && slidePosition(now) == selected) {
			slideDone = true;
			if (config.switchParticles) {
				spark(player.getInventory().getItem(selected), left + 11 + selected * SLOT_STEP, top + 11);
			}
		}
		float numberAlpha = numberAlpha(now);
		boolean drawParticles = !particles.isEmpty() || !breaks.isEmpty();
		if (numberAlpha <= 0.01f && !drawParticles) {
			return;
		}
		g.nextStratum();
		drawBreaks(g, now);
		if (numberAlpha > 0.01f) {
			Font font = Minecraft.getInstance().font;
			int color = RenderUtil.withAlpha(config.numberColor, numberAlpha);
			for (int i = 0; i < SLOTS; i++) {
				Matrix3x2fStack pose = g.pose();
				pose.pushMatrix();
				pose.translate(left + 3 + i * SLOT_STEP, top + 3);
				pose.scale(config.numberScale);
				g.text(font, String.valueOf(i + 1), 0, 0, color, true);
				pose.popMatrix();
			}
		}
		if (drawParticles) {
			particles.draw(g);
		}
	}

	private float numberAlpha(long now) {
		return switch (config.slotNumbers) {
			case OFF -> 0f;
			case ALWAYS -> 1f;
			case WHILE_SWITCHING -> {
				float since = (now - lastSwitchMs) / 1000f;
				if (lastSwitchMs == 0 || since > config.numberHoldSeconds + NUMBER_FADE_SECONDS) {
					yield 0f;
				}
				float in = Math.min(1f, since / NUMBER_FADE_SECONDS);
				float out = Math.clamp((config.numberHoldSeconds + NUMBER_FADE_SECONDS - since) / NUMBER_FADE_SECONDS, 0f, 1f);
				yield Math.min(in, out);
			}
		};
	}

	// ---------------------------------------------------------------- breaking

	/**
	 * An item of the local player broke. If it was in the hotbar or the offhand, it swells, flashes and bursts into
	 * shards in its own colours.
	 */
	public void onItemBroken(Player player, ItemStack stack) {
		if (!isEnabled() || !config.breakShatter || stack.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		int width = mc.getWindow().getGuiScaledWidth();
		float y = mc.getWindow().getGuiScaledHeight() - 11;
		float x;
		if (player.getOffhandItem() == stack) {
			x = player.getMainArm() == HumanoidArm.RIGHT ? width / 2f - 91 - 18 : width / 2f + 91 + 18;
		} else {
			int slot = -1;
			for (int i = 0; i < SLOTS; i++) {
				if (player.getInventory().getItem(i) == stack) {
					slot = i;
				}
			}
			if (slot < 0) {
				return;
			}
			x = width / 2f - 91 + 11 + slot * SLOT_STEP;
		}
		breaks.add(new Break(stack.copy(), x, y, System.currentTimeMillis()));
		int[] palette = ItemColors.palette(stack);
		for (int i = 0; i < SHARDS; i++) {
			double angle = -Math.PI / 2 + (random.nextDouble() - 0.5) * Math.PI * 1.6;
			float speed = 40f + random.nextFloat() * 45f;
			int color = palette.length == 0 ? 0xFFB0B0B0 : palette[random.nextInt(palette.length)];
			particles.spawn(x + (random.nextFloat() - 0.5f) * 10f, y + (random.nextFloat() - 0.5f) * 10f,
					(float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed, 12f, color, false, 0.7f, MAX_PARTICLES);
		}
	}

	/** The broken item swelling and flashing white for a moment, before only its shards are left. */
	private void drawBreaks(GuiGraphicsExtractor g, long now) {
		breaks.removeIf(b -> now - b.startMs() > BREAK_MS);
		for (Break b : breaks) {
			float t = (now - b.startMs()) / (float) BREAK_MS;
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(b.x(), b.y());
			pose.scale(1f + 0.5f * t);
			pose.rotate((random.nextFloat() - 0.5f) * 0.3f * t);
			g.item(b.stack(), -HALF_ITEM, -HALF_ITEM);
			pose.popMatrix();
			int flash = RenderUtil.withAlpha(0xFFFFFFFF, 0.7f * (1f - t));
			int half = Math.round(8 + 4 * t);
			g.fill(Math.round(b.x()) - half, Math.round(b.y()) - half, Math.round(b.x()) + half, Math.round(b.y()) + half, flash);
		}
	}

	/** A small ring of sparks around the frame, in the item's rarity colour (white without an item). */
	private void spark(ItemStack stack, float cx, float cy) {
		int color = stack.isEmpty() ? 0xFFFFFFFF : RarityFeature.INSTANCE.rarityColor(stack);
		for (int i = 0; i < config.particleCount; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			float speed = 25f + random.nextFloat() * 20f;
			particles.spawn(cx + (float) Math.cos(angle) * 9f, cy + (float) Math.sin(angle) * 9f,
					(float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed, 10f, color, true,
					PARTICLE_LIFETIME, MAX_PARTICLES);
		}
	}
}
