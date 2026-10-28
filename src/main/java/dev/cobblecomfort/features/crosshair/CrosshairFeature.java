package dev.cobblecomfort.features.crosshair;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A hit marker around the crosshair when an attack lands (yellow for critical hits, red and larger for kills), and the
 * attack cooldown as a thin ring around the crosshair in place of vanilla's indicator below it.
 */
public final class CrosshairFeature extends Feature<CrosshairConfig> {
	public static final CrosshairFeature INSTANCE = new CrosshairFeature();

	/** The crosshair sprite is this large; its centre pixel is the middle of the screen. */
	private static final int CROSSHAIR = 15;
	/** How long after an attack the hit is still looked for: the server's answer takes a moment. */
	private static final int CONFIRM_TICKS = 10;
	private static final float MARKER_SECONDS = 0.35f;
	private static final float RING_RADIUS = 9f;
	private static final float RING_FADE_SECONDS = 0.25f;
	private static final int RING_TRACK = 0x60000000;
	private static final int SHADOW = 0x80000000;
	private static final float CRIT_STRENGTH = 0.9f;

	private enum Hit {
		NORMAL, CRIT, KILL
	}

	// The attack waiting for its hit to show (the target's hurt animation) or for the target to die.
	private LivingEntity pending;
	private int pendingHurt;
	private boolean pendingCrit;
	private int pendingTicks;
	private boolean pendingHit;

	private Hit marker;
	private long markerAt;

	// What vanilla's attack indicator would have shown this frame.
	private boolean charging;
	private boolean readyOnTarget;
	private boolean wasCharging;
	private long fullAt;
	private boolean crosshairDrawn;

	private final List<int[]> ring = new ArrayList<>();

