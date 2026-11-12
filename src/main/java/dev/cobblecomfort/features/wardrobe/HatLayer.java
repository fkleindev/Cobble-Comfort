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
	static final float V = 1f / 32f;
	/** The top of the head, in the head's space (y down). */
	private static final float HEAD_TOP = -0.5f;
	private static final long FORGET_NANOS = 2_000_000_000L;
	/** Wreath: two strands down the back, of three pieces each. */
	private static final float[] STRANDS = {-4f, 4f};
	private static final int STRAND_PIECES = 3;
	private static final float PIECE = 4f;
	/** Wreath strands: speed lost per second on top of the setting, and how freely each piece bends from the one above. */
	static final double STRAND_DAMPING = 6.0;
	private static final float STRAND_BEND = 0.35f;
	private static final int PENDULUMS = 6;
	/** Witch's hat: the cone above the band in pieces that sway. */
	private static final int WITCH_PIECES = 5;
	/** Beanie: how far the pompom follows its pendulum. */
	private static final float POMPOM_STIFF = 0.6f;
	/** Party hat: its layers, where its middle sits and how far each layer leans left (voxels). */
	/** Bunny ears: each of their two pieces this long, and half as wide (voxels). */
	private static final float EAR_PIECE = 8f;
	private static final float EAR_HALF = 2.6f;
	/** Sombrero: where its pompoms hang round the brim (x, z in voxels). */
	private static final float[][] SOMBRERO_POMPOMS = {{17f, 0f}, {-17f, 0f}, {10f, 14f}, {-10f, 14f}, {10f, -14f}, {-10f, -14f}};
	private static final int PARTY_LAYERS = 6;
	private static final float PARTY_X = -2f;
	private static final float PARTY_LEAN = 0.4f;
	/** Elf hat: its cone's layers, how far each leans back (voxels), and where the cone ends (voxels up). */
	private static final int ELF_LAYERS = 6;
	private static final float ELF_LEAN = 0.7f;
	private static final float ELF_TOP = 1f + 1.5f * ELF_LAYERS;
	/** Santa hat: its cone's layers, and how far each leans to the right and back (voxels). */
	private static final int SANTA_LAYERS = 7;
	private static final float SANTA_LEAN_X = 0.9f;
	private static final float SANTA_LEAN_Z = 0.3f;
	/** Cap: where its lower edge and visor sit, in voxels up from the top of the head. */
	private static final float CAP_BASE = -2f;
	/** Cap: the colour under its visor. */
	private static final int CAP_UNDER = 0xFF2E4A26;
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
	/** Jack o' lantern: how far its walls come down over the head (voxels), to the chin. */
	private static final float PUMPKIN_DEPTH = 16.5f;

	/** On a player drawn as a wardrobe tile: the hat to show there instead of their own. */
	static final RenderStateDataKey<Hat> PREVIEW = RenderStateDataKey.create(() -> "cobblecomfort:hat_preview");

	private static final Int2ObjectOpenHashMap<Swing> SWINGS = new Int2ObjectOpenHashMap<>();
	/** Each wearer's propeller: how far it has turned (radians) and when (seconds). */
	private static final Int2ObjectOpenHashMap<float[]> SPINS = new Int2ObjectOpenHashMap<>();

	/** The swinging parts of one player's hat, for the style they were made for. */
	private record Swing(Hat.Style style, CharmPendulum[] pendulums) {
	}

	/**
	 * A box in head space (blocks, y down) and its colour; {@code helmet} if it is part of what shows the helmet,
	 * {@code glow} if it lights itself (a carved pumpkin's face); what it is made of, or null for the piece's own
	 * material (see {@link #material}).
	 */
	record Box(float x0, float y0, float z0, float x1, float y1, float z1, int color, boolean helmet, boolean glow,
			CompanionTexture.Material material) {
		Box(float x0, float y0, float z0, float x1, float y1, float z1, int color, boolean helmet) {
			this(x0, y0, z0, x1, y1, z1, color, helmet, false, null);
		}

		Box glowing() {
			return new Box(x0, y0, z0, x1, y1, z1, color, helmet, true, CompanionTexture.Material.PLAIN);
		}

		/** The same box made of {@code material} (a metal buckle on a cloth hat). */
		Box in(CompanionTexture.Material material) {
			return new Box(x0, y0, z0, x1, y1, z1, color, helmet, glow, material);
		}
	}

	/**
	 * What the piece being drawn is made of, for its boxes that do not say (render thread: set by each piece's
	 * drawing, plain again after).
	 */
	private static CompanionTexture.Material material = CompanionTexture.Material.PLAIN;

	/** Draws {@code drawing} with the boxes it submits made of {@code material} where they do not say otherwise. */
	static void in(CompanionTexture.Material material, Runnable drawing) {
		CompanionTexture.Material before = HatLayer.material;
		HatLayer.material = material;
		try {
			drawing.run();
		} finally {
			HatLayer.material = before;
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
		Hat hat = preview != null || WardrobeScreen.isTile(state) ? preview : WardrobeFeature.INSTANCE.hatFor(player);
		if (hat == null) {
			return;
		}
		HelmetLook helmet = HelmetLook.of(state.headEquipment);
		pose.pushPose();
		getParentModel().root().translateAndRotate(pose);
		getParentModel().head.translateAndRotate(pose);
		// A tile shows another hat than the one worn: it gets its own pendulums (which just hang in a screen).
		CharmPendulum[] pendulums = preview != null ? fresh() : pendulums(state.id, hat.style);
		// How fast the wearer moves, in blocks a second (a tile in the wardrobe stands still).
		float speed = preview != null ? 0f : (float) player.position().subtract(player.xo, player.yo, player.zo).length() * 20f;
		draw(pose, collector, hat, helmet, pendulums, preview != null ? -1 : state.id, speed, light);
		pose.popPose();
	}

	/** Draws a hat with no helmet under it in the head's space, its moving parts hanging still. */
	static void draw(PoseStack pose, SubmitNodeCollector collector, Hat hat, int light) {
		draw(pose, collector, hat, null, fresh(), -1, 0f, light);
	}

	private static void draw(PoseStack pose, SubmitNodeCollector collector, Hat hat, HelmetLook helmet, CharmPendulum[] pendulums,
			int entityId, float speed, int light) {
		in(WornMaterials.of(hat.style), () -> drawIn(pose, collector, hat, helmet, pendulums, entityId, speed, light));
	}

	/** Draws a hat, textured in its material ({@link WornMaterials}). */
	private static void drawIn(PoseStack pose, SubmitNodeCollector collector, Hat hat, HelmetLook helmet, CharmPendulum[] pendulums,
			int entityId, float speed, int light) {
		pose.pushPose();
		pose.translate(0f, sink(hat.style) * V, 0f);
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
			case BEANIE -> beanieHat(boxes, hat, helmet);
			case CAP -> capHat(boxes, hat, helmet);
			case NIGHTCAP -> nightcapHat(boxes, hat, helmet);
			case PROPELLER -> propellerHat(boxes, hat, helmet);
			case BUNNY -> bunnyHat(boxes, hat, helmet);
			case MORTARBOARD -> mortarboardHat(boxes, hat, helmet);
			case TOPHAT -> tophatHat(boxes, hat, helmet);
			case COWBOY -> cowboyHat(boxes, hat, helmet);
			case HEADPHONES -> headphonesHat(boxes, hat, helmet);
			case PARTY -> partyHat(boxes, hat, helmet);
			case SOMBRERO -> sombreroHat(boxes, hat, helmet);
			case BERET -> beretHat(boxes, hat, helmet);
			case TYROLEAN -> tyroleanHat(boxes, hat, helmet);
			case CAPTAIN -> captainHat(boxes, hat, helmet);
			case USHANKA -> ushankaHat(boxes, hat, helmet);
			case TIARA -> tiaraHat(boxes, hat, helmet);
			case NINJA -> ninjaHat(boxes, hat, helmet);
			case AXOLOTL -> axolotlHat(boxes, hat, helmet);
			case FOX -> foxHat(boxes, hat, helmet);
			case SANTA -> santaHat(boxes, hat, helmet);
			case REINDEER -> reindeerHat(boxes, hat, helmet);
			case ELF -> elfHat(boxes, hat, helmet);
			case SPARKLE -> sparkleHat(boxes, hat, helmet);
		}
		submit(pose, collector, boxes, helmet, light);
		if (hat.fancy && hat.style != Hat.Style.CROWN) {
			new Chains(pose, collector, pendulums, WardrobeFeature.INSTANCE.damping(), light, entityId, speed).draw(hat, helmet);
		}
		pose.popPose();
	}

	/**
	 * How far a hat is pulled down over the head (voxels), all of it with its moving parts: the models are built with
	 * their lower edge at the top of the head, and worn hats and caps come down to the forehead. Crowns, wreaths, bands
	 * and things that sit on top stay where they are.
	 */
	private static float sink(Hat.Style style) {
		return switch (style) {
			case CAP, PROPELLER, MINER, PIRATE, STRAW, TOPHAT, COWBOY, SOMBRERO, TYROLEAN, WITCH -> 4f;
			case NIGHTCAP, AVIATOR, FISHER, CHEF, MORTARBOARD, CAPTAIN, USHANKA, SANTA, ELF, AXOLOTL, FOX -> 3f;
			case BEANIE, BERET, ACORN, MUSHROOM -> 2f;
			default -> 0f;
		};
	}

	/** Draws a hat's moving parts: chains of stiff pieces, hanging down or standing up, each on its own pendulum. */
	record Chains(PoseStack pose, SubmitNodeCollector collector, CharmPendulum[] pendulums, double damping, int light,
			int entityId, float speed) {
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
					hang(WITCH_PIECES, at(9f, -1f, -12f), 5f, 1, k -> spider(hat));
				}
				case PUMPKIN -> stand(0, at(1f, 5.25f, 0f), 3f, 2, new Vector3f(0.6f, 0f, 0f), k -> vine(hat, k));
				// A tail is heavy and stiff: it settles quickly.
				case HORNS -> hang(0, at(0f, -3f, 10.5f), 4f, 3, damping + TAIL_DAMPING, TAIL_BEND, k -> tail(hat, k));
				case BAT -> stand(0, at(0f, 1f, -2f), 3f, 2, new Vector3f(), k -> spring(hat, k));
				case AVIATOR -> {
					hang(0, at(-9.25f, -3f, 0f), 6f, 1, k -> earFlap(hat));
					hang(1, at(9.25f, -3f, 0f), 6f, 1, k -> earFlap(hat));
				}
				case MINER -> hang(0, at(0f, 0f, 11.5f), 5f, 2, k -> lampCable(hat, k));
				case PIRATE -> {
					stand(0, at(6f, 7f, 6f), 4f, 2, new Vector3f(0.15f, 0f, 0.25f), k -> feather(hat.accent, 1f, k));
					stand(2, at(7.5f, 6.5f, 3.5f), 3.5f, 2, new Vector3f(0.3f, 0f, 0.1f), k -> feather(ARGB.scaleRGB(hat.accent, 0.85f), 0.7f, k));
				}
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
				case CAP -> hang(0, at(2.5f, CAP_BASE + 0.5f, 9.9f), 3f, 1, k -> strapEnd(hat));
				case NIGHTCAP -> hang(0, at(10f, 3f, 0f), 4f, 3, damping + STRAND_DAMPING, 0.6f, k -> nightcapTip(hat, k));
				case PROPELLER -> spin(at(0f, 6.5f, 0f), blades(hat));
				case BUNNY -> {
					int inner = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
					stand(0, at(-4.5f, 1f, -1f), EAR_PIECE, 2, new Vector3f(-0.08f, 0f, -0.15f), k -> ear(hat, inner, k));
					stand(2, at(4.5f, 1f, -1f), EAR_PIECE, 2, new Vector3f(0.08f, 0f, -0.15f), k -> ear(hat, inner, k));
				}
				case MORTARBOARD -> hang(0, at(12f, 3.75f, 0f), 3f, 2, k -> tassel(hat, k));
				case TOPHAT -> stand(0, at(8.5f, 2.5f, 3f), 4f, 2, new Vector3f(0.2f, 0f, 0.15f), k -> plume(hat, k));
				case COWBOY -> {
					hang(0, at(-9.5f, -1f, 0f), 9f, 1, k -> stampedeString(hat));
					hang(1, at(9.5f, -1f, 0f), 9f, 1, k -> stampedeString(hat));
				}
				case HEADPHONES -> hang(0, at(-10f, -11f, 0f), 5f, 2, k -> cable(hat, k));
				case SOMBRERO -> {
					for (int i = 0; i < SOMBRERO_POMPOMS.length; i++) {
						hang(i, at(SOMBRERO_POMPOMS[i][0], -1f, SOMBRERO_POMPOMS[i][1]), 4f, 1, k -> sombreroPompom(hat));
					}
				}
				case BERET -> stand(0, at(1f, 3.75f, 0f), 1.5f, 1, new Vector3f(), POMPOM_STIFF, damping, k -> beretStalk(hat));
				case TYROLEAN -> {
					stand(0, at(-8.5f, 1f, 4f), 3f, 2, new Vector3f(-0.1f, 0f, 0.2f), k -> chamoisBrush(hat, k));
					stand(2, at(-8.75f, 1f, 1.5f), 4f, 2, new Vector3f(-0.1f, 0f, 0.35f), k -> feather(hat.accent, 0.8f, k));
				}
				case CAPTAIN -> {
					hang(0, at(-8.25f, -1.6f, -10f), 2.75f, 1, k -> captainTassel(hat));
					hang(1, at(8.25f, -1.6f, -10f), 2.75f, 1, k -> captainTassel(hat));
				}
				case USHANKA -> {
					hang(0, at(-9.9f, -1f, 0f), 7f, 1, k -> earFlapTied(hat));
					hang(1, at(9.9f, -1f, 0f), 7f, 1, k -> earFlapTied(hat));
				}
				case TIARA -> hang(0, at(0f, 1.5f, -10f), 3.6f, 1, k -> tiaraDrop(hat));
				case NINJA -> {
					hang(0, at(-1f, -4f, 10.75f), 4f, 3, damping, 0.6f, k -> bandTail(hat, k));
					hang(3, at(1f, -4f, 10.75f), 4f, 3, damping, 0.6f, k -> bandTail(hat, k + 1));
				}
				// Long and soft: they flop about, a paw at the end of each.
				case AXOLOTL -> {
					hang(0, at(-10.25f, -4f, -1f), 5f, 3, damping + STRAND_DAMPING, 0.6f, k -> axolotlFlap(hat, k));
					hang(3, at(10.25f, -4f, -1f), 5f, 3, damping + STRAND_DAMPING, 0.6f, k -> axolotlFlap(hat, k));
				}
				// The ears twitch; the bushy tail hangs down behind and swings heavily.
				case FOX -> {
					stand(0, at(-4.75f, 6.5f, -1f), FOX_EAR, 1, new Vector3f(-0.12f, 0f, 0.05f), k -> foxEar(hat));
					stand(1, at(4.75f, 6.5f, -1f), FOX_EAR, 1, new Vector3f(0.12f, 0f, 0.05f), k -> foxEar(hat));
					hang(2, at(0f, 0f, 9.5f), 4.5f, 3, damping + TAIL_DAMPING, 0.55f, k -> foxTail(hat, k));
				}
				case PARTY -> stand(0, at(PARTY_X - PARTY_LEAN * PARTY_LAYERS, 1.5f * PARTY_LAYERS, 0f), 1f, 1, new Vector3f(), POMPOM_STIFF,
						damping, k -> partyPompom(hat));
				case SANTA -> hang(0, at(SANTA_LEAN_X * (SANTA_LAYERS - 1) + 3.5f, 1.5f + 1.5f * SANTA_LAYERS, 0f),
						4f, 2, damping + STRAND_DAMPING, 0.6f, k -> santaTip(hat, k));
				case REINDEER -> hang(0, at(-9.75f, 0f, -1f), 1.5f, 1, k -> jingleBell(hat));
				// The tip curls back, its bell bobbing at the end.
				case ELF -> stand(0, at(0f, ELF_TOP, ELF_LEAN * (ELF_LAYERS - 1)), 3f, 3, new Vector3f(0f, 0f, 0.35f), k -> elfTip(hat, k));
				case BEANIE -> {
					if (hat == Hat.BEANIE_BEE) {
						stand(0, at(8f, 1f, -4f), 7f, 1, new Vector3f(0.7f, 0f, -0.3f), k -> bee());
					} else {
						// A pompom bounces: it follows its pendulum more than other standing parts.
						stand(0, at(0f, 6.5f, 0f), 1.5f, 1, new Vector3f(), POMPOM_STIFF, damping, k -> pompom(hat));
					}
				}
				default -> {
				}
			}
		}

		/** A point on the head in voxels, y up from its top. */
		private static Vector3f at(float x, float yUp, float z) {
			return new Vector3f(x * V, HEAD_TOP - yUp * V, z * V);
		}

		/** {@code count} pieces of {@code length} voxels hanging one from the next. Pieces are given y down. */
		void hang(int first, Vector3f anchor, float length, int count, IntFunction<List<Box>> piece) {
			hang(first, anchor, length, count, damping, 1f, piece);
		}

		/**
		 * As above, losing {@code damping} speed per second; each piece bends from the one above only by {@code bend} of
		 * what its pendulum would (1: freely, less: stiffer).
		 */
		void hang(int first, Vector3f anchor, float length, int count, double damping, float bend, IntFunction<List<Box>> piece) {
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
		void stand(int first, Vector3f anchor, float length, int count, Vector3f bend, IntFunction<List<Box>> piece) {
			stand(first, anchor, length, count, bend, STIFF, damping, piece);
		}

		/** As above, following the pendulums by {@code stiff} (less: stiffer) and losing {@code damping} speed per second. */
		void stand(int first, Vector3f anchor, float length, int count, Vector3f bend, float stiff, double damping,
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

		/** Boxes turning round {@code anchor} (a propeller): faster the faster the wearer moves. */
		private void spin(Vector3f anchor, List<Box> boxes) {
			double now = System.nanoTime() / 1e9;
			if (SPINS.size() > 64) {
				SPINS.clear();
			}
			float[] turn = SPINS.computeIfAbsent(entityId, id -> new float[]{0f, (float) (now % 1e4)});
			float seconds = (float) Math.clamp(now % 1e4 - turn[1], 0.0, 0.1);
			turn[1] = (float) (now % 1e4);
			turn[0] = (turn[0] + seconds * (1.5f + Math.min(speed, 12f) * 1.5f) * Mth.TWO_PI) % Mth.TWO_PI;
			place(anchor, new Quaternionf().rotationY(turn[0]), boxes);
		}

		private void place(Vector3f anchor, Quaternionf rotation, List<Box> boxes) {
			pose.pushPose();
			pose.translate(anchor.x, anchor.y, anchor.z);
			pose.mulPose(new Matrix4f().rotation(rotation));
			submit(pose, collector, boxes, null, light);
			pose.popPose();
		}
	}

	static void submit(PoseStack pose, SubmitNodeCollector collector, List<Box> boxes, HelmetLook helmet, int light) {
		// Textured like the companions, a pattern pixel a voxel big; each box in its own material or the piece's.
		CompanionTexture.Material piece = material;
		collector.submitCustomGeometry(pose, CompanionTexture.type(), (p, vc) -> {
			for (Box b : boxes) {
				CompanionTexture.box(vc, p, Math.min(b.x0, b.x1), Math.min(b.y0, b.y1), Math.min(b.z0, b.z1),
						Math.max(b.x0, b.x1), Math.max(b.y0, b.y1), Math.max(b.z0, b.z1), b.color, b.glow ? FULL_BRIGHT : light,
						b.material != null ? b.material : piece, 0, 2 * V, false);
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
		// A dome over the head in steps.
		ring(out, null, 2, 3f, 6f, 9f, (i, row) -> hat.main[i % 5 == 0 ? 1 : 0]);
		out.add(up(-6f, 1f, -6f, 6f, 3f, 6f, hat.main[0], false));
		out.add(up(-7f, 3f, -7f, 7f, 4f, 7f, hat.main[0], false));
		out.add(up(-4.5f, 4f, -4.5f, 4.5f, 4.75f, 4.5f, hat.main[0], false));
		// Seams over the top, front to back.
		for (float x : new float[]{-3f, 0f, 3f}) {
			out.add(up(x - 0.3f, 4f, -7f, x + 0.3f, 4.2f, 7f, hat.main[1], false));
			out.add(up(x - 0.3f, 4.75f, -4.5f, x + 0.3f, 4.95f, 4.5f, hat.main[1], false));
		}
		ring(out, helmet, 2, -0.5f, 9.5f, 10.25f, (i, row) -> hat.main[1]);
		int lens = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
		for (int s = -1; s <= 1; s += 2) {
			float x0 = s < 0 ? -6f : 1f;
			out.add(up(x0, -2f, -11.5f, x0 + 5f, 1.5f, -10.25f, hat.second, false));
			out.add(up(x0 + 0.75f, -1.25f, -11.75f, x0 + 4.25f, 0.75f, -11.5f, lens, false));
			// A glint on the glass.
			out.add(up(x0 + 1f, 0f, -11.85f, x0 + 1.75f, 0.5f, -11.75f, 0xFFF4F8FF, false));
			// The strap's buckle at the side.
			float bx = s < 0 ? -10.6f : 10.25f;
			out.add(up(bx, -2.75f, -1.25f, bx + 0.35f, -0.25f, 1.25f, hat.second, false));
			out.add(up(s < 0 ? -10.65f : 10.6f, -2f, -0.5f, s < 0 ? -10.6f : 10.65f, -1f, 0.5f, ARGB.scaleRGB(hat.second, 0.6f), false));
		}
		out.add(up(-1f, -1f, -11f, 1f, 0f, -10.25f, hat.second, false));
	}

	private static List<Box> earFlap(Hat hat) {
		return List.of(raw(-0.75f, 0f, -3f, 0.75f, 6f, 3f, hat.main[0]), raw(-0.5f, 6f, -2.5f, 0.5f, 6.75f, 2.5f, hat.main[2]));
	}

	/** A miner's hard hat: a dome with a ridge, its brim showing the helmet, and a lamp in front shining in the trim's colour. */
	private static void minerHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 1, 0f, 9f, 11.5f, (i, row) -> hat.main[1]);
		// A tall dome in steps, with a ridge from front to back over it.
		ring(out, null, 4, 4f, 8f, 10f, (i, row) -> hat.main[0]);
		ring(out, null, 2, 6f, 6f, 9f, (i, row) -> hat.main[0]);
		out.add(up(-6f, 4f, -6f, 6f, 7f, 6f, hat.main[0], false));
		out.add(up(-4f, 7f, -4f, 4f, 7.75f, 4f, hat.main[0], false));
		out.add(up(-1f, 4f, -10.5f, 1f, 6.5f, 10.5f, hat.main[1], false));
		out.add(up(-1f, 6.5f, -7f, 1f, 7.75f, 7f, hat.main[1], false));
		out.add(up(-1f, 7.75f, -4.5f, 1f, 8.5f, 4.5f, hat.main[1], false));
		// A lip round the brim's edge.
		ring(out, null, 1, 0.25f, 11.5f, 12f, (i, row) -> lighter(hat.main[1]));
		// Rivets round the dome (the front ones either side of the lamp).
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			for (float x : side == 0 ? new float[]{-6.5f, 6.5f} : new float[]{-5f, 0f, 5f}) {
				part.add(up(x - 0.4f, 1.25f, -10.25f, x + 0.4f, 2.05f, -10f, lighter(hat.main[0]), false));
			}
			turn(part, side, out);
		}
		int glow = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
		out.add(up(-2.5f, 0.5f, -12f, 2.5f, 4f, -10f, hat.second, false));
		out.add(up(-1.75f, 1.25f, -12.3f, 1.75f, 3.25f, -12f, glow, false).glowing());
		// A metal ring round the lamp's glass, and the clip holding it to the dome.
		int metal = 0xFFA0A0A8;
		out.add(up(-2.25f, 3.25f, -12.35f, 2.25f, 3.75f, -12f, metal, false));
		out.add(up(-2.25f, 0.75f, -12.35f, 2.25f, 1.25f, -12f, metal, false));
		out.add(up(-2.25f, 1.25f, -12.35f, -1.75f, 3.25f, -12f, metal, false));
		out.add(up(1.75f, 1.25f, -12.35f, 2.25f, 3.25f, -12f, metal, false));
		out.add(up(-1f, 4f, -11f, 1f, 4.5f, -9.75f, hat.second, false));
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
		out.add(up(-8.5f, 2f, -8.5f, 8.5f, 7f, 8.5f, felt, false));
		out.add(up(-7f, 7f, -7f, 7f, 8.5f, 7f, felt, false));
		out.add(up(-5f, 8.5f, -5f, 5f, 9.25f, 5f, felt, false));
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
		// A skull and bones on the front point.
		int bone = 0xFFF0EEE6;
		out.add(up(-2f, 0.75f, -15.3f, 2f, 1.25f, -15.2f, bone, false));
		out.add(up(-1f, 1.5f, -15.35f, 1f, 3.25f, -15.2f, bone, false));
		out.add(up(-0.75f, 2.25f, -15.4f, -0.25f, 2.75f, -15.35f, 0xFF1A1A1A, false));
		out.add(up(0.25f, 2.25f, -15.4f, 0.75f, 2.75f, -15.35f, 0xFF1A1A1A, false));
	}

	/** A feather, {@code size} times as broad as the big one. */
	private static List<Box> feather(int color, float size, int k) {
		float half = (k == 0 ? 1.6f : 1f) * size;
		return List.of(raw(-half, -4f, -0.3f, half, 0f, 0.3f, color), raw(-0.15f, -4f, -0.4f, 0.15f, 0f, 0.4f, ARGB.scaleRGB(color, 0.8f)));
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
		ring(out, null, 5, 7f, 7f, 9f, weave);
		out.add(up(-7f, 6f, -7f, 7f, 7f, 7f, hat.main[0], false));
		// A smaller layer on top rounds the crown off.
		out.add(up(-5.5f, 7f, -5.5f, 5.5f, 7.75f, 5.5f, hat.main[1], false));
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
		// A tall crown, a little narrower at the top.
		IntBinaryOperator panels = (i, row) -> i % 4 == 0 ? hat.main[1] : hat.main[0];
		ring(out, null, 3, 4f, 8f, 9.5f, panels);
		ring(out, null, 2, 6f, 7.5f, 9f, panels);
		out.add(up(-7.5f, 3f, -7.5f, 7.5f, 6.5f, 7.5f, hat.main[0], false));
		out.add(up(-5.5f, 6.5f, -5.5f, 5.5f, 7.25f, 5.5f, hat.main[0], false));
		// Rows of stitching round the brim, and two fishing flies hooked into the band.
		int stitch = lighter(hat.main[1]);
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			part.add(up(-11f, -1f, -11.15f, 11f, -0.9f, -10.85f, stitch, false));
			part.add(up(-12.75f, -2f, -12.9f, 12.75f, -1.9f, -12.6f, stitch, false));
			turn(part, side, out);
		}
		out.add(up(5f, 0f, -10.1f, 6f, 0.75f, -9.75f, 0xFFE8C040, false));
		out.add(up(5.25f, 0.75f, -10.05f, 5.75f, 1.5f, -9.85f, 0xFFC83A3A, false));
		out.add(up(-6.5f, 0f, -10.1f, -5.5f, 0.75f, -9.75f, 0xFF3A6AC8, false));
		out.add(up(-6.25f, 0.75f, -10.05f, -5.75f, 1.5f, -9.85f, 0xFFF4F4F0, false));
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
		// The puff swells out over the band, pleated all round, and rounds off on top.
		List<Box> out = new ArrayList<>();
		float[][] layers = {{-1f, 0f, 9.5f}, {-3f, -1f, 10.75f}, {-5f, -3f, 11.25f}, {-6f, -5f, 10.5f}};
		for (float[] l : layers) {
			pleats(out, l[0], l[1], l[2], hat.main[0], hat.main[1]);
		}
		float[][] dome = {{-6.75f, -6f, 9f}, {-7.25f, -6.75f, 7f}, {-7.6f, -7.25f, 4.5f}};
		for (float[] d : dome) {
			out.add(raw(-d[2] + 1.5f, d[0], -d[2], d[2] - 1.5f, d[1], d[2], hat.main[0]));
			out.add(raw(-d[2], d[0], -d[2] + 1.5f, d[2], d[1], d[2] - 1.5f, hat.main[0]));
		}
		return out;
	}

	/** A layer of the toque's puff (y down): ribs standing out between grooves set back, round a square of half width r. */
	private static void pleats(List<Box> out, float y0, float y1, float r, int color, int groove) {
		float e = r - 1.5f;
		out.add(raw(-e, y0, -e, e, y1, e, color));
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			part.add(raw(-1.25f, y0, -r, 1.25f, y1, -e, color));
			boolean rib = false;
			for (float x = 1.25f; x < e; rib = !rib) {
				float x1 = Math.min(e, x + (rib ? 2.5f : 1f));
				float z0 = rib ? -r : -r + 0.8f;
				part.add(raw(x, y0, z0, x1, y1, -e, rib ? color : groove));
				part.add(raw(-x1, y0, z0, -x, y1, -e, rib ? color : groove));
				x = x1;
			}
			part.add(raw(e, y0, -r + 0.5f, r - 0.5f, y1, -e, groove));
			turn(part, side, out);
		}
	}

	/**
	 * A knitted beanie: a ribbed body with a stripe showing the helmet above the folded cuff, and a patch in front
	 * framed in the trim's colour. The bee beanie has stitched daisies and a bee on its patch (and one buzzing round
	 * it); the others a pompom.
	 */
	private static void beanieHat(List<Box> out, Hat hat, HelmetLook helmet) {
		IntBinaryOperator ribs = (i, row) -> i % 2 == 0 ? hat.main[0] : hat.main[1];
		ring(out, null, 4, -1f, 9f, 10.5f, (i, row) -> i % 2 == 0 ? hat.main[2] : hat.main[1]);
		// A knitted-in stripe just above the cuff shows the helmet (plain knit without one).
		ring(out, helmet, 1, 0f, 8f, 9.75f, ribs);
		ring(out, null, 3, 3f, 8f, 9.75f, ribs);
		ring(out, null, 2, 5f, 6f, 8.75f, ribs);
		out.add(up(-6f, 3f, -6f, 6f, 5f, 6f, hat.main[0], false));
		out.add(up(-7f, 5f, -7f, 7f, 6f, 7f, hat.main[0], false));
		out.add(up(-4.5f, 6f, -4.5f, 4.5f, 6.5f, 4.5f, hat.main[0], false));
		// The patch on the cuff.
		int frame = helmet != null && helmet.trim() != 0 ? helmet.trim() : ARGB.scaleRGB(hat.accent, 0.8f);
		out.add(up(-3.25f, -4.75f, -10.8f, 3.25f, -1.25f, -10.5f, frame, false));
		out.add(up(-2.75f, -4.25f, -10.9f, 2.75f, -1.75f, -10.8f, hat.accent, false));
		if (hat != Hat.BEANIE_BEE) {
			return;
		}
		// A bee on the patch, facing left: body, stripes, eye and a wing.
		int yellow = 0xFFF6CE3A;
		int brown = 0xFF4A2A14;
		out.add(up(-2f, -3.75f, -11f, 1.5f, -2.25f, -10.9f, yellow, false));
		out.add(up(-0.75f, -3.75f, -11.05f, -0.25f, -2.25f, -11f, brown, false));
		out.add(up(0.5f, -3.75f, -11.05f, 1f, -2.25f, -11f, brown, false));
		out.add(up(-2f, -3f, -11.05f, -1.5f, -2.5f, -11f, 0xFFF4F4F0, false));
		out.add(up(-1.75f, -2.75f, -11.1f, -1.5f, -2.5f, -11.05f, 0xFF1A1A1A, false));
		out.add(up(-0.75f, -2.25f, -11f, 0.75f, -1.75f, -10.9f, 0xFFE8F0FF, false));
		// Stitched daisies round the body.
		float[][] daisies = {{-5f, 1.75f, -9.75f}, {3.5f, 2.25f, -9.75f}, {-1f, 4f, -8.75f}};
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			for (float[] d : daisies) {
				float x = d[0] + (side % 2 == 0 ? 0f : 1.5f);
				float z = d[2] - 0.2f;
				part.add(up(x - 1.25f, d[1] - 0.4f, z, x + 1.25f, d[1] + 0.4f, d[2], hat.second, false));
				part.add(up(x - 0.4f, d[1] - 1.25f, z, x + 0.4f, d[1] + 1.25f, d[2], hat.second, false));
				part.add(up(x - 0.4f, d[1] - 0.4f, z - 0.05f, x + 0.4f, d[1] + 0.4f, d[2], hat.third, false));
			}
			turn(part, side, out);
		}
	}

	private static List<Box> pompom(Hat hat) {
		int shade = ARGB.scaleRGB(hat.second, 0.88f);
		return List.of(raw(-0.5f, -1.5f, -0.5f, 0.5f, 0f, 0.5f, hat.second), raw(-2.5f, -6f, -2.5f, 2.5f, -1.5f, 2.5f, hat.second),
				raw(-3f, -5.25f, -2f, 3f, -2.25f, 2f, shade), raw(-2f, -5.25f, -3f, 2f, -2.25f, 3f, shade),
				raw(-1.75f, -6.5f, -1.75f, 1.75f, -6f, 1.75f, hat.second));
	}

	/** The bee buzzing beside the bee beanie, at the end of an invisible stalk: it bobs and beats its wings. */
	private static List<Box> bee() {
		double seconds = System.nanoTime() % 1_000_000_000_000L / 1e9;
		float y = -7f + (float) Math.sin(seconds * 3.0) * 0.7f;
		float lift = (float) Math.sin(seconds * 40.0) * 0.5f;
		int yellow = 0xFFF6CE3A;
		int brown = 0xFF4A2A14;
		List<Box> out = new ArrayList<>();
		out.add(raw(-1.25f, y - 1f, -1.75f, 1.25f, y + 1f, 1.75f, yellow));
		out.add(raw(-1.3f, y - 1.05f, -0.5f, 1.3f, y + 1.05f, 0f, brown));
		out.add(raw(-1.3f, y - 1.05f, 0.75f, 1.3f, y + 1.05f, 1.25f, brown));
		out.add(raw(-0.25f, y - 0.25f, 1.75f, 0.25f, y + 0.25f, 2.25f, brown));
		out.add(raw(-1f, y - 0.5f, -1.85f, -0.4f, y + 0.1f, -1.75f, 0xFF1A1A1A));
		out.add(raw(0.4f, y - 0.5f, -1.85f, 1f, y + 0.1f, -1.75f, 0xFF1A1A1A));
		out.add(raw(-2.75f, y - 1.5f + lift, -0.75f, -0.25f, y - 1f + lift, 0.75f, 0xFFE8F0FF));
		out.add(raw(0.25f, y - 1.5f + lift, -0.75f, 2.75f, y - 1f + lift, 0.75f, 0xFFE8F0FF));
		return out;
	}

	/**
	 * A baseball cap: six panels with a button on top, a curved visor whose edge shows the helmet, a logo in front in the
	 * trim's colour (a creeper's face on the creeper cap) and a strap end dangling from the closure behind.
	 */
	private static void capHat(List<Box> out, Hat hat, HelmetLook helmet) {
		float b = CAP_BASE;
		IntBinaryOperator panels = (i, row) -> i % 5 == 0 ? hat.main[1] : hat.main[0];
		ring(out, null, 5, b + 5f, 8f, 9.5f, panels);
		ring(out, null, 2, b + 7f, 6f, 8.75f, panels);
		out.add(up(-6f, b + 3f, -6f, 6f, b + 7f, 6f, hat.main[0], false));
		out.add(up(-6.5f, b + 7f, -6.5f, 6.5f, b + 8f, 6.5f, hat.main[0], false));
		out.add(up(-4f, b + 8f, -4f, 4f, b + 8.5f, 4f, hat.main[0], false));
		out.add(up(-1f, b + 8.5f, -1f, 1f, b + 9.25f, 1f, hat.main[1], false));
		out.add(up(-3f, b, 9.5f, 3f, b + 1f, 9.75f, hat.main[1], false));
		// The visor, row by row from the forehead to its rounded tip, straight out; its edge shows the helmet.
		float[][] rows = {{-10.5f, 8.5f}, {-11.5f, 8.5f}, {-12.5f, 8.25f}, {-13.5f, 7.75f}, {-14.5f, 7f}, {-15.5f, 5.75f},
				{-16.25f, 3.75f}};
		float z1 = -9.5f;
		for (int k = 0; k < rows.length; k++) {
			float z0 = rows[k][0];
			float half = rows[k][1];
			float dip = b;
			int edge = helmet != null ? helmet.tile(k, 0) : hat.main[1];
			out.add(up(-half, dip, z0, half, dip + 0.75f, z1, edge, helmet != null));
			float inner = k == rows.length - 1 ? 0f : 1f;
			if (half - 1f > 0f) {
				out.add(up(-half + 1f, dip + 0.75f, z0 + inner, half - 1f, dip + 0.85f, z1, hat.main[0], false));
				// Stitching along the edge, and the underside in its own colour.
				for (int s = -1; s <= 1; s += 2) {
					float x = s * (half - 2f);
					out.add(up(x - 0.2f, dip + 0.85f, z0, x + 0.2f, dip + 0.95f, z1, lighter(hat.main[0]), false));
				}
				out.add(up(-half + 0.5f, dip - 0.1f, z0 + 0.5f, half - 0.5f, dip, z1, CAP_UNDER, false));
			}
			z1 = z0;
		}
		// Eyelets near the top of the panels.
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			for (float x : new float[]{-5f, 5f}) {
				part.add(up(x - 0.4f, b + 4f, -9.6f, x + 0.4f, b + 4.8f, -9.5f, ARGB.scaleRGB(hat.main[1], 0.6f), false));
			}
			turn(part, side, out);
		}
		// The logo on the front panels.
		int trim = helmet != null && helmet.trim() != 0 ? helmet.trim() : 0;
		if (hat == Hat.CAP_CREEPER) {
			out.add(up(-2f, b + 1f, -9.75f, 2f, b + 5f, -9.5f, trim != 0 ? trim : hat.accent, false));
			// A creeper's face, eight pixels of half a voxel: eyes, nose, mouth.
			int[][] face = {{1, 1, 2, 2}, {5, 1, 6, 2}, {3, 3, 4, 4}, {2, 4, 5, 5}, {2, 6, 2, 6}, {5, 6, 5, 6}};
			for (int[] f : face) {
				float x0 = -2f + f[0] * 0.5f;
				float x1 = -2f + (f[2] + 1) * 0.5f;
				float y1 = b + 5f - f[1] * 0.5f;
				float y0 = b + 5f - (f[3] + 1) * 0.5f;
				out.add(up(x0, y0, -9.85f, x1, y1, -9.75f, hat.second, false));
			}
		} else {
			int logo = trim != 0 ? trim : hat.accent;
			out.add(up(-1.75f, b + 1.5f, -9.75f, 1.75f, b + 4.5f, -9.5f, logo, false));
			out.add(up(-0.75f, b + 2.5f, -9.85f, 0.75f, b + 3.5f, -9.75f, hat.main[0], false));
		}
	}

	private static List<Box> strapEnd(Hat hat) {
		return List.of(raw(-0.6f, 0f, -0.15f, 0.6f, 3f, 0.15f, hat.main[1]), raw(-0.7f, 3f, -0.2f, 0.7f, 3.5f, 0.2f, 0xFFC0C0C8));
	}

	/** A long striped nightcap: a rim showing the helmet, its tip flopped over to one side, hanging down with a pompom. */
	private static void nightcapHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 2, -1f, 9f, 10f, (i, row) -> hat.main[2]);
		IntBinaryOperator stripes = (i, row) -> row % 2 == 0 ? hat.main[0] : hat.main[1];
		ring(out, null, 3, 2f, 8f, 9.5f, stripes);
		ring(out, null, 2, 4f, 6f, 8.5f, stripes);
		out.add(up(-6f, 2f, -6f, 6f, 5f, 6f, hat.main[0], false));
		// Rising to the right, where the tip flops over.
		out.add(up(-3f, 5f, -4f, 7f, 6.5f, 4f, hat.main[1], false));
		out.add(up(3f, 6.5f, -3f, 9f, 7.5f, 3f, hat.main[0], false));
		out.add(up(7f, 3f, -3f, 11f, 7f, 3f, hat.main[1], false));
	}

	private static List<Box> nightcapTip(Hat hat, int k) {
		float half = 2.25f - 0.5f * k;
		// Striped on like the cap: two stripes a piece.
		List<Box> out = new ArrayList<>();
		out.add(raw(-half, 0f, -half, half, 2f, half, hat.main[1]));
		out.add(raw(-half, 2f, -half, half, 4f, half, hat.main[0]));
		if (k == 2) {
			// A fluffy pompom: a ball with tufts on every side.
			out.add(raw(-1.75f, 4f, -1.75f, 1.75f, 7.5f, 1.75f, hat.accent));
			int tuft = ARGB.scaleRGB(hat.accent, 0.92f);
			out.add(raw(-2.25f, 4.75f, -1f, 2.25f, 6.75f, 1f, tuft));
			out.add(raw(-1f, 4.75f, -2.25f, 1f, 6.75f, 2.25f, tuft));
			out.add(raw(-1f, 7.5f, -1f, 1f, 8f, 1f, tuft));
		}
		return out;
	}

	private static List<Box> partyPompom(Hat hat) {
		int shade = ARGB.scaleRGB(hat.second, 0.88f);
		return List.of(raw(-1.5f, -3.5f, -1.5f, 1.5f, -0.5f, 1.5f, hat.second), raw(-1.75f, -3f, -1f, 1.75f, -1f, 1f, shade),
				raw(-1f, -3f, -1.75f, 1f, -1f, 1.75f, shade), raw(-0.4f, -0.5f, -0.4f, 0.4f, 0f, 0.4f, hat.second));
	}

	/** A propeller beanie: coloured segments, a short visor whose edge shows the helmet, a propeller on top that spins. */
	private static void propellerHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, null, 4, 2f, 8f, 9.5f, (i, row) -> hat.main[(i / 5) % 3]);
		ring(out, null, 2, 4f, 6f, 8.75f, (i, row) -> hat.main[(i / 5 + 1) % 3]);
		out.add(up(-6f, 2f, -6f, 6f, 4.5f, 6f, hat.main[2], false));
		out.add(up(-4f, 4.5f, -4f, 4f, 5f, 4f, hat.main[2], false));
		out.add(up(-1f, 5f, -1f, 1f, 5.5f, 1f, hat.accent, false));
		out.add(up(-0.4f, 5.5f, -0.4f, 0.4f, 6.5f, 0.4f, 0xFFB0B0B8, false));
		int edge = helmet != null ? helmet.tile(0, 0) : hat.main[1];
		out.add(up(-6f, -2f, -12.5f, 6f, -1.25f, -9.5f, edge, helmet != null));
		out.add(up(-5f, -1.25f, -11.5f, 5f, -1.15f, -9.5f, hat.main[0], false));
	}

	/** The propeller's blades round its hub, drawn turned by the caller. */
	private static List<Box> blades(Hat hat) {
		// Each blade pitched: its leading edge a step higher than its trailing one.
		return List.of(raw(-8f, -1f, -1.25f, -0.75f, -0.5f, 0f, hat.main[1]), raw(-8f, -0.5f, 0f, -0.75f, 0f, 1.25f, hat.main[1]),
				raw(0.75f, -1f, 0f, 8f, -0.5f, 1.25f, hat.main[2]), raw(0.75f, -0.5f, -1.25f, 8f, 0f, 0f, hat.main[2]),
				raw(-1f, -1f, -1f, 1f, 0.25f, 1f, hat.accent), raw(-0.6f, -1.5f, -0.6f, 0.6f, -1f, 0.6f, lighter(hat.accent)));
	}

	/** A headband showing the helmet with two long bunny ears that flop. */
	private static void bunnyHat(List<Box> out, Hat hat, HelmetLook helmet) {
		headband(out, helmet, hat.main[2], -1f);
	}

	/** A band over the top of the head from ear to ear, {@code z} its front edge; the helmet's look if one is worn. */
	private static void headband(List<Box> out, HelmetLook helmet, int own, float z) {
		boolean shows = helmet != null;
		// From the head's surface out past its hat layer, so it sits close with or without one.
		out.add(up(-9.25f, 0f, z - 1.5f, 9.25f, 1.25f, z + 1.5f, shows ? helmet.tile(0, 0) : own, shows));
		out.add(up(-9.25f, -6f, z - 1.5f, -8f, 1.25f, z + 1.5f, shows ? helmet.tile(1, 1) : own, shows));
		out.add(up(8f, -6f, z - 1.5f, 9.25f, 1.25f, z + 1.5f, shows ? helmet.tile(2, 1) : own, shows));
	}

	private static List<Box> ear(Hat hat, int inner, int k) {
		float half = k == 0 ? EAR_HALF : EAR_HALF - 0.4f;
		List<Box> out = new ArrayList<>();
		out.add(raw(-half, -EAR_PIECE, -0.8f, half, 0f, 0.8f, hat.main[0]));
		// The inside runs on from one piece into the next, with fur round it only at the base and the tip.
		float tip = k == 0 ? -EAR_PIECE : -EAR_PIECE + 0.75f;
		out.add(raw(-half + 0.8f, tip, -0.9f, half - 0.8f, k == 0 ? -0.75f : 0f, -0.8f, inner));
		if (k == 1) {
			out.add(raw(-half + 0.6f, -EAR_PIECE - 1f, -0.7f, half - 0.6f, -EAR_PIECE, 0.7f, hat.main[0]));
		}
		return out;
	}

	/** A mortarboard: a cap whose band shows the helmet, a flat square board, a button in the trim's colour and a cord. */
	private static void mortarboardHat(List<Box> out, Hat hat, HelmetLook helmet) {
		// A round cap swelling out under the board.
		roundRing(out, helmet, 2, 1f, 8.5f, 9.75f, (i, row) -> hat.main[1]);
		round(out, 0f, 1f, 2f, 10f, 2f, hat.main[0]);
		round(out, 0f, 2f, 2.5f, 9f, 2f, hat.main[0]);
		// The board, thick, its shadow under the edge, its edge bound.
		out.add(up(-12.5f, 2.25f, -12.5f, 12.5f, 2.5f, 12.5f, ARGB.scaleRGB(hat.main[0], 0.7f), false));
		out.add(up(-13f, 2.5f, -13f, 13f, 3.75f, 13f, hat.main[0], false));
		for (int side = 0; side < 4; side++) {
			turn(List.of(up(-13.1f, 2.5f, -13.1f, 12.75f, 3.9f, -12.75f, hat.main[1], false)), side, out);
		}
		int button = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
		out.add(up(-1f, 3.75f, -1f, 1f, 4.25f, 1f, button, false));
		out.add(up(0f, 3.75f, -0.25f, 12f, 4f, 0.25f, hat.accent, false));
	}

	private static List<Box> tassel(Hat hat, int k) {
		Box cord = raw(-0.3f, 0f, -0.3f, 0.3f, 3f, 0.3f, hat.accent);
		if (k == 0) {
			return List.of(cord);
		}
		List<Box> out = new ArrayList<>(List.of(cord, raw(-1.25f, 2.75f, -1.25f, 1.25f, 3.5f, 1.25f, hat.second),
				raw(-1f, 3.5f, -1f, 1f, 5f, 1f, hat.accent)));
		// The fringe: loose threads of slightly different lengths.
		float[] lengths = {7f, 7.5f, 6.75f, 7.25f};
		int t = 0;
		for (float dx : new float[]{-0.7f, 0.2f}) {
			for (float dz : new float[]{-0.7f, 0.2f}) {
				out.add(raw(dx, 5f, dz, dx + 0.5f, lengths[t++], dz + 0.5f, t % 2 == 0 ? hat.accent : ARGB.scaleRGB(hat.accent, 0.85f)));
			}
		}
		return out;
	}

	/** A top hat: a brim curled at the sides, a band showing the helmet with a pin in the trim's colour, a feather. */
	private static void tophatHat(List<Box> out, Hat hat, HelmetLook helmet) {
		int brim = hat.main[1];
		// The brim: round, dipping a little in front and behind, rolled up at the sides in steps.
		round(out, 0f, -1f, 0f, 10.5f, 2f, brim);
		for (int s = -1; s <= 1; s += 2) {
			out.add(up(-7.5f, -1.6f, s < 0 ? -12.25f : 10.5f, 7.5f, -0.6f, s < 0 ? -10.5f : 12.25f, brim, false));
			out.add(up(s < 0 ? -11.75f : 10.5f, -0.5f, -9f, s < 0 ? -10.5f : 11.75f, 0.75f, 9f, brim, false));
			out.add(up(s < 0 ? -12.75f : 11.5f, 0.75f, -8f, s < 0 ? -11.5f : 12.75f, 1.75f, 8f, brim, false));
			out.add(up(s < 0 ? -13.25f : 12.5f, 1.75f, -6.5f, s < 0 ? -12.5f : 13.25f, 2.5f, 6.5f, lighter(brim), false));
		}
		// A round crown, a little waisted, its top edge standing out.
		roundRing(out, helmet, 2, 2.5f, 8.75f, 9.75f, (i, row) -> hat.accent);
		float[][] crown = {{0f, 2.5f, 9.25f}, {2.5f, 5f, 8.75f}, {5f, 8f, 8.5f}, {8f, 10.5f, 8.85f}, {10.5f, 12f, 9.25f}};
		for (float[] c : crown) {
			round(out, 0f, c[0], c[1], c[2], 2f, hat.main[0]);
		}
		round(out, 0f, 12f, 12.5f, 9.5f, 2f, lighter(hat.main[0]));
		int pin = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.second;
		out.add(up(5f, 0.75f, -10f, 6.5f, 2.25f, -9.75f, pin, false));
		// A bow on the band at the left.
		out.add(up(-10.25f, 0.75f, -2.5f, -9.75f, 2.25f, -0.5f, hat.accent, false));
		out.add(up(-10.25f, 0.75f, 0.5f, -9.75f, 2.25f, 2.5f, hat.accent, false));
		out.add(up(-10.4f, 1f, -0.5f, -9.75f, 2f, 0.5f, ARGB.scaleRGB(hat.accent, 0.75f), false));
	}

	private static List<Box> plume(Hat hat, int k) {
		float half = k == 0 ? 1.5f : 1f;
		return List.of(raw(-0.3f, -4f, -half, 0.3f, 0f, half, hat.second), raw(-0.4f, -4f, -0.15f, 0.4f, 0f, 0.15f,
				ARGB.scaleRGB(hat.second, 0.8f)));
	}

	/**
	 * A cowboy hat: a wide brim curled up at the sides, a creased crown, a band showing the helmet with a concho in the
	 * trim's colour, and a stampede string hanging loose under the chin.
	 */
	private static void cowboyHat(List<Box> out, Hat hat, HelmetLook helmet) {
		int brim = hat.main[1];
		// The brim: dipping in front and behind, rolled up high at the sides in steps.
		out.add(up(-10.5f, -1f, -10f, 10.5f, 0f, 10f, brim, false));
		for (int s = -1; s <= 1; s += 2) {
			out.add(up(-10.5f, -1.5f, s < 0 ? -12f : 10f, 10.5f, -0.5f, s < 0 ? -10f : 12f, brim, false));
			out.add(up(-8f, -2f, s < 0 ? -13.5f : 12f, 8f, -1f, s < 0 ? -12f : 13.5f, brim, false));
			out.add(up(s < 0 ? -12.5f : 10.5f, -0.5f, -11f, s < 0 ? -10.5f : 12.5f, 0.75f, 11f, brim, false));
			out.add(up(s < 0 ? -14f : 12.5f, 0.75f, -10f, s < 0 ? -12.5f : 14f, 2f, 10f, brim, false));
			out.add(up(s < 0 ? -15f : 14f, 2f, -8.5f, s < 0 ? -14f : 15f, 3.25f, 8.5f, lighter(brim), false));
		}
		ring(out, helmet, 2, 2f, 9.5f, 10f, (i, row) -> hat.accent);
		// The crown narrows towards the top, pinched in at the front on both sides.
		int felt = hat.main[0];
		int pinch = ARGB.scaleRGB(felt, 0.8f);
		out.add(up(-8.5f, 0f, -8.5f, 8.5f, 3f, 8.5f, felt, false));
		out.add(up(-8.25f, 3f, -8.75f, 8.25f, 5f, 8.25f, felt, false));
		// Each: bottom, top, half width behind, half width at the pinched front, how far forward.
		float[][] pinched = {{5f, 7f, 7.75f, 4.25f, 8.75f}, {7f, 8.5f, 7f, 3.75f, 8.5f}};
		for (float[] p : pinched) {
			float back = -p[4] + 2.5f;
			out.add(up(-p[2], p[0], back, p[2], p[1], 7.75f, felt, false));
			out.add(up(-p[3], p[0], -p[4], p[3], p[1], back, felt, false));
			out.add(up(-p[2], p[0], back - 0.1f, -p[3], p[1], back, pinch, false));
			out.add(up(p[3], p[0], back - 0.1f, p[2], p[1], back, pinch, false));
		}
		// The creased top: two ridges either side of a deep groove.
		for (int s = -1; s <= 1; s += 2) {
			out.add(up(s < 0 ? -5.5f : 1f, 8.5f, -5f, s < 0 ? -1f : 5.5f, 9.5f, 5f, felt, false));
			out.add(up(s < 0 ? -4.5f : 1.5f, 9.5f, -4f, s < 0 ? -1.5f : 4.5f, 10f, 4f, felt, false));
		}
		out.add(up(-1f, 8.3f, -4.5f, 1f, 8.5f, 4.5f, ARGB.scaleRGB(felt, 0.7f), false));
		int concho = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.second;
		out.add(up(-1.25f, 0.25f, -10.25f, 1.25f, 1.75f, -10f, concho, false));
	}

	private static List<Box> stampedeString(Hat hat) {
		return List.of(raw(-0.2f, 0f, -0.2f, 0.2f, 9f, 0.2f, hat.accent), raw(-0.6f, 9f, -0.6f, 0.6f, 10.2f, 0.6f, hat.second));
	}

	/** Headphones: the band over the head shows the helmet, the cups' caps take the trim's colour, a cable dangles. */
	private static void headphonesHat(List<Box> out, Hat hat, HelmetLook helmet) {
		headband(out, helmet, hat.main[0], 0f);
		// A soft pad on top of the band.
		out.add(up(-5f, 1.25f, -1.25f, 5f, 2f, 1.25f, hat.main[1], false));
		int cap = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
		for (int s = -1; s <= 1; s += 2) {
			float x0 = s < 0 ? -11f : 9f;
			out.add(up(x0, -11f, -3f, x0 + 2f, -5f, 3f, hat.main[0], false));
			out.add(up(s < 0 ? -9f : 8f, -10.5f, -2.5f, s < 0 ? -8f : 9f, -5.5f, 2.5f, hat.main[1], false));
			float c0 = s < 0 ? -11.5f : 11f;
			out.add(up(c0, -10f, -2f, c0 + 0.5f, -6f, 2f, cap, false));
			// A grille on the cap.
			float g0 = s < 0 ? c0 - 0.1f : c0 + 0.5f;
			for (float y : new float[]{-9f, -8.25f, -7.5f}) {
				out.add(up(g0, y, -1.5f, g0 + 0.1f, y + 0.4f, 1.5f, ARGB.scaleRGB(cap, 0.7f), false));
			}
		}
	}

	private static List<Box> cable(Hat hat, int k) {
		Box wire = raw(-0.25f, 0f, -0.25f, 0.25f, 5f, 0.25f, 0xFF2A2A2C);
		return k == 0 ? List.of(wire) : List.of(wire, raw(-0.5f, 5f, -0.5f, 0.5f, 6.5f, 0.5f, 0xFFC0C0C8));
	}

	/** A party hat: a striped cone with a pompom on its tip, the elastic down the cheeks showing the helmet. */
	private static void partyHat(List<Box> out, Hat hat, HelmetLook helmet) {
		// Leaning a little to the left, as party hats do.
		for (int k = 0; k < PARTY_LAYERS; k++) {
			float half = 4.5f - 0.7f * k;
			float x = PARTY_X - PARTY_LEAN * k;
			out.add(up(x - half, 1.5f * k, -half, x + half, 1.5f * k + 1.5f, half, k % 2 == 0 ? hat.main[0] : hat.main[1], false));
		}
		boolean shows = helmet != null;
		for (int s = -1; s <= 1; s += 2) {
			float x0 = s < 0 ? -9.25f : 8f;
			out.add(up(x0, -13f, -0.5f, x0 + 1.25f, 0f, 0.5f, shows ? helmet.tile(s < 0 ? 0 : 1, 0) : 0xFFE8E8E8, shows));
		}
	}

	/**
	 * A sombrero: a tall rounded cone, its band showing the helmet, and a very wide brim turned up at the rim with an
	 * embroidered border in the trim's colour; pompoms dangle all round the brim.
	 */
	private static void sombreroHat(List<Box> out, Hat hat, HelmetLook helmet) {
		// The brim falls away from the crown, then swings up to its rim.
		int brim = hat.main[1];
		round(out, 0f, -1f, 0f, 10.5f, 2f, brim);
		frame(out, -1.75f, -0.75f, 10f, 14f, brim);
		frame(out, -1.25f, -0.25f, 14f, 16.5f, brim);
		ring(out, null, 2, 1.5f, 16.5f, 18f, (i, row) -> row == 0 ? lighter(hat.main[1]) : hat.main[1]);
		int embroidery = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
		ring(out, null, 1, -0.1f, 14.25f, 15.75f, (i, row) -> i % 2 == 0 ? embroidery : hat.second);
		ring(out, helmet, 2, 2f, 8.5f, 9.5f, (i, row) -> i % 2 == 0 ? hat.accent : hat.second);
		ring(out, null, 3, 5f, 8f, 9.25f, (i, row) -> hat.main[0]);
		ring(out, null, 3, 8f, 7f, 8.5f, (i, row) -> hat.main[0]);
		ring(out, null, 2, 10f, 5.5f, 7.5f, (i, row) -> hat.main[0]);
		out.add(up(-7f, 2f, -7f, 7f, 8f, 7f, hat.main[0], false));
		out.add(up(-5.5f, 8f, -5.5f, 5.5f, 10f, 5.5f, hat.main[0], false));
		out.add(up(-5f, 10f, -5f, 5f, 11f, 5f, hat.main[0], false));
		out.add(up(-3f, 11f, -3f, 3f, 11.75f, 3f, hat.main[0], false));
	}

	private static List<Box> sombreroPompom(Hat hat) {
		return List.of(raw(-0.15f, 0f, -0.15f, 0.15f, 2f, 0.15f, hat.main[1]), raw(-1f, 2f, -1f, 1f, 4f, 1f, hat.accent),
				raw(-1.25f, 2.5f, -0.5f, 1.25f, 3.5f, 0.5f, ARGB.scaleRGB(hat.accent, 0.88f)));
	}

	/**
	 * A beret: a soft disc pulled over to the right, its rim showing the helmet, a palette pinned at the side with dabs of
	 * paint (one in the trim's colour) and a brush tucked in; the little stalk on top wobbles.
	 */
	private static void beretHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 1, 0f, 8.5f, 9.25f, (i, row) -> hat.main[1]);
		out.add(up(-9f, 0f, -9.5f, 11f, 1.5f, 9.5f, hat.main[0], false));
		out.add(up(-8f, 1.5f, -8.5f, 12f, 2.5f, 8.5f, hat.main[0], false));
		out.add(up(10f, -0.5f, -7.5f, 12.5f, 1.5f, 7.5f, hat.main[0], false));
		out.add(up(-6f, 2.5f, -6.5f, 9f, 3.25f, 6.5f, hat.main[0], false));
		out.add(up(-3f, 3.25f, -4f, 6f, 3.75f, 4f, hat.main[0], false));
		// The palette on the left front, with dabs of paint.
		int wood = 0xFFC8A06A;
		out.add(up(-7f, -0.25f, -9.85f, -3f, 1.25f, -9.5f, wood, false));
		out.add(up(-6.5f, 1.25f, -9.85f, -3.5f, 1.5f, -9.5f, wood, false));
		int paint = helmet != null && helmet.trim() != 0 ? helmet.trim() : 0xFFD83A30;
		int[] dabs = {paint, 0xFF3A6AD8, 0xFFF2D23A};
		for (int i = 0; i < dabs.length; i++) {
			float x = -6.5f + i * 1.2f;
			out.add(up(x, 0.5f, -9.95f, x + 0.6f, 1.1f, -9.85f, dabs[i], false));
		}
		// A brush tucked in at the left, its bristles dipped in paint.
		out.add(up(-9.75f, 0.5f, 1f, -9f, 6f, 1.75f, 0xFF8A5A32, false));
		out.add(up(-9.85f, 6f, 0.9f, -8.9f, 7f, 1.85f, 0xFFD8D0C0, false));
		out.add(up(-9.9f, 7f, 0.85f, -8.85f, 7.75f, 1.9f, hat.accent, false));
	}

	private static List<Box> beretStalk(Hat hat) {
		return List.of(raw(-0.4f, -1.5f, -0.4f, 0.4f, 0f, 0.4f, hat.main[1]));
	}

	/**
	 * A Tyrolean hat: a creased crown, a narrow brim turned up behind, a twisted cord band showing the helmet with a
	 * badge in the trim's colour, and a chamois brush and a feather that bob at the side.
	 */
	private static void tyroleanHat(List<Box> out, Hat hat, HelmetLook helmet) {
		out.add(up(-11.5f, -1f, -11.5f, 11.5f, 0f, 11.5f, hat.main[1], false));
		out.add(up(-10f, 0f, 10f, 10f, 3f, 11.5f, hat.main[1], false));
		out.add(up(-8f, 0f, 11.5f, 8f, 3.75f, 12f, hat.main[1], false));
		ring(out, helmet, 1, 1f, 9f, 9.75f, (i, row) -> i % 2 == 0 ? hat.main[2] : lighter(hat.main[2]));
		ring(out, null, 4, 5f, 8f, 9f, (i, row) -> hat.main[0]);
		ring(out, null, 2, 7f, 6.5f, 8.25f, (i, row) -> hat.main[0]);
		out.add(up(-6.5f, 1f, -6.5f, 6.5f, 7f, 6.5f, hat.main[0], false));
		out.add(up(-6f, 7f, -5f, -0.75f, 7.75f, 5f, hat.main[0], false));
		out.add(up(0.75f, 7f, -5f, 6f, 7.75f, 5f, hat.main[0], false));
		out.add(up(-0.75f, 7f, -4f, 0.75f, 7.3f, 4f, ARGB.scaleRGB(hat.main[0], 0.75f), false));
		int badge = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.second;
		out.add(up(-6.5f, 1.5f, -9.2f, -4.5f, 3.5f, -8.95f, badge, false));
		out.add(up(-6f, 2f, -9.3f, -5f, 3f, -9.2f, lighter(badge), false));
	}

	private static List<Box> chamoisBrush(Hat hat, int k) {
		if (k == 0) {
			return List.of(raw(-0.6f, -3f, -0.6f, 0.6f, 0f, 0.6f, 0xFF4A3424));
		}
		// The brush fans out, dark at the base and pale at the tips.
		return List.of(raw(-1f, -2f, -1f, 1f, 0f, 1f, 0xFF3A2C22), raw(-1.75f, -4f, -1.75f, 1.75f, -2f, 1.75f, 0xFF6A5444),
				raw(-1.5f, -5.5f, -1.5f, 1.5f, -4f, 1.5f, 0xFFD8D0C0));
	}

	/**
	 * A captain's cap: a wide flat top over a band showing the helmet, an anchor in a laurel wreath (the anchor in the
	 * trim's colour), a gold cord over the glossy visor and buttons at its ends, from which small tassels hang.
	 */
	private static void captainHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 4, 2f, 9f, 9.75f, (i, row) -> hat.main[1]);
		int cloth = hat.main[0];
		round(out, 0f, 2f, 4f, 10f, 2f, cloth);
		// The top: wide and rounded, standing high in front and falling away behind.
		round(out, 0f, 4f, 4.3f, 11.1f, 2.5f, ARGB.scaleRGB(cloth, 0.85f));
		round(out, 0f, 4.3f, 5.5f, 11f, 2.5f, cloth);
		out.add(up(-10f, 5.5f, -9.5f, 10f, 6.75f, 3f, cloth, false));
		out.add(up(-9f, 5.5f, -10.5f, 9f, 6.75f, 3f, cloth, false));
		out.add(up(-10f, 5.5f, 3f, 10f, 6.25f, 8.5f, cloth, false));
		out.add(up(-9f, 5.5f, 3f, 9f, 6.25f, 10f, cloth, false));
		out.add(up(-8.5f, 6.75f, -9f, 8.5f, 7.5f, -2f, cloth, false));
		out.add(up(-7.5f, 6.75f, -10f, 7.5f, 7.5f, -2f, cloth, false));
		out.add(up(-6.5f, 7.5f, -9.25f, 6.5f, 7.9f, -4.5f, lighter(cloth), false));
		// The visor, glossy, with a highlight along it.
		float[][] rows = {{-10.75f, 7.5f}, {-11.75f, 7.25f}, {-12.75f, 6.5f}, {-13.5f, 5f}};
		float z1 = -9.5f;
		for (int k = 0; k < rows.length; k++) {
			float dip = -2f - 0.15f * k * k;
			out.add(up(-rows[k][1], dip, rows[k][0], rows[k][1], dip + 0.75f, z1, hat.main[2], false));
			z1 = rows[k][0];
		}
		out.add(up(-5f, -1.25f, -11.5f, 5f, -1.15f, -11f, lighter(hat.main[2]), false));
		// The gold cord, twisted, standing out over the visor, and its buttons.
		for (int s = 0; s < 16; s++) {
			float x = -8f + s;
			boolean even = s % 2 == 0;
			out.add(up(x, even ? -1.35f : -1.1f, -10.45f, x + 1f, even ? -0.85f : -0.6f, -9.75f,
					even ? hat.accent : ARGB.scaleRGB(hat.accent, 0.78f), false));
		}
		for (int s = -1; s <= 1; s += 2) {
			float x = s * 8.25f;
			out.add(up(x - 0.6f, -1.6f, -10.65f, x + 0.6f, -0.4f, -9.75f, lighter(hat.accent), false));
		}
		// The badge, standing out: an anchor in a laurel wreath.
		int anchor = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.second;
		float z = -9.85f;
		out.add(up(-0.25f, -0.25f, z - 0.45f, 0.25f, 1.75f, z, anchor, false));
		out.add(up(-0.9f, 1.25f, z - 0.45f, 0.9f, 1.5f, z, anchor, false));
		out.add(up(-1f, -0.5f, z - 0.45f, 1f, -0.25f, z, anchor, false));
		out.add(up(-1f, -0.25f, z - 0.45f, -0.75f, 0.25f, z, anchor, false));
		out.add(up(0.75f, -0.25f, z - 0.45f, 1f, 0.25f, z, anchor, false));
		for (int s = -1; s <= 1; s += 2) {
			out.add(up(s < 0 ? -2.25f : 1.75f, -0.25f, z - 0.35f, s < 0 ? -1.75f : 2.25f, 1.5f, z, hat.accent, false));
			out.add(up(s < 0 ? -2f : 1.25f, -0.5f, z - 0.35f, s < 0 ? -1.25f : 2f, -0.25f, z, hat.accent, false));
		}
	}

	private static List<Box> captainTassel(Hat hat) {
		return List.of(raw(-0.15f, 0f, -0.15f, 0.15f, 1.5f, 0.15f, hat.accent), raw(-0.4f, 1.5f, -0.4f, 0.4f, 2.75f, 0.4f, hat.accent));
	}

	/**
	 * An ushanka: a tall cloth crown, a thick fur rim with its front flap turned up (its edge showing the helmet, as does a
	 * strip round the crown), a badge in the trim's colour on the flap, and ear flaps with ties that dangle.
	 */
	private static void ushankaHat(List<Box> out, Hat hat, HelmetLook helmet) {
		IntBinaryOperator fur = (i, row) -> (i * 7 + row * 3) % 5 == 0 ? hat.main[2] : hat.main[1];
		ring(out, null, 3, 1f, 9f, 10.75f, fur);
		ring(out, helmet, 1, 2f, 9.25f, 9.75f, (i, row) -> ARGB.scaleRGB(hat.main[0], 0.75f));
		ring(out, null, 3, 5f, 8f, 9.25f, (i, row) -> hat.main[0]);
		out.add(up(-8f, 1f, -8f, 8f, 5f, 8f, hat.main[0], false));
		out.add(up(-8f, 5f, -8f, 8f, 6f, 8f, hat.main[0], false));
		out.add(up(-6f, 6f, -6f, 6f, 6.6f, 6f, hat.main[0], false));
		// The front flap, turned up, its fur tufted.
		out.add(up(-8.75f, 0f, -11.25f, 8.75f, 4.5f, -9.25f, hat.main[1], false));
		for (float x = -7.5f; x <= 7.5f; x += 2.5f) {
			out.add(up(x, 0.5f + (Math.abs(x) % 2f), -11.4f, x + 1f, 1.5f + (Math.abs(x) % 2f), -11.25f, hat.main[2], false));
		}
		boolean shows = helmet != null;
		out.add(up(-8.75f, 4.5f, -11.25f, 8.75f, 5f, -9.25f, shows ? helmet.tile(0, 0) : hat.main[2], shows));
		int badge = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.second;
		out.add(up(-1f, 1.75f, -11.4f, 1f, 3.25f, -11.25f, badge, false));
		out.add(up(-0.4f, 2.15f, -11.5f, 0.4f, 2.85f, -11.4f, lighter(badge), false));
	}

	private static List<Box> earFlapTied(Hat hat) {
		return List.of(raw(-1f, 0f, -3.5f, 1f, 7f, 3.5f, hat.main[1]), raw(-1.1f, 1f, -3f, 1.1f, 2f, -2f, hat.main[2]),
				raw(-0.15f, 7f, -0.15f, 0.15f, 10f, 0.15f, hat.accent), raw(-0.4f, 10f, -0.4f, 0.4f, 10.75f, 0.4f, hat.accent));
	}

	/**
	 * A tiara: a fine circlet showing the helmet, rising in front to points with pearls, a big centre stone in the trim's
	 * colour between smaller ones, and a drop pendant that swings on the forehead.
	 */
	private static void tiaraHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 1, 0.5f, 8.75f, 9.25f, (i, row) -> hat.main[1]);
		float z = -9.25f;
		// Points either side of the middle, lower further out, joined by a fine arch.
		float[][] points = {{-6.5f, 1.5f}, {-4f, 2.75f}, {4f, 2.75f}, {6.5f, 1.5f}};
		for (float[] p : points) {
			out.add(up(p[0] - 0.6f, 0.5f, z - 0.5f, p[0] + 0.6f, 0.5f + p[1], z, hat.main[0], false));
			out.add(up(p[0] - 0.45f, 0.5f + p[1], z - 0.45f, p[0] + 0.45f, 1.4f + p[1], z + 0.05f, 0xFFF4F0E6, false));
			out.add(up(p[0] - 0.5f, 0.9f, z - 0.6f, p[0] + 0.5f, 1.9f, z - 0.5f, hat.second, false));
		}
		out.add(up(-7f, 1.75f, z - 0.3f, 7f, 2f, z, hat.main[0], false));
		// The middle: a tall setting with the big stone.
		int stone = helmet != null && helmet.trim() != 0 ? helmet.trim() : hat.accent;
		out.add(up(-1.75f, 0.5f, z - 0.5f, 1.75f, 4.75f, z, hat.main[0], false));
		out.add(up(-0.75f, 4.75f, z - 0.4f, 0.75f, 5.75f, z, hat.main[0], false));
		out.add(up(-1.1f, 1.5f, z - 0.75f, 1.1f, 3.75f, z - 0.5f, stone, false));
		out.add(up(-0.5f, 2.9f, z - 0.85f, 0f, 3.4f, z - 0.75f, lighter(stone), false));
	}

	private static List<Box> tiaraDrop(Hat hat) {
		return List.of(raw(-0.15f, 0f, -0.15f, 0.15f, 1.5f, 0.15f, hat.main[0]), raw(-0.6f, 1.5f, -0.6f, 0.6f, 3f, 0.6f, hat.second),
				raw(-0.35f, 3f, -0.35f, 0.35f, 3.6f, 0.35f, hat.second));
	}

	/**
	 * A ninja's headband: the band round the brow shows the helmet, a metal plate in front engraved in the trim's colour,
	 * a knot behind and two long tails that stream out as the wearer moves.
	 */
	private static void ninjaHat(List<Box> out, Hat hat, HelmetLook helmet) {
		ring(out, helmet, 2, -3f, 9f, 9.75f, (i, row) -> row == 0 ? hat.main[0] : hat.main[1]);
		int metal = 0xFFB8BCC4;
		out.add(up(-3.25f, -5.25f, -10.25f, 3.25f, -2.75f, -9.75f, ARGB.scaleRGB(metal, 0.7f), false));
		out.add(up(-3f, -5f, -10.35f, 3f, -3f, -10.25f, metal, false));
		int engraving = helmet != null && helmet.trim() != 0 ? helmet.trim() : 0xFF3A3A40;
		// A leaf-like mark: a curl and a stroke.
		float z = -10.45f;
		out.add(up(-1.5f, -4.5f, z, 1f, -4.25f, -10.35f, engraving, false));
		out.add(up(0.75f, -4.5f, z, 1f, -3.5f, -10.35f, engraving, false));
		out.add(up(-1f, -3.75f, z, 1f, -3.5f, -10.35f, engraving, false));
		out.add(up(-1.5f, -4.25f, z, -1.25f, -3.25f, -10.35f, engraving, false));
		out.add(up(1.5f, -4.75f, z, 1.75f, -4f, -10.35f, engraving, false));
		for (float[] r : new float[][]{{-2.6f, -4.6f}, {2.35f, -4.6f}, {-2.6f, -3.65f}, {2.35f, -3.65f}}) {
			out.add(up(r[0], r[1], z, r[0] + 0.25f, r[1] + 0.25f, -10.35f, ARGB.scaleRGB(metal, 0.6f), false));
		}
		out.add(up(-1.5f, -5.5f, 9.5f, 1.5f, -2.5f, 10.75f, hat.main[1], false));
	}

	private static List<Box> bandTail(Hat hat, int k) {
		return List.of(raw(-0.75f, 0f, -0.15f, 0.75f, 4f, 0.15f, k % 2 == 0 ? hat.main[0] : hat.main[1]));
	}

	// ---------------------------------------------------------------- axolotl

	/** The gills on the right, three fronds of steps {x0, x1, y0, y1} out from the head: up, out and down. */
	private static final float[][][] GILLS = {
			{{8.5f, 10.5f, 5f, 6.5f}, {10f, 11.75f, 6.25f, 8f}, {11.25f, 12.75f, 7.75f, 9.5f}},
			{{9.5f, 12f, 2.75f, 4.25f}, {11.5f, 14f, 3.25f, 4.75f}, {13.5f, 15.25f, 3.75f, 5.25f}},
			{{9.75f, 12f, 0f, 1.5f}, {11.5f, 13.5f, -1f, 0.5f}, {13f, 14.75f, -2f, -0.5f}}};
	/** Embroidered spots on the dome, {x, z}. */
	private static final float[][] AXOLOTL_SPOTS = {{-4.5f, -3f}, {3f, -4.5f}, {5f, 1.5f}, {-2f, 3.5f}, {-5.5f, 2f}, {1f, 0f}};

	/**
	 * A plush axolotl hat: a soft dome with embroidered spots, a fin down the back, the face on the forehead (button
	 * eyes far apart, a wide smile and rosy cheeks), three fluffy gill fronds a side, a knitted cuff in the belly colour
	 * that shows the helmet, and long flaps hanging down past the cheeks, each ending in a little paw.
	 */
	private static void axolotlHat(List<Box> out, Hat hat, HelmetLook helmet) {
		int plush = hat.main[0];
		int shade = hat.main[1];
		int belly = hat.main[2];
		ring(out, null, 4, 1f, 8.5f, 10f, (i, row) -> (i * 5 + row * 3) % 7 == 0 ? shade : plush);
		ring(out, helmet, 1, -3f, 8.75f, 10.25f, (i, row) -> i % 2 == 0 ? belly : ARGB.scaleRGB(belly, 0.92f));
		round(out, 0f, 1f, 4f, 9.5f, 1.5f, plush);
		round(out, 0f, 4f, 5.75f, 8.25f, 2.25f, plush);
		round(out, 0f, 5.75f, 6.75f, 6.25f, 2.5f, plush);
		round(out, 0f, 6.75f, 7.25f, 4f, 1.5f, plush);
		for (float[] s : AXOLOTL_SPOTS) {
			float y = Math.max(Math.abs(s[0]), Math.abs(s[1])) > 4f ? 6.75f : 7.25f;
			out.add(up(s[0] - 0.5f, y, s[1] - 0.5f, s[0] + 0.5f, y + 0.08f, s[1] + 0.5f, shade, false));
		}
		// A seam over the top, and the soft fin running down the back.
		out.add(up(-0.2f, 7.25f, -4f, 0.2f, 7.3f, 1f, shade, false));
		float[][] fin = {{7.25f, 8f, 1f, 4f}, {6f, 7.5f, 4f, 6.5f}, {3.5f, 6.25f, 6.5f, 8.5f}, {0.5f, 4f, 8.5f, 10.25f}};
		for (float[] f : fin) {
			out.add(up(-0.5f, f[0], f[2], 0.5f, f[1], f[3], shade, false));
		}
		// The face: a pale muzzle, button eyes with a shine, a wide smile turned up at the corners, rosy cheeks.
		out.add(up(-4f, -3f, -10.1f, 4f, -1.25f, -10f, belly, false));
		for (int s = -1; s <= 1; s += 2) {
			float eye = s * 6.25f;
			out.add(up(eye - 1f, -1.75f, -10.15f, eye + 1f, 0.75f, -10f, hat.second, false));
			out.add(up(eye - 0.5f, -0.25f, -10.2f, eye, 0.25f, -10.15f, 0xFFFFFFFF, false));
			out.add(up(s < 0 ? -3f : 2.5f, -2f, -10.2f, s < 0 ? -2.5f : 3f, -1.5f, -10.1f, hat.second, false));
			out.add(up(s < 0 ? -5f : 3.75f, -2.75f, -10.15f, s < 0 ? -3.75f : 5f, -2.25f, -10.1f, hat.third, false));
		}
		out.add(up(-2.5f, -2.25f, -10.2f, 2.5f, -2f, -10.1f, hat.second, false));
		gills(out, hat);
	}

	/** Three fronds a side: plush stalks with lighter tufts along the top and a darker edge underneath. */
	private static void gills(List<Box> out, Hat hat) {
		int tuft = lighter(hat.accent);
		int under = ARGB.scaleRGB(hat.accent, 0.75f);
		for (int s = -1; s <= 1; s += 2) {
			for (float[][] frond : GILLS) {
				for (float[] g : frond) {
					float x0 = s > 0 ? g[0] : -g[1];
					float x1 = s > 0 ? g[1] : -g[0];
					out.add(up(x0, g[2], -3f, x1, g[3], -1.5f, hat.accent, false));
					out.add(up(x0, g[2] - 0.05f, -3.05f, x1, g[2] + 0.4f, -1.45f, under, false));
					float outer = s > 0 ? x1 - 0.75f : x0 + 0.25f;
					out.add(up(outer, g[3], -2.75f, outer + 0.5f, g[3] + 0.5f, -1.75f, tuft, false));
					float inner = s > 0 ? x0 + 0.25f : x1 - 0.75f;
					out.add(up(inner, g[3], -2.6f, inner + 0.5f, g[3] + 0.35f, -1.9f, tuft, false));
				}
			}
		}
	}

	// ---------------------------------------------------------------- fox

	/** How tall a fox ear stands (voxels). */
	private static final float FOX_EAR = 7.5f;

	/**
	 * A fox hat: a furry dome with a darker stripe along the top, the fox's face on the forehead (a white muzzle with a
	 * snout and nose standing out, white cheeks, narrow slanted eyes), fluffy white tufts at the sides and a fluffy white
	 * cuff that shows the helmet; tall ears with dark tips and a bushy tail behind come from {@link #foxEar} and
	 * {@link #foxTail}.
	 */
	private static void foxHat(List<Box> out, Hat hat, HelmetLook helmet) {
		int fur = hat.main[0];
		int shade = hat.main[1];
		int white = hat.main[2];
		ring(out, null, 4, 1f, 8.5f, 10f, (i, row) -> (i * 3 + row) % 5 == 0 ? shade : fur);
		ring(out, helmet, 1, -3f, 8.75f, 10.25f, (i, row) -> i % 2 == 0 ? white : ARGB.scaleRGB(white, 0.92f));
		round(out, 0f, 1f, 4f, 9.5f, 1.5f, fur);
		round(out, 0f, 4f, 5.75f, 8.25f, 2.25f, fur);
		round(out, 0f, 5.75f, 6.75f, 6.25f, 2.5f, fur);
		round(out, 0f, 6.75f, 7.25f, 4f, 1.5f, fur);
		out.add(up(-1.5f, 7.25f, -2f, 1.5f, 7.35f, 4f, shade, false));
		out.add(up(-2f, 6.75f, 4f, 2f, 6.85f, 6f, shade, false));
		// The face: white muzzle and cheeks, the snout with its nose, slanted eyes with a shine.
		out.add(up(-2.5f, -3f, -10.1f, 2.5f, -1.5f, -10f, white, false));
		for (int s = -1; s <= 1; s += 2) {
			out.add(mirrored(s, 4f, -3f, -10.1f, 8.5f, -1.75f, -10f, white));
			out.add(mirrored(s, 6.25f, -1.75f, -10.1f, 8.5f, -0.75f, -10f, white));
			float eye = s * 4f;
			out.add(up(eye - 1.25f, -0.75f, -10.15f, eye + 1.25f, 0.25f, -10f, hat.second, false));
			out.add(mirrored(s, 2.75f, -1.25f, -10.15f, 3.75f, -0.75f, -10f, hat.second));
			out.add(mirrored(s, 4.25f, -0.5f, -10.2f, 4.75f, 0f, -10.15f, 0xFFFFFFFF));
			// Fluffy tufts sticking out at the sides.
			out.add(mirrored(s, 9.75f, -3.5f, -7f, 11f, -1f, -3.5f, white));
			out.add(mirrored(s, 10.75f, -3f, -6.25f, 11.5f, -1.75f, -4.25f, white));
		}
		out.add(up(-2f, -3f, -12f, 2f, -1.25f, -10f, white, false));
		out.add(up(-1.5f, -1.5f, -11.75f, 1.5f, -0.75f, -10f, fur, false));
		out.add(up(-0.75f, -2f, -12.2f, 0.75f, -1.25f, -11.9f, hat.accent, false));
		out.add(up(-1f, -2.75f, -12.05f, 1f, -2.5f, -12f, hat.accent, false));
	}

	/** A box given on the right (x out from the middle, y up), mirrored to the left for {@code side} -1. */
	private static Box mirrored(int side, float x0, float yUp0, float z0, float x1, float yUp1, float z1, int color) {
		return side > 0 ? up(x0, yUp0, z0, x1, yUp1, z1, color, false) : up(-x1, yUp0, z0, -x0, yUp1, z1, color, false);
	}

	/** A fox's ear, narrowing to a dark tip, pale inside with a tuft of white fur at the base. */
	private static List<Box> foxEar(Hat hat) {
		int fur = hat.main[0];
		return List.of(raw(-3f, -2.5f, -1f, 3f, 0f, 1f, fur), raw(-2.25f, -4.5f, -0.9f, 2.25f, -2.5f, 0.9f, fur),
				raw(-1.5f, -6f, -0.8f, 1.5f, -4.5f, 0.8f, hat.accent), raw(-0.75f, -FOX_EAR, -0.7f, 0.75f, -6f, 0.7f, hat.accent),
				raw(-2f, -2.5f, -1.1f, 2f, 0f, -1f, hat.third), raw(-1.25f, -4.25f, -1f, 1.25f, -2.5f, -0.9f, hat.third),
				raw(-1f, -1.25f, -1.2f, 1f, 0f, -1.1f, hat.main[2]));
	}

	/** A piece of the bushy tail: slim at the hat, thick in the middle with tufts of fur, a white tip at the end. */
	private static List<Box> foxTail(Hat hat, int k) {
		int fur = hat.main[0];
		int shade = hat.main[1];
		int white = hat.main[2];
		List<Box> out = new ArrayList<>();
		switch (k) {
			case 0 -> {
				out.add(raw(-1.5f, 0f, -1.5f, 1.5f, 4.5f, 1.5f, fur));
				out.add(raw(-1.75f, 2.5f, -1.25f, 1.75f, 4.5f, 1.25f, shade));
			}
			case 1 -> {
				out.add(raw(-2.25f, 0f, -2.25f, 2.25f, 4.5f, 2.25f, fur));
				out.add(raw(-2.6f, 1f, -1.5f, 2.6f, 3.5f, 1.5f, fur));
				out.add(raw(-1.5f, 1f, -2.6f, 1.5f, 3.5f, 2.6f, fur));
				out.add(raw(-2.65f, 1.5f, 0.5f, -2.5f, 2.75f, 1.25f, shade));
				out.add(raw(2.5f, 2.25f, -1f, 2.65f, 3.25f, 0f, shade));
				out.add(raw(-0.5f, 1.75f, 2.5f, 0.5f, 3f, 2.65f, shade));
			}
			default -> {
				out.add(raw(-2f, 0f, -2f, 2f, 2f, 2f, fur));
				out.add(raw(-2f, 2f, -2f, 2f, 4.5f, 2f, white));
				out.add(raw(-2.35f, 2.5f, -1.25f, 2.35f, 4f, 1.25f, white));
				out.add(raw(-1f, 4.5f, -1f, 1f, 5.5f, 1f, white));
			}
		}
		return out;
	}

	/** One piece of a long side flap: ribbed plush with a belly-coloured stripe in front; the last one a paw with toes. */
	private static List<Box> axolotlFlap(Hat hat, int k) {
		int plush = hat.main[0];
		int belly = hat.main[2];
		List<Box> out = new ArrayList<>();
		float end = k == 2 ? 3.5f : 5f;
		for (float y = 0f; y < end - 0.01f; y += 1.25f) {
			int rib = Math.round(y / 1.25f) % 2 == 0 ? plush : ARGB.scaleRGB(plush, 0.93f);
			out.add(raw(-1.5f, y, -1.25f, 1.5f, Math.min(y + 1.25f, end), 1.25f, rib));
		}
		out.add(raw(-0.75f, 0f, -1.35f, 0.75f, end, -1.25f, belly));
		if (k == 0) {
			// Sewn on under the cuff.
			out.add(raw(-1.6f, 0f, -1.35f, 1.6f, 0.35f, 1.35f, hat.main[1]));
		} else if (k == 1) {
			out.add(raw(-1.55f, 2f, 0.25f, -1.5f, 2.75f, 1f, hat.main[1]));
			out.add(raw(-0.5f, 3f, 1.25f, 0.25f, 3.75f, 1.3f, hat.main[1]));
		} else {
			// The paw: a soft pad in front and four little toes.
			out.add(raw(-2f, 3.5f, -1.5f, 2f, 5.25f, 1.5f, plush));
			out.add(raw(-1f, 3.85f, -1.6f, 1f, 4.9f, -1.5f, belly));
			for (float x : new float[]{-1.9f, -0.85f, 0.2f, 1.25f}) {
				out.add(raw(x, 5.25f, -1.1f, x + 0.65f, 6f, 1.1f, hat.main[1]));
			}
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

	/**
	 * A slab with its corners cut in by {@code c} (in two steps when deep), half width {@code r} round x = {@code cx},
	 * from {@code y0} up to {@code y1}: a layer of something round.
	 */
	private static void round(List<Box> out, float cx, float y0, float y1, float r, float c, int color) {
		out.add(up(cx - r + c, y0, -r, cx + r - c, y1, r, color, false));
		out.add(up(cx - r, y0, -r + c, cx + r, y1, r - c, color, false));
		if (c > 1f) {
			float d = r - c * 0.4f;
			out.add(up(cx - d, y0, -d, cx + d, y1, d, color, false));
		}
	}

	/** Like {@link #ring}, but with its corners cut in, round a round crown. */
	private static void roundRing(List<Box> out, HelmetLook helmet, int rows, float top, float inner, float outer, IntBinaryOperator own) {
		float edge = outer - 1.5f;
		float w = 2f * edge / 8f;
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			for (int row = 0; row < rows; row++) {
				for (int t = 0; t <= 8; t++) {
					int i = side * 10 + t;
					int color = helmet != null ? helmet.tile(i, row) : own.applyAsInt(i, row);
					part.add(t < 8
							? up(-edge + w * t, top - row - 1f, -outer, -edge + w * (t + 1), top - row, -inner, color, helmet != null)
							: up(edge, top - row - 1f, -outer + 0.6f, outer - 0.6f, top - row, -inner, color, helmet != null));
				}
			}
			turn(part, side, out);
		}
	}

	/** A flat square frame from half width {@code inner} out to {@code outer}: a band of a brim. */
	private static void frame(List<Box> out, float y0, float y1, float inner, float outer, int color) {
		out.add(up(-outer, y0, -outer, outer, y1, -inner, color, false));
		out.add(up(-outer, y0, inner, outer, y1, outer, color, false));
		out.add(up(-outer, y0, -inner, -inner, y1, inner, color, false));
		out.add(up(inner, y0, -inner, outer, y1, inner, color, false));
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
		// A dome: the gills under it, its pale rim, then tiers rounding up to the top.
		round(out, 0f, -1f, 0f, 12f, 3f, hat.third);
		round(out, 0f, 0f, 1.25f, 13f, 3f, hat.main[1]);
		float[][] tiers = {{1.25f, 2.75f, 12.75f, 3f}, {2.75f, 4.25f, 11.75f, 3f}, {4.25f, 5.5f, 10f, 3f}, {5.5f, 6.5f, 7.5f, 2.5f},
				{6.5f, 7.25f, 4.5f, 1.5f}};
		for (float[] t : tiers) {
			round(out, 0f, t[0], t[1], t[2], t[3], red);
		}
		// Dots on the tiers' tops (x, z, height) and round the side.
		float[][] dots = {{-1.5f, -1f, 7.25f}, {-6f, 2f, 6.5f}, {3f, -5.5f, 6.5f}, {8f, 1f, 5.5f}, {-4f, 8f, 5.5f},
				{-9.5f, -4f, 5.5f}, {1f, -9.75f, 5.5f}, {9.75f, -6f, 4.25f}, {-11.75f, 3f, 4.25f}, {-3f, 9.75f, 4.25f}};
		for (float[] d : dots) {
			out.add(up(d[0], d[2], d[1], d[0] + 2f, d[2] + 0.4f, d[1] + 2f, hat.accent, false));
		}
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			float shift = side % 2 == 0 ? 0f : 2f;
			part.add(up(-7f + shift, 1.5f, -13.05f, -5f + shift, 2.5f, -12.75f, hat.accent, false));
			part.add(up(4f + shift, 3f, -12.05f, 6f + shift, 4f, -11.75f, hat.accent, false));
			// More on the rim and the upper tier.
			part.add(up(-9.5f + shift, 0.25f, -13.3f, -8f + shift, 1f, -13f, hat.accent, false));
			part.add(up(0.5f - shift, 0.25f, -13.3f, 2f - shift, 1f, -13f, hat.accent, false));
			part.add(up(-4f + shift, 4.5f, -10.3f, -2.5f + shift, 5.25f, -10f, hat.accent, false));
			// Gills under the cap, round the stem.
			for (float x = -9f; x <= 9f; x += 3f) {
				part.add(up(x - 0.15f, -1.1f, -12f, x + 0.15f, -1f, -10f, ARGB.scaleRGB(hat.third, 0.8f), false));
			}
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
		// A patch sewn on the cone, with big stitches at its corners.
		int patch = ARGB.scaleRGB(hat.accent, 0.7f);
		out.add(up(-6.5f, 2.5f, -8.75f, -3f, 4.75f, -8.5f, patch, false));
		for (float[] c : new float[][]{{-6.5f, 2.5f}, {-3.5f, 2.5f}, {-6.5f, 4.25f}, {-3.5f, 4.25f}}) {
			out.add(up(c[0], c[1], -8.85f, c[0] + 0.5f, c[1] + 0.5f, -8.75f, 0xFFD8D0C0, false));
		}
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
		List<Box> out = new ArrayList<>();
		out.add(raw(-half, -3f, -half, half, 0f, half, color));
		// Folds in the cloth, darker creases down its sides.
		int fold = ARGB.scaleRGB(color, 0.75f);
		float x = half * 0.4f;
		out.add(raw(x - 0.25f, -3f, -half - 0.05f, x + 0.25f, 0f, half + 0.05f, fold));
		out.add(raw(-half - 0.05f, -3f, -x - 0.25f, half + 0.05f, 0f, -x + 0.25f, fold));
		if (k == WITCH_PIECES - 1) {
			out.add(raw(-1f, -5f, -1f, 1f, -3f, 1f, color));
			out.add(raw(-0.5f, -6f, -0.5f, 0.5f, -5f, 0.5f, color));
			// A little star on the tip.
			out.add(raw(-1.25f, -7.25f, -0.25f, 1.25f, -6.75f, 0.25f, hat.second));
			out.add(raw(-0.25f, -8.25f, -0.25f, 0.25f, -5.75f, 0.25f, hat.second));
			out.add(raw(-0.6f, -7.6f, -0.3f, 0.6f, -6.4f, 0.3f, lighter(hat.second)));
		}
		return out;
	}

	private static List<Box> spider(Hat hat) {
		int body = hat.third;
		List<Box> out = new ArrayList<>();
		out.add(raw(-0.15f, 0f, -0.15f, 0.15f, 5f, 0.15f, 0xFFD8D8DC));
		out.add(raw(-0.8f, 4.8f, -0.8f, 0.8f, 5.8f, 0.8f, body));
		out.add(raw(-1.2f, 5.6f, -1.2f, 1.2f, 7.6f, 1.2f, body));
		out.add(raw(-2.6f, 6f, -0.2f, 2.6f, 6.4f, 0.2f, body));
		out.add(raw(-2.6f, 6.9f, -0.2f, 2.6f, 7.3f, 0.2f, body));
		out.add(raw(-0.2f, 6.4f, -2.6f, 0.2f, 6.8f, 2.6f, body));
		out.add(raw(-0.6f, 5.1f, -0.9f, 0.6f, 5.4f, -0.8f, 0xFFE02828).glowing());
		return out;
	}

	/**
	 * A carved pumpkin worn as a helmet over the whole head: ribbed walls, its face lit from inside at the eyes, a lid
	 * with a stem, and a rim round the neck showing the helmet; a vine curls from the stem and springs.
	 */
	private static void pumpkinHat(List<Box> out, Hat hat, HelmetLook helmet) {
		// Layers from the neck up, each as far out as the pumpkin is wide there: bulging in the middle, drawn in at the
		// neck and the lid. Each: bottom, top (voxels up), half width, colour.
		float[][] layers = {
				{-PUMPKIN_DEPTH, -15f, 9.75f, 1}, {-15f, -13f, 10.5f, 0}, {-13f, -3f, 11f, 0}, {-3f, -0.5f, 10.5f, 0},
				{-0.5f, 1.5f, 9.75f, 2}};
		for (float[] layer : layers) {
			float y0 = layer[0];
			float y1 = layer[1];
			float r = layer[2];
			int color = layer[3] == 1 ? ARGB.scaleRGB(hat.main[0], 0.85f) : hat.main[(int) layer[3]];
			int groove = ARGB.scaleRGB(color, 0.8f);
			for (int side = 0; side < 4; side++) {
				List<Box> part = new ArrayList<>();
				// Ribs standing out between grooves set back, the corners stepped in so it looks round.
				float edge = r - 2f;
				float[] cuts = {-edge, -5.5f, -4.5f, -0.5f, 0.5f, 4.5f, 5.5f, edge};
				for (int c = 0; c + 1 < cuts.length; c++) {
					boolean rib = c % 2 == 0;
					part.add(up(cuts[c], y0, rib ? -r : -r + 0.6f, cuts[c + 1], y1, -r + 1.2f, rib ? color : groove, false));
				}
				part.add(up(edge, y0, -r + 0.6f, r - 1f, y1, -r + 1.6f, groove, false));
				turn(part, side, out);
			}
		}
		// The lid: a rounded top in steps, the stem standing from it.
		out.add(up(-8f, 1.5f, -8f, 8f, 2.5f, 8f, hat.main[2], false));
		out.add(up(-5.5f, 2.5f, -5.5f, 5.5f, 3.25f, 5.5f, hat.main[0], false));
		out.add(up(-1.25f, 3.25f, -1.25f, 1.25f, 5.25f, 1.25f, hat.second, false));
		// Its rim round the neck shows the helmet.
		ring(out, helmet, 1, -PUMPKIN_DEPTH + 1f, 8.75f, 10.25f, (i, row) -> hat.main[1]);
		// The face, lit from inside: pointed eyes, a nose and a wide grin with teeth, on the widest layer.
		int glow = hat.accent;
		float[][] face = {
				{-7f, -6f, -3f, -5f}, {-6.25f, -5f, -3.75f, -4f}, {-5.5f, -4f, -4.5f, -3f},
				{3f, -6f, 7f, -5f}, {3.75f, -5f, 6.25f, -4f}, {4.5f, -4f, 5.5f, -3f},
				{-1f, -8f, 1f, -7f}, {-0.5f, -7f, 0.5f, -6.5f},
				{-6f, -11f, 6f, -10f}, {-7f, -10f, -4f, -9f}, {-2f, -10f, 2f, -9f}, {4f, -10f, 7f, -9f},
				{-7.5f, -9f, -6f, -8f}, {6f, -9f, 7.5f, -8f}, {-4f, -12f, 4f, -11f}};
		for (float[] f : face) {
			out.add(up(f[0], f[1], -11.1f, f[2], f[3], -10f, glow, false).glowing());
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

	// ---------------------------------------------------------------- christmas and new year

	/**
	 * A Santa hat: a thick tufted fur brim (with the helmet's look below it if one is worn), a tall soft cone leaning to
	 * the back right, from whose top the tip flops over and hangs with a fluffy pompom.
	 */
	private static void santaHat(List<Box> out, Hat hat, HelmetLook helmet) {
		int cloth = hat.main[0];
		int fur = hat.main[2];
		if (helmet != null) {
			ring(out, helmet, 1, -1.5f, 9f, 9.75f, (i, row) -> fur);
		}
		ring(out, null, 3, 1.5f, 9f, 11f, (i, row) -> (i + row) % 3 == 0 ? ARGB.scaleRGB(fur, 0.9f) : fur);
		for (int k = 0; k < SANTA_LAYERS; k++) {
			float half = 8.5f - 1.05f * k;
			float x = SANTA_LEAN_X * k;
			float z = SANTA_LEAN_Z * k;
			// Shaded on the side it leans away from, so it looks soft and round.
			out.add(up(x - half, 1.5f + 1.5f * k, z - half, x + half, 3f + 1.5f * k, z + half, cloth, false));
			out.add(up(x - half - 0.1f, 1.5f + 1.5f * k, z - half + 1f, x - half + 0.9f, 3f + 1.5f * k, z + half - 1f, hat.main[1], false));
		}
	}

	private static List<Box> santaTip(Hat hat, int k) {
		float half = 2.25f - 0.5f * k;
		List<Box> out = new ArrayList<>();
		out.add(raw(-half, 0f, -half, half, 4f, half, k == 0 ? hat.main[0] : hat.main[1]));
		if (k == 1) {
			out.add(raw(-2f, 4f, -2f, 2f, 8f, 2f, hat.accent));
			int tuft = ARGB.scaleRGB(hat.accent, 0.9f);
			out.add(raw(-2.5f, 4.75f, -1.25f, 2.5f, 7.25f, 1.25f, tuft));
			out.add(raw(-1.25f, 4.75f, -2.5f, 1.25f, 7.25f, 2.5f, tuft));
			out.add(raw(-1.25f, 8f, -1.25f, 1.25f, 8.5f, 1.25f, tuft));
		}
		return out;
	}

	/**
	 * Reindeer antlers on a headband (showing the helmet): each a beam climbing outwards in steps with tines branching
	 * off, light at their tips; furry ears below them, and a jingle bell on a bow at the side.
	 */
	private static void reindeerHat(List<Box> out, Hat hat, HelmetLook helmet) {
		headband(out, helmet, hat.main[2], -1f);
		int antler = hat.main[0];
		int tip = hat.main[1];
		// Beam steps and tines on the right (x0, y0, x1, y1 in voxels up), mirrored for the left.
		float[][] beam = {{4.5f, 1f, 6.5f, 4f}, {5.5f, 4f, 7.5f, 7f}, {6.5f, 7f, 8.5f, 10f}, {7.5f, 10f, 9f, 12.5f}};
		float[][] tines = {{8.5f, 5f, 11f, 6f}, {10f, 6f, 11f, 8.5f}, {4f, 8f, 6.5f, 9f}, {4f, 9f, 5f, 11f}};
		for (int s = -1; s <= 1; s += 2) {
			for (float[] b : beam) {
				out.add(up(s > 0 ? b[0] : -b[2], b[1], -1.75f, s > 0 ? b[2] : -b[0], b[3], -0.25f, antler, false));
			}
			for (float[] t : tines) {
				out.add(up(s > 0 ? t[0] : -t[2], t[1], -1.6f, s > 0 ? t[2] : -t[0], t[3], -0.4f, antler, false));
			}
			// The tips, lighter.
			for (float[] t : new float[][]{{7.5f, 12.5f, 9f}, {10f, 8.5f, 11f}, {4f, 11f, 5f}}) {
				out.add(up(s > 0 ? t[0] : -t[2], t[1], -1.6f, s > 0 ? t[2] : -t[0], t[1] + 0.75f, -0.4f, tip, false));
			}
			// A front tine low on the beam.
			out.add(up(s * 5.5f - 0.5f, 2.5f, -4f, s * 5.5f + 0.5f, 3.5f, -1.75f, antler, false));
			// A furry ear sticking out sideways under the antler.
			out.add(up(s < 0 ? -12f : 8.75f, -2f, -1.5f, s < 0 ? -8.75f : 12f, 0f, 0.5f, hat.accent, false));
			out.add(up(s < 0 ? -11.5f : 9.25f, -1.5f, -1.6f, s < 0 ? -9.25f : 11.5f, -0.5f, -1.5f, lighter(hat.accent), false));
		}
		// The bow the bell hangs from.
		out.add(up(-10.5f, -0.5f, -3f, -9.25f, 1.5f, -1.75f, hat.second, false));
		out.add(up(-10.5f, -0.5f, -0.25f, -9.25f, 1.5f, 1f, hat.second, false));
		out.add(up(-10.75f, 0f, -1.75f, -9.25f, 1f, -0.25f, ARGB.scaleRGB(hat.second, 0.8f), false));
	}

	private static List<Box> jingleBell(Hat hat) {
		int metal = hat.third;
		return List.of(raw(-0.3f, 0f, -0.3f, 0.3f, 0.6f, 0.3f, ARGB.scaleRGB(metal, 0.7f)), raw(-1.1f, 0.6f, -1.1f, 1.1f, 2.8f, 1.1f, metal),
				raw(-1.4f, 1f, -0.8f, 1.4f, 2.4f, 0.8f, metal), raw(-0.8f, 1f, -1.4f, 0.8f, 2.4f, 1.4f, metal),
				raw(-0.7f, 2.1f, -1.45f, 0.7f, 2.3f, -1.4f, 0xFF2A2A2E), raw(-0.6f, 1.1f, -1.45f, -0.2f, 1.5f, -1.4f, lighter(metal)));
	}

	/** An elf's hat: a cuff showing the helmet with zigzag points, and a tall cone leaning back to a curling tip with a bell. */
	private static void elfHat(List<Box> out, Hat hat, HelmetLook helmet) {
		int cuff = hat.main[2];
		ring(out, helmet, 2, 1f, 9f, 10.25f, (i, row) -> cuff);
		for (int side = 0; side < 4; side++) {
			List<Box> part = new ArrayList<>();
			for (float x : new float[]{-7.5f, -2.5f, 2.5f, 7.5f}) {
				part.add(up(x - 1.5f, -2f, -10.25f, x + 1.5f, -1f, -9.5f, cuff, false));
				part.add(up(x - 0.6f, -3f, -10.25f, x + 0.6f, -2f, -9.5f, cuff, false));
			}
			turn(part, side, out);
		}
		boolean striped = hat == Hat.WINTER_ELF_STRIPED;
		for (int k = 0; k < ELF_LAYERS; k++) {
			float half = 8f - 1.1f * k;
			float z = ELF_LEAN * k;
			int color = striped && k % 2 == 1 ? hat.main[1] : hat.main[0];
			out.add(up(-half, 1f + 1.5f * k, z - half, half, 2.5f + 1.5f * k, z + half, color, false));
		}
	}

	private static List<Box> elfTip(Hat hat, int k) {
		float half = 1.75f - 0.45f * k;
		List<Box> out = new ArrayList<>();
		out.add(raw(-half, -3f, -half, half, 0f, half, hat == Hat.WINTER_ELF_STRIPED && k % 2 == 0 ? hat.main[1] : hat.main[0]));
		if (k == 2) {
			out.add(raw(-1f, -4.75f, -1f, 1f, -3f, 1f, hat.accent));
			out.add(raw(-0.6f, -4.5f, -1.05f, -0.2f, -4.1f, -1f, lighter(hat.accent)));
		}
		return out;
	}

	/**
	 * A small New Year's top hat worn tilted to one side: a band and a brim, glittering with sparkles that twinkle, and
	 * the elastic down the cheeks showing the helmet.
	 */
	private static void sparkleHat(List<Box> out, Hat hat, HelmetLook helmet) {
		float cx = 2f;
		// A round brim rolled up a little at the sides.
		round(out, cx, 0f, 0.75f, 7f, 1.5f, hat.main[1]);
		for (int s = -1; s <= 1; s += 2) {
			out.add(up(s < 0 ? cx - 7.75f : cx + 7f, 0.5f, -5f, s < 0 ? cx - 7f : cx + 7.75f, 1.5f, 5f, hat.main[1], false));
		}
		float lean = 0.35f;
		int layers = 7;
		for (int k = 0; k < layers; k++) {
			round(out, cx + lean * k, 0.75f + 1.25f * k, 2f + 1.25f * k, 4.5f, 1f, k < 2 ? hat.accent : hat.main[0]);
		}
		// The top's edge standing out a little.
		round(out, cx + lean * (layers - 1), 0.75f + 1.25f * layers, 1.1f + 1.25f * layers, 4.75f, 1f, lighter(hat.main[0]));
		// Sparkles on the front and the right, a few lit at a time.
		double seconds = System.nanoTime() % 1_000_000_000_000L / 1e9;
		float[][] spots = {{-2.5f, 4f}, {1.5f, 6f}, {3f, 3.5f}, {-1f, 8f}, {2.5f, 8.75f}, {-3f, 6.75f}};
		for (int i = 0; i < spots.length; i++) {
			float y = spots[i][1];
			float x = cx + lean * ((y - 0.75f) / 1.25f) + spots[i][0];
			boolean lit = ((int) (seconds * 4.0) + i * 2) % 5 < 2;
			Box front = up(x - 0.35f, y - 0.35f, -4.65f, x + 0.35f, y + 0.35f, -4.5f, lit ? hat.second : lighter(hat.main[0]), false);
			out.add(lit ? front.glowing() : front);
			float sx = cx + lean * ((y - 0.75f) / 1.25f) + 4.5f;
			Box side = up(sx, y - 0.35f, spots[i][0] - 0.35f, sx + 0.15f, y + 0.35f, spots[i][0] + 0.35f,
					lit ? lighter(hat.main[0]) : hat.second, false);
			out.add(lit ? side : side.glowing());
		}
		boolean shows = helmet != null;
		for (int s = -1; s <= 1; s += 2) {
			float x0 = s < 0 ? -9.25f : 8f;
			out.add(up(x0, -13f, -0.5f, x0 + 1.25f, 0f, 0.5f, shows ? helmet.tile(s < 0 ? 0 : 1, 0) : 0xFFE8E8E8, shows));
		}
	}

	// ---------------------------------------------------------------- geometry

	/** A box in voxels with y measured up from the top of the head, -z the front. */
	private static Box up(float x0, float yUp0, float z0, float x1, float yUp1, float z1, int color, boolean helmet) {
		return new Box(x0 * V, HEAD_TOP - yUp1 * V, z0 * V, x1 * V, HEAD_TOP - yUp0 * V, z1 * V, color, helmet);
	}

	/** A box in voxels as given (y down), for things hanging. */
	static Box raw(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
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
			out.add(new Box(x0, b.y0, z0, x1, b.y1, z1, b.color, b.helmet, b.glow, b.material));
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

	static int lighter(int color) {
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

	static CharmPendulum[] fresh() {
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
		SPINS.clear();
	}
}
