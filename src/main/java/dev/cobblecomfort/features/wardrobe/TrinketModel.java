package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.features.wardrobe.HatLayer.Box;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
		if (trinket.style == Trinket.Style.END_CRYSTAL) {
			crystal(pose, collector, trinket, light);
		} else if (trinket.style == Trinket.Style.COMPASS) {
			needle(pose, collector, trinket, light);
		}
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
			case WINTER_BAUBLE_RED, WINTER_BAUBLE_GOLD, WINTER_BAUBLE_BLUE -> {
				// A glass bauble under its metal cap, with a band round it and a shine.
				out.add(raw(-0.25f, 0f, -0.25f, 0.25f, 0.5f, 0.25f, t.accent));
				out.add(raw(-0.75f, 0.5f, -0.75f, 0.75f, 1.3f, 0.75f, t.accent));
				out.add(raw(-1.75f, 1.6f, -1.75f, 1.75f, 4.6f, 1.75f, a));
				out.add(raw(-2.1f, 2.1f, -1.25f, 2.1f, 4.1f, 1.25f, a));
				out.add(raw(-1.25f, 2.1f, -2.1f, 1.25f, 4.1f, 2.1f, a));
				out.add(raw(-1.25f, 1.25f, -1.25f, 1.25f, 4.95f, 1.25f, a));
				out.add(raw(-2.15f, 2.9f, -1.3f, 2.15f, 3.3f, 1.3f, b));
				out.add(raw(-1.3f, 2.9f, -2.15f, 1.3f, 3.3f, 2.15f, b));
				out.add(raw(-1.1f, 2.15f, 2.1f, -0.5f, 2.75f, 2.15f, c));
			}
			case WINTER_GINGERBREAD_CLASSIC, WINTER_GINGERBREAD_DARK, WINTER_GINGERBREAD_HONEY -> {
				// A flat gingerbread man with an iced face, buttons and zigzags on his arms and legs.
				out.add(raw(-0.25f, 0f, -0.25f, 0.25f, 0.5f, 0.25f, b));
				out.add(raw(-1.25f, 0.5f, -0.4f, 1.25f, 2.75f, 0.4f, a));
				out.add(raw(-1.5f, 2.75f, -0.4f, 1.5f, 5.25f, 0.4f, a));
				out.add(raw(-2.75f, 3f, -0.4f, -1.5f, 4f, 0.4f, a));
				out.add(raw(1.5f, 3f, -0.4f, 2.75f, 4f, 0.4f, a));
				out.add(raw(-1.5f, 5.25f, -0.4f, -0.25f, 7f, 0.4f, a));
				out.add(raw(0.25f, 5.25f, -0.4f, 1.5f, 7f, 0.4f, a));
				out.add(raw(-0.75f, 1.2f, 0.4f, -0.35f, 1.6f, 0.45f, c));
				out.add(raw(0.35f, 1.2f, 0.4f, 0.75f, 1.6f, 0.45f, c));
				out.add(raw(-0.6f, 2f, 0.4f, 0.6f, 2.25f, 0.45f, b));
				out.add(raw(-0.25f, 3.25f, 0.4f, 0.25f, 3.75f, 0.45f, t.accent));
				out.add(raw(-0.25f, 4.25f, 0.4f, 0.25f, 4.75f, 0.45f, t.accent));
				for (int s = -1; s <= 1; s += 2) {
					out.add(raw(s < 0 ? -2.55f : 2.15f, 3.4f, 0.4f, s < 0 ? -2.15f : 2.55f, 3.6f, 0.45f, b));
					out.add(raw(s < 0 ? -1.3f : 0.45f, 6.4f, 0.4f, s < 0 ? -0.45f : 1.3f, 6.6f, 0.45f, b));
				}
			}
			case WINTER_CANDY_CANE_RED, WINTER_CANDY_CANE_GREEN, WINTER_CANDY_CANE_MINT -> {
				// Hung by its crook: the hook to the left, the long stem down the right, striped all along.
				out.add(raw(-1.5f, 0f, -0.5f, 1.5f, 1f, 0.5f, a));
				out.add(raw(-0.5f, 0f, -0.55f, 0.25f, 1f, 0.55f, b));
				out.add(raw(-1.5f, 1f, -0.5f, -0.5f, 2.5f, 0.5f, b));
				for (float y = 1f; y < 8f; y += 0.75f) {
					boolean stripe = Math.round((y - 1f) / 0.75f) % 2 == 0;
					out.add(raw(0.5f, y, -0.5f, 1.5f, Math.min(y + 0.75f, 8f), 0.5f, stripe ? b : c));
				}
				out.add(raw(0.6f, 1.5f, 0.5f, 0.9f, 6.5f, 0.55f, t.accent));
			}
			case WINTER_SNOWMAN_RED, WINTER_SNOWMAN_GREEN, WINTER_SNOWMAN_BLUE -> {
				// Three snowballs with a little top hat, coal eyes and buttons, a carrot nose and a scarf.
				int coal = t.accent;
				out.add(raw(-0.75f, 0f, -0.75f, 0.75f, 1.2f, 0.75f, coal));
				out.add(raw(-1.2f, 1.2f, -1.2f, 1.2f, 1.45f, 1.2f, coal));
				out.add(raw(-1f, 1.45f, -1f, 1f, 3.1f, 1f, a));
				out.add(raw(-1.3f, 3f, -1.3f, 1.3f, 3.6f, 1.3f, b));
				out.add(raw(0.5f, 3.6f, 1f, 1.1f, 5f, 1.4f, b));
				out.add(raw(-1.3f, 3.6f, -1.3f, 1.3f, 5.1f, 1.3f, a));
				out.add(raw(-1.75f, 5f, -1.75f, 1.75f, 8f, 1.75f, a));
				out.add(raw(-2.05f, 5.5f, -1.4f, 2.05f, 7.5f, 1.4f, c));
				out.add(raw(-0.55f, 2f, 1f, -0.2f, 2.35f, 1.05f, coal));
				out.add(raw(0.2f, 2f, 1f, 0.55f, 2.35f, 1.05f, coal));
				out.add(raw(-0.2f, 2.45f, 1f, 0.2f, 2.75f, 1.8f, 0xFFE8862A));
				out.add(raw(-0.2f, 4f, 1.3f, 0.2f, 4.35f, 1.35f, coal));
				out.add(raw(-0.2f, 6f, 1.75f, 0.2f, 6.35f, 1.8f, coal));
			}
			case END_CRYSTAL_CLASSIC, END_CRYSTAL_VOID -> cage(out, t);
			case COMPASS_IRON, COMPASS_BRASS -> compass(out, t);
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

	// ---------------------------------------------------------------- end crystal

	/** Where the crystal floats in its cage, y down from the string (voxels). */
	private static final float CRYSTAL_Y = 4.75f;
	private static final float CAGE = 2.75f;
	private static final int BEDROCK = 0xFF4A4A4E;

	/**
	 * The cage an end crystal floats in: an obsidian cap with its purple glints and a ring for the string, four slim
	 * bars, and a base of bedrock, lit from above by the crystal.
	 */
	private static void cage(List<Box> out, Trinket t) {
		int obsidian = t.main[0];
		int glint = ARGB.scaleRGB(t.main[2], 0.45f);
		out.add(raw(-0.35f, -0.4f, -0.1f, 0.35f, 0f, 0.1f, 0xFF3A3A42));
		out.add(raw(-0.1f, 0f, -0.1f, 0.1f, 0.35f, 0.1f, 0xFF3A3A42));
		out.add(raw(-1.5f, 0.35f, -1.5f, 1.5f, 0.6f, 1.5f, obsidian));
		out.add(raw(-CAGE, 0.6f, -CAGE, CAGE, 1.35f, CAGE, obsidian));
		out.add(raw(-CAGE, 8.15f, -CAGE, CAGE, 9.15f, CAGE, BEDROCK));
		float bar = CAGE - 0.4f;
		for (float x : new float[]{-CAGE, bar}) {
			for (float z : new float[]{-CAGE, bar}) {
				out.add(raw(x, 1.35f, z, x + 0.4f, 8.15f, z + 0.4f, obsidian));
			}
		}
		// The obsidian's glints and the bedrock's speckles, round each side.
		float[][] glints = {{-1.75f, 0.8f}, {0.5f, 0.95f}, {1.6f, 0.7f}};
		float[][] specks = {{-2f, 8.4f, 1}, {-0.5f, 8.7f, 0}, {0.75f, 8.35f, 1}, {1.75f, 8.75f, 0}, {-1.25f, 8.8f, 0}};
		for (int side = 0; side < 4; side++) {
			for (float[] g : glints) {
				out.add(face(side, g[0] + side * 0.3f, g[1], 0.4f, 0.3f, glint));
			}
			for (float[] s : specks) {
				out.add(face(side, s[0] - side * 0.25f, s[1], 0.45f, 0.3f, s[2] > 0 ? 0xFF7A7A80 : 0xFF2A2A2E));
			}
		}
		out.add(raw(-1.25f, 8.1f, -1.25f, 1.25f, 8.15f, 1.25f, ARGB.scaleRGB(t.main[2], 0.6f)).glowing());
	}

	/** A small square on one side of the cap or base (0 front, then round), at x across it and y down, standing out a little. */
	private static Box face(int side, float x, float y, float w, float h, int color) {
		float out0 = CAGE;
		float out1 = CAGE + 0.05f;
		return switch (side) {
			case 0 -> raw(x, y, -out1, x + w, y + h, -out0, color);
			case 1 -> raw(out0, y, x, out1, y + h, x + w, color);
			case 2 -> raw(-x - w, y, out0, -x, y + h, out1, color);
			default -> raw(-out1, y, -x - w, -out0, y + h, -x, color);
		};
	}

	/**
	 * The crystal, as the game's own: two glass frames turning inside each other, each tilted, round a glowing core, all
	 * bobbing gently; two motes of its light circle it.
	 */
	private static void crystal(PoseStack pose, SubmitNodeCollector collector, Trinket t, int light) {
		float seconds = System.nanoTime() % 1_000_000_000_000L / 1e9f;
		float y = CRYSTAL_Y + 0.35f * Mth.sin(seconds * 2f);
		float spin = seconds * 1.5f % Mth.TWO_PI;
		Quaternionf tilt = new Quaternionf().rotationAxis(Mth.PI / 3f, Mth.SQRT_OF_TWO / 2f, 0f, Mth.SQRT_OF_TWO / 2f);
		pose.pushPose();
		pose.translate(0f, y * V, 0f);
		pose.mulPose(new Matrix4f().rotationY(spin).rotate(tilt));
		HatLayer.submit(pose, collector, frame(1.35f, 0.25f, t.main[1]), null, light);
		pose.mulPose(new Matrix4f().rotationY(spin).rotate(tilt));
		HatLayer.submit(pose, collector, frame(0.95f, 0.2f, ARGB.scaleRGB(t.main[1], 0.9f)), null, light);
		pose.mulPose(new Matrix4f().rotationY(spin).rotate(tilt));
		HatLayer.submit(pose, collector, List.of(raw(-0.55f, -0.55f, -0.55f, 0.55f, 0.55f, 0.55f, t.main[2]).glowing(),
				raw(-0.6f, -0.3f, -0.3f, 0.6f, 0.3f, 0.3f, t.accent).glowing(),
				raw(-0.3f, -0.6f, -0.3f, 0.3f, 0.6f, 0.3f, t.accent).glowing(),
				raw(-0.3f, -0.3f, -0.6f, 0.3f, 0.3f, 0.6f, t.accent).glowing()), null, light);
		pose.popPose();
		List<Box> motes = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			float a = seconds * 2.5f + i * Mth.PI;
			float mx = 1.9f * Mth.cos(a);
			float mz = 1.9f * Mth.sin(a);
			float my = CRYSTAL_Y + 1.2f * Mth.sin(seconds * 3f + i * 2f);
			motes.add(raw(mx - 0.15f, my - 0.15f, mz - 0.15f, mx + 0.15f, my + 0.15f, mz + 0.15f, t.accent).glowing());
		}
		HatLayer.submit(pose, collector, motes, null, light);
	}

	/** The twelve edges of a cube {@code half} from its middle, {@code e} thick. */
	private static List<Box> frame(float half, float e, int color) {
		List<Box> out = new ArrayList<>();
		float far = half - e;
		for (float a : new float[]{-half, far}) {
			for (float b : new float[]{-half, far}) {
				out.add(raw(-half, a, b, half, a + e, b + e, color));
				out.add(raw(a, -half, b, a + e, half, b + e, color));
				out.add(raw(a, b, -half, a + e, b + e, half, color));
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- compass

	/** Where the dial's middle is, y down from the string (voxels), and how far out its face lies. */
	private static final float DIAL_Y = 4.5f;
	private static final float DIAL_Z = 0.65f;

	/**
	 * A pocket compass hanging by its bow: the crown with its button, a round case with a raised bezel round the dial
	 * (a red mark at north, ticks at the other points and between them), and the lid swung open to the side on its hinge,
	 * a compass rose engraved inside it. The needle is drawn by {@link #needle}.
	 */
	private static void compass(List<Box> out, Trinket t) {
		int metal = t.main[0];
		int shade = t.main[1];
		int dial = t.main[2];
		// The bow, the crown and its button.
		out.add(raw(-0.6f, 0f, -0.15f, 0.6f, 0.25f, 0.15f, metal));
		out.add(raw(-0.6f, 0.25f, -0.15f, -0.35f, 0.9f, 0.15f, metal));
		out.add(raw(0.35f, 0.25f, -0.15f, 0.6f, 0.9f, 0.15f, metal));
		out.add(raw(-0.5f, 0.9f, -0.5f, 0.5f, 1.2f, 0.5f, shade));
		out.add(raw(-0.35f, 1.2f, -0.35f, 0.35f, 1.6f, 0.35f, metal));
		// The case, round, its back a little darker.
		out.add(raw(-2.5f, 1.5f, -0.6f, 2.5f, 7.5f, 0.6f, metal));
		out.add(raw(-3f, 2f, -0.6f, 3f, 7f, 0.6f, metal));
		out.add(raw(-2.75f, 1.75f, -0.65f, 2.75f, 7.25f, -0.6f, shade));
		// The bezel standing out round the dial.
		out.add(raw(-2.25f, 1.6f, 0.6f, 2.25f, 2.25f, 0.85f, metal));
		out.add(raw(-2.25f, 6.75f, 0.6f, 2.25f, 7.4f, 0.85f, metal));
		out.add(raw(-2.9f, 2.25f, 0.6f, -2.25f, 6.75f, 0.85f, metal));
		out.add(raw(2.25f, 2.25f, 0.6f, 2.9f, 6.75f, 0.85f, metal));
		out.add(raw(-2.25f, 2.25f, 0.6f, 2.25f, 6.75f, DIAL_Z, dial));
		// North in red, the other points and the ticks between them dark.
		int ink = 0xFF2A2A30;
		out.add(raw(-0.25f, 2.35f, DIAL_Z, 0.25f, 2.95f, DIAL_Z + 0.05f, t.accent));
		out.add(raw(-0.2f, 6.1f, DIAL_Z, 0.2f, 6.65f, DIAL_Z + 0.05f, ink));
		out.add(raw(-2.15f, DIAL_Y - 0.2f, DIAL_Z, -1.6f, DIAL_Y + 0.2f, DIAL_Z + 0.05f, ink));
		out.add(raw(1.6f, DIAL_Y - 0.2f, DIAL_Z, 2.15f, DIAL_Y + 0.2f, DIAL_Z + 0.05f, ink));
		for (float[] d : new float[][]{{-1.55f, 2.95f}, {1.3f, 2.95f}, {-1.55f, 5.8f}, {1.3f, 5.8f}}) {
			out.add(raw(d[0], d[1], DIAL_Z, d[0] + 0.25f, d[1] + 0.25f, DIAL_Z + 0.05f, ARGB.scaleRGB(dial, 0.7f)));
		}
		// The lid, open to the left on its hinge, the rose engraved inside.
		out.add(raw(-3.4f, 2.25f, -0.45f, -2.9f, 6.75f, 0.15f, shade));
		out.add(raw(-8.75f, 1.75f, -0.6f, -3.25f, 7.25f, -0.15f, metal));
		out.add(raw(-9.25f, 2.25f, -0.6f, -3.25f, 6.75f, -0.15f, metal));
		out.add(raw(-8.5f, 2.25f, -0.15f, -3.75f, 6.75f, -0.1f, shade));
		float rose = -6.125f;
		out.add(raw(rose - 0.15f, 2.75f, -0.1f, rose + 0.15f, 6.25f, -0.05f, metal));
		out.add(raw(rose - 1.75f, DIAL_Y - 0.15f, -0.1f, rose + 1.75f, DIAL_Y + 0.15f, -0.05f, metal));
		out.add(raw(rose - 0.6f, DIAL_Y - 0.6f, -0.1f, rose + 0.6f, DIAL_Y + 0.6f, -0.05f, metal));
		out.add(raw(rose - 0.3f, DIAL_Y - 0.3f, -0.1f, rose + 0.3f, DIAL_Y + 0.3f, -0.03f, shade));
	}

	/**
	 * The needle, turning to the north of the world (in a screen, where there is no north, it slowly searches round),
	 * trembling a little: red to the north, pale to the south, on a pin in the middle.
	 */
	private static void needle(PoseStack pose, SubmitNodeCollector collector, Trinket t, int light) {
		float seconds = System.nanoTime() % 1_000_000_000_000L / 1e9f;
		Matrix3f linear = pose.last().pose().get3x3(new Matrix3f());
		float angle;
		if (linear.transform(new Vector3f(0f, 1f, 0f)).length() > 4f || Math.abs(linear.determinant()) < 1e-12f) {
			angle = seconds * 0.8f;
		} else {
			// Entities are drawn turned as the world is, only moved to the camera: north is -z there.
			Vector3f north = linear.invert().transform(new Vector3f(0f, 0f, -1f));
			angle = (float) Math.atan2(north.x, -north.y);
		}
		angle += 0.06f * Mth.sin(seconds * 6f);
		pose.pushPose();
		pose.translate(0f, DIAL_Y * V, (DIAL_Z + 0.05f) * V);
		pose.mulPose(new Matrix4f().rotationZ(angle));
		HatLayer.submit(pose, collector, List.of(raw(-0.3f, -1.25f, 0f, 0.3f, 0f, 0.1f, t.accent),
				raw(-0.15f, -2f, 0f, 0.15f, -1.25f, 0.1f, t.accent), raw(-0.3f, 0f, 0f, 0.3f, 1.25f, 0.1f, 0xFFE8E8EC),
				raw(-0.15f, 1.25f, 0f, 0.15f, 2f, 0.1f, 0xFFE8E8EC), raw(-0.3f, -0.3f, 0.1f, 0.3f, 0.3f, 0.2f, t.main[1])), null, light);
		pose.popPose();
	}
}
