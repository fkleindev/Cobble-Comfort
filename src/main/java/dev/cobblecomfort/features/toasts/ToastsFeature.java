package dev.cobblecomfort.features.toasts;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.cobblecomfort.features.smoothgui.ScreenFade;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * Toasts slide in and out eased, close up when one leaves, stack repeated advancements and recipes and stay a shorter
 * time. The player list fades and slides in, shows the ping as a number, lets players glide to new places and fade in
 * when they join, and frames the heads in their team colour.
 */
public final class ToastsFeature extends Feature<ToastsConfig> {
	public static final ToastsFeature INSTANCE = new ToastsFeature();

	private static final float TOAST_GLIDE = 12f;
	private static final float TAB_SECONDS = 0.18f;
	private static final float TAB_SLIDE = 8f;
	private static final float ROW_GLIDE = 14f;
	private static final float JOIN_SECONDS = 0.3f;
	/** Players already there when the list opens just appear with it; later ones fade in. */
	private static final long OPEN_GRACE_NANOS = 300_000_000L;
	private static final int BADGE_COLOR = 0xFFFFFF55;
	private static final int BADGE_RIGHT = 26;
	private static final int BADGE_TOP = 18;
	private static final int ICON_WIDTH = 10;
	private static final int NO_TEAM = 0xFF2A2A2A;

	// Toasts
	private int toastSlot;
	private long toastNanos;
	private float toastStep;

	// Player list
	private static final class Row {
		float y;
		int x;
		long born;
		long seen;
	}

	private final Map<UUID, Row> rows = new HashMap<>();
	private long openNanos;
	private boolean snap;
	private long tabNanos;
	private float tabStep;
	private long frame;
	private float listFade = 1f;
	private float listAlpha = 1f;
	private boolean listActive;

	private ToastsFeature() {
		super("toasts", ToastsConfig.class, ToastsConfig::new);
	}

	@Override
	public void init() {
	}

	@Override
	public ConfigCategory buildCategory() {
		return ToastsConfigUi.buildCategory(this);
	}

	private static float elapsed(long last, long now) {
		return last == 0 ? 0f : Math.min((now - last) / 1e9f, 0.05f);
	}

	private static float glide(float value, float target, float seconds, float rate) {
		float next = value + (target - value) * (1f - (float) Math.exp(-seconds * rate));
		return Math.abs(target - next) < 0.05f ? target : next;
	}

	// ---------------------------------------------------------------- toasts

	public boolean smooth() {
		return isEnabled() && config.smoothToasts;
	}

	public boolean stack() {
		return isEnabled() && config.stackToasts;
	}

	public boolean hideRecipes() {
		return isEnabled() && config.hideRecipeToasts;
	}

	public double displayTime(double vanilla) {
		return isEnabled() ? vanilla * config.toastTime : vanilla;
	}

	/** Start of the visible toasts: they are laid out top to bottom in the order they came. */
	public void beginToasts() {
		toastSlot = 0;
		long now = System.nanoTime();
		toastStep = elapsed(toastNanos, now);
		toastNanos = now;
	}

	/** How far a toast has slid in (0 to 1), eased instead of linear. */
	public float slide(float portion) {
		return smooth() ? Easing.EASE_OUT_CUBIC.apply(Math.clamp(portion, 0f, 1f)) : portion;
	}

	/**
	 * The slot a toast is drawn in: right below the toasts that came before it, so the others close up when one has
	 * gone, gliding there.
	 */
	public float toastY(ToastSlot toast, int vanillaSlot, IntFunction<Float> yForSlot) {
		if (!smooth()) {
			return yForSlot.apply(vanillaSlot);
		}
		float target = yForSlot.apply(toastSlot);
		toastSlot += toast.cobblecomfort$slots();
		float y = toast.cobblecomfort$y();
		y = Float.isNaN(y) ? target : glide(y, target, toastStep, TOAST_GLIDE);
		toast.cobblecomfort$setY(y);
		return y;
	}

