package dev.cobblecomfort.features.map.render;

import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.features.map.waypoint.Waypoint;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Waypoint marker animations: a new waypoint drops onto its place with a small bounce and a ring spreading where it
 * lands; on the minimap, the edge arrow of a waypoint that has just left the view pulses.
 */
public final class WaypointAnim {
	private static final float DROP_MS = 550f;
	private static final float DROP_HEIGHT = 14f;
	/** When the bounce first touches the ground (easeOutBounce reaches 1 at this share of the drop). */
	private static final float FIRST_CONTACT = 0.364f;
	private static final float RING_MS = 450f;
	private static final float PULSE_MS = 650f;

	private WaypointAnim() {
	}

	/** How far above its place a waypoint added at {@code addedMs} is drawn (negative = up), 0 once it has landed. */
	public static float dropOffset(Waypoint waypoint, long now) {
		float t = (now - waypoint.addedMs) / DROP_MS;
		if (waypoint.addedMs == 0 || t >= 1f || t < 0f) {
			return 0f;
		}
		return -(1f - bounce(t)) * DROP_HEIGHT;
	}

	/** The ring spreading from where a new waypoint lands, in its colour. */
	public static void impact(GuiGraphicsExtractor g, Waypoint waypoint, float x, float y, long now, float scale) {
		if (waypoint.addedMs == 0) {
			return;
		}
		float t = (now - waypoint.addedMs - FIRST_CONTACT * DROP_MS) / RING_MS;
		if (t < 0f || t >= 1f) {
			return;
		}
		ring(g, x, y, (4f + 12f * easeOut(t)) * scale, waypoint.rgb() | 0xFF000000, 1f - t);
	}

	/** Size factor of an edge arrow whose waypoint left the view at {@code leftMs}: grows and settles back. */
	public static float pulse(long leftMs, long now) {
		float t = (now - leftMs) / PULSE_MS;
		if (leftMs == 0 || t >= 1f || t < 0f) {
			return 1f;
		}
		return 1f + 0.9f * (float) Math.sin(Math.PI * Math.min(1f, t * 1.6f)) * (1f - t);
	}

	/** The ring around a pulsing edge arrow. */
	public static void pulseRing(GuiGraphicsExtractor g, float x, float y, long leftMs, long now, int color) {
		float t = (now - leftMs) / PULSE_MS;
		if (leftMs == 0 || t >= 1f || t < 0f) {
			return;
		}
		ring(g, x, y, 3f + 9f * easeOut(t), color, (1f - t) * 0.9f);
	}

	private static void ring(GuiGraphicsExtractor g, float x, float y, float radius, int color, float alpha) {
		int size = Math.max(2, Math.round(radius * 2f));
		MapMarkers.ring(g, Math.round(x) - size / 2, Math.round(y) - size / 2, size, RenderUtil.withAlpha(color, alpha));
	}

	private static float easeOut(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	/** The usual "ease out bounce": falls, then two smaller bounces. */
	private static float bounce(float t) {
		float n = 7.5625f;
		float d = 2.75f;
		if (t < 1f / d) {
			return n * t * t;
		}
		if (t < 2f / d) {
			t -= 1.5f / d;
			return n * t * t + 0.75f;
		}
		if (t < 2.5f / d) {
			t -= 2.25f / d;
			return n * t * t + 0.9375f;
		}
		t -= 2.625f / d;
		return n * t * t + 0.984375f;
	}
}
