package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.features.wardrobe.HatLayer.Box;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;

import java.util.ArrayList;
import java.util.List;

import static dev.cobblecomfort.features.wardrobe.HatLayer.V;
import static dev.cobblecomfort.features.wardrobe.HatLayer.lighter;
import static dev.cobblecomfort.features.wardrobe.HatLayer.raw;

/**
 * The back charms that are not pets, as voxel models half a skin pixel per voxel, hanging from the end of their string
 * (y down from it, +z facing away from the wearer).
 */
public final class TrinketModel {
	/** How far below the end of the string a charm reaches (blocks): the middle of its weight for the pendulum. */
	public static final float HEIGHT = 8f * V;
	/** On a player drawn as a wardrobe tile: the charm to show there instead of their own. */
	public static final RenderStateDataKey<Trinket> PREVIEW = RenderStateDataKey.create(() -> "cobblecomfort:trinket_preview");

	private TrinketModel() {
	}

	public static void submit(PoseStack pose, SubmitNodeCollector collector, Trinket trinket, int light) {
		HatLayer.submit(pose, collector, boxes(trinket), null, light);
	}

	private static List<Box> boxes(Trinket t) {
		List<Box> out = new ArrayList<>();
		int a = t.main[0];
		int b = t.main[1];
		int c = t.main.length > 2 ? t.main[2] : b;
		switch (t) {
			case MINI_CREEPER -> {
				out.add(raw(-1.75f, 0f, -1.75f, 1.75f, 3.5f, 1.75f, a));
				out.add(raw(-1.25f, 3.5f, -1f, 1.25f, 7f, 1f, b));
				out.add(raw(-1.5f, 7f, -1.25f, -0.25f, 8f, 1.25f, c));
				out.add(raw(0.25f, 7f, -1.25f, 1.5f, 8f, 1.25f, c));
				// The face, on the side away from the wearer.
				out.add(raw(-1.25f, 0.75f, 1.75f, -0.5f, 1.5f, 1.85f, t.accent));
				out.add(raw(0.5f, 0.75f, 1.75f, 1.25f, 1.5f, 1.85f, t.accent));
				out.add(raw(-0.5f, 1.5f, 1.75f, 0.5f, 2.25f, 1.85f, t.accent));
				out.add(raw(-0.9f, 2.25f, 1.75f, 0.9f, 3f, 1.85f, t.accent));
			}
			case MINI_AXOLOTL -> {
				out.add(raw(-1.75f, 0f, -1.5f, 1.75f, 3f, 1.5f, a));
				out.add(raw(-1.25f, 3f, -1.25f, 1.25f, 6f, 1.25f, b));
				out.add(raw(-0.75f, 6f, -0.4f, 0.75f, 8.5f, 0.4f, b));
				for (int s = -1; s <= 1; s += 2) {
					float x0 = s < 0 ? -2.75f : 1.75f;
					out.add(raw(x0, 0f, -0.3f, x0 + 1f, 0.6f, 0.3f, c));
					out.add(raw(x0, 1.1f, -0.3f, x0 + 1f, 1.7f, 0.3f, c));
					out.add(raw(x0, 2.2f, -0.3f, x0 + 1f, 2.8f, 0.3f, c));
				}
				out.add(raw(-1.25f, 1f, 1.5f, -0.75f, 1.5f, 1.6f, t.accent));
				out.add(raw(0.75f, 1f, 1.5f, 1.25f, 1.5f, 1.6f, t.accent));
			}
			case MINI_BEE -> {
				out.add(raw(-1.5f, 0f, -1.75f, 1.5f, 4f, 1.75f, a));
				out.add(raw(-1.55f, 1f, -1.8f, 1.55f, 1.6f, 1.8f, b));
				out.add(raw(-1.55f, 2.6f, -1.8f, 1.55f, 3.2f, 1.8f, b));
				out.add(raw(-0.3f, 4f, -0.3f, 0.3f, 4.75f, 0.3f, b));
				out.add(raw(-3.25f, 0.25f, -0.25f, -1.5f, 1.75f, 0.25f, c));
				out.add(raw(1.5f, 0.25f, -0.25f, 3.25f, 1.75f, 0.25f, c));
				out.add(raw(-1f, 0.5f, 1.75f, -0.4f, 1.1f, 1.85f, t.accent));
				out.add(raw(0.4f, 0.5f, 1.75f, 1f, 1.1f, 1.85f, t.accent));
			}
			case MINI_SLIME -> {
				out.add(raw(-2f, 0f, -2f, 2f, 4f, 2f, a));
				out.add(raw(-1.25f, 0.75f, -1.25f, 1.25f, 3.25f, 1.25f, c));
				out.add(raw(-1.25f, 0.75f, 2f, -0.5f, 1.5f, 2.1f, t.accent));
				out.add(raw(0.5f, 0.75f, 2f, 1.25f, 1.5f, 2.1f, t.accent));
				out.add(raw(-0.25f, 2.25f, 2f, 0.25f, 2.75f, 2.1f, t.accent));
				out.add(raw(-2.05f, 0f, -2.05f, 2.05f, 0.5f, 2.05f, lighter(a)));
			}
			case LANTERN_FIRE, LANTERN_SOUL -> {
				out.add(raw(-0.5f, 0f, -0.25f, 0.5f, 1f, 0.25f, b));
				out.add(raw(-1.5f, 1f, -1.5f, 1.5f, 1.75f, 1.5f, a));
				out.add(raw(-1.25f, 1.75f, -1.25f, 1.25f, 5f, 1.25f, t.accent).glowing());
				for (float[] p : new float[][]{{-1.5f, -1.5f}, {1f, -1.5f}, {-1.5f, 1f}, {1f, 1f}}) {
					out.add(raw(p[0], 1.75f, p[1], p[0] + 0.5f, 5f, p[1] + 0.5f, a));
				}
				out.add(raw(-1.5f, 5f, -1.5f, 1.5f, 5.75f, 1.5f, a));
			}
			case LUCKY_CLOVER -> {
				for (float[] l : new float[][]{{-2f, 0.5f}, {0.25f, 0.5f}, {-2f, 2.75f}, {0.25f, 2.75f}}) {
					out.add(raw(l[0], l[1], -0.3f, l[0] + 1.75f, l[1] + 1.75f, 0.3f, a));
					out.add(raw(l[0] + 0.5f, l[1] + 0.5f, 0.3f, l[0] + 1.25f, l[1] + 1.25f, 0.4f, b));
				}
				out.add(raw(-0.3f, 0f, -0.3f, 0.3f, 0.5f, 0.3f, t.accent));
				out.add(raw(-0.25f, 4.5f, -0.25f, 0.25f, 7f, 0.25f, t.accent));
			}
			case LUCKY_HORSESHOE -> {
				out.add(raw(-2.5f, 0.5f, -0.4f, -1.25f, 5.5f, 0.4f, a));
				out.add(raw(1.25f, 0.5f, -0.4f, 2.5f, 5.5f, 0.4f, a));
				out.add(raw(-2f, 5.5f, -0.4f, 2f, 6.75f, 0.4f, a));
				out.add(raw(-0.3f, 0f, -0.3f, 0.3f, 0.5f, 0.3f, b));
				for (float y : new float[]{1.5f, 3.5f}) {
					out.add(raw(-2.1f, y, 0.4f, -1.65f, y + 0.5f, 0.5f, t.accent));
					out.add(raw(1.65f, y, 0.4f, 2.1f, y + 0.5f, 0.5f, t.accent));
				}
			}
			case LUCKY_STAR -> {
				out.add(raw(-0.75f, 0f, -0.4f, 0.75f, 1.5f, 0.4f, a));
				out.add(raw(-2.75f, 1.5f, -0.4f, 2.75f, 3f, 0.4f, a));
				out.add(raw(-1.5f, 3f, -0.4f, 1.5f, 4.5f, 0.4f, a));
				out.add(raw(-2.25f, 4.5f, -0.4f, -0.75f, 5.75f, 0.4f, b));
				out.add(raw(0.75f, 4.5f, -0.4f, 2.25f, 5.75f, 0.4f, b));
				out.add(raw(-0.6f, 2f, 0.4f, 0.6f, 3f, 0.5f, t.accent).glowing());
			}
			case HALLOWEEN_GHOST_WHITE, HALLOWEEN_GHOST_SOUL, HALLOWEEN_GHOST_LILAC -> {
				// Softly glowing: a domed head, little arms, and a hem of three wavy tails.
				out.add(raw(-1.25f, 0f, -1f, 1.25f, 0.5f, 1f, c).glowing());
				out.add(raw(-1.75f, 0.5f, -1.5f, 1.75f, 4.5f, 1.5f, a).glowing());
				out.add(raw(-2f, 3.5f, -1.75f, 2f, 5f, 1.75f, a).glowing());
				out.add(raw(-2.75f, 2.25f, -0.5f, -1.75f, 3.25f, 0.5f, b).glowing());
				out.add(raw(1.75f, 2.5f, -0.5f, 2.75f, 3.5f, 0.5f, b).glowing());
				float[] tails = {6f, 5.5f, 6.25f};
				for (int i = 0; i < tails.length; i++) {
					float x = -2f + i * 4f / 3f;
					out.add(raw(x, 5f, -1.75f, x + 4f / 3f, tails[i], 1.75f, i % 2 == 0 ? b : a).glowing());
				}
				out.add(raw(-1.1f, 1.5f, 1.5f, -0.4f, 2.5f, 1.6f, t.accent));
				out.add(raw(0.4f, 1.5f, 1.5f, 1.1f, 2.5f, 1.6f, t.accent));
				out.add(raw(-0.4f, 3f, 1.5f, 0.4f, 3.75f, 1.6f, t.accent));
			}
			case HALLOWEEN_JACK_FIRE, HALLOWEEN_JACK_SOUL -> {
				out.add(raw(-0.3f, 0f, -0.3f, 0.3f, 0.6f, 0.3f, 0xFF3A3A40));
				out.add(raw(-0.4f, 0.4f, -0.4f, 0.4f, 1.2f, 0.4f, c));
				out.add(raw(-1.75f, 1.2f, -1.5f, 1.75f, 4.2f, 1.5f, a));
				out.add(raw(-2f, 1.6f, -1.2f, 2f, 3.8f, 1.2f, a));
				out.add(raw(-1.4f, 1f, -1.4f, 1.4f, 4.4f, 1.4f, a));
				out.add(raw(-0.15f, 1.2f, -1.6f, 0.15f, 4.2f, -1.5f, b));
				out.add(raw(-2.05f, 1.6f, -0.15f, -2f, 3.8f, 0.15f, b));
				out.add(raw(2f, 1.6f, -0.15f, 2.05f, 3.8f, 0.15f, b));
				out.add(raw(-1.2f, 1.9f, 1.5f, -0.4f, 2.6f, 1.6f, t.accent).glowing());
				out.add(raw(0.4f, 1.9f, 1.5f, 1.2f, 2.6f, 1.6f, t.accent).glowing());
				out.add(raw(-1.1f, 3f, 1.5f, 1.1f, 3.6f, 1.6f, t.accent).glowing());
				out.add(raw(-0.3f, 3f, 1.6f, 0.3f, 3.3f, 1.65f, a));
			}
			case AUTUMN_ACORN_LIGHT, AUTUMN_ACORN_DARK, AUTUMN_ACORN_GOLD -> {
				out.add(raw(-0.25f, 0f, -0.25f, 0.25f, 0.8f, 0.25f, t.accent));
				out.add(raw(-1.4f, 0.8f, -1.4f, 1.4f, 1.2f, 1.4f, c));
				out.add(raw(-1.9f, 1.2f, -1.9f, 1.9f, 2.5f, 1.9f, c));
				// The cap's scales.
				for (float[] d : new float[][]{{-1.4f, 1.5f}, {0.2f, 1.6f}, {-0.6f, 2f}, {1f, 2.05f}}) {
					out.add(raw(d[0], d[1], 1.9f, d[0] + 0.4f, d[1] + 0.35f, 1.95f, t.accent));
					out.add(raw(-d[0] - 0.4f, d[1], -1.95f, -d[0], d[1] + 0.35f, -1.9f, t.accent));
				}
				out.add(raw(-1.4f, 2.5f, -1.4f, 1.4f, 5f, 1.4f, a));
				out.add(raw(-1.45f, 4.2f, -1.45f, 1.45f, 5f, 1.45f, b));
				out.add(raw(-1f, 5f, -1f, 1f, 5.8f, 1f, b));
				out.add(raw(-0.35f, 5.8f, -0.35f, 0.35f, 6.3f, 0.35f, c));
				out.add(raw(-1.05f, 2.9f, 1.4f, -0.55f, 4f, 1.45f, lighter(a)));
			}
			case HALLOWEEN_CANDY_RED, HALLOWEEN_CANDY_ORANGE, HALLOWEEN_CANDY_PURPLE -> {
				// A sweet in its wrapper, twisted shut at both ends.
				out.add(raw(-0.25f, 0f, -0.25f, 0.25f, 1.5f, 0.25f, c));
				out.add(raw(-1.5f, 1.5f, -1.25f, 1.5f, 4f, 1.25f, a));
				out.add(raw(-0.9f, 1.45f, -1.3f, -0.4f, 4.05f, 1.3f, b));
				out.add(raw(0.4f, 1.45f, -1.3f, 0.9f, 4.05f, 1.3f, b));
				for (int s = -1; s <= 1; s += 2) {
					out.add(raw(s < 0 ? -2.2f : 1.5f, 2.25f, -0.6f, s < 0 ? -1.5f : 2.2f, 3.25f, 0.6f, c));
					out.add(raw(s < 0 ? -3.4f : 2.2f, 1.6f, -0.3f, s < 0 ? -2.2f : 3.4f, 3.9f, 0.3f, a));
					out.add(raw(s < 0 ? -3.5f : 3.4f, 1.4f, -0.2f, s < 0 ? -3.4f : 3.5f, 2.1f, 0.2f, b));
				}
				out.add(raw(-1.2f, 1.9f, 1.25f, -0.6f, 2.4f, 1.35f, t.accent));
			}
			default -> {
				out.add(raw(-0.4f, 0f, -0.2f, 0.4f, 0.75f, 0.2f, ARGB.scaleRGB(a, 0.7f)));
				out.add(raw(-1f, 0.75f, -1f, 1f, 2.5f, 1f, a));
				out.add(raw(-1.4f, 2.5f, -1.4f, 1.4f, 3.25f, 1.4f, a));
				out.add(raw(-1.6f, 3.25f, -1.6f, 1.6f, 3.6f, 1.6f, b));
				out.add(raw(-0.35f, 3.6f, -0.35f, 0.35f, 4.1f, 0.35f, t.accent));
				out.add(raw(0.4f, 1f, 1f, 0.8f, 2.25f, 1.05f, lighter(a)));
			}
		}
		return out;
	}
}
