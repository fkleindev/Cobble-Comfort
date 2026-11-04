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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static dev.cobblecomfort.features.wardrobe.HatLayer.V;

/**
 * Draws what a player wears on the face, in the head's space: voxels half a skin pixel big, rows counted down from the
 * top of the head (the eyes at rows 8 and 9, the mouth at 12 and 13), depth out from the face. The front of each piece
 * is drawn from a little picture, one character a voxel; straps and arms go round the sides. Hidden under a helmet and
 * inside a jack o' lantern hat. Paint on the skin is drawn first, under the rest. Goggles flip up while looking up. A
 * mask's feather and a bandana's knot ends swing on pendulums, like the moving parts of
 * hats.
 */
final class FaceLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	/** On a player drawn as a wardrobe tile: the piece to show there instead of their own. */
	static final RenderStateDataKey<Face> PREVIEW = RenderStateDataKey.create(() -> "cobblecomfort:face_preview");
	/** Half the head's width with its outer layer, where arms and straps run along the sides (voxels). */
	private static final float SIDE = 9f;
	/** Where the front of the head's outer layer is; pieces lie on it. */
	private static final float FRONT = 8f;
	private static final long FORGET_NANOS = 2_000_000_000L;
	/** The pendulums of the swinging parts, per player. */
	private static final Int2ObjectOpenHashMap<CharmPendulum[]> SWINGS = new Int2ObjectOpenHashMap<>();
	/** How far up each player's goggles are flipped. */
	private static final Int2ObjectOpenHashMap<Flip> FLIPS = new Int2ObjectOpenHashMap<>();
	/** Looking up further than this (degrees) flips goggles up; they come down again below {@link #FLIP_DOWN}. */
	private static final float FLIP_UP = -30f;
	private static final float FLIP_DOWN = -15f;
	/** How fast the goggles and then their loupe swing (per second). */
	private static final float FLIP_SPEED = 9f;
	private static final float LOUPE_SPEED = 4.5f;
	/** How far the goggles and the loupe swing up (degrees). */
	private static final float GOGGLES_ANGLE = -35f;
	/** How far the goggles slide up onto the forehead (voxels). */
	private static final float GOGGLES_LIFT = 5f;
	private static final float LOUPE_ANGLE = -110f;
	/** How far out from the face paint lies: just on the skin's outer layer. */
	private static final float SKIN_D0 = 1.02f;
	private static final float SKIN_D1 = 1.12f;

	FaceLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
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
		Face preview = state.getData(PREVIEW);
		List<Face> faces = preview != null ? List.of(preview)
				: WardrobeScreen.isTile(state) ? List.of() : WardrobeFeature.INSTANCE.facesFor(player);
		if (faces.isEmpty()) {
			return;
		}
		// A helmet worn over it hides it, unless a hat hides the helmet; so does a pumpkin over the head.
		if (preview == null && (!state.headEquipment.isEmpty() && !WardrobeFeature.INSTANCE.hidesHelmet(state)
				|| WardrobeFeature.INSTANCE.hatFor(player) instanceof Hat hat && hat.style == Hat.Style.PUMPKIN)) {
			return;
		}
		pose.pushPose();
		getParentModel().root().translateAndRotate(pose);
		getParentModel().head.translateAndRotate(pose);
		// A tile shows a piece not worn: it gets its own pendulums (which just hang in a screen).
		CharmPendulum[] pendulums = preview != null ? HatLayer.fresh() : pendulums(state.id);
		HatLayer.Chains chains = new HatLayer.Chains(pose, collector, pendulums, WardrobeFeature.INSTANCE.damping(), light,
				preview != null ? -1 : state.id, 0f);
		// Goggles flip up while looking up; a tile shows them down.
		Flip flip = preview != null || faces.stream().noneMatch(face -> face.style == Face.Style.GOGGLES) ? Flip.DOWN : flip(state.id, xRot);
		for (Face face : faces) {
			draw(pose, collector, face, light, flip);
			swinging(chains, face);
		}
		pose.popPose();
	}

	/** The swinging parts of one player's face pieces. */
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
		FLIPS.clear();
	}

	/** How far a player's goggles (and then their loupe) are flipped up: 0 down over the eyes, 1 up on the forehead. */
	static final class Flip {
		static final Flip DOWN = new Flip();
		boolean up;
		float goggles;
		float loupe;
		long last;
	}

	/** Moves a player's goggles towards where their looking up or down puts them. */
	private static Flip flip(int entityId, float xRot) {
		long now = System.nanoTime();
		Flip flip = FLIPS.get(entityId);
		if (flip == null) {
			flip = new Flip();
			flip.last = now;
			FLIPS.put(entityId, flip);
			if (FLIPS.size() > 64) {
				FLIPS.int2ObjectEntrySet().removeIf(e -> now - e.getValue().last > FORGET_NANOS);
			}
		}
		// A little apart, so they don't flap at the edge.
		if (xRot < FLIP_UP) {
			flip.up = true;
		} else if (xRot > FLIP_DOWN) {
			flip.up = false;
		}
		float dt = Math.min((now - flip.last) / 1e9f, 0.1f);
		flip.last = now;
		float target = flip.up ? 1f : 0f;
		flip.goggles += (target - flip.goggles) * (1f - (float) Math.exp(-FLIP_SPEED * dt));
		// The loupe swings after them, and comes down only once they are.
		float loupeTarget = flip.up ? 1f : Math.min(flip.goggles * 2f, 1f);
		flip.loupe += (loupeTarget - flip.loupe) * (1f - (float) Math.exp(-LOUPE_SPEED * dt));
		return flip;
	}

	/**
	 * The parts of a piece that move: the masquerade mask's feather springs back as the head turns and nods (pendulums 0
	 * and 1), the ends of a bandana's knot swing behind the head (2 to 5).
	 */
	private static void swinging(HatLayer.Chains chains, Face face) {
		switch (face.style) {
			case MASQUERADE -> chains.stand(0, point(9.5f, 5f, -FRONT - 2.125f), 4f, 2, new Vector3f(0.06f, 0f, 0f),
					k -> feather(face, k));
			case BANDANA -> {
				// Cloth: soft, so it bends and settles soon.
				double damping = chains.damping() + HatLayer.STRAND_DAMPING;
				chains.hang(2, point(-0.875f, 12.5f, SIDE + 1.375f), 1.75f, 2, damping, 0.6f, k -> knotEnd(k, face.main[0], 1.75f));
				chains.hang(4, point(0.875f, 12.5f, SIDE + 1.375f), 1.25f, 2, damping, 0.6f, k -> knotEnd(k, face.main[1], 1.25f));
			}
			default -> {
			}
		}
	}

	/**
	 * One of the feather's two pieces, standing up from where it is stuck in the mask ({@code k} 0 the lower, quill and
	 * all; 1 the upper with its tip): y up is negative, the piece from -its length to 0.
	 */
	private static List<Box> feather(Face f, int k) {
		int shade = ARGB.scaleRGB(f.second, 0.75f);
		String[] rows = k == 0 ? new String[]{" Ff", " Ff", " f ", " f "} : new String[]{"  F", " FF", " FF", "FFf", "FFf"};
		// The lower piece is 4 voxels tall, the upper one stands on it and is 5 with its tip.
		float top = k == 0 ? -4f : -5f;
		List<Box> out = new ArrayList<>();
		for (int r = 0; r < rows.length; r++) {
			for (int i = 0; i < 3; i++) {
				char c = rows[r].charAt(i);
				if (c != ' ') {
					out.add(HatLayer.raw(i - 1.5f, top + r, -0.125f, i - 0.5f, top + r + 1f, 0.125f, c == 'F' ? f.second : shade));
				}
			}
		}
		return out;
	}

	/** One of the two pieces of an end of a bandana's knot, hanging down: the lower one a little darker. */
	private static List<Box> knotEnd(int k, int color, float length) {
		int c = k == 0 ? color : ARGB.scaleRGB(color, 0.85f);
		float half = k == 0 ? 0.625f : 0.55f;
		return List.of(HatLayer.raw(-half, 0f, -0.375f, half, length + (k == 0 ? 0.2f : 0f), 0.375f, c));
	}

	/** Draws a piece in the head's space, its moving parts left out (they are drawn with the pendulums). */
	static void draw(PoseStack pose, SubmitNodeCollector collector, Face face, int light, Flip flip) {
		List<Box> boxes = new ArrayList<>();
		switch (face.style) {
			case GLASSES -> glasses(boxes, face);
			case SUNGLASSES -> sunglasses(boxes, face);
			case EYEPATCH -> eyepatch(boxes, face);
			case MASQUERADE -> masquerade(boxes, face);
			case BANDANA -> bandana(boxes, face);
			case MUSTACHE -> mustache(boxes, face);
			case HALLOWEEN_MASK -> halloweenMask(boxes, face);
			case GOGGLES -> goggles(pose, collector, boxes, face, light, flip);
			case PIPE -> pipe(boxes, face);
			case BLUSH, FRECKLES, WAR_PAINT -> paint(boxes, face);
			case REINDEER_NOSE -> nose(boxes, face, 1f, true);
			case CLOWN_NOSE -> {
				nose(boxes, face, 1.4f, false);
				// The elastic round the head.
				boxes.add(front(-SIDE, 10.75f, 1f, -2f, 11.25f, 1.25f, face.second));
				boxes.add(front(2f, 10.75f, 1f, SIDE, 11.25f, 1.25f, face.second));
				side(boxes, -1, 10.75f, 11.25f, 0.4f, face.second);
				side(boxes, 1, 10.75f, 11.25f, 0.4f, face.second);
				band(boxes, 10.75f, 11.25f, SIDE, 0.4f, face.second);
			}
		}
		HatLayer.submit(pose, collector, boxes, null, light);
	}

	// ---------------------------------------------------------------- eyes

	/** Round glasses: two rings of wire over the eyes, a glint on each lens, the arms back to the ears. */
	private static void glasses(List<Box> out, Face f) {
		art(out, -9f, 6f, 1.25f, 2f, c -> c == '#' ? f.main[0] : c == '*' ? f.accent : 0, "",
				"   ####    ####   ",
				"  #*   ####*   #  ",
				"###    #  #    ###",
				"  #    #  #    #  ",
				"   ####    ####   ");
		arms(out, 8f, 9f, f.main[1]);
	}

	/** Sunglasses: dark lenses under a heavy brow of a frame, a sheen across them. */
	private static void sunglasses(List<Box> out, Face f) {
		art(out, -9f, 6f, 1.25f, 2f, c -> switch (c) {
			case '#' -> f.main[0];
			case 'l' -> f.main[1];
			case 'h' -> f.main[2];
			case '*' -> f.accent;
			default -> 0;
		}, "",
				" ################ ",
				"##*hllll##*hllll##",
				"  hlllll  hlllll  ",
				"  llllll  llllll  ",
				"   llll    llll   ");
		arms(out, 7f, 8f, f.main[0]);
	}

	/** An eye patch over one eye, its strap slanting over the brow and round the back of the head. */
	private static void eyepatch(List<Box> out, Face f) {
		art(out, -9f, 3f, 1.25f, 1.75f, c -> switch (c) {
			case 'P' -> f.main[0];
			case 'p' -> f.main[1];
			case 's' -> f.main[2];
			case 'x' -> f.accent;
			default -> 0;
		}, "",
				"ss                ",
				"  ss              ",
				"    sss           ",
				"       ssssPPPP   ",
				"          PPPPPPss",
				"          PPxxPP  ",
				"          pPPPPp  ",
				"           pppp   ");
		side(out, -1, 3f, 4f, 0.5f, f.main[2]);
		side(out, 1, 7f, 8f, 0.5f, f.main[2]);
		// Across the back, from low on the patch's side to high on the other.
		int steps = 12;
		for (int i = 0; i < steps; i++) {
			float x0 = -SIDE - 0.5f + i * (2f * SIDE + 1f) / steps;
			float t = 3f + 4f * i / (steps - 1);
			out.add(head(x0, t, SIDE, x0 + (2f * SIDE + 1f) / steps, t + 1f, SIDE + 0.5f, f.main[2]));
		}
	}

	/** A masquerade mask: wings sweeping up at the sides, a trim along the top with a jewel, a feather on one side. */
	private static void masquerade(List<Box> out, Face f) {
		art(out, -11f, 2f, 1.25f, 2f, c -> switch (c) {
			case 'M' -> f.main[0];
			case 'm' -> f.main[1];
			case 'T' -> f.accent;
			case 'G' -> f.second;
			default -> 0;
		}, "",
				"M                    M",
				"MM                  MM",
				"MMM                MMM",
				"mMMTTTTTTTGGTTTTTTTMMm",
				" mMMMMMMMMMMMMMMMMMMm ",
				"  mMM    MMMM    MMm  ",
				"   mM    MMMM    Mm   ",
				"   mMM   MMMM   MMm   ",
				"    mmmmmm  mmmmmm    ");
		side(out, -1, 6f, 6.75f, 0.5f, f.main[1]);
		side(out, 1, 6f, 6.75f, 0.5f, f.main[1]);
		band(out, 6f, 6.75f, SIDE, 0.5f, f.main[1]);
	}

	/** A Halloween half mask over the eyes and nose: a carved pumpkin glowing inside, or a skull. */
	private static void halloweenMask(List<Box> out, Face f) {
		boolean pumpkin = f == Face.HALLOWEEN_MASK_PUMPKIN;
		String[] rows = pumpkin ? new String[]{
				"        gg        ",
				"   OOoOOOOOOoOO   ",
				" OoOOoOOOOOOoOOoO ",
				"OOoOOoOOOOOOoOOoOO",
				"OOoOOoOOOOOOoOOoOO",
				"OOoOYYOOOOOOYYOoOO",
				"OOoYYYYOOOOYYYYoOO",
				"OOYYYYYYOOYYYYYYOO",
				"OOoOOoOOOOOOoOOoOO",
				"OOoOOoOOYYOOoOOoOO",
				"OOoOOoOYYYYOoOOoOO",
				" O  O  O  O  O  O "} : new String[]{
				"    OOOOOOOOOO    ",
				"  OOOOOOOOOOOOOO  ",
				" OOOOOOgOOOOOOOOO ",
				"OOOOOOOgOOOOOOOOOO",
				"OOOOOOOOgOOOOOOOOO",
				"OOOYYYYOOOOYYYYOOO",
				"OOYYYYYYOOYYYYYYOO",
				"OOYYYYYYOOYYYYYYOO",
				"OoOYYYYOOOOYYYYOoO",
				"OoOOOOOOYYOOOOOOoO",
				" oOOOOOYOOYOOOOOo ",
				"  oOoOoOoOoOoOoO  "};
		art(out, -9f, 1f, 1.25f, 2f, c -> switch (c) {
			case 'O' -> f.main[0];
			case 'o' -> f.main[1];
			case 'Y' -> f.accent;
			case 'g' -> f.second;
			default -> 0;
		}, pumpkin ? "Y" : "", rows);
		side(out, -1, 5f, 6f, 0.5f, f.main[2]);
		side(out, 1, 5f, 6f, 0.5f, f.main[2]);
		band(out, 5f, 6f, SIDE, 0.5f, f.main[2]);
	}

	// ---------------------------------------------------------------- nose and mouth

	/** A bandana over the nose and mouth, its point hanging below the chin, knotted at the back. */
	private static void bandana(List<Box> out, Face f) {
		art(out, -9f, 10f, 1.25f, 2f, c -> switch (c) {
			case 'B' -> f.main[0];
			case 'b' -> f.main[1];
			case 'w' -> f.accent;
			default -> 0;
		}, "",
				"BBBBBBBBBBBBBBBBBB",
				"BBwBBBBBwBBBBBBwBB",
				"bBBBBwBBBBBBwBBBBb",
				"BBBbbBBBBBBBBbbBBB",
				"BwBBBBBBwBBBBBBBwB",
				"BBBBBbbbbbbbbBBBBB",
				"  BBBBwBBBBwBBBB  ",
				"    BBBBBBBBBB    ",
				"      BbbbbB      ",
				"        bb        ");
		side(out, -1, 10f, 13f, 0.75f, f.main[1]);
		side(out, 1, 10f, 13f, 0.75f, f.main[1]);
		band(out, 10f, 12.5f, SIDE, 0.75f, f.main[1]);
		// The knot (its two ends swing, see swinging).
		out.add(head(-1.5f, 10f, SIDE + 0.75f, 1.5f, 12.5f, SIDE + 2f, f.main[0]));
	}

	/** A moustache under the nose: twirled up at the ends, or a bushy walrus one over the mouth. */
	private static void mustache(List<Box> out, Face f) {
		boolean walrus = f == Face.MUSTACHE_WALRUS_GREY || f == Face.MUSTACHE_WALRUS_GINGER;
		String[] rows = walrus ? new String[]{
				"    MMMMMMMMMM    ",
				"   MMMMMMMMMMMM   ",
				"   MmMMmMMmMMmM   ",
				"   MmMmM  MmMmM   ",
				"   m m      m m   "} : new String[]{
				"  M            M  ",
				"  MM   MMMM   MM  ",
				"   MMMMMMMMMMMM   ",
				"    mmmm  mmmm    "};
		art(out, -9f, walrus ? 11f : 10f, 1.25f, 2.25f, c -> c == 'M' ? f.main[0] : c == 'm' ? f.main[1] : 0, "", rows);
	}

	/** A round nose on the middle of the face, {@code size} times the reindeer's, with a shine; glowing if it {@code glows}. */
	private static void nose(List<Box> out, Face f, float size, boolean glows) {
		float s = size;
		float t = 11f;
		List<Box> nose = new ArrayList<>();
		nose.add(front(-1.5f * s, t - 1.25f * s, 1f, 1.5f * s, t + 1.25f * s, 1f + 2f * s, f.main[0]));
		nose.add(front(-1f * s, t - 1.75f * s, 1f, 1f * s, t + 1.75f * s, 1f + 2.25f * s, f.main[0]));
		nose.add(front(-1.75f * s, t - 0.75f * s, 1f, 1.75f * s, t + 0.75f * s, 1f + 1.75f * s, f.main[0]));
		nose.add(front(-1f * s, t + 1.25f * s, 1f, 1f * s, t + 1.75f * s, 1f + 2f * s, f.main[1]));
		nose.add(front(-1f * s, t - 1.25f * s, 1f + 2.25f * s, -0.25f * s, t - 0.5f * s, 1.15f + 2.25f * s, f.accent));
		for (Box box : nose) {
			out.add(glows ? box.glowing() : box);
		}
	}

	/**
	 * Steampunk goggles: two deep eyecups with lenses set in, on a leather strap; a loupe on a little arm hangs in front
	 * of one of them. Looking up flips them up onto the forehead on their hinge at the strap, the loupe after them.
	 */
	private static void goggles(PoseStack pose, SubmitNodeCollector collector, List<Box> out, Face f, int light, Flip flip) {
		// The strap stays round the head.
		side(out, -1, 6f, 8f, 0.75f, f.main[2]);
		side(out, 1, 6f, 8f, 0.75f, f.main[2]);
		band(out, 6f, 8f, SIDE, 0.75f, f.main[2]);
		// The buckles where the cups hang from it.
		out.add(head(-SIDE - 1f, 6.25f, -FRONT - 1f, -SIDE - 0.5f, 7.75f, -FRONT + 0.5f, f.main[1]));
		out.add(head(SIDE + 0.5f, 6.25f, -FRONT - 1f, SIDE + 1f, 7.75f, -FRONT + 0.5f, f.main[1]));

		List<Box> cups = new ArrayList<>();
		Palette frame = c -> c == 'F' ? f.main[0] : c == 'f' ? f.main[1] : c == 'r' ? f.accent : 0;
		Palette lens = c -> c == 'L' ? f.main[3] : c == 'l' ? ARGB.scaleRGB(f.main[3], 0.7f) : c == 'g' ? f.accent : 0;
		String[] rows = {
				"  ffFrff  ffrFff  ",
				" fFLLLLFffFLLLLFf ",
				"fFLgLLLLFFLgLLLLFf",
				"fFLLLLLlFFLLLLLlFf",
				" fFLLllFffFLLllFf ",
				"  ffFFff  ffFFff  "};
		art(cups, -9f, 6f, 1.25f, 3.75f, frame, "", rows);
		art(cups, -9f, 6f, 1.25f, 3.25f, lens, "", rows);
		// The arms from the cups to the buckles.
		cups.add(head(-SIDE - 0.5f, 7.5f, -FRONT - 2.5f, -SIDE + 0.5f, 8.5f, -FRONT - 0.5f, f.main[1]));
		cups.add(head(SIDE - 0.5f, 7.5f, -FRONT - 2.5f, SIDE + 0.5f, 8.5f, -FRONT - 0.5f, f.main[1]));
		// The loupe's hinge on top of the right cup.
		cups.add(front(4.5f, 6f, 3.5f, 6.5f, 6.75f, 4.25f, f.main[1]));

		List<Box> loupe = new ArrayList<>();
		art(loupe, 3f, 6f, 3.9f, 4.4f, c -> c == 'a' ? f.main[1] : c == 'R' ? f.main[0] : c == 'L' ? f.second : c == 'g' ? f.accent : 0, "",
				"  aa  ",
				" RRRR ",
				"RgLLLR",
				"RLLLLR",
				" RRRR ");

		pose.pushPose();
		// Pushed up onto the forehead: slid up, the bottoms tipped out.
		float up = smooth(flip.goggles);
		pose.translate(0f, -GOGGLES_LIFT * V * up, -0.5f * V * up);
		turn(pose, point(0f, 6.5f, -FRONT - 1f), GOGGLES_ANGLE * up);
		HatLayer.submit(pose, collector, cups, null, light);
		turn(pose, point(0f, 6.125f, -FRONT - 4.15f), LOUPE_ANGLE * smooth(flip.loupe));
		HatLayer.submit(pose, collector, loupe, null, light);
		pose.popPose();
	}

	/** Eases a swing in and out. */
	private static float smooth(float t) {
		return t * t * (3f - 2f * t);
	}

	/** Turns what follows {@code degrees} about a line across the head through {@code pivot}; negative turns the front up. */
	private static void turn(PoseStack pose, Vector3f pivot, float degrees) {
		if (degrees == 0f) {
			return;
		}
		pose.translate(pivot.x, pivot.y, pivot.z);
		pose.rotate(new Quaternionf().rotateX((float) Math.toRadians(degrees)));
		pose.translate(-pivot.x, -pivot.y, -pivot.z);
	}

	/**
	 * A tobacco pipe in the corner of the mouth: its stem going out and down, the bowl standing up at its end with an
	 * ember glowing in it. A corncob's bowl is knobbly. (The smoke rises from {@link #PIPE_BOWL}.)
	 */
	private static void pipe(List<Box> out, Face f) {
		int bowl = f.main[0];
		int shade = f.main[1];
		int stem = f.main[2];
		// The mouthpiece, and the stem bending down to the bowl.
		out.add(front(3.5f, 12.5f, 0.75f, 4.5f, 13.25f, 2.5f, stem));
		out.add(front(3.5f, 12.9f, 2.5f, 4.5f, 13.65f, 4f, stem));
		out.add(front(3.45f, 13.3f, 4f, 4.55f, 14.1f, 5f, shade));
		// The bowl: rounder in the middle, a darker foot.
		out.add(front(2.75f, 11f, 5f, 5.25f, 14.5f, 7.5f, bowl));
		out.add(front(2.5f, 11.5f, 5.25f, 5.5f, 14f, 7.25f, bowl));
		out.add(front(3f, 14.5f, 5.25f, 5f, 15.1f, 7.25f, shade));
		// The rim, and the ember inside it.
		out.add(front(2.75f, 10.75f, 5f, 5.25f, 11f, 5.5f, f.accent));
		out.add(front(2.75f, 10.75f, 7f, 5.25f, 11f, 7.5f, f.accent));
		out.add(front(2.75f, 10.75f, 5.5f, 3.25f, 11f, 7f, f.accent));
		out.add(front(4.75f, 10.75f, 5.5f, 5.25f, 11f, 7f, f.accent));
		out.add(front(3.25f, 10.85f, 5.5f, 4.75f, 11.05f, 7f, f.second).glowing());
		out.add(front(3.75f, 10.8f, 6f, 4.25f, 11.1f, 6.5f, 0xFFFFD84A).glowing());
		if (f == Face.PIPE_CORNCOB) {
			// Rows of kernels standing out of the cob.
			for (int r = 0; r < 3; r++) {
				float t = 11.5f + r;
				for (int i = 0; i < 2; i++) {
					float x = 2.85f + i * 1.3f + (r % 2) * 0.6f;
					out.add(front(x, t, 7.25f, x + 0.6f, t + 0.6f, 7.65f, f.accent));
				}
				float a = (r % 2) * 0.5f;
				float b = ((r + 1) % 2) * 0.5f;
				out.add(front(2.2f, t + a, 5.75f, 2.5f, t + a + 0.6f, 6.75f, f.accent));
				out.add(front(5.5f, t + b, 5.75f, 5.8f, t + b + 0.6f, 6.75f, f.accent));
			}
		} else {
			// A band of the darker wood round the bowl.
			out.add(front(2.45f, 12.75f, 5.2f, 5.55f, 13.1f, 7.3f, shade));
		}
	}

	/** Where smoke rises from a pipe, in the head's space (blocks). */
	static final Vector3f PIPE_BOWL = point(4f, 10.75f, -FRONT - 6.25f);

	// ---------------------------------------------------------------- skin

	/** Paint on the skin: blush on the cheeks, freckles over the nose and cheeks, or stripes of war paint. */
	private static void paint(List<Box> out, Face f) {
		Palette palette = c -> switch (c) {
			case 'P' -> f.main[0];
			case 'p' -> f.main[1];
			case 'a' -> f.accent;
			default -> 0;
		};
		switch (f.style) {
			case BLUSH -> art(out, -8f, 10f, SKIN_D0, SKIN_D1, palette, "",
					" aPPa      aPPa ",
					"aPpPPa    aPPpPa",
					" aPPa      aPPa ");
			case FRECKLES -> art(out, -8f, 10f, SKIN_D0, SKIN_D1, palette, "",
					"  p P p  P p P  ",
					" P p  P  P  p P ",
					"  P  p      P p ");
			default -> {
				// Two stripes under each eye with a light one between them, a line down between the eyes and one on the chin.
				art(out, -8f, 10f, SKIN_D0, SKIN_D1, palette, "",
						"pPPPPp    pPPPPp",
						"aaaaa      aaaaa",
						"pPPPp      pPPPp");
				art(out, -8f, 5f, SKIN_D0, SKIN_D1, palette, "",
						"       PP       ",
						"       PP       ",
						"       pp       ");
				art(out, -8f, 14f, SKIN_D0, SKIN_D1, palette, "",
						"      pPPp      ",
						"       PP       ");
			}
		}
	}

	// ---------------------------------------------------------------- building

	/** The arms of glasses: from the hinges back along both sides to the ears, rows {@code t0} to {@code t1}. */
	private static void arms(List<Box> out, float t0, float t1, int color) {
		for (int s = -1; s <= 1; s += 2) {
			out.add(s < 0 ? head(-SIDE - 0.75f, t0, -FRONT - 2f, -SIDE, t1, 1f, color)
					: head(SIDE, t0, -FRONT - 2f, SIDE + 0.75f, t1, 1f, color));
		}
	}

	/** A strap along one side of the head ({@code s} -1 or 1), from the front to the back, {@code thick} voxels out. */
	private static void side(List<Box> out, int s, float t0, float t1, float thick, int color) {
		out.add(s < 0 ? head(-SIDE - thick, t0, -FRONT - 2f, -SIDE, t1, SIDE + thick, color)
				: head(SIDE, t0, -FRONT - 2f, SIDE + thick, t1, SIDE + thick, color));
	}

	/** A strap straight across the back of the head, {@code z} voxels behind its middle. */
	private static void band(List<Box> out, float t0, float t1, float z, float thick, int color) {
		out.add(head(-SIDE - thick, t0, z, SIDE + thick, t1, z + thick, color));
	}

	/**
	 * A flat picture in front of the face, one voxel a character: its left edge at {@code x0}, its first row at row
	 * {@code t0}, from {@code d0} to {@code d1} out from the face. Runs of the same character are one box; a character
	 * with no colour (0) is left open, one in {@code glow} lights itself.
	 */
	private static void art(List<Box> out, float x0, float t0, float d0, float d1, Palette palette, String glow, String... rows) {
		for (int r = 0; r < rows.length; r++) {
			String row = rows[r];
			int start = 0;
			while (start < row.length()) {
				char c = row.charAt(start);
				int end = start + 1;
				while (end < row.length() && row.charAt(end) == c) {
					end++;
				}
				int color = c == ' ' ? 0 : palette.color(c);
				if (color != 0) {
					Box box = front(x0 + start, t0 + r, d0, x0 + end, t0 + r + 1, d1, color);
					out.add(glow.indexOf(c) >= 0 ? box.glowing() : box);
				}
				start = end;
			}
		}
	}

	/** What colour each character of a picture is (0: none). */
	private interface Palette {
		int color(char c);
	}

	/** A box in front of the face: x in voxels, rows down from the top of the head, depth out from the face. */
	private static Box front(float x0, float t0, float d0, float x1, float t1, float d1, int color) {
		return head(x0, t0, -FRONT - d1, x1, t1, -FRONT - d0, color);
	}

	/** A point in the head's space, given like {@link #head}. */
	private static Vector3f point(float x, float t, float z) {
		return new Vector3f(x * V, (t - 16f) * V, z * V);
	}

	/** A box in the head's space: x and z in voxels (-z the front), rows down from the top of the head. */
	private static Box head(float x0, float t0, float z0, float x1, float t1, float z1, int color) {
		return new Box(x0 * V, (t0 - 16f) * V, z0 * V, x1 * V, (t1 - 16f) * V, z1 * V, color, false);
	}
}
