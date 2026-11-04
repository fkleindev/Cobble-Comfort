package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.features.wardrobe.HatLayer.Box;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.List;

import static dev.cobblecomfort.features.wardrobe.HatLayer.V;
import static dev.cobblecomfort.features.wardrobe.HatLayer.lighter;
import static dev.cobblecomfort.features.wardrobe.HatLayer.raw;

/**
 * The weapon and tool charms other than the name tag, as voxel models hanging from the end of their string (y down
 * from it, z towards the item's front). Drawn in the item's own space, where a unit is the whole item: a voxel here is
 * half an item pixel, and the details go down to a quarter of one. Parts lying on others stand out of them a little,
 * so no two faces share a plane.
 */
public final class ToolCharmModel {
	/** How far below the end of the string a charm reaches (item units): the middle of its weight for the pendulum. */
	public static final float HEIGHT = 6f * V * ToolCharmModel.SCALE;
	/** The models are made in half skin pixels, like the back charms; on an item they are drawn a little bigger. */
	static final float SCALE = 1.25f;
	private static final int WIRE = 0xFFB0B4BC;
	private static final int WIRE_DARK = 0xFF6E727A;
	private static final int WIRE_LIGHT = 0xFFE4E8EE;
	private static final int EYE_WHITE = 0xFFF4F4F0;
	private static final int PUPIL = 0xFF141418;
	/** A hair of extra size for a part that lies on another. */
	private static final float OUT = 0.05f;

	private ToolCharmModel() {
	}

	public static void submit(PoseStack pose, SubmitNodeCollector collector, ToolCharm charm, int light) {
		pose.pushPose();
		pose.scale(SCALE, SCALE, SCALE);
		HatLayer.submit(pose, collector, boxes(charm), null, light);
		pose.popPose();
	}

	private static List<Box> boxes(ToolCharm t) {
		List<Box> out = new ArrayList<>();
		int a = t.main[0];
		int b = t.main.length > 1 ? t.main[1] : a;
		int c = t.main.length > 2 ? t.main[2] : b;
		switch (t.style) {
			case TASSEL -> tassel(out, t, a, b);
			case ORE -> ore(out, t, a, b);
			case FEATHERS -> feathers(out, t, a, b, c);
			case LURE -> lure(out, t, a, b, c);
			case BELL -> bell(out, t, a, b);
			case GLOW_BERRY -> glowBerry(out, t, a, b);
			case NAME_TAG -> {
			}
		}
		return out;
	}

	/**
	 * A cord loop through a bead, a rounded head wound with thread, a band of the second colour with a shiny stripe, and a
	 * full fringe of thin strands round about, uneven at the ends and some darker where they twist.
	 */
	private static void tassel(List<Box> out, ToolCharm t, int a, int b) {
		int band = t.accent;
		// The loop the string goes through, and the bead under it.
		out.add(raw(-0.45f, 0f, -0.12f, -0.25f, 0.7f, 0.12f, band));
		out.add(raw(0.25f, 0f, -0.12f, 0.45f, 0.7f, 0.12f, band));
		ball(out, 0f, 1.05f, 0f, 0.5f, band);
		out.add(raw(-0.18f, 0.7f, 0.42f, 0.08f, 0.95f, 0.52f, lighter(band)));
		// The head, rounded at the top, with two turns of thread round it.
		out.add(raw(-0.7f, 1.5f, -0.7f, 0.7f, 1.8f, 0.7f, a));
		out.add(raw(-1.05f, 1.8f, -1.05f, 1.05f, 2.9f, 1.05f, a));
		out.add(raw(-1.1f, 2.05f, -1.1f, 1.1f, 2.2f, 1.1f, darker(a, 0.75f)));
		out.add(raw(-1.1f, 2.45f, -1.1f, 1.1f, 2.6f, 1.1f, darker(a, 0.75f)));
		out.add(raw(-0.8f, 1.85f, 1.05f, -0.45f, 2.75f, 1.1f, lighter(a)));
		// The band, with a stripe of thread catching the light.
		out.add(raw(-1.25f, 2.9f, -1.25f, 1.25f, 3.5f, 1.25f, band));
		out.add(raw(-1.3f, 3.1f, -1.3f, 1.3f, 3.25f, 1.3f, lighter(band)));
		// The fringe: a ring of strands round the outside and more within, flaring out a little at the bottom.
		float[] lengths = {4.7f, 5.3f, 4.9f, 5.6f, 5.1f, 4.5f, 5.4f, 4.8f, 5.2f, 5.5f, 4.6f, 5f};
		int i = 0;
		for (int gx = 0; gx < 4; gx++) {
			for (int gz = 0; gz < 4; gz++) {
				boolean corner = (gx == 0 || gx == 3) && (gz == 0 || gz == 3);
				if (corner) {
					continue;
				}
				float x = (gx - 1.5f) * 0.6f;
				float z = (gz - 1.5f) * 0.6f;
				float length = lengths[i % lengths.length];
				int color = (gx + gz) % 2 == 0 ? a : b;
				out.add(raw(x - 0.3f, 3.5f, z - 0.3f, x + 0.3f, 3.5f + length - 0.6f, z + 0.3f, color));
				// The ends spread out and are a shade darker.
				float fx = x * 1.15f;
				float fz = z * 1.15f;
				out.add(raw(fx - 0.3f, 3.5f + length - 0.6f, fz - 0.3f, fx + 0.3f, 3.5f + length, fz + 0.3f, darker(color, 0.85f)));
				i++;
			}
		}
		// A few twists down the strands in front.
		out.add(raw(-0.65f, 4.6f, 0.9f + OUT, -0.25f, 4.8f, 0.95f + OUT, darker(a, 0.7f)));
		out.add(raw(0.25f, 5.4f, 0.9f + OUT, 0.65f, 5.6f, 0.95f + OUT, darker(b, 0.7f)));
	}