	/** "+2" on a toast that stands in for more, or "×3" on a recipe toast with several recipes. */
	public void drawBadge(GuiGraphicsExtractor g, String text) {
		// Like an item count, on the corner of the toast's icon.
		Font font = Minecraft.getInstance().font;
		g.nextStratum();
		g.text(font, text, BADGE_RIGHT - font.width(text), BADGE_TOP, BADGE_COLOR, true);
	}

	// ---------------------------------------------------------------- player list

	public void tabOpened() {
		openNanos = System.nanoTime();
		snap = true;
	}

	/** Start of the player list: fades and slides it in. */
	public void beginList(GuiGraphicsExtractor g) {
		long now = System.nanoTime();
		tabStep = elapsed(tabNanos, now);
		tabNanos = now;
		frame++;
		listActive = isEnabled();
		if (!listActive) {
			return;
		}
		float t = config.tabFade ? Math.min(1f, (now - openNanos) / 1e9f / TAB_SECONDS) : 1f;
		float eased = Easing.EASE_OUT_CUBIC.apply(t);
		listAlpha = eased;
		listFade = ScreenFade.push(eased);
		g.pose().pushMatrix();
		g.pose().translate(0f, -TAB_SLIDE * (1f - eased));
	}

	public void endList(GuiGraphicsExtractor g) {
		if (!listActive) {
			return;
		}
		ScreenFade.restore(listFade);
		g.pose().popMatrix();
		long current = frame;
		rows.values().removeIf(r -> r.seen != current);
		snap = false;
		listActive = false;
	}

	/** Where a player's row is drawn: gliding to a new place, and fading in when the player just joined. */
	public int rowY(int y, int x, PlayerInfo info) {
		if (!listActive || !config.tabSort) {
			return y;
		}
		long now = System.nanoTime();
		Row row = rows.get(info.getProfile().id());
		if (row == null) {
			row = new Row();
			row.y = y;
			row.born = snap || now - openNanos < OPEN_GRACE_NANOS ? 0 : now;
			rows.put(info.getProfile().id(), row);
		} else if (snap || row.x != x) {
			row.y = y;
		} else {
			row.y = glide(row.y, y, tabStep, ROW_GLIDE);
		}
		row.x = x;
		row.seen = frame;
		float alpha = row.born == 0 ? 1f : Math.min(1f, (now - row.born) / 1e9f / JOIN_SECONDS);
		// Each row starts from the list's own fade; the rows after the last player draw with the last row's.
		ScreenFade.restore(listFade);
		ScreenFade.push(listAlpha * Easing.EASE_OUT_CUBIC.apply(alpha));
		return Math.round(row.y);
	}

	/** Extra width of each player column for the ping number. */
	public int pingExtraWidth() {
		if (!isEnabled() || !config.pingNumbers) {
			return 0;
		}
		return Minecraft.getInstance().font.width("9999") + 1 - ICON_WIDTH;
	}

	/** The ping in milliseconds, coloured by how good it is; returns false to leave vanilla's bars. */
	public boolean drawPing(GuiGraphicsExtractor g, int width, int x, int y, PlayerInfo info) {
		if (!isEnabled() || !config.pingNumbers) {
			return false;
		}
		int latency = info.getLatency();
		String text = latency < 0 ? "?" : String.valueOf(latency);
		int color = latency < 0 ? 0xFFAAAAAA : latency < 100 ? 0xFF55FF55 : latency < 250 ? 0xFFFFFF55 : latency < 600 ? 0xFFFFAA00 : 0xFFFF5555;
		Font font = Minecraft.getInstance().font;
		g.text(font, text, x + width - 1 - font.width(text), y, color, true);
		return true;
	}

	/** A frame around a player's head in their team colour. */
	public void drawHeadFrame(GuiGraphicsExtractor g, int x, int y, int size, PlayerInfo info) {
		if (!isEnabled() || !config.headFrames) {
			return;
		}
		PlayerTeam team = info.getTeam();
		int color = team == null ? NO_TEAM : team.getColor().map(c -> 0xFF000000 | c.rgb()).orElse(NO_TEAM);
		g.fill(x - 1, y - 1, x + size + 1, y + size + 1, color);
	}
}
