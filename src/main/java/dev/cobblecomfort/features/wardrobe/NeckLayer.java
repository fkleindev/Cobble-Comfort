package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.features.namecharm.CharmPendulum;
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
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static dev.cobblecomfort.features.wardrobe.HatLayer.V;
import static dev.cobblecomfort.features.wardrobe.HatLayer.lighter;
import static dev.cobblecomfort.features.wardrobe.HatLayer.raw;

/**
 * Draws what a player wears round the neck, in the body's space (voxels half a skin pixel big, y down from the top of
 * the shoulders, -z the front): over the chestplate if there is one, else close to the body. Scarf ends, pendants,
 * ties and the like swing on {@link CharmPendulum}s.
 */
final class NeckLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** On a player drawn as a wardrobe tile: the piece to show there instead of their own. */
	static final RenderStateDataKey<Neck> PREVIEW = RenderStateDataKey.create(() -> "cobblecomfort:neck_preview");
	/** How far the front lies from the middle of the body: the body (and its jacket layer), or a chestplate over it. */
	private static final float BARE = 4.5f;
	private static final float ARMOURED = 6f;
	private static final long FORGET_NANOS = 2_000_000_000L;

	private static final Int2ObjectOpenHashMap<Swing> SWINGS = new Int2ObjectOpenHashMap<>();

	/** The swinging parts of one player's piece, for the kind they were made for. */
	private record Swing(Neck.Style style, CharmPendulum[] pendulums) {
	}

	NeckLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
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
		Neck preview = state.getData(PREVIEW);
		Neck neck = preview != null ? preview : WardrobeFeature.INSTANCE.neckFor(player);
		if (neck == null) {
			return;
		}
		float z = state.chestEquipment.isEmpty() ? BARE : ARMOURED;
		pose.pushPose();
		getParentModel().root().translateAndRotate(pose);
		getParentModel().body.translateAndRotate(pose);
		List<Box> boxes = new ArrayList<>();
		switch (neck.style) {
			case SCARF -> scarf(boxes, neck, z);
			case NECKLACE -> necklace(boxes, neck, z);
			case BOWTIE, LEI -> {
			}
			case TIE -> boxes.add(raw(-1.25f, 0f, -z - 1.2f, 1.25f, 2f, -z, neck.main[0]));
			case BANDANA -> bandana(boxes, neck, z);
			case BELL -> collar(boxes, neck, z);
			case BOA -> boa(boxes, neck, z);
			case LEAF_SCARF -> {
				scarf(boxes, neck, z);
				leaves(boxes, neck, z);
			}
			case PUMPKIN_CHAIN -> pumpkinChain(boxes, neck, z);
			case VAMPIRE -> vampire(boxes, neck, z);
			case BONES -> bones(boxes, neck, z);
		}
		HatLayer.submit(pose, collector, boxes, null, light);
		CharmPendulum[] pendulums = preview != null ? HatLayer.fresh() : pendulums(state.id, neck.style);
		HatLayer.Chains chains = new HatLayer.Chains(pose, collector, pendulums, WardrobeFeature.INSTANCE.damping(), light,
				preview != null ? -1 : state.id, 0f);
		double damping = WardrobeFeature.INSTANCE.damping();
		switch (neck.style) {
			case SCARF, LEAF_SCARF -> {
				chains.hang(0, at(2f, 3f, -z - 1.25f), 4f, 2, damping + HatLayer.STRAND_DAMPING, 0.5f, k -> scarfEnd(neck, k, 2));
				chains.hang(2, at(5f, 3f, -z - 1.25f), 4f, 3, damping + HatLayer.STRAND_DAMPING, 0.5f, k -> scarfEnd(neck, k, 3));
			}
			case NECKLACE -> chains.hang(0, at(0f, 6.2f, -z - 0.5f), 1f, 1, k -> pendant(neck));
			case BOWTIE -> chains.hang(0, at(0f, 1.25f, -z - 0.75f), 1f, 1, damping + 8.0, 1f, k -> bow(neck));
			case TIE -> chains.hang(0, at(0f, 2f, -z - 0.6f), 5f, 2, damping + 2.0, 0.5f, k -> tie(neck, k));
			case BANDANA -> {
				chains.hang(0, at(0f, 3f, -z - 0.5f), 3f, 1, k -> bandanaTip(neck));
				chains.hang(1, at(-1f, 1f, z + 1.5f), 3f, 1, k -> tail(neck));
				chains.hang(2, at(1f, 1f, z + 1.5f), 3f, 1, k -> tail(neck));
			}
			case LEI -> {
				chains.hang(0, at(0f, 0f, -z - 1f), 5f, 1, damping + 2.0, 1f, k -> leiFront(neck));
				backFlowers(neck, z, pose, collector, light);
			}
			case BELL -> chains.hang(0, at(0f, 1.75f, -z - 0.6f), 1f, 1, k -> bell(neck));
			case BOA -> {
				chains.hang(0, at(-3f, 2.5f, -z - 1.5f), 4f, 2, damping + 2.0, 0.6f, k -> boaEnd(neck, k));
				chains.hang(2, at(3f, 2.5f, -z - 1.5f), 4f, 2, damping + 2.0, 0.6f, k -> boaEnd(neck, k + 1));
			}
			case PUMPKIN_CHAIN -> chains.hang(0, at(0f, 6.2f, -z - 0.5f), 1f, 1, k -> jack(neck));
			case BONES -> chains.hang(0, at(0f, 6.2f, -z - 0.5f), 1f, 1, k -> skull(neck));
			case VAMPIRE -> {
			}
		}
		pose.popPose();
	}

	/** A point in the body's space, in voxels (y down from the top of the shoulders). */
	private static Vector3f at(float x, float y, float z) {
		return new Vector3f(x * V, y * V, z * V);
	}

	// ---------------------------------------------------------------- scarf

	/** The scarf's colour at a spot of it: stripes across, plain with a shaded edge, or a plaid. */
	private static int scarfColor(Neck neck, float x, float y) {
		int column = Math.floorMod((int) Math.floor(x / 2f), 2);
		int row = Math.floorMod((int) Math.floor(y / 1.5f), 2);
		return switch (neck) {
			case SCARF_STRIPED -> column == 0 ? neck.main[0] : neck.main[1];
			case SCARF_PLAID -> column == row ? neck.main[0] : column == 0 ? neck.main[2] : neck.main[1];
			default -> row == 1 ? neck.main[1] : neck.main[0];
		};
	}

	/** A thick scarf wound round the neck, in front and behind (its sides hide under the head). */
	private static void scarf(List<Box> out, Neck neck, float z) {
		for (float x = -7.5f; x < 7.5f; x += 1f) {
			for (float y = 0f; y < 3.5f; y += 1.75f) {
				int color = scarfColor(neck, x, y);
				out.add(raw(x, y, -z - 2.5f, x + 1f, y + 1.75f, -z, color));
				out.add(raw(x, y, z, x + 1f, y + 1.75f, z + 2.5f, color));
			}
		}
		// A fold along the top.
		out.add(raw(-7.5f, -0.25f, -z - 2.25f, 7.5f, 0f, -z - 0.25f, ARGB.scaleRGB(scarfColor(neck, 0f, 0f), 0.85f)));
	}

	/** One piece of a hanging scarf end (the last one with a fringe), striped like the scarf. */
	private static List<Box> scarfEnd(Neck neck, int k, int pieces) {
		List<Box> out = new ArrayList<>();
		for (float y = 0f; y < 4f; y += 2f) {
			out.add(raw(-1.5f, y, -0.6f, 1.5f, y + 2f, 0.6f, neck == Neck.SCARF_STRIPED
					? ((int) (y / 2f) + k) % 2 == 0 ? neck.main[0] : neck.main[1] : scarfColor(neck, k * 2f + y, y)));
		}
		boolean leafy = neck.style == Neck.Style.LEAF_SCARF;
		if (k == pieces - 1) {
			for (float x = -1.5f; x < 1.5f; x += 1f) {
				out.add(raw(x + 0.2f, 4f, -0.4f, x + 0.6f, 5.25f, 0.4f, leafy ? neck.main[1] : neck.main[2]));
			}
		}
		if (leafy) {
			leaf(out, k % 2 == 0 ? -0.5f : 0.5f, 1.75f, -0.6f, k % 2 == 0 ? neck.accent : neck.main[2]);
		}
		return out;
	}

	/** Autumn leaves knitted into the front of the scarf. */
	private static void leaves(List<Box> out, Neck neck, float z) {
		int[] colors = {neck.main[2], neck.accent, neck.second, neck.main[2], neck.accent};
		float[][] spots = {{-5.5f, 1.25f}, {-2.5f, 2.25f}, {0.5f, 1f}, {3.5f, 2.25f}, {6f, 1f}};
		for (int i = 0; i < spots.length; i++) {
			leaf(out, spots[i][0], spots[i][1], -z - 2.5f, colors[i]);
		}
	}

	/** A small leaf facing the front at (x, y), standing on a surface at z: a body with a point above and below. */
	private static void leaf(List<Box> out, float x, float y, float z, int color) {
		out.add(raw(x - 0.75f, y - 0.5f, z - 0.15f, x + 0.75f, y + 0.5f, z, color));
		out.add(raw(x - 0.25f, y - 1f, z - 0.15f, x + 0.25f, y + 1f, z, color));
		out.add(raw(x - 0.1f, y - 0.4f, z - 0.2f, x + 0.1f, y + 0.9f, z - 0.15f, ARGB.scaleRGB(color, 0.75f)));
	}

	// ---------------------------------------------------------------- pumpkin chain

	/** A cord from the shoulders down to the chest with little pumpkins along it, and across the back. */
	private static void pumpkinChain(List<Box> out, Neck neck, float z) {
		for (int t = 0; t <= 10; t++) {
			float y = 6f * t / 10f;
			for (int s = -1; s <= 1; s += 2) {
				float x = s * 5.5f * (1f - t / 10f);
				out.add(raw(x - 0.25f, y - 0.25f, -z - 0.5f, x + 0.25f, y + 0.25f, -z, neck.main[2]));
			}
		}
		out.add(raw(-5.5f, 0f, z, 5.5f, 0.5f, z + 0.5f, neck.main[2]));
		for (int t : new int[]{3, 7}) {
			for (int s = -1; s <= 1; s += 2) {
				float x = s * 5.5f * (1f - t / 10f);
				float y = 6f * t / 10f + 0.5f;
				out.add(raw(x - 0.9f, y - 0.6f, -z - 1.6f, x + 0.9f, y + 0.9f, -z - 0.2f, neck.main[0]));
				out.add(raw(x - 0.6f, y - 0.8f, -z - 1.4f, x + 0.6f, y + 1.1f, -z - 0.4f, neck.main[0]));
				out.add(raw(x - 0.15f, y - 0.6f, -z - 1.7f, x + 0.15f, y + 0.9f, -z - 1.6f, neck.main[1]));
				out.add(raw(x - 0.2f, y - 1.2f, -z - 1.1f, x + 0.2f, y - 0.8f, -z - 0.7f, neck.second));
			}
		}
	}

	/** The jack-o'-lantern hanging in the middle, its face lit. */
	private static List<Box> jack(Neck neck) {
		int skin = neck.main[0];
		int glow = neck.accent;
		return List.of(raw(-0.3f, 0f, -0.2f, 0.3f, 0.6f, 0.2f, neck.main[2]), raw(-0.35f, 0.5f, -0.35f, 0.35f, 1.2f, 0.35f, neck.second),
				raw(-1.6f, 1.2f, -1.4f, 1.6f, 4f, 1.4f, skin), raw(-1.9f, 1.6f, -1.1f, 1.9f, 3.6f, 1.1f, skin),
				raw(-1.2f, 1f, -1.2f, 1.2f, 4.3f, 1.2f, skin), raw(-1.95f, 1.6f, -0.15f, 1.95f, 3.6f, 0.15f, neck.main[1]),
				raw(-1.1f, 1.8f, -1.5f, -0.35f, 2.4f, -1.4f, glow).glowing(), raw(0.35f, 1.8f, -1.5f, 1.1f, 2.4f, -1.4f, glow).glowing(),
				raw(-1f, 2.9f, -1.5f, 1f, 3.4f, -1.4f, glow).glowing(), raw(-0.25f, 2.9f, -1.55f, 0.25f, 3.15f, -1.5f, skin));
	}

	// ---------------------------------------------------------------- vampire collar

	/**
	 * A vampire's collar: a cape's shoulders with a brooch in front, and a tall stiff collar rising round the back and
	 * sides of the head, flaring out as it rises, lined inside, with points at its front corners.
	 */
	private static void vampire(List<Box> out, Neck neck, float z) {
		int cloth = neck.main[0];
		int edge = neck.main[1];
		int lining = neck.main[2];
		out.add(raw(-8f, -0.5f, -z - 1f, 8f, 1.5f, z + 1.5f, cloth));
		out.add(raw(-8.1f, 1.25f, -z - 1.1f, 8.1f, 1.75f, z + 1.6f, edge));
		for (int r = 0; r < 9; r++) {
			float y1 = -0.5f - r;
			float y0 = y1 - 1f;
			float lean = r * 0.45f;
			float half = 8.5f + r * 0.35f;
			float back = z + 1f + lean;
			int color = r == 8 ? edge : cloth;
			out.add(raw(-half, y0, back, half, y1, back + 1f, color));
			out.add(raw(-half + 1f, y0, back - 0.3f, half - 1f, y1, back, lining));
			for (int s = -1; s <= 1; s += 2) {
				float x0 = s < 0 ? -half : half - 1f;
				out.add(raw(x0, y0, -3f + lean * 0.5f, x0 + 1f, y1, back + 1f, color));
				float in = s < 0 ? -half + 1f : half - 1.3f;
				out.add(raw(in, y0, -3f + lean * 0.5f, in + 0.3f, y1, back, lining));
			}
		}
		// The points at the front corners.
		for (int s = -1; s <= 1; s += 2) {
			float half = 8.5f + 9 * 0.35f;
			float x0 = s < 0 ? -half : half - 1f;
			out.add(raw(x0, -11.5f, -1f, x0 + 1f, -9.5f, 1.5f, edge));
			out.add(raw(x0, -12.5f, -0.5f, x0 + 1f, -11.5f, 0.5f, edge));
		}
		out.add(raw(-1.5f, 0f, -z - 1.4f, 1.5f, 2.5f, -z - 1f, neck.accent));
		out.add(raw(-0.75f, 0.5f, -z - 1.6f, 0.75f, 2f, -z - 1.4f, neck.second));
	}

	// ---------------------------------------------------------------- bones

	/** Little bones strung on a cord from the shoulders down to the chest, and the cord across the back. */
	private static void bones(List<Box> out, Neck neck, float z) {
		int bone = neck.main[0];
		int knob = neck.main[1];
		for (int t = 0; t <= 10; t++) {
			float y = 6f * t / 10f;
			for (int s = -1; s <= 1; s += 2) {
				float x = s * 5.5f * (1f - t / 10f);
				out.add(raw(x - 0.2f, y - 0.2f, -z - 0.4f, x + 0.2f, y + 0.2f, -z, neck.main[2]));
			}
		}
		out.add(raw(-5.5f, 0f, z, 5.5f, 0.5f, z + 0.5f, neck.main[2]));
		for (int t : new int[]{1, 4, 7}) {
			for (int s = -1; s <= 1; s += 2) {
				float x = s * 5.5f * (1f - t / 10f);
				float y = 6f * t / 10f;
				// Lying along the cord: across near the shoulders, upright further down.
				if (t < 5) {
					out.add(raw(x - 1f, y - 0.25f, -z - 0.9f, x + 1f, y + 0.25f, -z - 0.3f, bone));
					out.add(raw(x - 1.4f, y - 0.6f, -z - 1f, x - 0.8f, y + 0.6f, -z - 0.2f, knob));
					out.add(raw(x + 0.8f, y - 0.6f, -z - 1f, x + 1.4f, y + 0.6f, -z - 0.2f, knob));
				} else {
					out.add(raw(x - 0.25f, y - 1f, -z - 0.9f, x + 0.25f, y + 1f, -z - 0.3f, bone));
					out.add(raw(x - 0.6f, y - 1.4f, -z - 1f, x + 0.6f, y - 0.8f, -z - 0.2f, knob));
					out.add(raw(x - 0.6f, y + 0.8f, -z - 1f, x + 0.6f, y + 1.4f, -z - 0.2f, knob));
				}
			}
		}
	}

	/** The skull hanging in the middle: a cranium, hollow eyes and nose, a jaw with teeth. */
	private static List<Box> skull(Neck neck) {
		int head = neck.accent;
		int dark = neck.second;
		return List.of(raw(-0.3f, 0f, -0.2f, 0.3f, 1f, 0.2f, neck.main[2]), raw(-1.75f, 1f, -1.6f, 1.75f, 3.75f, 1.6f, head),
				raw(-1.25f, 3.75f, -1.3f, 1.25f, 4.75f, 1.3f, head), raw(-1.25f, 2f, -1.7f, -0.3f, 2.9f, -1.6f, dark),
				raw(0.3f, 2f, -1.7f, 1.25f, 2.9f, -1.6f, dark), raw(-0.25f, 3.1f, -1.7f, 0.25f, 3.6f, -1.6f, dark),
				raw(-1f, 4.1f, -1.4f, 1f, 4.3f, -1.3f, dark), raw(-0.1f, 3.75f, -1.4f, 0.1f, 4.75f, -1.3f, dark));
	}

	// ---------------------------------------------------------------- necklace

	/** A fine chain from the shoulders down to a point on the chest, and across the back. */
	private static void necklace(List<Box> out, Neck neck, float z) {
		for (int t = 0; t <= 10; t++) {
			float y = 6f * t / 10f;
			for (int s = -1; s <= 1; s += 2) {
				float x = s * 5.5f * (1f - t / 10f);
				out.add(raw(x - 0.3f, y - 0.3f, -z - 0.6f, x + 0.3f, y + 0.3f, -z, t % 2 == 0 ? neck.main[0] : neck.main[1]));
			}
		}
		out.add(raw(-5.5f, 0f, z, 5.5f, 0.5f, z + 0.5f, neck.main[1]));
	}

	/** The pendant on its bail: a heart, an emerald, an ender pearl, a little totem or a compass. */
	private static List<Box> pendant(Neck neck) {
		List<Box> out = new ArrayList<>();
		out.add(raw(-0.4f, 0f, -0.2f, 0.4f, 1f, 0.2f, neck.main[0]));
		int c = neck.accent;
		int d = neck.second;
		switch (neck) {
			case NECKLACE_HEART -> {
				out.add(raw(-1.5f, 1f, -0.4f, -0.25f, 2f, 0.4f, c));
				out.add(raw(0.25f, 1f, -0.4f, 1.5f, 2f, 0.4f, c));
				out.add(raw(-1.5f, 2f, -0.4f, 1.5f, 3f, 0.4f, c));
				out.add(raw(-1f, 3f, -0.4f, 1f, 3.75f, 0.4f, c));
				out.add(raw(-0.5f, 3.75f, -0.4f, 0.5f, 4.5f, 0.4f, c));
				out.add(raw(-1.1f, 1.4f, -0.5f, -0.6f, 1.9f, -0.4f, d));
			}
			case NECKLACE_EMERALD -> {
				out.add(raw(-0.5f, 1f, -0.4f, 0.5f, 1.5f, 0.4f, c));
				out.add(raw(-1f, 1.5f, -0.5f, 1f, 2.5f, 0.5f, c));
				out.add(raw(-1.25f, 2.5f, -0.5f, 1.25f, 3.5f, 0.5f, c));
				out.add(raw(-1f, 3.5f, -0.5f, 1f, 4.25f, 0.5f, c));
				out.add(raw(-0.5f, 4.25f, -0.4f, 0.5f, 4.75f, 0.4f, c));
				out.add(raw(-0.6f, 2f, -0.6f, -0.1f, 2.75f, -0.5f, d));
			}
			case NECKLACE_PEARL -> {
				out.add(raw(-1f, 1f, -1f, 1f, 3.5f, 1f, c));
				out.add(raw(-1.4f, 1.5f, -0.6f, 1.4f, 3f, 0.6f, c));
				out.add(raw(-0.6f, 1.5f, -1.4f, 0.6f, 3f, 1.4f, c));
				out.add(raw(-0.5f, 1.75f, -1.5f, 0.25f, 2.5f, -1.4f, d));
			}
			case NECKLACE_TOTEM -> {
				out.add(raw(-0.75f, 1f, -0.4f, 0.75f, 2.25f, 0.4f, c));
				out.add(raw(-0.6f, 2.25f, -0.35f, 0.6f, 4f, 0.35f, d));
				out.add(raw(-1.25f, 2.5f, -0.3f, 1.25f, 3f, 0.3f, c));
				out.add(raw(-0.5f, 1.4f, -0.45f, -0.2f, 1.7f, -0.4f, 0xFF1A1A1A));
				out.add(raw(0.2f, 1.4f, -0.45f, 0.5f, 1.7f, -0.4f, 0xFF1A1A1A));
			}
			default -> {
				out.add(raw(-1.4f, 1f, -0.3f, 1.4f, 3.8f, 0.3f, c));
				out.add(raw(-1.1f, 1.3f, -0.4f, 1.1f, 3.5f, -0.3f, 0xFFE8E4DA));
				out.add(raw(-0.15f, 1.6f, -0.5f, 0.15f, 2.45f, -0.4f, d));
				out.add(raw(-0.15f, 2.45f, -0.5f, 0.15f, 3.2f, -0.4f, 0xFF4A4A50));
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- bow tie and tie

	/** A bow tie: a knot and wings widening out, with dots on the dotted one. */
	private static List<Box> bow(Neck neck) {
		List<Box> out = new ArrayList<>();
		out.add(raw(-0.9f, 0.25f, -0.75f, 0.9f, 2f, 0.75f, neck.main[1]));
		for (int s = -1; s <= 1; s += 2) {
			float[][] steps = {{0.9f, 2.25f, 0.5f, 1.75f}, {2.25f, 3.5f, 0f, 2.25f}, {3.5f, 4.5f, -0.25f, 2.5f}};
			for (float[] w : steps) {
				float x0 = s > 0 ? w[0] : -w[1];
				float x1 = s > 0 ? w[1] : -w[0];
				out.add(raw(x0, w[2], -0.55f, x1, w[3], 0.55f, neck.main[0]));
			}
			if (neck.accent != 0) {
				out.add(raw(s > 0 ? 2.5f : -3.25f, 0.6f, -0.65f, s > 0 ? 3.25f : -2.5f, 1.35f, -0.55f, neck.accent));
			}
		}
		return out;
	}

	/** The tie hanging from its knot, widening down to a point, with stripes across. */
	private static List<Box> tie(Neck neck, int k) {
		float half = k == 0 ? 1.25f : 1.6f;
		List<Box> out = new ArrayList<>();
		out.add(raw(-half, 0f, -0.4f, half, 5f, 0.4f, neck.main[0]));
		for (float s = 1f; s < 5f; s += 1.75f) {
			out.add(raw(-half, s, -0.5f, half, s + 0.5f, -0.4f, neck.main[1]));
		}
		if (k == 1) {
			out.add(raw(-1.1f, 5f, -0.4f, 1.1f, 6f, 0.4f, neck.main[0]));
			out.add(raw(-0.5f, 6f, -0.4f, 0.5f, 6.75f, 0.4f, neck.main[0]));
		}
		return out;
	}

	// ---------------------------------------------------------------- bandana

	/** The upper part of a bandana tied round the neck, its knot behind. */
	private static void bandana(List<Box> out, Neck neck, float z) {
		float[] halves = {6.5f, 5.5f, 4.5f};
		for (int r = 0; r < halves.length; r++) {
			out.add(raw(-halves[r], r, -z - 1f, halves[r], r + 1f, -z, r == 0 ? neck.main[1] : neck.main[0]));
		}
		for (float[] d : new float[][]{{-4.5f, 1.25f}, {3f, 1.25f}, {-1f, 2.25f}}) {
			out.add(raw(d[0], d[1], -z - 1.1f, d[0] + 0.75f, d[1] + 0.5f, -z - 1f, neck.accent));
		}
		out.add(raw(-6.5f, 0f, z, 6.5f, 1f, z + 1f, neck.main[1]));
		out.add(raw(-1.5f, -0.5f, z, 1.5f, 1.5f, z + 1.5f, neck.main[1]));
	}

	/** The bandana's point in front, which flaps. */
	private static List<Box> bandanaTip(Neck neck) {
		List<Box> out = new ArrayList<>();
		float[] halves = {3.5f, 2.5f, 1.5f, 0.6f};
		for (int r = 0; r < halves.length; r++) {
			out.add(raw(-halves[r], r, -0.5f, halves[r], r + 1f, 0.5f, neck.main[0]));
		}
		out.add(raw(-0.4f, 0.5f, -0.6f, 0.4f, 1f, -0.5f, neck.accent));
		return out;
	}

	private static List<Box> tail(Neck neck) {
		return List.of(raw(-0.6f, 0f, -0.2f, 0.6f, 3f, 0.2f, neck.main[0]));
	}

	// ---------------------------------------------------------------- lei

	/** A flower of the lei at (x, y), facing out. */
	private static void flower(List<Box> out, Neck neck, float x, float y, float z, int i) {
		out.add(raw(x - 0.9f, y - 0.9f, z - 0.9f, x + 0.9f, y + 0.9f, z + 0.9f, neck.main[i % 3]));
		out.add(raw(x - 1.2f, y - 0.4f, z - 0.4f, x + 1.2f, y + 0.4f, z + 0.4f, ARGB.scaleRGB(neck.main[i % 3], 0.9f)));
		out.add(raw(x - 0.35f, y - 0.35f, z - 1.05f, x + 0.35f, y + 0.35f, z - 0.9f, neck.accent));
	}

	/** The front of the lei: flowers in a U from the shoulders down over the chest. */
	private static List<Box> leiFront(Neck neck) {
		List<Box> out = new ArrayList<>();
		float[][] u = {{-6f, 0f}, {-5.5f, 1.5f}, {-4.5f, 3f}, {-3f, 4f}, {-1.5f, 4.75f}, {0f, 5f}, {1.5f, 4.75f}, {3f, 4f},
				{4.5f, 3f}, {5.5f, 1.5f}, {6f, 0f}};
		for (int i = 0; i < u.length; i++) {
			flower(out, neck, u[i][0], u[i][1], 0f, i);
		}
		return out;
	}

	/** The back of the lei, across the shoulders. */
	private static void backFlowers(Neck neck, float z, PoseStack pose, SubmitNodeCollector collector, int light) {
		List<Box> out = new ArrayList<>();
		int i = 0;
		for (float x = -6f; x <= 6f; x += 2f) {
			flower(out, neck, x, 0.75f, z + 1f, i++);
		}
		HatLayer.submit(pose, collector, out, null, light);
	}

	// ---------------------------------------------------------------- bell

	/** A leather collar with a buckle and studs. */
	private static void collar(List<Box> out, Neck neck, float z) {
		out.add(raw(-6f, 0.25f, -z - 0.75f, 6f, 1.75f, -z, neck.main[0]));
		out.add(raw(-6f, 0.25f, z, 6f, 1.75f, z + 0.75f, neck.main[0]));
		out.add(raw(-6f, 0.25f, -z - 0.8f, 6f, 0.5f, -z, neck.main[1]));
		out.add(raw(-6f, 1.5f, -z - 0.8f, 6f, 1.75f, -z, neck.main[1]));
		out.add(raw(-4.5f, 0f, -z - 1f, -2.75f, 2f, -z - 0.75f, neck.second));
		out.add(raw(-4.1f, 0.4f, -z - 1.05f, -3.15f, 1.6f, -z - 1f, neck.main[0]));
		for (float x : new float[]{2.5f, 4.5f}) {
			out.add(raw(x, 0.75f, -z - 0.95f, x + 0.5f, 1.25f, -z - 0.75f, neck.second));
		}
	}

	/** A cowbell on its loop, flaring at the mouth, with a clapper. */
	private static List<Box> bell(Neck neck) {
		int metal = neck.accent;
		return List.of(raw(-0.4f, 0f, -0.2f, 0.4f, 0.75f, 0.2f, ARGB.scaleRGB(metal, 0.7f)),
				raw(-1f, 0.75f, -1f, 1f, 2.5f, 1f, metal), raw(-1.4f, 2.5f, -1.4f, 1.4f, 3.25f, 1.4f, metal),
				raw(-1.6f, 3.25f, -1.6f, 1.6f, 3.6f, 1.6f, ARGB.scaleRGB(metal, 0.85f)),
				raw(-0.35f, 3.6f, -0.35f, 0.35f, 4.1f, 0.35f, 0xFF3A3A40),
				raw(-0.8f, 1f, -1.05f, -0.4f, 2.25f, -1f, lighter(metal)));
	}

	// ---------------------------------------------------------------- boa

	/** Puffs of feathers round the neck, in front and behind. */
	private static void boa(List<Box> out, Neck neck, float z) {
		int i = 0;
		for (float x = -7f; x <= 7f; x += 1.75f) {
			float lift = (i % 3) * 0.4f;
			int color = neck.main[i % 3];
			out.add(raw(x - 1.2f, -0.5f + lift, -z - 2.5f, x + 1.2f, 2.5f + lift, -z, color));
			out.add(raw(x - 1.2f, -0.5f + lift, z, x + 1.2f, 2.5f + lift, z + 2.5f, color));
			i++;
		}
	}

	/** A piece of a hanging boa end: a puff with tufts out of it. */
	private static List<Box> boaEnd(Neck neck, int k) {
		return List.of(raw(-1.3f, 0f, -1.3f, 1.3f, 4f, 1.3f, neck.main[k % 2]), raw(-1.7f, 1f, -0.8f, 1.7f, 3f, 0.8f, neck.main[2]),
				raw(-0.8f, 1f, -1.7f, 0.8f, 3f, 1.7f, neck.main[(k + 1) % 2]));
	}

	// ---------------------------------------------------------------- pendulums

	/** The swinging parts of one player's piece, hanging still again when they put on another kind. */
	private static CharmPendulum[] pendulums(int entityId, Neck.Style style) {
		long now = System.nanoTime();
		Swing swing = SWINGS.get(entityId);
		if (swing == null || swing.style() != style) {
			swing = new Swing(style, HatLayer.fresh());
			SWINGS.put(entityId, swing);
			if (SWINGS.size() > 64) {
				SWINGS.int2ObjectEntrySet().removeIf(e -> now - e.getValue().pendulums()[0].lastSeen > FORGET_NANOS);
			}
		}
		return swing.pendulums();
	}

	static void forget() {
		SWINGS.clear();
	}
}
