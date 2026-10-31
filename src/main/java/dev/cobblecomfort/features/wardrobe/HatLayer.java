package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cobblecomfort.features.namecharm.CharmPendulum;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;
import java.util.function.IntFunction;

/**
 * Draws the hat on a player's head, in place of their helmet: voxels half a skin pixel big. The wreath's hanging strands
 * swing on {@link CharmPendulum}s. The part that shows the helmet (the crown's band, the wreath's ring) glints when the
 * helmet is enchanted; the front gem or flower takes the colour of its trim.
 */
final class HatLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("cobblecomfort", "textures/misc/hat.png");
	private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);
	/** One voxel: half a skin pixel. */
	private static final float V = 1f / 32f;
	/** The top of the head, in the head's space (y down). */
	private static final float HEAD_TOP = -0.5f;
	private static final long FORGET_NANOS = 2_000_000_000L;
	/** Wreath: two strands down the back, of three pieces each. */
	private static final float[] STRANDS = {-4f, 4f};
	private static final int STRAND_PIECES = 3;
	private static final float PIECE = 4f;
	/** Wreath strands: speed lost per second on top of the setting, and how freely each piece bends from the one above. */
	private static final double STRAND_DAMPING = 6.0;
	private static final float STRAND_BEND = 0.35f;
	private static final int PENDULUMS = 6;
	/** Witch's hat: the cone above the band in pieces that sway. */
	private static final int WITCH_PIECES = 5;
	/** Witch's hat: how far the cone follows its pendulums, and the speed it loses per second on top of the setting. */
	private static final float WITCH_STIFF = 0.18f;
	private static final double WITCH_DAMPING = 3.0;
	/** Devil's tail: speed lost per second on top of the setting. */
	private static final double TAIL_DAMPING = 6.0;
	/** Devil's tail: how freely each piece bends from the one above. */
	private static final float TAIL_BEND = 0.35f;
	/** Wreath: the ring's rows (half a pixel each) down from the top of the head, and the flowers' middle above it. */
	private static final int RING_ROWS = 4;
	private static final float FLOWERS = 2f;
	private static final int TWIG_LIGHT = 0xFF8A6A42;
	private static final int TWIG_DARK = 0xFF5E4428;
	/** Light that ignores the surroundings: something that glows. */
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int ACORN_NUT = 0xFFB98448;

	/** On a player drawn as a wardrobe tile: the hat to show there instead of their own. */
	static final RenderStateDataKey<Hat> PREVIEW = RenderStateDataKey.create(() -> "cobblecomfort:hat_preview");

	private static final Int2ObjectOpenHashMap<Swing> SWINGS = new Int2ObjectOpenHashMap<>();

	/** The swinging parts of one player's hat, for the style they were made for. */
	private record Swing(Hat.Style style, CharmPendulum[] pendulums) {
	}

	/**
	 * A box in head space (blocks, y down) and its colour; {@code helmet} if it is part of what shows the helmet,
	 * {@code glow} if it lights itself (a carved pumpkin's face).
	 */
	private record Box(float x0, float y0, float z0, float x1, float y1, float z1, int color, boolean helmet, boolean glow) {
		Box(float x0, float y0, float z0, float x1, float y1, float z1, int color, boolean helmet) {
			this(x0, y0, z0, x1, y1, z1, color, helmet, false);
		}

		Box glowing() {
			return new Box(x0, y0, z0, x1, y1, z1, color, helmet, true);
		}
	}

	HatLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
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
		Hat preview = state.getData(PREVIEW);
		Hat hat = preview != null ? preview : WardrobeFeature.INSTANCE.hatFor(player);
		if (hat == null) {
			return;
		}
		HelmetLook helmet = HelmetLook.of(state.headEquipment);
		pose.pushPose();
		getParentModel().root().translateAndRotate(pose);
		getParentModel().head.translateAndRotate(pose);
		List<Box> boxes = new ArrayList<>();
		switch (hat.style) {
			case CROWN -> crown(boxes, hat, helmet);
			case WREATH, LEAVES -> wreath(boxes, hat, helmet);
			case ACORN -> acornHat(boxes, hat, helmet);
			case MUSHROOM -> mushroomHat(boxes, hat, helmet);
			case WITCH -> witchHat(boxes, hat, helmet);
			case PUMPKIN -> pumpkinHat(boxes, hat, helmet);
			case HORNS -> hornsHat(boxes, hat, helmet);
			case BAT -> batHat(boxes, hat, helmet);
			case AVIATOR -> aviatorHat(boxes, hat, helmet);
			case MINER -> minerHat(boxes, hat, helmet);
			case PIRATE -> pirateHat(boxes, hat, helmet);
			case HALO -> haloHat(boxes, hat, helmet);
			case STRAW -> strawHat(boxes, hat, helmet);
			case FISHER -> fisherHat(boxes, hat, helmet);
			case CHEF -> chefHat(boxes, hat, helmet);
		}
		submit(pose, collector, boxes, helmet, light);
		if (hat.fancy && hat.style != Hat.Style.CROWN) {
			// A tile shows another hat than the one worn: it gets its own pendulums (which just hang in a screen).
			CharmPendulum[] pendulums = preview != null ? fresh() : pendulums(state.id, hat.style);
			new Chains(pose, collector, pendulums, WardrobeFeature.INSTANCE.damping(), light).draw(hat, helmet);
		}
		pose.popPose();
	}

	/** Draws a hat's moving parts: chains of stiff pieces, hanging down or standing up, each on its own pendulum. */
	private record Chains(PoseStack pose, SubmitNodeCollector collector, CharmPendulum[] pendulums, double damping, int light) {
		void draw(Hat hat, HelmetLook helmet) {
			switch (hat.style) {
				case WREATH, LEAVES -> {
					for (int s = 0; s < STRANDS.length; s++) {
						int strand = s;
						hang(s * STRAND_PIECES, at(STRANDS[s], -RING_ROWS, 11f), PIECE, STRAND_PIECES, damping + STRAND_DAMPING, STRAND_BEND,
								k -> strandPiece(hat, k, strand));
					}
				}
				case ACORN -> stand(0, at(0f, 5f, 0f), 2.5f, 2, new Vector3f(), k -> acornStem(hat, k));
				case MUSHROOM -> {
					for (int i = 0; i < 4; i++) {
						float x = i < 2 ? -10f : 10f;
						float z = i % 2 == 0 ? -10f : 10f;
						hang(i, at(x, -1f, z), 4f, 1, k -> fringe(hat));
					}
				}
				case WITCH -> {
					// Everything above the band sways, leaning a little back.
					stand(0, at(0f, 5f, 0f), 3f, WITCH_PIECES, new Vector3f(0f, 0f, 0.17f), WITCH_STIFF, damping + WITCH_DAMPING,
							k -> witchTip(hat, k));
					hang(WITCH_PIECES, at(9f, -1f, -12f), 9f, 1, k -> spider(hat));
				}
				case PUMPKIN -> stand(0, at(1f, 11f, 0f), 3f, 2, new Vector3f(0.6f, 0f, 0f), k -> vine(hat, k));
				// A tail is heavy and stiff: it settles quickly.
				case HORNS -> hang(0, at(0f, -3f, 10.5f), 4f, 3, damping + TAIL_DAMPING, TAIL_BEND, k -> tail(hat, k));
				case BAT -> stand(0, at(0f, 1f, -2f), 3f, 2, new Vector3f(), k -> spring(hat, k));
				case AVIATOR -> {
					hang(0, at(-9.75f, -3f, 0f), 6f, 1, k -> earFlap(hat));
					hang(1, at(9.75f, -3f, 0f), 6f, 1, k -> earFlap(hat));
				}
				case MINER -> hang(0, at(0f, 0f, 11.5f), 5f, 2, k -> lampCable(hat, k));
				case PIRATE -> stand(0, at(6f, 6f, 6f), 4f, 2, new Vector3f(0.15f, 0f, 0.25f), k -> feather(hat, k));
				// Tilted back a little, so it shows from the front.
				case HALO -> stand(0, at(0f, 1f, 0f), 5f, 1, new Vector3f(0f, 0f, 0.3f), k -> halo(hat));
				case STRAW -> {
					hang(0, at(-1.5f, 0.5f, 10f), 4f, 2, damping + STRAND_DAMPING, STRAND_BEND, k -> ribbon(hat));
					hang(2, at(1.5f, 0.5f, 10f), 4f, 2, damping + STRAND_DAMPING, STRAND_BEND, k -> ribbon(hat));
				}
				case FISHER -> {
					int top = helmet != null && helmet.trim() != 0 ? helmet.trim() : 0xFFE02828;
					hang(0, at(8f, -2f, -11.5f), 5f, 1, k -> bobber(top));
					hang(1, at(-11.5f, -2f, 4f), 6f, 1, k -> fish());
				}
				case CHEF -> stand(0, at(0f, 2f, 0f), 3f, 2, new Vector3f(), k -> toque(hat, k));
				default -> {
				}
			}
		}

		/** A point on the head in voxels, y up from its top. */
		private static Vector3f at(float x, float yUp, float z) {
			return new Vector3f(x * V, HEAD_TOP - yUp * V, z * V);
		}

		/** {@code count} pieces of {@code length} voxels hanging one from the next. Pieces are given y down. */
		private void hang(int first, Vector3f anchor, float length, int count, IntFunction<List<Box>> piece) {
			hang(first, anchor, length, count, damping, 1f, piece);
		}

		/**
		 * As above, losing {@code damping} speed per second; each piece bends from the one above only by {@code bend} of
		 * what its pendulum would (1: freely, less: stiffer).
		 */
		private void hang(int first, Vector3f anchor, float length, int count, double damping, float bend, IntFunction<List<Box>> piece) {
			Vector3f above = null;
			for (int k = 0; k < count; k++) {
				Vector3f down = pendulums[first + k].hang(pose.last().pose(), anchor, length * V, damping);
				if (down == null) {
					return;
				}
				if (above != null) {
					down = new Vector3f(above).lerp(down, bend).normalize();
				}
				above = down;
				place(anchor, new Quaternionf().rotationTo(0f, 1f, 0f, down.x, down.y, down.z), piece.apply(k));
				// The next piece hangs from the end of this one.
				anchor = new Vector3f(down).mul(length * V).add(anchor);
			}
		}

		/** How far a standing piece follows its pendulum: stiffer than something hanging. */
		private static final float STIFF = 0.35f;

		/**
		 * {@code count} pieces standing on each other, springing back like pendulums turned upside down; each piece leans
		 * {@code bend} more than the one below (a witch's hat tip that flops). Pieces are given y down, from -length to 0.
		 */
		private void stand(int first, Vector3f anchor, float length, int count, Vector3f bend, IntFunction<List<Box>> piece) {
			stand(first, anchor, length, count, bend, STIFF, damping, piece);
		}

		/** As above, following the pendulums by {@code stiff} (less: stiffer) and losing {@code damping} speed per second. */
		private void stand(int first, Vector3f anchor, float length, int count, Vector3f bend, float stiff, double damping,
				IntFunction<List<Box>> piece) {
			for (int k = 0; k < count; k++) {
				Vector3f down = pendulums[first + k].hang(pose.last().pose(), anchor, length * V, damping);
				if (down == null) {
					return;
				}
				Vector3f up = new Vector3f(down.x, -down.y, down.z).mul(stiff).add(0f, stiff - 1f, 0f).add(new Vector3f(bend).mul(k + 1f)).normalize();
				place(anchor, new Quaternionf().rotationTo(0f, -1f, 0f, up.x, up.y, up.z), piece.apply(k));
				anchor = new Vector3f(up).mul(length * V).add(anchor);
			}
		}

		private void place(Vector3f anchor, Quaternionf rotation, List<Box> boxes) {
			pose.pushPose();
			pose.translate(anchor.x, anchor.y, anchor.z);
			pose.mulPose(new Matrix4f().rotation(rotation));
			submit(pose, collector, boxes, null, light);
			pose.popPose();
		}
	}

	private static void submit(PoseStack pose, SubmitNodeCollector collector, List<Box> boxes, HelmetLook helmet, int light) {
		collector.submitCustomGeometry(pose, TYPE, (p, vc) -> {
			for (Box box : boxes) {
				box(vc, p, box, light);
			}
		});
		if (helmet != null && helmet.glint()) {
			List<Box> glinting = boxes.stream().filter(Box::helmet).toList();
			// After the band itself: the glint only draws where it already is.
			collector.order(1).submitCustomGeometry(pose, RenderTypes.trimmedArmorGlint(), (p, vc) -> {
				for (Box box : glinting) {
					box(vc, p, box, light);
				}
			});
		}
	}

	// ---------------------------------------------------------------- crown

	/**
	 * A crown round the top of the head: a band showing the helmet between two metal lips, a gem on each side, low points
	 * with pearls on top (white on the fine ones).
	 */
	private static void crown(List<Box> out, Hat hat, HelmetLook helmet) {
		int light = hat.main[0];
		int metal = hat.main[1];
		int dark = hat.main[2];
		int pearl = hat.fancy ? hat.third : light;
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			// The band, two rows of tiles from one corner to the next (the next side starts at the corner).
			for (int t = 0; t < 10; t++) {
				float x = -10f + 1.9f * t;
				for (int row = 0; row < 2; row++) {
					int i = side * 10 + t;
					int color = helmet != null ? helmet.tile(i, row) : (row == 1 && i % 4 == 2 ? dark : metal);
					part.add(up(x, 1f - 2f * row - 2f, -10f, x + 1.9f, 1f - 2f * row, -9f, color, true));
				}
			}
			// Lips above and below.
			part.add(up(-10.5f, 1f, -10.5f, 9f, 2f, -9f, light, false));
			part.add(up(-10.5f, -4f, -10.5f, 9f, -3f, -9f, dark, false));
			// The gem in the middle of the band; the front one shows the helmet's trim.
			int gem = side == 0 && helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
			part.add(up(-1f, -1f, -11f, 0f, 0f, -10f, lighter(gem), side == 0 && helmet != null && helmet.trim() != 0));
			part.add(up(0f, -1f, -11f, 1f, 0f, -10f, gem, false));
			part.add(up(-1f, -2f, -11f, 0f, -1f, -10f, gem, false));
			part.add(up(0f, -2f, -11f, 1f, -1f, -10f, ARGB.scaleRGB(gem, 0.7f), false));
			// The corner post with a pearl.
			part.add(up(-10.5f, 2f, -10.5f, -9f, 4f, -9f, metal, false));
			part.add(up(-10.25f, 4f, -10.25f, -9.25f, 5f, -9.25f, pearl, false));
			// Small points either side.
			for (float x : new float[]{-6.5f, 4.5f}) {
				part.add(up(x, 2f, -10.5f, x + 2f, 3f, -9f, metal, false));
				part.add(up(x + 0.5f, 3f, -10.25f, x + 1.5f, 4f, -9.25f, light, false));
			}
			// The fleur in the middle.
			part.add(up(-2f, 2f, -10.5f, 2f, 3f, -9f, metal, false));
			part.add(up(-1f, 3f, -10.5f, 1f, 5f, -9f, light, false));
			part.add(up(-2.5f, 3f, -10.25f, -1f, 4f, -9.25f, metal, false));
			part.add(up(1f, 3f, -10.25f, 2.5f, 4f, -9.25f, metal, false));
			part.add(up(-0.5f, 5f, -10.25f, 0.5f, 6f, -9.25f, pearl, false));
			turn(part, side, out);
		}
	}

	// ---------------------------------------------------------------- wreath

	/**
	 * A flower wreath: a woven ring round the forehead showing the helmet (twigs without one), and on its upper edge
	 * leaves and round flowers facing out. The flower in front has the helmet's trim as its centre. The autumn wreath has
	 * maple leaves and acorns instead, the acorn in front capped in the trim's colour.
	 */
	private static void wreath(List<Box> out, Hat hat, HelmetLook helmet) {
		int leaf = hat.second;
		int leafDark = hat.third;
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			for (int t = 0; t < 10; t++) {
				float x = -11f + 2f * t;
				for (int row = 0; row < RING_ROWS; row++) {
					int i = side * 10 + t;
					int color = helmet != null ? helmet.tile(i, row) : (i + row) % 2 == 0 ? TWIG_LIGHT : TWIG_DARK;
					part.add(up(x, -row - 1f, -11f, x + 2f, -row, -9f, color, true));
				}
			}
			float y = FLOWERS;
			// A leaf sticking out of the corner.
			part.add(up(-13f, y, -13f, -11f, y + 1f, -11f, leaf, false));
			part.add(up(-12f, y + 1f, -12.5f, -11f, y + 2f, -11f, leafDark, false));
			part.add(up(-12.5f, y - 1f, -11f, -11f, y, -10f, leafDark, false));
			if (hat.style == Hat.Style.LEAVES) {
				autumnLeaves(part, hat, helmet, side, y);
			} else if (side == 0) {
				// In front a big flower between two small ones.
				flower(part, -7f, y, hat.main[1], hat.accent);
				flower(part, 7f, y, hat.main[1], hat.accent);
				int centre = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
				flower(part, 0f, y, hat.main[0], centre);
				part.add(up(-1f, y + 3f, -11f, 1f, y + 4f, -10f, hat.main[0], false));
				part.add(up(-1f, y - 4f, -11f, 1f, y - 3f, -10f, hat.main[0], false));
				part.add(up(-4f, y - 1f, -11f, -3f, y + 1f, -10f, hat.main[0], false));
				part.add(up(3f, y - 1f, -11f, 4f, y + 1f, -10f, hat.main[0], false));
			} else {
				flower(part, -5f, y, hat.main[side % 2], hat.accent);
				flower(part, 5f, y, hat.main[(side + 1) % 2], hat.accent);
				// Leaves between them.
				part.add(up(-1f, y, -13f, 1f, y + 1f, -11f, leaf, false));
				part.add(up(-1f, y + 1f, -11f, 0f, y + 2f, -10f, leafDark, false));
				part.add(up(0f, y - 1f, -11f, 1f, y, -10f, leafDark, false));
			}
			turn(part, side, out);
		}
	}

	/** A round flower facing out from the front, centred at ({@code cx}, {@code cy}). */
	private static void flower(List<Box> out, float cx, float cy, int petal, int centre) {
		int shade = ARGB.scaleRGB(petal, 0.82f);
		out.add(up(cx - 1f, cy + 1f, -11f, cx + 1f, cy + 3f, -10f, petal, false));
		out.add(up(cx - 1f, cy - 3f, -11f, cx + 1f, cy - 1f, -10f, petal, false));
		out.add(up(cx - 3f, cy - 1f, -11f, cx - 1f, cy + 1f, -10f, petal, false));
		out.add(up(cx + 1f, cy - 1f, -11f, cx + 3f, cy + 1f, -10f, petal, false));
		out.add(up(cx - 2f, cy + 1f, -11f, cx - 1f, cy + 2f, -10f, shade, false));
		out.add(up(cx + 1f, cy + 1f, -11f, cx + 2f, cy + 2f, -10f, shade, false));
		out.add(up(cx - 2f, cy - 2f, -11f, cx - 1f, cy - 1f, -10f, shade, false));
		out.add(up(cx + 1f, cy - 2f, -11f, cx + 2f, cy - 1f, -10f, shade, false));
		out.add(up(cx - 1f, cy - 1f, -13f, cx + 1f, cy + 1f, -11f, centre, false));
	}

	/** One side of the autumn wreath: maple leaves with an acorn between. */
	private static void autumnLeaves(List<Box> part, Hat hat, HelmetLook helmet, int side, float y) {
		if (side == 0) {
			maple(part, -6f, y, hat.main[0]);
			maple(part, 6f, y, hat.main[2]);
			acorn(part, 0f, y, helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent);
		} else {
			maple(part, -5f, y, hat.main[side % 3]);
			maple(part, 5f, y, hat.main[(side + 1) % 3]);
			acorn(part, 0f, y - 0.5f, hat.accent);
		}
	}

	/** A maple leaf facing out from the front, centred at ({@code cx}, {@code cy}). */
	private static void maple(List<Box> out, float cx, float cy, int color) {
		int shade = ARGB.scaleRGB(color, 0.8f);
		out.add(up(cx - 1f, cy - 1f, -11f, cx + 1f, cy + 3f, -10f, color, false));
		out.add(up(cx - 0.5f, cy + 3f, -11f, cx + 0.5f, cy + 4f, -10f, color, false));
		out.add(up(cx - 3f, cy, -11f, cx - 1f, cy + 2f, -10f, color, false));
		out.add(up(cx + 1f, cy, -11f, cx + 3f, cy + 2f, -10f, color, false));
		out.add(up(cx - 3.5f, cy + 2f, -11f, cx - 2.5f, cy + 3f, -10f, color, false));
		out.add(up(cx + 2.5f, cy + 2f, -11f, cx + 3.5f, cy + 3f, -10f, color, false));
		out.add(up(cx - 2f, cy - 2f, -11f, cx - 1f, cy, -10f, shade, false));
		out.add(up(cx + 1f, cy - 2f, -11f, cx + 2f, cy, -10f, shade, false));
		out.add(up(cx - 0.25f, cy - 1f, -11.2f, cx + 0.25f, cy + 3f, -11f, shade, false));
		out.add(up(cx - 0.5f, cy - 3f, -11f, cx + 0.5f, cy - 1f, -10f, TWIG_DARK, false));
	}

	/** An acorn facing out from the front, centred at ({@code cx}, {@code cy}), its cap in {@code cap}. */
	private static void acorn(List<Box> out, float cx, float cy, int cap) {
		out.add(up(cx - 1.5f, cy + 0.5f, -12f, cx + 1.5f, cy + 2f, -10f, cap, false));
		out.add(up(cx - 1f, cy - 2f, -11.5f, cx + 1f, cy + 0.5f, -10.5f, ACORN_NUT, false));
		out.add(up(cx - 0.25f, cy + 2f, -11.25f, cx + 0.25f, cy + 3f, -10.75f, TWIG_DARK, false));
	}

	/**
	 * One piece of a hanging strand: a stem with a leaf on alternating sides, the last one ending in a blossom (an acorn
	 * on the autumn wreath).
	 */
	private static List<Box> strandPiece(Hat hat, int k, int strand) {
		List<Box> out = new ArrayList<>();
		boolean right = (k + strand) % 2 == 0;
		if (hat.style == Hat.Style.LEAVES) {
			out.add(raw(-0.5f, 0f, -0.5f, 0.5f, PIECE, 0.5f, TWIG_DARK));
			int leaf = hat.main[(k + strand) % 3];
			out.add(right ? raw(0.5f, 1f, -0.5f, 3f, 2.5f, 0.5f, leaf) : raw(-3f, 1f, -0.5f, -0.5f, 2.5f, 0.5f, leaf));
			if (k == STRAND_PIECES - 1) {
				out.add(raw(-1.5f, PIECE, -1.5f, 1.5f, PIECE + 1.5f, 1.5f, hat.accent));
				out.add(raw(-1f, PIECE + 1.5f, -1f, 1f, PIECE + 4f, 1f, ACORN_NUT));
				out.add(raw(-0.5f, PIECE + 4f, -0.5f, 0.5f, PIECE + 4.5f, 0.5f, hat.accent));
			}
			return out;
		}
		out.add(raw(-0.5f, 0f, -0.5f, 0.5f, PIECE, 0.5f, hat.third));
		out.add(right ? raw(0.5f, 1f, -0.5f, 2.5f, 2f, 0.5f, hat.second) : raw(-2.5f, 1f, -0.5f, -0.5f, 2f, 0.5f, hat.second));
		if (k == STRAND_PIECES - 1) {
			int petal = hat.main[strand % 2];
			out.add(raw(-1.5f, PIECE, -1.5f, 1.5f, PIECE + 2f, 1.5f, petal));
			out.add(raw(-0.5f, PIECE + 2f, -0.5f, 0.5f, PIECE + 3f, 0.5f, ARGB.scaleRGB(petal, 0.82f)));
			out.add(raw(-0.5f, PIECE + 0.5f, -2f, 0.5f, PIECE + 1.5f, 2f, hat.accent));
		}
		return out;
	}

	// ---------------------------------------------------------------- all year

	/**
	 * An aviator's leather cap: the goggles' strap round it shows the helmet, the lenses take the trim's colour, and the
	 * ear flaps hang loose.
	 */
	private static void aviatorHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, null, 4, 1f, 8f, 9.5f, (i, row) -> row == 3 ? hat.main[2] : hat.main[i % 5 == 0 ? 1 : 0]);
		out.add(up(-9.5f, 1f, -9.5f, 9.5f, 2.5f, 9.5f, hat.main[0], false));
		out.add(up(-0.5f, 2.5f, -9.5f, 0.5f, 3f, 9.5f, hat.main[1], false));
		ring(out, helmet, 2, -0.5f, 9.5f, 10.25f, (i, row) -> hat.main[1]);
		int lens = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
		for (int s = -1; s <= 1; s += 2) {
			float x0 = s < 0 ? -6f : 1f;
			out.add(up(x0, -2f, -11.5f, x0 + 5f, 1.5f, -10.25f, hat.second, false));
			out.add(up(x0 + 0.75f, -1.25f, -11.75f, x0 + 4.25f, 0.75f, -11.5f, lens, false));
		}
	}

	private static List<Box> earFlap(Hat hat) {
		return List.of(raw(-0.75f, 0f, -3f, 0.75f, 6f, 3f, hat.main[0]), raw(-0.5f, 6f, -2.5f, 0.5f, 6.75f, 2.5f, hat.main[2]));
	}

	/** A miner's hard hat: a dome with a ridge, its brim showing the helmet, and a lamp in front shining in the trim's colour. */
	private static void minerHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 1, 0f, 9f, 11.5f, (i, row) -> hat.main[1]);
		ring(out, null, 3, 3f, 8f, 10f, (i, row) -> hat.main[0]);
		out.add(up(-8f, 3f, -8f, 8f, 5f, 8f, hat.main[0], false));
		out.add(up(-5f, 5f, -5f, 5f, 6f, 5f, hat.main[0], false));
		out.add(up(-1f, 3f, -10.5f, 1f, 5.5f, 10.5f, hat.main[1], false));
		out.add(up(-1f, 5.5f, -6f, 1f, 6.5f, 6f, hat.main[1], false));
		int glow = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
		out.add(up(-2.5f, 0.5f, -12f, 2.5f, 4f, -10f, hat.second, false));
		out.add(up(-1.75f, 1.25f, -12.3f, 1.75f, 3.25f, -12f, glow, false).glowing());
	}

	private static List<Box> lampCable(Hat hat, int k) {
		Box cable = raw(-0.4f, 0f, -0.4f, 0.4f, 5f, 0.4f, 0xFF2A2A2C);
		return k == 0 ? List.of(cable) : List.of(cable, raw(-1.5f, 5f, -1f, 1.5f, 8f, 1f, hat.second));
	}

	/**
	 * A pirate's tricorne: its brim turned up into a triangle pointing forward, a band showing the helmet, a cockade in
	 * the trim's colour and a feather that springs.
	 */
	private static void pirateHat(List<Box> out, Hat hat, HelmetLook helmet) {
		int felt = hat.main[0];
		ring(out, helmet, 2, 2f, 8.5f, 9.25f, (i, row) -> hat.main[1]);
		out.add(up(-8.5f, 2f, -8.5f, 8.5f, 6f, 8.5f, felt, false));
		out.add(up(-7f, 6f, -7f, 7f, 7f, 7f, felt, false));
		// The brim: a triangle from the back corners to a point in front, its edges turned up.
		for (float z = -14f; z < 12f; z += 2f) {
			float half = 13f * (z + 15f) / 26f;
			out.add(up(-half, -1f, z, half, 0f, z + 2f, hat.main[1], false));
		}
		out.add(up(-13f, 0f, 10f, 13f, 4f, 12f, felt, false));
		out.add(up(-13f, 4f, 10f, 13f, 4.5f, 12f, hat.second, false));
		for (int t = 0; t <= 12; t++) {
			float z = 11f - 25f * t / 12f;
			float x = 13f * (z + 14f) / 25f;
			for (int s = -1; s <= 1; s += 2) {
				out.add(up(s * x - 1.2f, 0f, z - 1.2f, s * x + 1.2f, 4f, z + 1.2f, felt, false));
				out.add(up(s * x - 1.2f, 4f, z - 1.2f, s * x + 1.2f, 4.5f, z + 1.2f, hat.second, false));
			}
		}
		int cockade = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.second;
		out.add(up(-8.5f, 1f, -3f, -7f, 4f, 0f, cockade, false));
	}

	private static List<Box> feather(Hat hat, int k) {
		float half = k == 0 ? 1.6f : 1f;
		return List.of(raw(-half, -4f, -0.3f, half, 0f, 0.3f, hat.accent), raw(-0.15f, -4f, -0.4f, 0.15f, 0f, 0.4f, ARGB.scaleRGB(hat.accent, 0.8f)));
	}

	/** A halo floating over the head, glowing; a thin circlet round the head shows the helmet. */
	private static void haloHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 1, -1f, 9f, 9.75f, (i, row) -> hat.main[1]);
	}

	/** The halo itself, at the end of an invisible stalk five voxels long, bobbing gently. */
	private static List<Box> halo(Hat hat) {
		double seconds = System.nanoTime() % 1_000_000_000_000L / 1e9;
		float y = -5f + (float) Math.sin(seconds * 2.0) * 0.5f;
		List<Box> out = new ArrayList<>();
		// An octagon: four sides and four corners.
		float[][] parts = {{-4f, -7.5f, 4f, -6f}, {-4f, 6f, 4f, 7.5f}, {-7.5f, -4f, -6f, 4f}, {6f, -4f, 7.5f, 4f},
				{-6.5f, -6.5f, -4f, -4f}, {4f, -6.5f, 6.5f, -4f}, {-6.5f, 4f, -4f, 6.5f}, {4f, 4f, 6.5f, 6.5f}};
		for (int i = 0; i < parts.length; i++) {
			float[] p = parts[i];
			int color = hat == Hat.HALO_RAINBOW
					? Mth.hsvToArgb((float) ((seconds * 0.25 + i / 8.0) % 1.0), 0.55f, 1f, 255)
					: i % 2 == 0 ? hat.main[0] : hat.main[1];
			out.add(raw(p[0], y - 0.75f, p[1], p[2], y, p[3], color).glowing());
		}
		return out;
	}

	/** A straw hat: a wide woven brim, a band showing the helmet with a bow in the trim's colour, ribbons behind. */
	private static void strawHat(List<Box> out, Hat hat, HelmetLook helmet) {
		IntBinaryOperator weave = (i, row) -> (i + row) % 2 == 0 ? hat.main[0] : hat.main[1];
		ring(out, null, 1, 0f, 9f, 14f, weave);
		ring(out, helmet, 2, 2f, 9f, 9.75f, (i, row) -> hat.accent);
		ring(out, null, 3, 5f, 7f, 9f, weave);
		out.add(up(-7f, 4f, -7f, 7f, 5f, 7f, hat.main[0], false));
		int bow = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
		out.add(up(-6.5f, 0f, -10.5f, -3f, 2f, -9.75f, bow, false));
		out.add(up(-5.25f, 0.5f, -10.75f, -4.25f, 1.5f, -10.5f, ARGB.scaleRGB(bow, 0.8f), false));
	}

	private static List<Box> ribbon(Hat hat) {
		return List.of(raw(-0.75f, 0f, -0.2f, 0.75f, 4f, 0.2f, hat.accent));
	}

	/** A soft fisherman's hat: a drooping brim, a band showing the helmet, a float and a fish hanging from the brim. */
	private static void fisherHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, null, 1, -1f, 9.5f, 12.5f, (i, row) -> hat.main[1]);
		ring(out, null, 1, -2f, 11.5f, 13f, (i, row) -> hat.main[1]);
		ring(out, helmet, 2, 1f, 9f, 9.75f, (i, row) -> hat.accent);
		ring(out, null, 3, 4f, 8f, 9.5f, (i, row) -> i % 4 == 0 ? hat.main[1] : hat.main[0]);
		out.add(up(-8f, 3f, -8f, 8f, 4.5f, 8f, hat.main[0], false));
	}

	/** The float on its line; its top takes the trim's colour (red without one). */
	private static List<Box> bobber(int top) {
		return List.of(raw(-0.15f, 0f, -0.15f, 0.15f, 5f, 0.15f, 0xFFE0E0E0), raw(-1f, 5f, -1f, 1f, 6f, 1f, top),
				raw(-1f, 6f, -1f, 1f, 7f, 1f, 0xFFF4F4F0));
	}

	private static List<Box> fish() {
		return List.of(raw(-0.15f, 0f, -0.15f, 0.15f, 6f, 0.15f, 0xFFE0E0E0), raw(-0.6f, 6f, -2.5f, 0.6f, 8f, 1.5f, 0xFF6A9AC8),
				raw(-0.4f, 6.5f, 1.5f, 0.4f, 7.5f, 3f, 0xFF4A7AA8), raw(-0.65f, 6.5f, -2f, 0.65f, 7f, -1.5f, 0xFF1A1A1A));
	}

	/** A chef's toque: a band showing the helmet (a badge on it in the trim's colour) and a tall puff that wobbles. */
	private static void chefHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 3, 2f, 9f, 9.75f, (i, row) -> hat.main[1]);
		if (helmet != null && helmet.trim() != 0) {
			out.add(up(-1f, 0f, -10f, 1f, 1.5f, -9.75f, helmet.trim(), false));
		}
	}

	private static List<Box> toque(Hat hat, int k) {
		if (k == 0) {
			return List.of(raw(-8.5f, -3f, -8.5f, 8.5f, 0f, 8.5f, hat.main[0]));
		}
		List<Box> out = new ArrayList<>();
		out.add(raw(-10f, -5f, -10f, 10f, 0f, 10f, hat.main[0]));
		out.add(raw(-8f, -6f, -8f, 8f, -5f, 8f, hat.main[0]));
		// Folds down the puff.
		for (float x : new float[]{-5f, 0f, 5f}) {
			out.add(raw(x - 0.5f, -5f, -10.2f, x + 0.5f, 0f, 10.2f, hat.main[1]));
			out.add(raw(-10.2f, -5f, x - 0.5f, 10.2f, 0f, x + 0.5f, hat.main[1]));
		}
		return out;
	}

	// ---------------------------------------------------------------- autumn and halloween

	/**
	 * A ring of tiles round the head, {@code rows} of one voxel from {@code top} down, between {@code inner} and
	 * {@code outer} from the middle: the helmet's look if there is one, else the {@code own} colour of each tile and row.
	 */
	private static void ring(List<Box> out, HelmetLook helmet, int rows, float top, float inner, float outer, IntBinaryOperator own) {
		float w = (outer + inner) / 10f;
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			for (int t = 0; t < 10; t++) {
				float x = -outer + w * t;
				for (int row = 0; row < rows; row++) {
					int i = side * 10 + t;
					int color = helmet != null ? helmet.tile(i, row) : own.applyAsInt(i, row);
					part.add(up(x, top - row - 1f, -outer, x + w, top - row, -inner, color, helmet != null));
				}
			}
			turn(part, side, out);
		}
	}

	/** An acorn's cup as a cap: a rim showing the helmet, rings of scales narrowing up, and a stalk on top that springs. */
	private static void acornHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 3, -1f, 9f, 11f, (i, row) -> hat.second);
		IntBinaryOperator scales = (i, row) -> (i + row) % 2 == 0 ? hat.main[0] : hat.main[1];
		ring(out, null, 3, 2f, 8f, 10.5f, scales);
		ring(out, null, 2, 4f, 5.5f, 9f, scales);
		ring(out, null, 1, 5f, 3f, 6.5f, scales);
		out.add(up(-8f, 1f, -8f, 8f, 2f, 8f, hat.main[1], false));
		out.add(up(-5.5f, 3f, -5.5f, 5.5f, 4f, 5.5f, hat.main[1], false));
		out.add(up(-3f, 4f, -3f, 3f, 5f, 3f, hat.main[2], false));
	}

	private static List<Box> acornStem(Hat hat, int k) {
		if (k == 0) {
			return List.of(raw(-0.75f, -2.5f, -0.75f, 0.75f, 0f, 0.75f, hat.accent));
		}
		return List.of(raw(-0.5f, -2.5f, -0.5f, 0.5f, 0f, 0.5f, hat.accent), raw(0.5f, -2.5f, -0.5f, 1.5f, -1.5f, 0.5f, hat.accent));
	}

	/**
	 * A fly agaric: its stem as a ring showing the helmet, the gills under a wide red cap with white dots, and fringes
	 * hanging from under the cap.
	 */
	private static void mushroomHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 4, -1f, 9f, 10f, (i, row) -> row == 3 ? ARGB.scaleRGB(hat.second, 0.88f) : hat.second);
		int red = hat.main[0];
		out.add(up(-12f, -1f, -12f, 12f, 0f, 12f, hat.third, false));
		out.add(up(-13f, 0f, -13f, 13f, 1.5f, 13f, hat.main[1], false));
		out.add(up(-12f, 1.5f, -12f, 12f, 3.5f, 12f, red, false));
		out.add(up(-9.5f, 3.5f, -9.5f, 9.5f, 5.5f, 9.5f, red, false));
		out.add(up(-5.5f, 5.5f, -5.5f, 5.5f, 6.5f, 5.5f, red, false));
		// Dots on top (x, z, height) and round the side.
		float[][] dots = {{-2f, -1f, 6.5f}, {-7.5f, 3f, 5.5f}, {5f, -7.5f, 5.5f}, {3f, 6.5f, 5.5f}, {-11f, -5f, 3.5f},
				{10f, 3f, 3.5f}, {-3f, -11.5f, 3.5f}, {6f, 10f, 3.5f}};
		for (float[] d : dots) {
			out.add(up(d[0], d[2], d[1], d[0] + 2f, d[2] + 0.4f, d[1] + 2f, hat.accent, false));
		}
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			float shift = side % 2 == 0 ? 0f : 2f;
			part.add(up(-7f + shift, 2f, -12.3f, -5f + shift, 3f, -12f, hat.accent, false));
			part.add(up(4f + shift, 1.5f, -12.3f, 6f + shift, 3f, -12f, hat.accent, false));
			turn(part, side, out);
		}
	}

	private static List<Box> fringe(Hat hat) {
		return List.of(raw(-0.5f, 0f, -0.5f, 0.5f, 4f, 0.5f, hat.third), raw(-1f, 4f, -1f, 1f, 5f, 1f, hat.second));
	}

	/**
	 * A witch's hat: a wide brim, its band showing the helmet with a buckle in the trim's colour, a cone narrowing up
	 * that sways from just above the band and leans a little back, and a spider hanging from the brim on its thread.
	 */
	private static void witchHat(List<Box> out, Hat hat, HelmetLook helmet) {
		out.add(up(-14f, -1f, -14f, 14f, 0f, 14f, hat.main[1], false));
		ring(out, helmet, 2, 2f, 8.5f, 9.5f, (i, row) -> hat.accent);
		out.add(up(-8.5f, 2f, -8.5f, 8.5f, 5f, 8.5f, hat.main[0], false));
		int buckle = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.second;
		out.add(up(-2f, 0f, -10f, -1.25f, 2f, -9.5f, buckle, false));
		out.add(up(1.25f, 0f, -10f, 2f, 2f, -9.5f, buckle, false));
		out.add(up(-1.25f, 1.5f, -10f, 1.25f, 2f, -9.5f, buckle, false));
		out.add(up(-1.25f, 0f, -10f, 1.25f, 0.5f, -9.5f, buckle, false));
	}

	/** One piece of the witch's cone, narrowing up: three voxels high, the last one with the point. */
	private static List<Box> witchTip(Hat hat, int k) {
		float half = 7f - 1.4f * k;
		int color = k < 2 ? hat.main[0] : hat.main[2];
		Box piece = raw(-half, -3f, -half, half, 0f, half, color);
		if (k < WITCH_PIECES - 1) {
			return List.of(piece);
		}
		return List.of(piece, raw(-1f, -5f, -1f, 1f, -3f, 1f, color), raw(-0.5f, -6f, -0.5f, 0.5f, -5f, 0.5f, color));
	}

	private static List<Box> spider(Hat hat) {
		int body = hat.third;
		List<Box> out = new ArrayList<>();
		out.add(raw(-0.15f, 0f, -0.15f, 0.15f, 9f, 0.15f, 0xFFD8D8DC));
		out.add(raw(-0.8f, 8.8f, -0.8f, 0.8f, 9.8f, 0.8f, body));
		out.add(raw(-1.2f, 9.6f, -1.2f, 1.2f, 11.6f, 1.2f, body));
		out.add(raw(-2.6f, 10f, -0.2f, 2.6f, 10.4f, 0.2f, body));
		out.add(raw(-2.6f, 10.9f, -0.2f, 2.6f, 11.3f, 0.2f, body));
		out.add(raw(-0.2f, 10.4f, -2.6f, 0.2f, 10.8f, 2.6f, body));
		out.add(raw(-0.6f, 9.1f, -0.9f, 0.6f, 9.4f, -0.8f, 0xFFE02828).glowing());
		return out;
	}

	/**
	 * A small carved pumpkin sitting on the head, its face lit from inside, on a band showing the helmet; a vine curls
	 * from the stem and springs.
	 */
	private static void pumpkinHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 2, 0f, 9f, 10f, (i, row) -> hat.second);
		ring(out, null, 8, 9f, 5f, 7f, (i, row) -> i % 3 == 0 ? hat.main[1] : row == 0 ? hat.main[2] : hat.main[0]);
		out.add(up(-6f, 0f, -6f, 6f, 1f, 6f, hat.main[1], false));
		out.add(up(-5f, 8f, -5f, 5f, 9f, 5f, hat.main[0], false));
		out.add(up(-4f, 9f, -4f, 4f, 10f, 4f, hat.main[2], false));
		out.add(up(-1f, 10f, -1f, 1f, 12f, 1f, hat.second, false));
		int glow = hat.accent;
		float[][] face = {
				{-5f, 5f, -2f, 6f}, {-4f, 6f, -3f, 7f}, {2f, 5f, 5f, 6f}, {3f, 6f, 4f, 7f},
				{-0.5f, 4f, 0.5f, 5f},
				{-4f, 2f, 4f, 3f}, {-5f, 3f, -3f, 4f}, {-1f, 3f, 1f, 4f}, {3f, 3f, 5f, 4f}};
		for (float[] f : face) {
			out.add(up(f[0], f[1], -7.3f, f[2], f[3], -6.9f, glow, false).glowing());
		}
	}

	private static List<Box> vine(Hat hat, int k) {
		Box stalk = raw(-0.4f, -3f, -0.4f, 0.4f, 0f, 0.4f, hat.second);
		if (k == 0) {
			return List.of(stalk, raw(0.4f, -2.5f, -0.3f, 2.6f, -1f, 0.3f, hat.third));
		}
		return List.of(stalk, raw(-1.6f, -3.4f, -0.4f, 0.4f, -2.6f, 0.4f, hat.second));
	}

	/** Devil's horns curving up and out from a band showing the helmet, and a tail at the back that swings. */
	private static void hornsHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 2, -1f, 9f, 10f, (i, row) -> hat.accent);
		// Each horn's pieces: x from the middle, z, height (voxels up) and shade.
		float[][] pieces = {{3.5f, 7f, -5.5f, -1.5f, 0f, 2f, 0}, {4f, 7.5f, -5f, -2f, 2f, 4f, 0}, {5f, 8f, -4.5f, -2.5f, 4f, 5.5f, 1},
				{5.75f, 8f, -4.25f, -2.75f, 5.5f, 7f, 1}, {6f, 7.75f, -4f, -3f, 7f, 8.5f, 2}, {6.25f, 7.25f, -3.75f, -3.25f, 8.5f, 9.5f, 2}};
		for (int s = -1; s <= 1; s += 2) {
			for (float[] p : pieces) {
				float x0 = s > 0 ? p[0] : -p[1];
				float x1 = s > 0 ? p[1] : -p[0];
				out.add(up(x0, p[4], p[2], x1, p[5], p[3], hat.main[(int) p[6]], false));
			}
		}
	}

	private static List<Box> tail(Hat hat, int k) {
		Box cord = raw(-0.5f, 0f, -0.5f, 0.5f, 4f, 0.5f, hat.main[1]);
		if (k < 2) {
			return List.of(cord);
		}
		return List.of(cord, raw(-2f, 4f, -0.5f, 2f, 5f, 0.5f, hat.main[0]), raw(-1.25f, 5f, -0.5f, 1.25f, 6f, 0.5f, hat.main[0]),
				raw(-0.5f, 6f, -0.5f, 0.5f, 7f, 0.5f, hat.main[0]));
	}

	/** A headband showing the helmet, with a little bat on a spring above it that bobs and flaps. */
	private static void batHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 2, -1f, 9f, 10f, (i, row) -> hat.third);
		out.add(up(-10f, 0f, -3f, 10f, 1f, -1f, hat.third, false));
		out.add(up(-10f, -1f, -3f, -9f, 0f, -1f, hat.third, false));
		out.add(up(9f, -1f, -3f, 10f, 0f, -1f, hat.third, false));
		out.add(up(-1.5f, 1f, -3.5f, 1.5f, 1.5f, -0.5f, hat.second, false));
	}

	private static List<Box> spring(Hat hat, int k) {
		List<Box> out = new ArrayList<>();
		out.add(raw(-0.3f, -3f, -0.3f, 0.3f, 0f, 0.3f, ARGB.scaleRGB(hat.second, 0.7f)));
		for (int j = 0; j < 3; j++) {
			out.add(raw(-1f, -j - 0.9f, -1f, 1f, -j - 0.4f, 1f, hat.second));
		}
		if (k == 1) {
			int body = hat.main[0];
			int wing = hat.main[1];
			out.add(raw(-1.5f, -7f, -1f, 1.5f, -3f, 1f, body));
			out.add(raw(-1.25f, -9f, -1f, 1.25f, -7f, 1f, body));
			out.add(raw(-1.25f, -10f, -0.5f, -0.5f, -9f, 0.5f, body));
			out.add(raw(0.5f, -10f, -0.5f, 1.25f, -9f, 0.5f, body));
			out.add(raw(-0.9f, -8.4f, -1.1f, -0.3f, -7.8f, -1f, hat.accent).glowing());
			out.add(raw(0.3f, -8.4f, -1.1f, 0.9f, -7.8f, -1f, hat.accent).glowing());
			// The wings beat up and down.
			float flap = (float) Math.sin(System.nanoTime() % 1_000_000_000_000L / 1e9 * 12.0) * 1.5f;
			for (int s = -1; s <= 1; s += 2) {
				float[][] wings = {{1.5f, 4f, 0f}, {4f, 6.5f, 1f}, {6.5f, 8f, 2f}};
				for (float[] w : wings) {
					float x0 = s > 0 ? w[0] : -w[1];
					float x1 = s > 0 ? w[1] : -w[0];
					float lift = -flap * w[2];
					out.add(raw(x0, -6.5f + lift, -0.25f, x1, -5f + lift - (w[2] == 2f ? 0.5f : 0f), 0.25f, wing));
				}
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- geometry

	/** A box in voxels with y measured up from the top of the head, -z the front. */
	private static Box up(float x0, float yUp0, float z0, float x1, float yUp1, float z1, int color, boolean helmet) {
		return new Box(x0 * V, HEAD_TOP - yUp1 * V, z0 * V, x1 * V, HEAD_TOP - yUp0 * V, z1 * V, color, helmet);
	}

	/** A box in voxels as given (y down), for things hanging. */
	private static Box raw(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
		return new Box(x0 * V, y0 * V, z0 * V, x1 * V, y1 * V, z1 * V, color, false);
	}

	/** Adds a front side's boxes turned to {@code side} (0 front, then a quarter turn each round the head). */
	private static void turn(List<Box> part, int side, List<Box> out) {
		for (Box b : part) {
			float x0 = b.x0, z0 = b.z0, x1 = b.x1, z1 = b.z1;
			for (int s = 0; s < side; s++) {
				// (x, z) -> (-z, x)
				float nx0 = -z1, nx1 = -z0, nz0 = x0, nz1 = x1;
				x0 = nx0;
				x1 = nx1;
				z0 = nz0;
				z1 = nz1;
			}
			out.add(new Box(x0, b.y0, z0, x1, b.y1, z1, b.color, b.helmet, b.glow));
		}
	}

	private static void box(VertexConsumer vc, PoseStack.Pose p, Box b, int light) {
		float x0 = b.x0, y0 = b.y0, z0 = b.z0, x1 = b.x1, y1 = b.y1, z1 = b.z1;
		int c = b.color;
		if (b.glow) {
			light = FULL_BRIGHT;
		}
		face(vc, p, c, light, 0f, 0f, -1f, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
		face(vc, p, c, light, 0f, 0f, 1f, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		face(vc, p, c, light, -1f, 0f, 0f, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		face(vc, p, c, light, 1f, 0f, 0f, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
		face(vc, p, c, light, 0f, -1f, 0f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		face(vc, p, c, light, 0f, 1f, 0f, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
	}

	/**
	 * A quad facing ({@code nx}, {@code ny}, {@code nz}), its corners turned the way that is seen from there. The texture
	 * is plain white; the UVs follow the position so a glint drawn over it moves across.
	 */
	private static void face(VertexConsumer vc, PoseStack.Pose p, int color, int light, float nx, float ny, float nz, float... c) {
		float ax = c[3] - c[0], ay = c[4] - c[1], az = c[5] - c[2];
		float bx = c[6] - c[3], by = c[7] - c[4], bz = c[8] - c[5];
		float facing = (ay * bz - az * by) * nx + (az * bx - ax * bz) * ny + (ax * by - ay * bx) * nz;
		for (int i = 0; i < 4; i++) {
			int k = (facing >= 0 ? i : 3 - i) * 3;
			float u = clampUv(0.5f + (c[k] + c[k + 2]) * 0.6f);
			float v = clampUv(0.5f + c[k + 1] * 0.6f);
			vc.addVertex(p, c[k], c[k + 1], c[k + 2]).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light).setNormal(p, nx, ny, nz);
		}
	}

	private static int lighter(int color) {
		return ARGB.color(255, Math.min(255, ARGB.red(color) + 60), Math.min(255, ARGB.green(color) + 60), Math.min(255, ARGB.blue(color) + 60));
	}

	private static float clampUv(float value) {
		return Math.max(0.01f, Math.min(0.99f, value));
	}

	/** The swinging parts of one player's hat, hanging still again when they put on another kind. */
	private static CharmPendulum[] pendulums(int entityId, Hat.Style style) {
		long now = System.nanoTime();
		Swing swing = SWINGS.get(entityId);
		if (swing == null || swing.style() != style) {
			swing = new Swing(style, fresh());
			SWINGS.put(entityId, swing);
			if (SWINGS.size() > 64) {
				SWINGS.int2ObjectEntrySet().removeIf(e -> now - e.getValue().pendulums()[0].lastSeen > FORGET_NANOS);
			}
		}
		return swing.pendulums();
	}

	private static CharmPendulum[] fresh() {
		long now = System.nanoTime();
		CharmPendulum[] list = new CharmPendulum[PENDULUMS];
		for (int i = 0; i < list.length; i++) {
			list[i] = new CharmPendulum();
			list[i].lastSeen = now;
		}
		return list;
	}

	static void forget() {
		SWINGS.clear();
	}
}