	/**
	 * A crystal in a wire setting: a loop and a cap with four prongs gripping it; the crystal pointed at both ends, its
	 * faces lit on one side and shaded on the other, a smaller crystal growing from its side, and glints that shine.
	 */
	private static void ore(List<Box> out, ToolCharm t, int a, int b) {
		int light = lighter(a);
		int dark = darker(b, 0.8f);
		// The loop and the cap.
		out.add(raw(-0.4f, 0f, -0.1f, -0.2f, 0.6f, 0.1f, WIRE));
		out.add(raw(0.2f, 0f, -0.1f, 0.4f, 0.6f, 0.1f, WIRE));
		out.add(raw(-0.4f, 0f, -0.1f, 0.4f, 0.15f, 0.1f, WIRE_LIGHT));
		out.add(raw(-0.6f, 0.6f, -0.6f, 0.6f, 0.9f, 0.6f, WIRE));
		out.add(raw(-1f, 0.9f, -1f, 1f, 1.25f, 1f, WIRE_DARK));
		// The crystal: a point on top, the body, and a longer point below.
		out.add(raw(-0.75f, 1.25f, -0.6f, 0.75f, 1.6f, 0.6f, light));
		out.add(raw(-1.15f, 1.6f, -0.95f, 1.15f, 4.2f, 0.95f, a));
		out.add(raw(-0.85f, 4.2f, -0.7f, 0.85f, 4.9f, 0.7f, b));
		out.add(raw(-0.5f, 4.9f, -0.4f, 0.5f, 5.6f, 0.4f, dark));
		out.add(raw(-0.2f, 5.6f, -0.15f, 0.2f, 6.1f, 0.15f, dark));
		// Its faces: lit on the left, shaded on the right and at the back.
		out.add(raw(-1.15f - OUT, 1.8f, -0.7f, -0.9f, 4f, 0.7f, light));
		out.add(raw(0.9f, 1.8f, -0.7f, 1.15f + OUT, 4f, 0.7f, b));
		out.add(raw(-0.6f, 1.8f, -0.95f - OUT, 0.6f, 4f, -0.8f, dark));
		out.add(raw(-0.15f, 1.7f, 0.95f, 0.15f, 4.4f, 0.95f + OUT, light));
		// A smaller crystal growing out at the side.
		out.add(raw(1.15f, 3f, -0.4f, 1.7f, 4f, 0.4f, b));
		out.add(raw(1.3f, 2.5f, -0.25f, 1.65f, 3f, 0.25f, light));
		// The prongs holding it.
		for (int side = -1; side <= 1; side += 2) {
			out.add(raw(side * 1.15f - 0.12f - (side < 0 ? OUT : 0f), 1.25f, -0.12f, side * 1.15f + 0.12f + (side > 0 ? OUT : 0f), 2.2f, 0.12f,
					WIRE));
			out.add(raw(-0.12f, 1.25f, side * 0.95f - 0.12f - (side < 0 ? OUT : 0f), 0.12f, 2.2f, side * 0.95f + 0.12f + (side > 0 ? OUT : 0f),
					WIRE));
		}
		// Glints.
		out.add(raw(-0.75f, 2.1f, 0.95f + OUT, -0.4f, 2.45f, 1f + OUT, t.accent).glowing());
		out.add(raw(0.35f, 3.3f, 0.95f + OUT, 0.6f, 3.55f, 1f + OUT, t.accent).glowing());
		out.add(raw(-0.25f, 4.5f, 0.7f + OUT, 0f, 4.7f, 0.75f + OUT, t.accent).glowing());
	}

