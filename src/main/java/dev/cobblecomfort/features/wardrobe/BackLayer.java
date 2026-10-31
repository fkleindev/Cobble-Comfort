package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.cobblecomfort.core.VanillaAssets;
import dev.cobblecomfort.features.namecharm.CharmPendulum;
import dev.cobblecomfort.features.petplush.Torso;
import dev.cobblecomfort.features.plush.PlushFeature;
import dev.cobblecomfort.features.wardrobe.HatLayer.Box;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static dev.cobblecomfort.features.wardrobe.HatLayer.V;
import static dev.cobblecomfort.features.wardrobe.HatLayer.lighter;
import static dev.cobblecomfort.features.wardrobe.HatLayer.raw;

/**
 * Draws what a player carries on the back instead of a plush backpack, in the body's space (voxels half a skin pixel
 * big, y down from the top of the shoulders, +z out of the back): a hiking pack, wings, a sword, a guitar or a shell,
 * and in autumn bat wings, a broom, a pumpkin pack or a scythe.
 * Each has a spot a back charm hangs from ({@link #charmAnchor}).
 */
public final class BackLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** On a player drawn as a wardrobe tile: the piece to show there instead of their own. */
	public static final RenderStateDataKey<BackPiece> PREVIEW = RenderStateDataKey.create(() -> "cobblecomfort:back_preview");
	/** The sword and the guitar lie across the back at this angle (degrees, the sword's hilt over the left shoulder). */
	private static final float SLANT = 35f;
	/** Where along the back slanted pieces have their middle (voxels down from the shoulders). */
	private static final float SLANT_MIDDLE = 11f;
	/** How far the wings rise from the shoulders (degrees). */
	private static final float WING_RISE = 20f;
	/** How much bigger than their own boxes the bags and the shell, the guitar and the wings are drawn. */
	private static final float BIG = 1.25f;
	private static final float GUITAR_SCALE = 1.3f;
	private static final float WING_SCALE = 1.8f;
	/** The sword: its size in blocks, and how far its tip lies from its middle, down and to the side (voxels). */
	private static final float SWORD_SIZE = 0.9f;
	private static final float SWORD_TIP = 11f;
	/** The leather of the broom's and the scythe's straps. */
	private static final int LEATHER = 0xFF5A3A22;
	private static final long FORGET_NANOS = 2_000_000_000L;

	private static final Int2ObjectOpenHashMap<CharmPendulum[]> SWINGS = new Int2ObjectOpenHashMap<>();

	BackLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Minecraft mc = Minecraft.getInstance();
		if (state.isInvisible || state.isSpectator || mc.level == null) {
			return;
		}
		Entity entity = mc.level.getEntity(state.id);
		if (!(entity instanceof AbstractClientPlayer player)) {
			return;
		}
		BackPiece preview = state.getData(PREVIEW);
		BackPiece piece = preview != null ? preview : WardrobeFeature.INSTANCE.backFor(player);
		if (piece == null || piece.plush() != null) {
			// None, or a plush backpack: the plush layer draws that.
			return;
		}
		pose.pushPose();
		getParentModel().root().translateAndRotate(pose);
		getParentModel().body.translateAndRotate(pose);
		float back = backOf(state, getParentModel()) / V;
		float front = -back;
		List<Box> boxes = new ArrayList<>();
		List<Box> straps = new ArrayList<>();
		switch (piece.style) {
			case HIKING -> {
				hiking(boxes, piece, back);
				shoulderStraps(straps, piece.main[2], back, front);
			}
			case SHELL -> {
				shell(boxes, piece, back);
				shoulderStraps(straps, piece.second, back, front);
			}
			case PUMPKIN_PACK -> {
				pumpkin(boxes, piece, back);
				shoulderStraps(straps, piece.main[2], back, front);
			}
			case WINGS, SWORD, GUITAR, PLUSH_WOLF, PLUSH_CAT, BAT_WINGS, BROOM, SCYTHE -> {
			}
		}
		// Bigger than life, grown out from the middle of the back; the straps stay on the body.
		pose.pushPose();
		pose.translate(0f, 0f, back * V);
		pose.scale(BIG, BIG, BIG);
		pose.translate(0f, 0f, -back * V);
		HatLayer.submit(pose, collector, boxes, null, light);
		pose.popPose();
		HatLayer.submit(pose, collector, straps, null, light);
		switch (piece.style) {
			case HIKING -> {
				CharmPendulum[] pendulums = preview != null ? HatLayer.fresh() : pendulums(state.id);
				new HatLayer.Chains(pose, collector, pendulums, WardrobeFeature.INSTANCE.damping(), light, state.id, 0f)
						.hang(0, new Vector3f(7f * BIG * V, 9f * BIG * V, (back + 3f * BIG) * V), 1.5f, 1, k -> mug(piece));
			}
			case WINGS, BAT_WINGS -> wings(pose, collector, piece, back, player, preview != null, light);
			case SWORD -> sword(pose, collector, piece, back, front, player, light);
			case GUITAR -> slanted(pose, collector, back, front, -SLANT, guitar(piece), strap(piece.main[2]), light);
			case BROOM -> slanted(pose, collector, back, front, SLANT, broom(piece), strap(LEATHER), light);
			case SCYTHE -> slanted(pose, collector, back, front, -SLANT, scythe(piece), strap(LEATHER), light);
			case SHELL, PLUSH_WOLF, PLUSH_CAT, PUMPKIN_PACK -> {
			}
		}
		pose.popPose();
	}

	/** Whether the piece is a pair of wings, which spread far beyond the back. */
	static boolean winged(BackPiece piece) {
		return piece.style == BackPiece.Style.WINGS || piece.style == BackPiece.Style.BAT_WINGS;
	}

	/** How far behind the body's middle its back lies (blocks), over the jacket layer or a chestplate. */
	public static float backOf(AvatarRenderState state, PlayerModel model) {
		return Torso.of(model.body).inflated(Torso.worn(state, false)).back();
	}

	/** Where a back charm's string is tied on this piece, in the body's space (blocks), given the back's depth. */
	public static Vector3f charmAnchor(BackPiece piece, float back, AbstractClientPlayer player, boolean still) {
		float b = back / V;
		return switch (piece.style) {
			case HIKING -> new Vector3f(-6.5f * BIG * V, 17f * BIG * V, (b + 3f * BIG) * V);
			// From the lower edge of one wing, moving with it.
			case WINGS -> wingPoint(b, beat(player, still), 8f, 8f, 1.2f);
			case SWORD -> new Vector3f(-SWORD_TIP * V, (SLANT_MIDDLE + SWORD_TIP) * V, (b + 1f) * V);
			case GUITAR -> slantPoint(-SLANT, 0f, 10f * GUITAR_SCALE, b + 2.5f * GUITAR_SCALE);
			case SHELL -> new Vector3f(0f, 18f * BIG * V, (b + 3f * BIG) * V);
			case PLUSH_WOLF, PLUSH_CAT -> new Vector3f(0f, 12f * V, (b + 2f) * V);
			case BAT_WINGS -> wingPoint(b, beat(player, still), 6f, 4.5f, 1.2f);
			// From the band round the bristles, and from the scythe's grip.
			case BROOM -> slantPoint(SLANT, 0f, 7f * GUITAR_SCALE, b + 2.5f * GUITAR_SCALE);
			case SCYTHE -> slantPoint(-SLANT, 3f * GUITAR_SCALE, -6f * GUITAR_SCALE, b + 1.5f * GUITAR_SCALE);
			case PUMPKIN_PACK -> new Vector3f(-6f * BIG * V, 15f * BIG * V, (b + 4.5f * BIG) * V);
		};
	}

	/** A point given along a slanted piece (x across it, y along it from its middle) in the body's space (blocks). */
	private static Vector3f slantPoint(float degrees, float x, float y, float z) {
		double a = Math.toRadians(degrees);
		float px = (float) (x * Math.cos(a) - y * Math.sin(a));
		float py = (float) (x * Math.sin(a) + y * Math.cos(a));
		return new Vector3f(px * V, (py + SLANT_MIDDLE) * V, z * V);
	}

	// ---------------------------------------------------------------- hiking pack

	/** A hiking pack: a tall bag with a flap, a front pocket and buckles, a bedroll on top and straps over the shoulders. */
	private static void hiking(List<Box> out, BackPiece piece, float back) {
		int cloth = piece.main[0];
		int shade = piece.main[1];
		int straps = piece.main[2];
		out.add(raw(-6f, 3f, back, 6f, 18f, back + 6f, cloth));
		out.add(raw(-6.25f, 2.5f, back - 0.25f, 6.25f, 7f, back + 6.5f, shade));
		out.add(raw(-4.5f, 10f, back + 6f, 4.5f, 16.5f, back + 7.5f, shade));
		out.add(raw(-4f, 10f, back + 7.5f, 4f, 10.75f, back + 7.75f, lighter(shade)));
		for (float x : new float[]{-3.5f, 2.5f}) {
			out.add(raw(x, 5.5f, back + 6.5f, x + 1f, 9.5f, back + 6.75f, straps));
			out.add(raw(x - 0.1f, 8.75f, back + 6.75f, x + 1.1f, 9.75f, back + 7f, piece.second));
		}
		// The bedroll on top, tied with two straps.
		out.add(raw(-7.5f, -0.5f, back + 0.5f, 7.5f, 3f, back + 5f, piece.accent));
		out.add(raw(-7.6f, 0f, back + 1f, 7.6f, 2.5f, back + 4.5f, ARGB.scaleRGB(piece.accent, 0.85f)));
		for (float x : new float[]{-4.5f, 3.5f}) {
			out.add(raw(x, -0.75f, back + 0.25f, x + 1f, 3.25f, back + 5.25f, straps));
		}
	}

	/** Straps from the top of the back over both shoulders and down the chest. */
	private static void shoulderStraps(List<Box> out, int color, float back, float front) {
		for (float x : new float[]{-5f, 3f}) {
			out.add(raw(x, -0.5f, front - 0.5f, x + 2f, 0f, back, color));
			out.add(raw(x, 0f, front - 0.5f, x + 2f, 9f, front, color));
		}
	}

	private static List<Box> mug(BackPiece piece) {
		int tin = 0xFFB0B4BC;
		return List.of(raw(-0.25f, 0f, -0.25f, 0.25f, 1f, 0.25f, 0xFF3A3A40), raw(-1.25f, 1f, -1.25f, 1.25f, 3.5f, 1.25f, tin),
				raw(-1.35f, 1f, -1.35f, 1.35f, 1.4f, 1.35f, lighter(tin)), raw(1.25f, 1.6f, -0.25f, 2f, 2.9f, 0.25f, tin));
	}

	// ---------------------------------------------------------------- shell

	/** A turtle shell on the back: plates rising in tiers to the middle, lined like scutes, a rim and straps. */
	private static void shell(List<Box> out, BackPiece piece, float back) {
		out.add(raw(-7.5f, 2f, back, 7.5f, 19f, back + 1.5f, piece.accent));
		out.add(raw(-6.5f, 3f, back + 1.5f, 6.5f, 18f, back + 3.5f, piece.main[0]));
		out.add(raw(-4.5f, 5f, back + 3.5f, 4.5f, 16f, back + 5f, piece.main[1]));
		out.add(raw(-2.5f, 7f, back + 5f, 2.5f, 14f, back + 5.75f, piece.main[0]));
		int line = piece.main[2];
		// Seams between the scutes.
		out.add(raw(-6.5f, 10.25f, back + 3.5f, 6.5f, 10.75f, back + 3.6f, line));
		out.add(raw(-0.25f, 3f, back + 3.5f, 0.25f, 5f, back + 3.6f, line));
		out.add(raw(-0.25f, 16f, back + 3.5f, 0.25f, 18f, back + 3.6f, line));
		out.add(raw(-4.5f, 7.75f, back + 5f, 4.5f, 8.25f, back + 5.1f, line));
		out.add(raw(-4.5f, 12.75f, back + 5f, 4.5f, 13.25f, back + 5.1f, line));
		out.add(raw(-2.75f, 5f, back + 5f, -2.25f, 16f, back + 5.1f, line));
		out.add(raw(2.25f, 5f, back + 5f, 2.75f, 16f, back + 5.1f, line));
	}

	// ---------------------------------------------------------------- wings

	/**
	 * Two wings from the shoulder blades, spread out and back, beating slowly at rest and faster while the wearer moves
	 * or falls: feathered (angel), two-lobed (fairy) or webbed between bones (bat).
	 */
	private static void wings(PoseStack pose, SubmitNodeCollector collector, BackPiece piece, float back, AbstractClientPlayer player,
			boolean still, int light) {
		float beat = beat(player, still);
		List<Box> wing = piece.style == BackPiece.Style.BAT_WINGS ? leatherWing(piece) : switch (piece) {
			case WINGS_FAIRY -> fairyWing(piece);
			case WINGS_BAT -> batWing(piece);
			default -> angelWing(piece);
		};
		for (int s = -1; s <= 1; s += 2) {
			pose.pushPose();
			pose.translate(s * 1.5f * V, 4f * V, (back + 0.5f) * V);
			// Out to the side and back, beating round the shoulder.
			pose.rotateDegrees(Axis.YP, -s * (30f + beat));
			pose.rotateDegrees(Axis.ZP, s * WING_RISE);
			pose.scale(WING_SCALE, WING_SCALE, WING_SCALE);
			HatLayer.submit(pose, collector, s > 0 ? wing : mirrored(wing), null, light);
			pose.popPose();
		}
	}

	/** How far the wings are swept back beyond their rest (degrees) now: slowly at rest, faster and wider on the move. */
	private static float beat(AbstractClientPlayer player, boolean still) {
		double seconds = System.nanoTime() % 1_000_000_000_000L / 1e9;
		float speed = still ? 0f : (float) player.position().subtract(player.xo, player.yo, player.zo).length() * 20f;
		float rate = 2.5f + Math.min(speed, 10f) * 1.2f;
		return (float) Math.sin(seconds * rate) * (6f + Math.min(speed, 10f) * 2f);
	}

	/** A point on one wing (wing voxels, as drawn) where it is now, in the body's space (blocks). */
	private static Vector3f wingPoint(float back, float beat, float x, float y, float z) {
		Matrix4f wing = new Matrix4f().translate(1.5f, 4f, back + 0.5f).rotateY(Mth.DEG_TO_RAD * -(30f + beat))
				.rotateZ(Mth.DEG_TO_RAD * WING_RISE).scale(WING_SCALE);
		return wing.transformPosition(new Vector3f(x, y, z)).mul(V);
	}

	/** The same boxes mirrored left to right (a left wing from a right one), without turning their faces inside out. */
	private static List<Box> mirrored(List<Box> boxes) {
		List<Box> out = new ArrayList<>(boxes.size());
		for (Box b : boxes) {
			out.add(new Box(-b.x1(), b.y0(), b.z0(), -b.x0(), b.y1(), b.z1(), b.color(), b.helmet(), b.glow()));
		}
		return out;
	}

	/** An angel's wing, reaching out along +x: a bony arm along the top and rows of feathers below, longest outside. */
	private static List<Box> angelWing(BackPiece piece) {
		List<Box> out = new ArrayList<>();
		out.add(raw(0f, -1.5f, 0f, 13f, 1f, 1.25f, piece.main[0]));
		out.add(raw(10f, -2.5f, 0f, 15f, 0.5f, 1.25f, piece.main[0]));
		float[][] rows = {{0f, 1f, 11f, 4f}, {2f, 4f, 13f, 8f}, {5f, 8f, 15f, 12f}, {9f, 12f, 16f, 15f}};
		for (int r = 0; r < rows.length; r++) {
			float[] row = rows[r];
			int color = piece.main[Math.min(r, 2)];
			for (float x = row[0]; x < row[2]; x += 1.5f) {
				float tip = row[3] + ((x - row[0]) / (row[2] - row[0])) * 2f;
				out.add(raw(x, row[1], 0.15f, x + 1.5f, tip, 1.1f, (int) (x * 2) % 3 == 0 ? ARGB.scaleRGB(color, 0.95f) : color));
			}
		}
		out.add(raw(0f, -1.6f, 1.2f, 13f, -1.3f, 1.3f, piece.accent));
		return out;
	}

	/** A fairy's wing: a big rounded upper lobe and a smaller lower one, with veins. */
	private static List<Box> fairyWing(BackPiece piece) {
		List<Box> out = new ArrayList<>();
		float[][] upper = {{0f, -2f, 6f, 4f}, {2f, -5f, 10f, 5f}, {5f, -7f, 13f, 3f}, {9f, -8f, 14.5f, 0f}};
		for (float[] u : upper) {
			out.add(raw(u[0], u[1], 0.3f, u[2], u[3], 0.9f, piece.main[0]));
		}
		float[][] lower = {{0f, 4f, 5f, 9f}, {2f, 6f, 8f, 12f}, {4f, 9f, 9f, 14f}};
		for (float[] l : lower) {
			out.add(raw(l[0], l[1], 0.3f, l[2], l[3], 0.9f, piece.main[1]));
		}
		out.add(raw(0f, -0.25f, 0.2f, 12f, 0.25f, 1f, piece.main[2]));
		out.add(raw(1f, -4f, 0.2f, 9f, -3.5f, 1f, piece.main[2]));
		out.add(raw(1f, 5f, 0.2f, 7f, 5.5f, 1f, piece.main[2]));
		out.add(raw(11f, -6f, 0.95f, 12.5f, -4.5f, 1.05f, piece.accent));
		return out;
	}

	/** A bat's wing: an arm along the top, finger bones fanning down, membrane between with a scalloped edge. */
	private static List<Box> batWing(BackPiece piece) {
		List<Box> out = new ArrayList<>();
		out.add(raw(0f, -1f, 0f, 14f, 1f, 1.25f, piece.main[2]));
		float[][] fingers = {{5f, 9f}, {9f, 11f}, {13f, 8f}};
		for (float[] f : fingers) {
			out.add(raw(f[0] - 0.4f, 1f, 0f, f[0] + 0.4f, f[1], 1.25f, piece.main[2]));
		}
		float[][] membrane = {{0f, 1f, 5f, 8f}, {5f, 1f, 9f, 10f}, {9f, 1f, 13f, 7f}, {13f, 1f, 14.5f, 3f}};
		for (float[] m : membrane) {
			out.add(raw(m[0], m[1], 0.35f, m[2], m[3], 0.9f, piece.main[0]));
			out.add(raw(m[0] + 1f, m[3], 0.35f, m[2] - 1f, m[3] + 1f, 0.9f, piece.main[1]));
		}
		out.add(raw(13.5f, -2f, 0f, 14.5f, -1f, 1.25f, piece.accent));
		return out;
	}

	/**
	 * A real bat's wing: a forearm to the wrist with its thumb claw, three long fingers fanning out from there, and the
	 * leathery membrane between them, its edge curving in between the finger tips.
	 */
	private static List<Box> leatherWing(BackPiece piece) {
		List<Box> out = new ArrayList<>();
		// Raised: the forearm climbs to the wrist, the outermost finger reaching higher still.
		float[] wrist = {7f, -8f};
		// From the body through the finger tips, innermost first: the membrane's lower edge.
		float[][] edge = {{0f, 4f}, {8f, 7f}, {13f, 1f}, {16f, -9f}};
		float[] tip = edge[edge.length - 1];
		for (float x = 0f; x < tip[0]; x += 0.5f) {
			float mid = x + 0.25f;
			float top = mid < wrist[0] ? -0.5f + (wrist[1] + 0.5f) * mid / wrist[0]
					: wrist[1] + (tip[1] - wrist[1]) * (mid - wrist[0]) / (tip[0] - wrist[0]);
			int segment = 0;
			while (segment < edge.length - 2 && mid > edge[segment + 1][0]) {
				segment++;
			}
			float[] a = edge[segment];
			float[] b = edge[segment + 1];
			float t = (mid - a[0]) / (b[0] - a[0]);
			// Scalloped: the edge curves back in between two tips.
			float bottom = a[1] + (b[1] - a[1]) * t - 2.2f * (float) Math.sin(Math.PI * t);
			if (bottom > top) {
				out.add(raw(x, top, 0.35f, x + 0.5f, bottom, 0.9f, segment % 2 == 0 ? piece.main[0] : piece.main[1]));
			}
		}
		// The forearm, thick, and the fingers, thin, as rows of little bones.
		bone(out, 0f, -0.5f, wrist[0], wrist[1], 1.1f, piece.main[2]);
		for (int i = 1; i < edge.length; i++) {
			bone(out, wrist[0], wrist[1], edge[i][0], edge[i][1], 0.7f, piece.main[2]);
		}
		out.add(raw(wrist[0] - 0.4f, wrist[1] - 2f, 0.3f, wrist[0] + 0.4f, wrist[1], 0.95f, piece.accent));
		out.add(raw(wrist[0] - 0.9f, wrist[1] - 2.4f, 0.3f, wrist[0] - 0.2f, wrist[1] - 1.8f, 0.95f, piece.accent));
		return out;
	}

	/** A straight bone from one point to another as a row of small cubes, standing out on both sides of the membrane. */
	private static void bone(List<Box> out, float x0, float y0, float x1, float y1, float size, int color) {
		float length = (float) Math.hypot(x1 - x0, y1 - y0);
		int steps = Math.max(1, (int) Math.ceil(length / (size * 0.6f)));
		for (int i = 0; i <= steps; i++) {
			float x = x0 + (x1 - x0) * i / steps;
			float y = y0 + (y1 - y0) * i / steps;
			out.add(raw(x - size / 2f, y - size / 2f, 0.15f, x + size / 2f, y + size / 2f, 1.1f, color));
		}
	}

	// ---------------------------------------------------------------- pumpkin pack

	/** A carved pumpkin worn as a pack: round with ribs, a stem and a leaf on top, its grinning face lit from inside. */
	private static void pumpkin(List<Box> out, BackPiece piece, float back) {
		int skin = piece.main[0];
		int rib = piece.main[1];
		int glow = piece.accent;
		out.add(raw(-6.5f, 5f, back + 0.5f, 6.5f, 16f, back + 8.5f, skin));
		out.add(raw(-5.5f, 4f, back + 1f, 5.5f, 17f, back + 8f, skin));
		out.add(raw(-7f, 6f, back + 1.5f, 7f, 15f, back + 7.5f, skin));
		out.add(raw(-5.5f, 5.5f, back + 8.5f, 5.5f, 15.5f, back + 9f, skin));
		// The ribs, as grooves round it.
		for (float x : new float[]{-5f, 5f}) {
			out.add(raw(x - 0.3f, 4.5f, back + 9f, x + 0.3f, 16.5f, back + 9.1f, rib));
			out.add(raw(x - 0.3f, 3.9f, back + 1f, x + 0.3f, 4f, back + 8f, rib));
		}
		for (float z : new float[]{3.5f, 6f}) {
			out.add(raw(-7.1f, 6f, back + z - 0.3f, -7f, 15f, back + z + 0.3f, rib));
			out.add(raw(7f, 6f, back + z - 0.3f, 7.1f, 15f, back + z + 0.3f, rib));
		}
		out.add(raw(-0.3f, 4.5f, back + 8.5f, 0.3f, 5.5f, back + 9.05f, rib));
		// The face: three-cornered eyes, a nose, and a grin with two teeth.
		for (int s = -1; s <= 1; s += 2) {
			float x = s * 3f;
			out.add(raw(x - 1.5f, 8.5f, back + 9f, x + 1.5f, 9.5f, back + 9.1f, glow).glowing());
			out.add(raw(x - 1f, 7.75f, back + 9f, x + 1f, 8.5f, back + 9.1f, glow).glowing());
			out.add(raw(x - 0.5f, 7f, back + 9f, x + 0.5f, 7.75f, back + 9.1f, glow).glowing());
		}
		out.add(raw(-0.5f, 10f, back + 9f, 0.5f, 11f, back + 9.1f, glow).glowing());
		out.add(raw(-4f, 12f, back + 9f, 4f, 13.5f, back + 9.1f, glow).glowing());
		out.add(raw(-4.5f, 11.25f, back + 9f, -3.5f, 12f, back + 9.1f, glow).glowing());
		out.add(raw(3.5f, 11.25f, back + 9f, 4.5f, 12f, back + 9.1f, glow).glowing());
		out.add(raw(-2f, 12f, back + 9.1f, -1f, 12.75f, back + 9.2f, skin));
		out.add(raw(1f, 12.75f, back + 9.1f, 2f, 13.5f, back + 9.2f, skin));
		// The stem, bent over, and a leaf.
		out.add(raw(-0.75f, 2f, back + 3.75f, 0.75f, 4f, back + 5.25f, piece.second));
		out.add(raw(0.75f, 2f, back + 3.75f, 2f, 2.75f, back + 5.25f, piece.second));
		out.add(raw(-3.5f, 3.5f, back + 4f, -0.75f, 4f, back + 6f, lighter(piece.second)));
	}

	// ---------------------------------------------------------------- slanted: sword and guitar

	/**
	 * Something lying across the back at {@code degrees}, its boxes given along it (y from its middle, +z out), with a
	 * strap across the chest the other way.
	 */
	private static void slanted(PoseStack pose, SubmitNodeCollector collector, float back, float front, float degrees, List<Box> boxes,
			List<Box> strap, int light) {
		pose.pushPose();
		pose.translate(0f, SLANT_MIDDLE * V, (back + 0.25f) * V);
		pose.rotateDegrees(Axis.ZP, degrees);
		pose.scale(GUITAR_SCALE, GUITAR_SCALE, GUITAR_SCALE);
		HatLayer.submit(pose, collector, boxes, null, light);
		pose.popPose();
		pose.pushPose();
		pose.translate(0f, SLANT_MIDDLE * V, (front - 0.5f) * V);
		pose.rotateDegrees(Axis.ZP, -degrees);
		HatLayer.submit(pose, collector, strap, null, light);
		pose.popPose();
	}

	private static List<Box> strap(int color) {
		return List.of(raw(-1f, -13f, 0f, 1f, 13f, 0.5f, color));
	}

	/**
	 * The sword itself, as the game draws it (resource packs included), across the back with its hilt over the left
	 * shoulder; a leather band holds it, and a strap runs across the chest the other way.
	 */
	private static void sword(PoseStack pose, SubmitNodeCollector collector, BackPiece piece, float back, float front,
			AbstractClientPlayer player, int light) {
		pose.pushPose();
		pose.translate(0f, SLANT_MIDDLE * V, (back + 0.6f) * V);
		// Item models are drawn y up; here y is down. The sprite's hilt comes out over the shoulder, its tip by the hip.
		pose.mulPose(new Matrix4f().rotationX((float) Math.PI));
		pose.rotateDegrees(Axis.ZP, 180f);
		pose.scale(SWORD_SIZE, SWORD_SIZE, 1f);
		if (!VanillaAssets.submitItem(pose, collector, piece.icon(), light)) {
			ItemStackRenderState item = new ItemStackRenderState();
			PlushFeature.INSTANCE.plain = true;
			try {
				Minecraft.getInstance().getItemModelResolver().updateForLiving(item, piece.icon(), ItemDisplayContext.NONE, player);
			} finally {
				PlushFeature.INSTANCE.plain = false;
			}
			item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
		}
		pose.popPose();
		pose.pushPose();
		pose.translate(0f, SLANT_MIDDLE * V, back * V);
		pose.rotateDegrees(Axis.ZP, -45f);
		HatLayer.submit(pose, collector, List.of(raw(-0.75f, -3f, 0f, 0.75f, 3f, 1.5f, piece.main[0])), null, light);
		pose.popPose();
		pose.pushPose();
		pose.translate(0f, SLANT_MIDDLE * V, (front - 0.5f) * V);
		pose.rotateDegrees(Axis.ZP, 45f);
		HatLayer.submit(pose, collector, strap(piece.main[1]), null, light);
		pose.popPose();
	}

	/** An acoustic guitar, head up: two bouts with a sound hole and bridge, a neck with frets and strings, pegs. */
	private static List<Box> guitar(BackPiece piece) {
		int body = piece.main[0];
		int edge = piece.main[1];
		int neck = piece.main[2];
		List<Box> out = new ArrayList<>();
		out.add(raw(-5f, 2f, 0f, 5f, 9f, 2.5f, body));
		out.add(raw(-4f, 9f, 0f, 4f, 10f, 2.5f, body));
		out.add(raw(-3.75f, -4f, 0f, 3.75f, 2f, 2.5f, body));
		out.add(raw(-3f, -5f, 0f, 3f, -4f, 2.5f, body));
		out.add(raw(-5.1f, 2f, -0.1f, -4.9f, 9f, 2.6f, edge));
		out.add(raw(4.9f, 2f, -0.1f, 5.1f, 9f, 2.6f, edge));
		out.add(raw(-1.25f, -0.5f, 2.5f, 1.25f, 2f, 2.6f, 0xFF1A1410));
		out.add(raw(1.5f, 0f, 2.5f, 3.25f, 3f, 2.6f, piece.accent));
		out.add(raw(-2f, 5.5f, 2.5f, 2f, 6.25f, 2.7f, neck));
		out.add(raw(-0.75f, -16f, 0.75f, 0.75f, -5f, 1.75f, neck));
		for (float y = -14f; y < -5f; y += 2.25f) {
			out.add(raw(-0.75f, y, 1.75f, 0.75f, y + 0.2f, 1.8f, 0xFFB0B0B8));
		}
		for (float x : new float[]{-0.45f, 0f, 0.45f}) {
			out.add(raw(x - 0.08f, -16f, 1.8f, x + 0.08f, 6f, 1.85f, piece.second));
		}
		out.add(raw(-1.25f, -19f, 0.5f, 1.25f, -16f, 2f, neck));
		for (float y : new float[]{-18.5f, -17.25f}) {
			out.add(raw(-1.75f, y, 1f, -1.25f, y + 0.5f, 1.5f, 0xFFD8D8DC));
			out.add(raw(1.25f, y, 1f, 1.75f, y + 0.5f, 1.5f, 0xFFD8D8DC));
		}
		return out;
	}

	/** A witch's broom, handle up: a knotty handle, bristles bound in two bands flaring out to a ragged end. */
	private static List<Box> broom(BackPiece piece) {
		int wood = piece.main[0];
		List<Box> out = new ArrayList<>();
		out.add(raw(-0.6f, -19f, 0.4f, 0.6f, 7f, 1.6f, wood));
		// Knots, or the bark's dark marks on birch.
		for (float[] k : new float[][]{{-14f, -1f}, {-8f, 1f}, {-2f, -1f}, {3f, 1f}}) {
			float x = k[1] < 0 ? -0.7f : 0.5f;
			out.add(raw(x, k[0], 0.6f, x + 0.2f, k[0] + 1f, 1.4f, piece.main[1]));
		}
		out.add(raw(-0.75f, -19.5f, 0.25f, 0.75f, -18.75f, 1.75f, piece.main[1]));
		// The bristles, in strands of two colours.
		float[][] layers = {{6.5f, 9f, 1.75f}, {9f, 12f, 2.75f}, {12f, 15f, 3.5f}};
		for (float[] l : layers) {
			for (float x = -l[2]; x < l[2] - 0.01f; x += 0.75f) {
				int color = Math.floorMod((int) Math.floor(x / 0.75f), 2) == 0 ? piece.main[2] : piece.second;
				out.add(raw(x, l[0], 1f - l[2] * 0.5f, Math.min(x + 0.75f, l[2]), l[1], 1f + l[2] * 0.5f, color));
			}
		}
		float[] ends = {16f, 15.5f, 16.75f, 15.75f, 16.5f, 15.25f, 16.25f, 16f, 15.5f};
		for (int i = 0; i < ends.length; i++) {
			float x = -3.5f + i * 7f / ends.length;
			out.add(raw(x, 15f, -0.5f, x + 7f / ends.length, ends[i], 2.5f, i % 2 == 0 ? piece.main[2] : piece.second));
		}
		out.add(raw(-2f, 6.25f, -0.1f, 2f, 7.5f, 2.1f, piece.accent));
		out.add(raw(-3f, 10f, -0.45f, 3f, 10.75f, 2.45f, piece.accent));
		return out;
	}

	/** A scythe, handle down: a long shaft with a side grip and a wrapped end, the curved blade sweeping out at the top. */
	private static List<Box> scythe(BackPiece piece) {
		int wood = piece.main[0];
		int blade = piece.main[2];
		List<Box> out = new ArrayList<>();
		out.add(raw(-0.6f, -21f, 0.4f, 0.6f, 14f, 1.6f, wood));
		out.add(raw(-0.75f, 14f, 0.25f, 0.75f, 15f, 1.75f, piece.main[1]));
		out.add(raw(0.6f, -6.5f, 0.6f, 3f, -5.5f, 1.4f, piece.main[1]));
		out.add(raw(2.5f, -7f, 0.5f, 3.25f, -5f, 1.5f, piece.main[1]));
		out.add(raw(-0.7f, 8f, 0.3f, 0.7f, 11f, 1.7f, piece.second));
		out.add(raw(-0.7f, -19.5f, 0.3f, 0.7f, -17.5f, 1.7f, piece.second));
		// The blade, a crescent from the top of the shaft to a point curling down, its sharp edge lighter.
		float[][] crescent = {{-3f, 0.75f, -22f, -19.5f}, {-6f, -3f, -22.5f, -20f}, {-9f, -6f, -22.5f, -20.5f},
				{-11f, -9f, -22f, -20.25f}, {-12.5f, -11f, -21.25f, -19.5f}, {-13.5f, -12.5f, -20f, -18f}};
		for (float[] c : crescent) {
			out.add(raw(c[0], c[2], 0.6f, c[1], c[3], 1.4f, blade));
			Box edge = raw(c[0], c[3] - 0.5f, 0.55f, c[1], c[3], 1.45f, piece.accent);
			out.add(piece == BackPiece.HALLOWEEN_SCYTHE_SOUL ? edge.glowing() : edge);
		}
		return out;
	}

	// ---------------------------------------------------------------- pendulums

	private static CharmPendulum[] pendulums(int entityId) {
		long now = System.nanoTime();
		CharmPendulum[] pendulums = SWINGS.get(entityId);
		if (pendulums == null) {
			pendulums = HatLayer.fresh();
			SWINGS.put(entityId, pendulums);
			if (SWINGS.size() > 64) {
				SWINGS.int2ObjectEntrySet().removeIf(e -> now - e.getValue()[0].lastSeen > FORGET_NANOS);
			}
		}
		return pendulums;
	}

	static void forget() {
		SWINGS.clear();
	}
}