	private CrosshairFeature() {
		super("crosshair", CrosshairConfig.class, CrosshairConfig::new);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> tick());
		// The ring's pixels, clockwise from the top.
		int[] last = null;
		for (int degree = 0; degree < 360; degree += 2) {
			double angle = Math.toRadians(degree);
			int[] point = {(int) Math.round(RING_RADIUS * Math.sin(angle)), (int) Math.round(-RING_RADIUS * Math.cos(angle))};
			if (last == null || last[0] != point[0] || last[1] != point[1]) {
				ring.add(point);
				last = point;
			}
		}
		int[] first = ring.getFirst();
		if (last != null && last != first && last[0] == first[0] && last[1] == first[1]) {
			ring.removeLast();
		}
	}

	@Override
	public ConfigCategory buildCategory() {
		return CrosshairConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- hits

	/** The player attacks an entity (before the attack charge resets). */
	public void onAttack(Player player, Entity target) {
		if (!isEnabled() || !config.hitMarker || !(target instanceof LivingEntity living)) {
			return;
		}
		pending = living;
		pendingHurt = living.hurtTime;
		pendingCrit = isCrit(player);
		pendingTicks = CONFIRM_TICKS;
		pendingHit = false;
	}

	/** Vanilla's conditions for a critical hit, as far as the client knows them. */
	private static boolean isCrit(Player player) {
		return player.getAttackStrengthScale(0.5f) > CRIT_STRENGTH && player.fallDistance > 0 && !player.onGround()
				&& !player.onClimbable() && !player.isInWater() && !player.hasEffect(MobEffects.BLINDNESS)
				&& !player.isPassenger() && !player.isSprinting();
	}

	private void tick() {
		if (pending == null) {
			return;
		}
		if (pending.isDeadOrDying()) {
			show(config.killMarker ? Hit.KILL : pendingCrit && config.critColor ? Hit.CRIT : Hit.NORMAL);
			pending = null;
			return;
		}
		if (!pendingHit && pending.hurtTime > pendingHurt) {
			show(pendingCrit && config.critColor ? Hit.CRIT : Hit.NORMAL);
			// Keep watching a little longer: the death can come a tick after the hit.
			pendingHit = true;
		}
		if (--pendingTicks <= 0 || pending.isRemoved()) {
			pending = null;
		}
	}

	private void show(Hit hit) {
		marker = hit;
		markerAt = System.currentTimeMillis();
	}

	// ---------------------------------------------------------------- drawing

	/** Start of vanilla's crosshair. */
	public void beginCrosshair() {
		charging = false;
		readyOnTarget = false;
		crosshairDrawn = false;
	}

	public void crosshairDrawn() {
		crosshairDrawn = true;
	}

	/** Whether to replace vanilla's attack indicator; notes what it would have shown. */
	public boolean replaceIndicator(boolean full) {
		if (!isEnabled() || !config.attackRing) {
			return false;
		}
		if (full) {
			readyOnTarget = true;
		} else {
			charging = true;
		}
		return true;
	}

	/** After vanilla's crosshair: the cooldown ring and the hit marker. */
	public void endCrosshair(GuiGraphicsExtractor g) {
		if (!isEnabled() || !crosshairDrawn) {
			return;
		}
		int cx = (g.guiWidth() - CROSSHAIR) / 2 + CROSSHAIR / 2;
		int cy = (g.guiHeight() - CROSSHAIR) / 2 + CROSSHAIR / 2;
		drawRing(g, cx, cy);
		drawMarker(g, cx, cy);
	}

	private void drawRing(GuiGraphicsExtractor g, int cx, int cy) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!config.attackRing || player == null || mc.options.attackIndicator().get() != AttackIndicatorStatus.CROSSHAIR) {
			return;
		}
		long now = System.currentTimeMillis();
		if (wasCharging && !charging) {
			fullAt = now;
		}
		wasCharging = charging;
		if (charging) {
			float strength = player.getAttackStrengthScale(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
			arc(g, cx, cy, ring.size(), RING_TRACK);
			arc(g, cx, cy, Math.round(ring.size() * Math.clamp(strength, 0f, 1f)), config.ringColor);
			return;
		}
		float fade = readyOnTarget ? 1f : 1f - (now - fullAt) / (RING_FADE_SECONDS * 1000f);
		if (fade > 0f) {
			arc(g, cx, cy, ring.size(), RenderUtil.withAlpha(config.ringColor, fade));
		}
	}

	private void arc(GuiGraphicsExtractor g, int cx, int cy, int count, int color) {
		for (int i = 0; i < count && i < ring.size(); i++) {
			int[] p = ring.get(i);
			g.fill(cx + p[0], cy + p[1], cx + p[0] + 1, cy + p[1] + 1, color);
		}
	}

	/** Four short diagonal strokes around the crosshair, pushed outwards a little as they fade. */
	private void drawMarker(GuiGraphicsExtractor g, int cx, int cy) {
		if (marker == null) {
			return;
		}
		float t = (System.currentTimeMillis() - markerAt) / (MARKER_SECONDS * 1000f);
		if (t >= 1f) {
			marker = null;
			return;
		}
		boolean kill = marker == Hit.KILL;
		int color = switch (marker) {
			case NORMAL -> config.hitColor;
			case CRIT -> config.critMarkerColor;
			case KILL -> config.killColor;
		};
		float alpha = t < 0.5f ? 1f : 1f - (t - 0.5f) * 2f;
		int gap = (kill ? 4 : 3) + Math.round(t * 2f);
		int length = kill ? 5 : 4;
		int thickness = kill ? 2 : 1;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				for (int k = 0; k < length; k++) {
					int x = cx + sx * (gap + k);
					int y = cy + sy * (gap + k);
					g.fill(x + 1, y + 1, x + 1 + thickness, y + 1 + thickness, RenderUtil.withAlpha(SHADOW, alpha));
					g.fill(x, y, x + thickness, y + thickness, RenderUtil.withAlpha(color, alpha));
				}
			}
		}
	}
}