	/**
	 * Three feathers bound together with a leather wrap and a bead: each with a pale quill down the middle, a vane on
	 * either side with a notch in it, and its tip in the second colour; the outer two spread to the sides.
	 */
	private static void feathers(List<Box> out, ToolCharm t, int a, int b, int c) {
		int leather = t.accent;
		// The wrap with its turns, and a bead under it.
		out.add(raw(-0.2f, 0f, -0.2f, 0.2f, 0.4f, 0.2f, leather));
		out.add(raw(-0.7f, 0.4f, -0.6f, 0.7f, 1.5f, 0.6f, leather));
		out.add(raw(-0.75f, 0.6f, -0.65f, 0.75f, 0.75f, 0.65f, darker(leather, 0.7f)));
		out.add(raw(-0.75f, 1.05f, -0.65f, 0.75f, 1.2f, 0.65f, darker(leather, 0.7f)));
		ball(out, 0f, 1.85f, 0f, 0.42f, lighter(leather));
		for (int f = -1; f <= 1; f++) {
			int length = f == 0 ? 7 : 6;
			float z = f * 0.12f;
			for (int k = 0; k < length; k++) {
				// Steps out to the side as it goes down, narrow at the root and the tip.
				float x = f * (0.35f + k * 0.5f);
				float half = k == 0 ? 0.3f : k == length - 1 ? 0.4f : 0.65f;
				float y0 = 1.4f + k;
				int vane = k >= length - 2 ? b : f == 0 ? a : c;
				if (t == ToolCharm.FEATHERS_CHICKEN) {
					vane = k >= length - 2 ? c : f == 0 ? a : b;
				}
				// A notch: one step of the vane cut short on one side.
				boolean notch = k == length / 2;
				float left = notch && f >= 0 ? half * 0.45f : half;
				float right = notch && f < 0 ? half * 0.45f : half;
				out.add(raw(x - left, y0, z - 0.12f, x + right, y0 + 1f, z + 0.12f, vane));
				// The quill, a little proud of the vane.
				if (k < length - 1) {
					out.add(raw(x - 0.09f, y0, z - 0.17f, x + 0.09f, y0 + 1f, z + 0.17f, lighter(vane)));
				}
			}
			// The bare end of the quill above the vane.
			out.add(raw(f * 0.2f - 0.09f, 1.1f, z - 0.1f, f * 0.2f + 0.09f, 1.45f, z + 0.1f, 0xFFE8E0C8));
		}
	}

