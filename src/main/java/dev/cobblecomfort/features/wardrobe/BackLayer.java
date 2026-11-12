package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.cobblecomfort.core.VanillaAssets;
import dev.cobblecomfort.features.namecharm.CharmPendulum;
import dev.cobblecomfort.features.petplush.Torso;
import dev.cobblecomfort.features.plush.PlushFeature;
import dev.cobblecomfort.features.wardrobe.HatLayer.Box;
import dev.cobblecomfort.features.wardrobe.CompanionTexture.Material;
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
	/** Feathered and fairy wings stand higher and spread wider than the others: less swept back, more raised, longer. */
	private static final float WINGS_SWEEP = 20f;
	private static final float WINGS_RISE = -12f;
	private static final float WINGS_SPREAD = 1.3f;
	/** The sword: its size in blocks, and how far its tip lies from its middle, down and to the side (voxels). */
	private static final float SWORD_SIZE = 0.9f;
	private static final float SWORD_TIP = 11f;
	/** Where the fishing rod's tip is along it, from its middle (voxels, before scaling; up is negative). */
	private static final float ROD_TIP = -27f;
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
		BackPiece piece = preview != null || WardrobeScreen.isTile(state) ? preview : WardrobeFeature.INSTANCE.backFor(player);
		if (piece == null || piece.plush() != null) {
			// None, or a plush backpack: the plush layer draws that.
			return;
		}
		HatLayer.in(WornMaterials.of(piece.style), () -> drawPiece(pose, collector, light, state, player, piece, preview));
	}

	/** Draws a back piece, textured in its material ({@link WornMaterials}). */
	private void drawPiece(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state,
			AbstractClientPlayer player, BackPiece piece, BackPiece preview) {
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
				// A strap across the chest between them, clipped in the middle.
				straps.add(raw(-3f, 4f, front - 0.75f, 3f, 4.75f, front - 0.5f, piece.main[2]));
				straps.add(raw(-0.75f, 3.75f, front - 1f, 0.75f, 5f, front - 0.75f, 0xFF2A2A30));
			}
			case SHELL -> {
				shell(boxes, piece, back);
				shoulderStraps(straps, piece.second, back, front);
			}
			case PUMPKIN_PACK -> {
				pumpkin(boxes, piece, back);
				shoulderStraps(straps, piece.main[2], back, front);
			}
			case GIFT_SACK -> {
				sack(boxes, piece, back);
				shoulderStraps(straps, piece.main[1], back, front);
			}
			case TREE -> {
				tree(boxes, piece, back);
				shoulderStraps(straps, LEATHER, back, front);
			}
			case ROCKETS -> {
				rockets(boxes, piece, back);
				shoulderStraps(straps, piece.second, back, front);
			}
			case BEEHIVE -> {
				beehive(boxes, piece, back);
				shoulderStraps(straps, LEATHER, back, front);
			}
			case WINGS, SWORD, GUITAR, PLUSH_WOLF, PLUSH_CAT, BAT_WINGS, BROOM, SCYTHE, ICE_WINGS, ROD -> {
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
			case WINGS, BAT_WINGS, ICE_WINGS -> wings(pose, collector, piece, back, player, preview != null, light);
			case SWORD -> sword(pose, collector, piece, back, front, player, light);
			case GUITAR -> slanted(pose, collector, back, front, -SLANT, guitar(piece), strap(piece.main[2]), light);
			case BROOM -> slanted(pose, collector, back, front, SLANT, broom(piece), strap(LEATHER), light);
			case SCYTHE -> slanted(pose, collector, back, front, -SLANT, scythe(piece), strap(LEATHER), light);
			case ROD -> {
				slanted(pose, collector, back, front, SLANT, rod(piece), strap(LEATHER), light);
				// The line hangs from the tip over the shoulder, a fish on its hook.
				CharmPendulum[] pendulums = preview != null ? HatLayer.fresh() : pendulums(state.id);
				new HatLayer.Chains(pose, collector, pendulums, WardrobeFeature.INSTANCE.damping(), light, state.id, 0f)
						.hang(0, slantPoint(SLANT, 0f, ROD_TIP * GUITAR_SCALE, back + 0.25f + GUITAR_SCALE), 6f, 2, k -> line(piece, k));
			}
			case BEEHIVE -> bees(pose, collector, back, light);
			case SHELL, PLUSH_WOLF, PLUSH_CAT, PUMPKIN_PACK, GIFT_SACK, TREE, ROCKETS -> {
			}
		}
		pose.popPose();
	}

	/** Whether the piece is a pair of wings, which spread far beyond the back. */
	static boolean winged(BackPiece piece) {
		return switch (piece.style) {
			case WINGS, BAT_WINGS, ICE_WINGS -> true;
			default -> false;
		};
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
			case WINGS -> wingPoint(piece.style, b, beat(player, still), 8f, 8f, 1.2f);
			case SWORD -> new Vector3f(-SWORD_TIP * V, (SLANT_MIDDLE + SWORD_TIP) * V, (b + 1f) * V);
			case GUITAR -> slantPoint(-SLANT, 0f, 10f * GUITAR_SCALE, b + 2.5f * GUITAR_SCALE);
			case SHELL -> new Vector3f(0f, 18f * BIG * V, (b + 3f * BIG) * V);
			case PLUSH_WOLF, PLUSH_CAT -> new Vector3f(0f, 12f * V, (b + 2f) * V);
			case BAT_WINGS -> wingPoint(piece.style, b, beat(player, still), 6f, 4.5f, 1.2f);
			// From the band round the bristles, and from the scythe's grip.
			case BROOM -> slantPoint(SLANT, 0f, 7f * GUITAR_SCALE, b + 2.5f * GUITAR_SCALE);
			case SCYTHE -> slantPoint(-SLANT, 3f * GUITAR_SCALE, -6f * GUITAR_SCALE, b + 1.5f * GUITAR_SCALE);
			case PUMPKIN_PACK -> new Vector3f(-6f * BIG * V, 15f * BIG * V, (b + 4.5f * BIG) * V);
			case GIFT_SACK -> new Vector3f(-6.5f * BIG * V, 15f * BIG * V, (b + 3.5f * BIG) * V);
			case TREE -> new Vector3f(-6.5f * BIG * V, 15f * BIG * V, (b + 3f * BIG) * V);
			case ROCKETS -> new Vector3f(0f, 15f * BIG * V, (b + 2.5f * BIG) * V);
			case ICE_WINGS -> wingPoint(piece.style, b, beat(player, still), 6f, 2f, 1.2f);
			// From the reel seat above the cork grip.
			case ROD -> slantPoint(SLANT, 0f, 5f * GUITAR_SCALE, b + 2f * GUITAR_SCALE);
			case BEEHIVE -> new Vector3f(-6f * BIG * V, 16f * BIG * V, (b + 3.5f * BIG) * V);
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
		// The bedroll's rolled-up ends: a spiral of the outside and a darker inside.
		for (int s = -1; s <= 1; s += 2) {
			float x0 = s < 0 ? -7.7f : 7.6f;
			out.add(raw(x0, 0.5f, back + 1.5f, x0 + 0.1f, 2f, back + 4f, ARGB.scaleRGB(piece.accent, 0.7f)));
			out.add(raw(x0, 1f, back + 2.25f, x0 + 0.1f, 1.5f, back + 3.25f, piece.accent));
		}
		int seam = ARGB.scaleRGB(shade, 0.8f);
		// The lid: a zip across it and a little patch with a mountain on it.
		out.add(raw(-5.5f, 6.25f, back + 6.5f, 5.5f, 6.5f, back + 6.6f, seam));
		out.add(raw(4.5f, 6.25f, back + 6.6f, 5f, 7.25f, back + 6.7f, piece.second));
		out.add(raw(-1.5f, 3.75f, back + 6.5f, 1.5f, 5.75f, back + 6.6f, 0xFFE8E4D8));
		out.add(raw(-1f, 5f, back + 6.6f, 1f, 5.5f, back + 6.65f, cloth));
		out.add(raw(-0.5f, 4.5f, back + 6.6f, 0.5f, 5f, back + 6.65f, cloth));
		out.add(raw(-0.15f, 4.25f, back + 6.6f, 0.15f, 4.5f, back + 6.65f, 0xFFFFFFFF));
		// Seams down the front, loops along the pocket's sides, the pocket's zip and its pull.
		for (int s = -1; s <= 1; s += 2) {
			out.add(raw(s * 5.5f - 0.1f, 7f, back + 6f, s * 5.5f + 0.1f, 18f, back + 6.1f, seam));
			for (float y = 10.5f; y < 16f; y += 1.75f) {
				out.add(raw(s * 5f - 0.25f, y, back + 6f, s * 5f + 0.25f, y + 0.75f, back + 6.3f, straps));
			}
		}
		out.add(raw(-4.25f, 11.25f, back + 7.5f, 4.25f, 11.5f, back + 7.6f, seam));
		out.add(raw(3f, 11.25f, back + 7.6f, 3.4f, 12.25f, back + 7.7f, piece.second));
		// A tougher bottom, and the loops a hip belt would go through.
		out.add(raw(-6.1f, 16.75f, back - 0.1f, 6.1f, 18.1f, back + 6.1f, seam));
		for (int s = -1; s <= 1; s += 2) {
			out.add(raw(s < 0 ? -6.3f : 6f, 15f, back + 0.25f, s < 0 ? -6f : 6.3f, 17f, back + 1.5f, straps));
		}
		// Left: a side pocket holding a water bottle with its cap. Right: two compression straps with buckles.
		out.add(raw(-7.5f, 11f, back + 1f, -6f, 16.75f, back + 5f, shade));
		out.add(raw(-7.55f, 11f, back + 1f, -6f, 11.5f, back + 5f, seam));
		out.add(raw(-7.25f, 7.75f, back + 1.75f, -6.25f, 11f, back + 4.25f, 0xFF5AA8D8));
		out.add(raw(-7.3f, 8.75f, back + 1.7f, -6.2f, 9.25f, back + 4.3f, 0xFFA8D8F0));
		out.add(raw(-7.1f, 7f, back + 2.25f, -6.4f, 7.75f, back + 3.75f, 0xFF2A2A30));
		for (float y : new float[]{8.5f, 13.5f}) {
			out.add(raw(6f, y, back + 0.5f, 6.25f, y + 1f, back + 5.5f, straps));
			out.add(raw(6.25f, y - 0.15f, back + 2.5f, 6.4f, y + 1.15f, back + 3.5f, piece.second));
		}
	}

	/** Straps from the top of the back over both shoulders and down the chest. */
	private static void shoulderStraps(List<Box> out, int color, float back, float front) {
		for (float x : new float[]{-5f, 3f}) {
			out.add(raw(x, -0.5f, front - 0.5f, x + 2f, 0f, back, color).in(Material.LEATHER));
			out.add(raw(x, 0f, front - 0.5f, x + 2f, 9f, front, color).in(Material.LEATHER));
		}
	}

	private static List<Box> mug(BackPiece piece) {
		int tin = 0xFFB0B4BC;
		return List.of(raw(-0.25f, 0f, -0.25f, 0.25f, 1f, 0.25f, 0xFF3A3A40), raw(-1.25f, 1f, -1.25f, 1.25f, 3.5f, 1.25f, tin).in(Material.METAL),
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
		List<Box> wing = piece.style == BackPiece.Style.BAT_WINGS ? leatherWing(piece)
				: piece.style == BackPiece.Style.ICE_WINGS ? iceWing(piece) : switch (piece) {
			case WINGS_FAIRY -> fairyWing(piece);
			case WINGS_BAT -> batWing(piece);
			default -> angelWing(piece);
		};
		for (int s = -1; s <= 1; s += 2) {
			pose.pushPose();
			pose.translate(s * 1.5f * V, 4f * V, (back + 0.5f) * V);
			// Out to the side and back, beating round the shoulder.
			pose.rotateDegrees(Axis.YP, -s * (sweep(piece.style) + beat));
			pose.rotateDegrees(Axis.ZP, s * rise(piece.style));
			pose.scale(WING_SCALE * spread(piece.style), WING_SCALE, WING_SCALE);
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

	private static float sweep(BackPiece.Style style) {
		return style == BackPiece.Style.WINGS ? WINGS_SWEEP : 30f;
	}

	private static float rise(BackPiece.Style style) {
		return style == BackPiece.Style.WINGS ? WINGS_RISE : WING_RISE;
	}

	private static float spread(BackPiece.Style style) {
		return style == BackPiece.Style.WINGS ? WINGS_SPREAD : 1f;
	}

	/** A point on one wing (wing voxels, as drawn) where it is now, in the body's space (blocks). */
	private static Vector3f wingPoint(BackPiece.Style style, float back, float beat, float x, float y, float z) {
		Matrix4f wing = new Matrix4f().translate(1.5f, 4f, back + 0.5f).rotateY(Mth.DEG_TO_RAD * -(sweep(style) + beat))
				.rotateZ(Mth.DEG_TO_RAD * rise(style)).scale(WING_SCALE * spread(style), WING_SCALE, WING_SCALE);
		return wing.transformPosition(new Vector3f(x, y, z)).mul(V);
	}

	/** The same boxes mirrored left to right (a left wing from a right one), without turning their faces inside out. */
	private static List<Box> mirrored(List<Box> boxes) {
		List<Box> out = new ArrayList<>(boxes.size());
		for (Box b : boxes) {
			out.add(new Box(-b.x1(), b.y0(), b.z0(), -b.x0(), b.y1(), b.z1(), b.color(), b.helmet(), b.glow(), b.material()));
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

	// ---------------------------------------------------------------- christmas and new year

	/** Santa's sack: bulging, gathered at the neck with a rope, a patch sewn on, and two wrapped gifts peeking out. */
	private static void sack(List<Box> out, BackPiece piece, float back) {
		int cloth = piece.main[0];
		int shade = piece.main[1];
		out.add(raw(-6f, 6f, back, 6f, 18f, back + 7f, cloth));
		out.add(raw(-5f, 5f, back + 0.5f, 5f, 19f, back + 6.5f, cloth));
		out.add(raw(-6.75f, 8f, back + 1f, 6.75f, 16f, back + 6f, cloth));
		out.add(raw(-5f, 17f, back + 0.25f, 5f, 18.5f, back + 6.75f, shade));
		// Folds where it is gathered.
		out.add(raw(-3f, 2.5f, back + 2f, 3f, 5f, back + 5f, cloth));
		for (float x : new float[]{-2f, 0f, 2f}) {
			out.add(raw(x - 0.2f, 4.5f, back + 5f, x + 0.2f, 8f, back + 7.1f, shade));
		}
		out.add(raw(-3.25f, 3.5f, back + 1.75f, 3.25f, 4.25f, back + 5.25f, piece.main[2]));
		out.add(raw(2.5f, 4.25f, back + 4.75f, 3.25f, 7f, back + 5.25f, piece.main[2]));
		out.add(raw(1.5f, 11f, back + 7f, 4f, 13.5f, back + 7.1f, shade));
		// The gifts, each with a ribbon round it and a bow on top.
		gift(out, -4f, 0f, -2f, back + 1.5f, back + 5f, piece.accent, piece.main[2]);
		gift(out, 0.25f, 3.25f, -0.5f, back + 2.5f, back + 5.5f, piece.second, piece.accent);
	}

	private static void gift(List<Box> out, float x0, float x1, float top, float z0, float z1, int paper, int ribbon) {
		float mx = (x0 + x1) / 2f;
		float mz = (z0 + z1) / 2f;
		out.add(raw(x0, top, z0, x1, 3f, z1, paper));
		out.add(raw(mx - 0.3f, top - 0.05f, z0 - 0.05f, mx + 0.3f, 3f, z1 + 0.05f, ribbon));
		out.add(raw(x0 - 0.05f, top - 0.05f, mz - 0.3f, x1 + 0.05f, 3f, mz + 0.3f, ribbon));
		out.add(raw(mx - 1.25f, top - 1f, mz - 0.4f, mx - 0.2f, top, mz + 0.4f, ribbon));
		out.add(raw(mx + 0.2f, top - 1f, mz - 0.4f, mx + 1.25f, top, mz + 0.4f, ribbon));
	}

	/**
	 * A little fir tree strapped to the back: tiers of needles narrowing up, snow or a shade on their edges, baubles,
	 * lights that glow, and a shining star on top.
	 */
	private static void tree(List<Box> out, BackPiece piece, float back) {
		int needles = piece.main[0];
		int edge = piece.main[1];
		out.add(raw(-1.25f, 17f, back + 2f, 1.25f, 19f, back + 4.5f, piece.main[2]));
		// Tiers: top, bottom (voxels down), half width, depth.
		float[][] tiers = {{12f, 17f, 6.5f, 6f}, {7.5f, 12.5f, 5f, 5f}, {3.5f, 8f, 3.5f, 4f}, {0.5f, 4f, 2f, 3f}};
		for (float[] t : tiers) {
			float z0 = back + 0.5f + (6f - t[3]) / 2f;
			out.add(raw(-t[2], t[0], z0, t[2], t[1], z0 + t[3], needles));
			out.add(raw(-t[2] - 0.25f, t[1] - 1f, z0 - 0.25f, t[2] + 0.25f, t[1], z0 + t[3] + 0.25f, edge));
		}
		// Baubles on the outer face, and lights glowing between them.
		float[][] baubles = {{-4f, 14.5f}, {3f, 15f}, {-1f, 10f}, {2.5f, 9f}, {-2f, 6f}, {1f, 2.5f}};
		int[] lights = {0xFFF2D23A, 0xFFF23A3A, 0xFF4A9AF2, 0xFFF4F4F0};
		for (int i = 0; i < baubles.length; i++) {
			float x = baubles[i][0];
			float y = baubles[i][1];
			float face = back + 6.5f - (6f - depthAt(tiers, y)) / 2f;
			out.add(raw(x - 0.6f, y - 0.6f, face, x + 0.6f, y + 0.6f, face + 0.6f, i % 2 == 0 ? piece.second : piece.accent));
			float lx = x + (i % 2 == 0 ? 2f : -2f);
			out.add(raw(lx - 0.3f, y + 1f, face, lx + 0.3f, y + 1.6f, face + 0.3f, lights[i % lights.length]).glowing());
		}
		// The star: a point up, two to the sides, and a glowing middle.
		float z = back + 2.75f;
		out.add(raw(-1f, -2f, z, 1f, 0.5f, z + 1f, piece.accent).glowing());
		out.add(raw(-0.4f, -3f, z, 0.4f, -2f, z + 1f, piece.accent).glowing());
		out.add(raw(-2f, -1.25f, z, 2f, -0.5f, z + 1f, piece.accent).glowing());
	}

	/** The depth of the tree's tier at the height {@code y} (voxels down). */
	private static float depthAt(float[][] tiers, float y) {
		for (float[] t : tiers) {
			if (y >= t[0] && y <= t[1]) {
				return t[3];
			}
		}
		return tiers[0][3];
	}

	/** Two New Year's rockets on a harness: striped tubes with pointed tips, fins and nozzles at the bottom, and fuses. */
	private static void rockets(List<Box> out, BackPiece piece, float back) {
		out.add(raw(-5f, 3f, back, 5f, 15f, back + 1f, piece.second));
		out.add(raw(-5.5f, 8f, back - 0.1f, 5.5f, 9f, back + 1.1f, ARGB.scaleRGB(piece.second, 0.8f)));
		for (int s = -1; s <= 1; s += 2) {
			float x = s * 3.25f;
			float z0 = back + 1f;
			float z1 = back + 4.5f;
			float mz = (z0 + z1) / 2f;
			out.add(raw(x - 1.75f, 4f, z0, x + 1.75f, 16f, z1, piece.main[0]));
			for (float y : new float[]{6f, 9.5f, 13f}) {
				out.add(raw(x - 1.8f, y, z0 - 0.05f, x + 1.8f, y + 1f, z1 + 0.05f, piece.main[1]));
			}
			out.add(raw(x - 1.25f, 2f, mz - 1.25f, x + 1.25f, 4f, mz + 1.25f, piece.main[2]));
			out.add(raw(x - 0.75f, 0.5f, mz - 0.75f, x + 0.75f, 2f, mz + 0.75f, piece.main[2]));
			out.add(raw(x - 0.3f, -0.5f, mz - 0.3f, x + 0.3f, 0.5f, mz + 0.3f, lighter(piece.main[2])));
			// Fins to the sides and out from the back.
			out.add(raw(x - 2.75f, 13.5f, mz - 0.25f, x - 1.75f, 17f, mz + 0.25f, piece.accent));
			out.add(raw(x + 1.75f, 13.5f, mz - 0.25f, x + 2.75f, 17f, mz + 0.25f, piece.accent));
			out.add(raw(x - 0.25f, 13.5f, z1, x + 0.25f, 17f, z1 + 1f, piece.accent));
			out.add(raw(x - 1f, 16f, mz - 1f, x + 1f, 17f, mz + 1f, 0xFF2A2A2E));
			out.add(raw(x - 0.15f, 17f, mz - 0.15f, x + 0.15f, 18.5f, mz + 0.15f, 0xFFB8A070));
		}
	}

	/**
	 * A wing of ice: long crystal shards fanning out from the shoulder, tapering to glowing tips, with frosted plates
	 * between them near the root and smaller shards branching off.
	 */
	private static List<Box> iceWing(BackPiece piece) {
		List<Box> out = new ArrayList<>();
		for (float x = 0f; x < 9f; x += 1f) {
			float top = -0.5f - 0.9f * x;
			float bottom = 3f - 0.1f * x;
			out.add(raw(x, top, 0.4f, x + 1f, bottom, 0.85f, x % 2 == 0 ? piece.main[2] : piece.main[1]));
		}
		float[][] shards = {{14f, -13f}, {17f, -6f}, {16f, 1f}, {11f, 6f}};
		for (float[] s : shards) {
			shard(out, 0f, 0.5f, s[0], s[1], 2.4f, 0.6f, piece);
			// A small shard off its middle.
			float mx = s[0] * 0.55f;
			float my = 0.5f + (s[1] - 0.5f) * 0.55f;
			shard(out, mx, my, mx + 2.5f, my - 2.5f, 1f, 0.5f, piece);
		}
		return out;
	}

	/** A crystal from one point to another as a row of cubes, tapering, light outside and glowing at its tip. */
	private static void shard(List<Box> out, float x0, float y0, float x1, float y1, float size0, float size1, BackPiece piece) {
		float length = (float) Math.hypot(x1 - x0, y1 - y0);
		int steps = Math.max(2, (int) Math.ceil(length / 0.7f));
		for (int i = 0; i <= steps; i++) {
			float t = (float) i / steps;
			float x = x0 + (x1 - x0) * t;
			float y = y0 + (y1 - y0) * t;
			float h = (size0 + (size1 - size0) * t) / 2f;
			int color = t < 0.4f ? piece.main[1] : piece.main[0];
			Box cube = raw(x - h, y - h, 0.6f - h * 0.5f, x + h, y + h, 0.6f + h * 0.5f, t > 0.85f ? piece.accent : color);
			out.add(t > 0.85f ? cube.glowing() : cube);
		}
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
		return List.of(raw(-1f, -13f, 0f, 1f, 13f, 0.5f, color).in(Material.LEATHER));
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
				out.add(raw(x, l[0], 1f - l[2] * 0.5f, Math.min(x + 0.75f, l[2]), l[1], 1f + l[2] * 0.5f, color).in(Material.STRAW));
			}
		}
		float[] ends = {16f, 15.5f, 16.75f, 15.75f, 16.5f, 15.25f, 16.25f, 16f, 15.5f};
		for (int i = 0; i < ends.length; i++) {
			float x = -3.5f + i * 7f / ends.length;
			out.add(raw(x, 15f, -0.5f, x + 7f / ends.length, ends[i], 2.5f, i % 2 == 0 ? piece.main[2] : piece.second).in(Material.STRAW));
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
			out.add(raw(c[0], c[2], 0.6f, c[1], c[3], 1.4f, blade).in(Material.METAL));
			Box edge = raw(c[0], c[3] - 0.5f, 0.55f, c[1], c[3], 1.45f, piece.accent);
			out.add(piece == BackPiece.HALLOWEEN_SCYTHE_SOUL ? edge.glowing() : edge);
		}
		return out;
	}

	// ---------------------------------------------------------------- beehive

	/** Honey dripping from under the roof: {x, length} of each drip. */
	private static final float[][] DRIPS = {{-5f, 2.5f}, {-3f, 1.5f}, {-1.25f, 3.5f}, {1f, 2f}, {3f, 4f}, {4.75f, 1.75f}};

	/**
	 * A beehive on the back: a box of planks with dark corner posts, a roof overhanging it with a ridge, a base, honey
	 * running down from under the roof in drips with glossy ends, the entrance slot with honey round it above a landing
	 * board, and on each side a window showing the comb inside.
	 */
	private static void beehive(List<Box> out, BackPiece piece, float back) {
		int wood = piece.main[0];
		int frame = piece.main[1];
		int lines = piece.main[2];
		int honey = piece.accent;
		float face = back + 7f;
		out.add(raw(-6f, 4f, back, 6f, 17.5f, face, wood));
		for (float y = 7.25f; y < 17f; y += 3.25f) {
			out.add(raw(-6f, y, face, 6f, y + 0.25f, face + 0.05f, lines));
			out.add(raw(-6.05f, y, back + 0.5f, 6.05f, y + 0.25f, face - 0.5f, lines));
		}
		for (int s = -1; s <= 1; s += 2) {
			out.add(raw(s < 0 ? -6.3f : 5.5f, 4f, face - 0.75f, s < 0 ? -5.5f : 6.3f, 17.5f, face + 0.3f, frame));
		}
		out.add(raw(-7f, 2.25f, back - 0.25f, 7f, 4f, face + 1f, piece.second));
		out.add(raw(-6.5f, 1.5f, back + 0.5f, 6.5f, 2.25f, face + 0.25f, ARGB.scaleRGB(piece.second, 0.85f)));
		out.add(raw(-7.05f, 3.5f, face + 0.5f, 7.05f, 4.05f, face + 1.05f, ARGB.scaleRGB(piece.second, 0.75f)));
		out.add(raw(-6.5f, 17.5f, back - 0.1f, 6.5f, 18.5f, face + 0.5f, frame));
		// Honey running down from under the roof.
		out.add(raw(-5.5f, 4f, face, 5.5f, 5f, face + 0.15f, honey));
		for (float[] d : DRIPS) {
			out.add(raw(d[0], 5f, face, d[0] + 0.75f, 5f + d[1], face + 0.15f, honey));
			out.add(raw(d[0] + 0.1f, 5f + d[1], face + 0.02f, d[0] + 0.65f, 5.5f + d[1], face + 0.2f, lighter(honey)));
		}
		// The entrance, honey round it, and the board the bees land on.
		out.add(raw(-2.75f, 12.75f, face + 0.05f, 2.75f, 13f, face + 0.15f, honey));
		out.add(raw(-2.5f, 13f, face, 2.5f, 14.25f, face + 0.1f, 0xFF2A1A0E));
		out.add(raw(-3f, 14.25f, face, 3f, 14.75f, face + 0.75f, piece.second));
		// A window on each side onto the comb: cells in rows, every other row shifted half a cell.
		int cell = ARGB.scaleRGB(honey, 0.85f);
		for (int s = -1; s <= 1; s += 2) {
			float x = s * 6f;
			out.add(raw(Math.min(x, x + s * 0.15f), 6.5f, back + 1f, Math.max(x, x + s * 0.15f), 13f, back + 6f, frame));
			int row = 0;
			for (float y = 7f; y < 12.5f; y += 1.25f) {
				for (float z = back + 1.5f + (row % 2) * 0.6f; z < back + 5.4f; z += 1.25f) {
					float out0 = x + s * 0.15f;
					float out1 = x + s * 0.25f;
					out.add(raw(Math.min(out0, out1), y, z, Math.max(out0, out1), y + 0.95f, z + 0.95f, (row + (int) z) % 3 == 0 ? cell : honey));
				}
				row++;
			}
		}
	}

	/**
	 * Two bees flying figures of eight in front of the hive (never through it or the wearer), bobbing up and down, each
	 * facing where it flies and beating its wings.
	 */
	private static void bees(PoseStack pose, SubmitNodeCollector collector, float back, int light) {
		double seconds = System.nanoTime() % 1_000_000_000_000L / 1e9;
		for (int i = 0; i < 2; i++) {
			double a = seconds * (1.1 + i * 0.35) + i * Math.PI;
			float x = (float) (10.0 * Math.sin(a));
			float z = back + 7f * BIG + 2.25f + (float) (2.0 * Math.sin(2.0 * a));
			float y = 8f + i * 4f + (float) Math.sin(seconds * 2.6 + i) * 1.5f;
			float dx = (float) (10.0 * Math.cos(a));
			float dz = (float) (4.0 * Math.cos(2.0 * a));
			pose.pushPose();
			pose.translate(x * V, y * V, z * V);
			pose.mulPose(new Matrix4f().rotationY((float) Math.atan2(-dx, -dz)));
			HatLayer.submit(pose, collector, bee(seconds + i * 0.13), null, light);
			pose.popPose();
		}
	}

	/** A bee facing -z: a striped body, a stinger, eyes, and two wings beating fast. */
	private static List<Box> bee(double seconds) {
		int yellow = 0xFFF6CE3A;
		int brown = 0xFF4A2A14;
		float lift = (float) Math.sin(seconds * 40.0) * 0.5f;
		return List.of(raw(-1.25f, -1f, -1.75f, 1.25f, 1f, 1.75f, yellow), raw(-1.3f, -1.05f, -0.5f, 1.3f, 1.05f, 0f, brown),
				raw(-1.3f, -1.05f, 0.75f, 1.3f, 1.05f, 1.25f, brown), raw(-0.25f, -0.25f, 1.75f, 0.25f, 0.25f, 2.25f, brown),
				raw(-1f, -0.5f, -1.85f, -0.4f, 0.1f, -1.75f, 0xFF1A1A1A), raw(0.4f, -0.5f, -1.85f, 1f, 0.1f, -1.75f, 0xFF1A1A1A),
				raw(-0.6f, -1.6f, -1.6f, -0.4f, -1f, -1.4f, brown), raw(0.4f, -1.6f, -1.6f, 0.6f, -1f, -1.4f, brown),
				raw(-2.75f, -1.5f + lift, -0.75f, -0.25f, -1f + lift, 0.75f, 0xFFE8F0FF),
				raw(0.25f, -1.5f + lift, -0.75f, 2.75f, -1f + lift, 0.75f, 0xFFE8F0FF));
	}

	// ---------------------------------------------------------------- fishing rod

	/**
	 * A fishing rod, tip up: a cork grip with a rubber butt, a reel on its seat with line wound on and a crank, a
	 * tapering blank with a maker's band, guides on thread wraps, and the line running up through them to the tip.
	 */
	private static List<Box> rod(BackPiece piece) {
		int blank = piece.main[0];
		int cork = piece.main[1];
		int line = piece.main[2];
		int metal = piece.accent;
		List<Box> out = new ArrayList<>();
		// The grip, with darker rings between its cork pieces, and the butt cap.
		out.add(raw(-0.85f, 6f, 0.15f, 0.85f, 14f, 1.85f, cork));
		for (float y = 7.5f; y < 14f; y += 2f) {
			out.add(raw(-0.9f, y, 0.1f, 0.9f, y + 0.3f, 1.9f, ARGB.scaleRGB(cork, 0.8f)));
		}
		out.add(raw(-0.95f, 14f, 0.05f, 0.95f, 15f, 1.95f, 0xFF2A2A30));
		// The reel seat with its hood rings, and the reel on it: line on the spool, a side plate, the crank and its knob.
		out.add(raw(-0.7f, 3.5f, 0.3f, 0.7f, 6f, 1.7f, metal));
		out.add(raw(-0.8f, 3.5f, 0.2f, 0.8f, 3.9f, 1.8f, ARGB.scaleRGB(metal, 0.75f)));
		out.add(raw(-0.8f, 5.6f, 0.2f, 0.8f, 6f, 1.8f, ARGB.scaleRGB(metal, 0.75f)));
		out.add(raw(-1.25f, 3.25f, 1.75f, 1.25f, 5.75f, 2.25f, line));
		out.add(raw(-1.75f, 2.75f, 2.25f, 1.75f, 6.25f, 2.75f, metal));
		out.add(raw(-1.25f, 3.25f, 2.75f, 1.25f, 5.75f, 2.8f, ARGB.scaleRGB(metal, 0.85f)));
		out.add(raw(-0.4f, 4.1f, 2.75f, 0.4f, 4.9f, 3f, 0xFF2A2A30));
		out.add(raw(0.4f, 4.3f, 2.75f, 1.6f, 4.7f, 3f, ARGB.scaleRGB(metal, 0.7f)));
		out.add(raw(1.3f, 4.2f, 3f, 1.8f, 4.8f, 3.8f, 0xFF2A2A30));
		// The blank, thinner towards the tip, a maker's band above the seat.
		out.add(raw(-0.5f, -10f, 0.5f, 0.5f, 3.5f, 1.5f, blank));
		out.add(raw(-0.4f, -20f, 0.6f, 0.4f, -10f, 1.4f, blank));
		out.add(raw(-0.25f, -27f, 0.75f, 0.25f, -20f, 1.25f, blank));
		out.add(raw(-0.6f, 2f, 0.4f, 0.6f, 3.5f, 1.6f, piece.second));
		out.add(raw(-0.62f, 2.6f, 0.38f, 0.62f, 2.9f, 1.62f, metal));
		if (piece == BackPiece.ROD_PUFFERFISH) {
			// Bamboo: a node every few voxels.
			for (float y = -24f; y < 2f; y += 4.5f) {
				float w = y < -20f ? 0.3f : y < -10f ? 0.45f : 0.55f;
				out.add(raw(-w, y, 1f - w, w, y + 0.4f, 1f + w, ARGB.scaleRGB(blank, 0.75f)));
			}
		}
		// Guides on thread wraps, each a little ring standing out from the blank.
		for (float g : new float[]{-2f, -9f, -15f, -20.5f, -25f}) {
			float w = g < -20f ? 0.25f : g < -10f ? 0.4f : 0.5f;
			float top = 1f + w;
			out.add(raw(-w - 0.08f, g - 0.6f, 1f - w - 0.08f, w + 0.08f, g + 0.6f, top + 0.08f, piece.second));
			out.add(raw(-0.12f, g - 0.15f, top, 0.12f, g + 0.15f, top + 0.6f, metal));
			out.add(raw(-0.4f, g - 0.15f, top + 0.6f, -0.25f, g + 0.15f, top + 1.1f, metal));
			out.add(raw(0.25f, g - 0.15f, top + 0.6f, 0.4f, g + 0.15f, top + 1.1f, metal));
			out.add(raw(-0.4f, g - 0.15f, top + 1.1f, 0.4f, g + 0.15f, top + 1.25f, metal));
		}
		out.add(raw(-0.3f, ROD_TIP - 0.4f, 0.7f, 0.3f, ROD_TIP, 1.6f, metal));
		out.add(raw(-0.06f, ROD_TIP, 1.85f, 0.06f, 3.25f, 1.95f, line));
		return out;
	}

	/** The line hanging from the tip; on its second piece a swivel and hook, and the fish caught on it. */
	private static List<Box> line(BackPiece piece, int k) {
		List<Box> out = new ArrayList<>();
		if (k == 0) {
			out.add(raw(-0.06f, 0f, -0.06f, 0.06f, 6f, 0.06f, piece.main[2]));
			return out;
		}
		out.add(raw(-0.06f, 0f, -0.06f, 0.06f, 3.5f, 0.06f, piece.main[2]));
		out.add(raw(-0.2f, 3.5f, -0.2f, 0.2f, 4.1f, 0.2f, 0xFF8A8E96));
		out.add(raw(-0.08f, 4.1f, -0.08f, 0.08f, 4.6f, 0.08f, 0xFFB0B4BC));
		double seconds = System.nanoTime() % 1_000_000_000_000L / 1e9;
		// Now and then it wriggles: the tail flicks to and fro for a moment.
		float burst = (float) Math.max(0.0, Math.sin(seconds * 0.9) - 0.4) / 0.6f;
		float wiggle = (float) Math.sin(seconds * 14.0) * burst;
		if (piece == BackPiece.ROD_PUFFERFISH) {
			pufferfish(out, 4.4f, wiggle, seconds);
		} else if (piece == BackPiece.ROD_SALMON) {
			fish(out, 4.4f, wiggle, 0xFFB8463A, 0xFF4A3A3A, 0xFFEAB494, 0xFF8A3A2E, 0xFF2E2224, 0xFFE0C8B8);
			// A salmon's hooked jaw, and the little fin on its back before the tail.
			out.add(raw(-1.2f, 4.4f, -0.5f, -0.6f, 5.6f, 0.5f, 0xFF8A3A2E));
			out.add(raw(1.25f + wiggle * 0.3f, 12f, -0.15f, 1.8f + wiggle * 0.3f, 12.6f, 0.15f, 0xFF8A3A2E));
		} else {
			fish(out, 4.4f, wiggle, 0xFFC8A878, 0xFF8C6E4C, 0xFFECE2C8, 0xFFA88A60, 0xFF6E5236, 0xFFF0E8D8);
			// The cod's barbel under its chin.
			out.add(raw(-1.25f, 5.6f, -0.1f, -0.9f, 6.4f, 0.1f, 0xFFA88A60));
		}
		return out;
	}

	/**
	 * A fish hanging head up by its mouth from {@code t}, its side to the front and back (back to +x): a darker back,
	 * pale belly, speckles and the lateral line, eyes on both sides, three fins along the back and two under, pectoral
	 * fins and a forked tail; the rear end swings {@code wiggle} to the side.
	 */
	private static void fish(List<Box> out, float t, float wiggle, int body, int back, int belly, int fin, int spot, int line) {
		float w = wiggle * 0.5f;
		out.add(raw(-0.9f, t, -0.7f, 0.9f, t + 1f, 0.7f, body));
		out.add(raw(-1.4f, t + 1f, -1f, 1.4f, t + 3f, 1f, body));
		out.add(raw(-1.75f, t + 3f, -1.15f, 1.75f, t + 7f, 1.15f, body));
		out.add(raw(-1.25f + w * 0.5f, t + 7f, -0.9f, 1.25f + w * 0.5f, t + 8.5f, 0.9f, body));
		out.add(raw(-0.6f + w, t + 8.5f, -0.5f, 0.6f + w, t + 9.25f, 0.5f, body));
		// The tail, two lobes round a notch.
		float tw = wiggle * 0.9f;
		out.add(raw(-0.35f + tw, t + 9.25f, -0.25f, 0.35f + tw, t + 9.9f, 0.25f, fin));
		out.add(raw(-1.9f + tw, t + 9.25f, -0.25f, -0.35f + tw, t + 10.75f, 0.25f, fin));
		out.add(raw(0.35f + tw, t + 9.25f, -0.25f, 1.9f + tw, t + 10.75f, 0.25f, fin));
		out.add(raw(-1.9f + tw, t + 10.4f, -0.27f, -1.2f + tw, t + 10.75f, 0.27f, ARGB.scaleRGB(fin, 0.8f)));
		out.add(raw(1.2f + tw, t + 10.4f, -0.27f, 1.9f + tw, t + 10.75f, 0.27f, ARGB.scaleRGB(fin, 0.8f)));
		// Back, belly and the line along the side.
		out.add(raw(0.95f, t + 1f, -1.05f, 1.45f, t + 3f, 1.05f, back));
		out.add(raw(1f, t + 3f, -1.2f, 1.8f, t + 7f, 1.2f, back));
		out.add(raw(-1.8f, t + 3f, -1.2f, -0.9f, t + 7f, 1.2f, belly));
		out.add(raw(-1.45f, t + 1.5f, -1.05f, -0.9f, t + 3f, 1.05f, belly));
		out.add(raw(0.15f, t + 3f, -1.22f, 0.35f, t + 7f, 1.22f, line));
		for (float[] s : new float[][]{{0.55f, 4f}, {-0.45f, 5.5f}, {0.6f, 6.2f}, {-0.3f, 3.6f}, {0.5f, 1.8f}}) {
			float z = s[1] < 3f ? 1.05f : 1.22f;
			out.add(raw(s[0], t + s[1], -z, s[0] + 0.35f, t + s[1] + 0.35f, z, spot));
		}
		// Eyes on both sides.
		out.add(raw(0.15f, t + 1.4f, -1.08f, 0.85f, t + 2.1f, 1.08f, 0xFFF4F4F0));
		out.add(raw(0.3f, t + 1.55f, -1.12f, 0.7f, t + 1.95f, 1.12f, 0xFF141414));
		// Fins: three along the back, two underneath, a pectoral fin each side.
		out.add(raw(1.75f, t + 3.25f, -0.2f, 2.5f, t + 4.5f, 0.2f, fin));
		out.add(raw(1.75f, t + 5f, -0.2f, 2.4f, t + 6.25f, 0.2f, fin));
		out.add(raw(1.25f + w * 0.5f, t + 7f, -0.2f, 1.9f + w * 0.5f, t + 8.25f, 0.2f, fin));
		out.add(raw(-2.3f, t + 5.25f, -0.2f, -1.75f, t + 6.5f, 0.2f, fin));
		out.add(raw(-1.8f + w * 0.5f, t + 7.1f, -0.2f, -1.25f + w * 0.5f, t + 8.2f, 0.2f, fin));
		out.add(raw(-0.6f, t + 3.25f, -1.6f, 0.2f, t + 4.5f, -1.15f, fin));
		out.add(raw(-0.6f, t + 3.25f, 1.15f, 0.2f, t + 4.5f, 1.6f, fin));
	}

	private static final int PUFFER = 0xFFF2CE3A;
	private static final int PUFFER_TEAL = 0xFF3A9AA8;

	/**
	 * A pufferfish blown up round in fright: spines all over, teal patches on its back, a pale belly, big eyes, little
	 * side fins beating fast and a stubby tail.
	 */
	private static void pufferfish(List<Box> out, float t, float wiggle, double seconds) {
		float c = t + 3.75f;
		out.add(raw(-0.5f, t, -0.5f, 0.5f, t + 0.75f, 0.5f, 0xFFE8862A));
		out.add(raw(-2.5f, c - 2.5f, -2.5f, 2.5f, c + 2.5f, 2.5f, PUFFER));
		out.add(raw(-2f, c - 3f, -2f, 2f, c + 3f, 2f, PUFFER));
		out.add(raw(-2.9f, c - 1.75f, -1.75f, 2.9f, c + 1.75f, 1.75f, PUFFER));
		out.add(raw(-1.75f, c - 1.75f, -2.9f, 1.75f, c + 1.75f, 2.9f, PUFFER));
		// Teal patches over the back (+x), the pale belly opposite.
		out.add(raw(2.5f, c - 1.5f, -1.5f, 2.95f, c + 1.5f, 1.5f, PUFFER_TEAL));
		for (float y : new float[]{-2f, 0.25f}) {
			out.add(raw(1.25f, c + y, -2.55f, 2.25f, c + y + 1f, 2.55f, PUFFER_TEAL));
		}
		out.add(raw(1f, c - 3.05f, -1f, 1.75f, c - 2.95f, 1f, PUFFER_TEAL));
		out.add(raw(-2.95f, c - 1.25f, -1.5f, -2.5f, c + 1.5f, 1.5f, 0xFFF8EAB0));
		out.add(raw(-2.55f, c - 1.25f, -1.25f, -1.75f, c + 1.5f, 1.25f, 0xFFF8EAB0));
		// Spines standing out of every side.
		int spine = 0xFFF4F0DC;
		for (float[] p : new float[][]{{-1.5f, -1.5f}, {0.5f, -1.75f}, {-0.5f, 0f}, {1.5f, 0.5f}, {-1.75f, 1.25f}, {0.25f, 1.75f}}) {
			out.add(raw(p[0] - 0.2f, c + p[1] - 0.2f, -3.4f, p[0] + 0.2f, c + p[1] + 0.2f, -2.9f, spine));
			out.add(raw(p[0] - 0.2f, c + p[1] - 0.2f, 2.9f, p[0] + 0.2f, c + p[1] + 0.2f, 3.4f, spine));
			out.add(raw(-3.4f, c + p[1] - 0.2f, p[0] - 0.2f, -2.95f, c + p[1] + 0.2f, p[0] + 0.2f, spine));
			out.add(raw(2.95f, c + p[1] - 0.2f, p[0] - 0.2f, 3.4f, c + p[1] + 0.2f, p[0] + 0.2f, spine));
		}
		// Eyes, a shine in each.
		out.add(raw(0.25f, c - 2.25f, -2.6f, 1.45f, c - 1f, 2.6f, 0xFF161616));
		out.add(raw(0.45f, c - 2.1f, -2.65f, 0.85f, c - 1.7f, 2.65f, 0xFFFFFFFF));
		// Side fins, beating fast; the stubby tail flicks.
		float beat = 0.5f + 0.35f * (float) Math.sin(seconds * 22.0);
		out.add(raw(-0.75f, c - 0.5f, -2.9f - beat, 0.25f, c + 0.75f, -2.9f, PUFFER_TEAL));
		out.add(raw(-0.75f, c - 0.5f, 2.9f, 0.25f, c + 0.75f, 2.9f + beat, PUFFER_TEAL));
		float tw = wiggle * 0.6f;
		out.add(raw(-0.5f + tw, c + 3f, -0.3f, 0.5f + tw, c + 3.6f, 0.3f, PUFFER));
		out.add(raw(-1.1f + tw, c + 3.6f, -0.2f, 1.1f + tw, c + 4.75f, 0.2f, PUFFER_TEAL));
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