	/**
	 * A little fish on a hook, hanging by its mouth: a swivel, the hook through its lip, a rounded body pale underneath,
	 * a stripe and a gill, an eye on each side, a fin on the back and at each side, and a forked tail.
	 */
	private static void lure(List<Box> out, ToolCharm t, int a, int b, int c) {
		int belly = lighter(a);
		int fin = darker(c, 0.85f);
		// The swivel and the hook coming out of the mouth, with its point turned up.
		out.add(raw(-0.25f, 0f, -0.1f, 0.25f, 0.15f, 0.1f, WIRE_LIGHT));
		out.add(raw(-0.25f, 0f, -0.1f, -0.1f, 0.5f, 0.1f, WIRE));
		out.add(raw(0.1f, 0f, -0.1f, 0.25f, 0.5f, 0.1f, WIRE));
		out.add(raw(-0.25f, 0.5f, -0.1f, 0.25f, 0.65f, 0.1f, WIRE));
		out.add(raw(-0.08f, 0.65f, -0.08f, 0.08f, 1.15f, 0.08f, WIRE));
		out.add(raw(-0.08f, 1f, -0.62f, 0.08f, 1.15f, -0.08f, WIRE_DARK));
		out.add(raw(-0.08f, 0.75f, -0.75f, 0.08f, 1.15f, -0.6f, WIRE));
		// Flat side on: its sides face the front and back of the item. Head, mouth up.
		out.add(raw(-0.35f, 1f, -0.4f, 0.35f, 1.3f, 0.4f, t.accent));
		out.add(raw(-0.8f, 1.3f, -0.5f, 0.8f, 2.1f, 0.5f, a));
		out.add(raw(-1.15f, 2.1f, -0.65f, 1.15f, 2.6f, 0.65f, a));
		// The body, widest in the middle, the belly side paler.
		out.add(raw(-1.35f, 2.6f, -0.7f, 1.35f, 4.2f, 0.7f, a));
		out.add(raw(0.75f, 2.4f, -0.72f, 1.4f, 4.3f, 0.72f, belly));
		out.add(raw(-1f, 4.2f, -0.55f, 1f, 4.9f, 0.55f, a));
		out.add(raw(-0.55f, 4.9f, -0.4f, 0.55f, 5.3f, 0.4f, a));
		// A stripe across, and the gill behind the eye.
		out.add(raw(-1.4f, 3.3f, -0.75f, 1.45f, 3.7f, 0.75f, c));
		out.add(raw(-0.85f, 2.15f, -0.7f, 0.7f, 2.3f, 0.7f, darker(a, 0.7f)));
		// The eyes.
		for (int side = -1; side <= 1; side += 2) {
			float z0 = side > 0 ? 0.5f : -0.55f;
			out.add(raw(-0.55f, 1.45f, z0, -0.05f, 1.95f, z0 + 0.05f, EYE_WHITE));
			float p0 = side > 0 ? 0.55f : -0.6f;
			out.add(raw(-0.45f, 1.55f, p0, -0.2f, 1.8f, p0 + 0.05f, PUPIL));
		}
		// The fins: along the back, and one at each side behind the gill.
		out.add(raw(-1.85f, 2.7f, -0.12f, -1.35f, 3.9f, 0.12f, fin));
		out.add(raw(-1.6f, 2.9f, -0.12f, -1.35f, 4.3f, 0.12f, b));
		for (int side = -1; side <= 1; side += 2) {
			float z0 = side > 0 ? 0.7f : -0.8f;
			out.add(raw(-0.2f, 2.5f, z0, 0.6f, 3.1f, z0 + 0.1f, fin));
		}
		// The tail, forked.
		out.add(raw(-0.3f, 5.3f, -0.25f, 0.3f, 5.6f, 0.25f, c));
		out.add(raw(-1.5f, 5.6f, -0.15f, -0.15f, 6.1f, 0.15f, c));
		out.add(raw(0.15f, 5.6f, -0.15f, 1.5f, 6.1f, 0.15f, c));
		out.add(raw(-1.8f, 6.1f, -0.15f, -0.9f, 6.6f, 0.15f, fin));
		out.add(raw(0.9f, 6.1f, -0.15f, 1.8f, 6.6f, 0.15f, fin));
	}

	/**
	 * A bell: a loop on a crown, a shoulder, the body widening down to a raised band and a heavy lip, lit down its left
	 * and shaded down its right, and the clapper on its rod showing under the mouth.
	 */
	private static void bell(List<Box> out, ToolCharm t, int a, int b) {
		int light = lighter(a);
		int dark = darker(b, 0.8f);
		// The loop and the crown it stands on.
		out.add(raw(-0.45f, 0f, -0.15f, 0.45f, 0.2f, 0.15f, b));
		out.add(raw(-0.45f, 0f, -0.15f, -0.25f, 0.65f, 0.15f, b));
		out.add(raw(0.25f, 0f, -0.15f, 0.45f, 0.65f, 0.15f, b));
		out.add(raw(-0.65f, 0.65f, -0.65f, 0.65f, 0.95f, 0.65f, dark));
		// Shoulder and waist.
		out.add(raw(-0.95f, 0.95f, -0.95f, 0.95f, 1.4f, 0.95f, a));
		out.add(raw(-1.15f, 1.4f, -1.15f, 1.15f, 2.6f, 1.15f, a));
		// The body flaring out, with a raised band.
		out.add(raw(-1.35f, 2.6f, -1.35f, 1.35f, 3.4f, 1.35f, a));
		out.add(raw(-1.45f, 3.4f, -1.45f, 1.45f, 3.7f, 1.45f, b));
		out.add(raw(-1.55f, 3.7f, -1.55f, 1.55f, 4.3f, 1.55f, a));
		// The lip, heavy, and the dark mouth under it.
		out.add(raw(-1.8f, 4.3f, -1.8f, 1.8f, 4.85f, 1.8f, b));
		out.add(raw(-1.85f, 4.45f, -1.85f, 1.85f, 4.6f, 1.85f, light));
		out.add(raw(-1.4f, 4.85f, -1.4f, 1.4f, 4.9f, 1.4f, 0xFF1E1A16));
		// Light down the left, shade down the right.
		out.add(raw(-1.2f - OUT, 1.5f, -0.6f, -1.05f, 2.5f, 0.6f, light));
		out.add(raw(-1.4f - OUT, 2.7f, -0.7f, -1.25f, 3.3f, 0.7f, light));
		out.add(raw(1.05f, 1.5f, -0.9f, 1.2f + OUT, 2.5f, 0.9f, dark));
		out.add(raw(1.25f, 2.7f, -1f, 1.4f + OUT, 3.3f, 1f, dark));
		out.add(raw(-0.85f, 1.5f, 1.15f, -0.45f, 2.5f, 1.2f + OUT, light));
		out.add(raw(-0.95f, 2.7f, 1.35f, -0.5f, 3.3f, 1.4f + OUT, light));
		out.add(raw(-0.95f, 3.8f, 1.55f, -0.55f, 4.2f, 1.6f + OUT, light));
		// The clapper.
		out.add(raw(-0.1f, 4.9f, -0.1f, 0.1f, 5.3f, 0.1f, t.accent));
		ball(out, 0f, 5.65f, 0f, 0.42f, t.accent);
		out.add(raw(-0.3f, 5.35f, 0.32f, -0.05f, 5.55f, 0.42f, lighter(t.accent)));
	}

	/**
	 * A twisting bit of vine: leaves with a pale vein, little stems, and round berries with a glowing shine that light
	 * up in the dark.
	 */
	private static void glowBerry(List<Box> out, ToolCharm t, int a, int b) {
		int vine = darker(a, 0.85f);
		// The vine, stepping from side to side as it hangs.
		float[][] vineSteps = {{0f, 0f, 1.2f}, {0.2f, 1.2f, 2.4f}, {-0.1f, 2.4f, 3.5f}, {0.15f, 3.5f, 4.6f}, {-0.15f, 4.6f, 5.6f}};
		for (float[] step : vineSteps) {
			out.add(raw(step[0] - 0.25f, step[1], -0.25f, step[0] + 0.25f, step[2], 0.25f, vine));
		}
		// Leaves, each with a vein down it.
		leaf(out, 0.4f, 1f, 1, b);
		leaf(out, -0.35f, 2.9f, -1, b);
		leaf(out, 0.35f, 4.3f, 1, darker(b, 0.9f));
		// Berries on short stems.
		float[][] berries = {{0.95f, 2.3f}, {-1.15f, 4.1f}, {-0.2f, 6.2f}, {1.1f, 5.3f}};
		for (float[] berry : berries) {
			float x = berry[0];
			float y = berry[1];
			out.add(raw(Math.min(x, 0f) - 0.08f, y - 0.75f, -0.08f, Math.max(x, 0f) + 0.08f, y - 0.6f, 0.08f, vine));
			ball(out, x, y, 0f, 0.6f, t.accent);
			out.add(raw(x - 0.4f, y - 0.4f, 0.48f, x - 0.1f, y - 0.1f, 0.6f + OUT, 0xFFFFF0C0).glowing());
			out.add(raw(x - 0.12f, y - 0.66f, -0.12f, x + 0.12f, y - 0.55f, 0.12f, darker(t.accent, 0.6f)));
		}
		// The berries glow from within.
		for (int i = out.size() - 1; i >= 0; i--) {
			Box box = out.get(i);
			if (box.color() == t.accent) {
				out.set(i, box.glowing());
			}
		}
	}

	/** A leaf out to one side ({@code side} ±1) from the vine at {@code x}, {@code y}, with a pale vein. */
	private static void leaf(List<Box> out, float x, float y, int side, int color) {
		float x0 = side > 0 ? x : x - 1.5f;
		float x1 = side > 0 ? x + 1.5f : x;
		out.add(raw(x0, y, -0.12f, x1, y + 0.7f, 0.12f, color));
		float tip0 = side > 0 ? x1 : x0 - 0.4f;
		out.add(raw(tip0, y + 0.15f, -0.1f, tip0 + 0.4f, y + 0.55f, 0.1f, color));
		float in = side > 0 ? x + 0.2f : x - 0.2f;
		out.add(raw(Math.min(in, side > 0 ? x1 : x0), y + 0.3f, -0.17f, Math.max(in, side > 0 ? x1 : x0), y + 0.42f, 0.17f, lighter(color)));
	}

	/** A rounded lump: three crossed boxes about {@code cx, cy, cz}, {@code r} from the middle to a face. */
	private static void ball(List<Box> out, float cx, float cy, float cz, float r, int color) {
		float s = r * 0.7f;
		out.add(raw(cx - r, cy - s, cz - s, cx + r, cy + s, cz + s, color));
		out.add(raw(cx - s, cy - r, cz - s, cx + s, cy + r, cz + s, color));
		out.add(raw(cx - s, cy - s, cz - r, cx + s, cy + s, cz + r, color));
	}

	private static int darker(int color, float factor) {
		return ARGB.scaleRGB(color, factor);
	}
}
