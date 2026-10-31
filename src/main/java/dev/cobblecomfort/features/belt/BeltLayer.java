package dev.cobblecomfort.features.belt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.cobblecomfort.features.namecharm.CharmPendulum;
import dev.cobblecomfort.features.namecharm.NameCharmRenderer;
import dev.cobblecomfort.features.petplush.Torso;
import dev.cobblecomfort.features.plush.PlushHits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Draws the bundles on a player's belt: little leather pouches in the bundle's colour, puffed up as they fill, each
 * hanging from the belt on a short cord and swinging with {@link CharmPendulum}. A thin belt runs round the waist.
 */
final class BeltLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("cobblecomfort", "textures/misc/belt_bundle.png");
	private static final RenderType TYPE = RenderTypes.entityCutout(TEXTURE);
	/** An undyed bundle's leather. */
	private static final int UNDYED = 0xFFCD8A55;

	// In the body's space (blocks, y down, +z is behind the player), relative to the torso as drawn.
	/** The belt: how far above the bottom of the torso, its height and thickness. */
	private static final float BELT_UP = 0.05f;
	private static final float BELT_HEIGHT = 0.04f;
	private static final float BELT_THICKNESS = 0.012f;
	/** Where the cords are tied, sideways, as a share of the half width (the arms hang further out). */
	private static final float SIDE = 0.7f;
	private static final float CORD = 0.035f;
	private static final float CORD_WIDTH = 0.01f;

	// The pouch, empty and full: width, body height, depth.
	private static final float WIDTH_EMPTY = 0.14f;
	private static final float WIDTH_FULL = 0.22f;
	private static final float HEIGHT_EMPTY = 0.12f;
	private static final float HEIGHT_FULL = 0.18f;
	private static final float DEPTH_EMPTY = 0.07f;
	private static final float DEPTH_FULL = 0.17f;
	/** Tie and gathered top above the body. */
	private static final float NECK = 0.05f;

	// Weapons, in the body's space. Pendulum indices after the bundles'.
	static final int MELEE = BeltBundle.MAX;
	static final int BOW = BeltBundle.MAX + 1;
	static final int QUIVER = BeltBundle.MAX + 2;
	static final int PENDULUMS = BeltBundle.MAX + 3;
	/** Weapons swing stiffer than pouches. */
	private static final double GEAR_DAMPING = 2.5;
	/** How far outside the torso the weapons hang, and the bow further out than its quiver. */
	private static final float GEAR_OUT = 0.03f;
	private static final float BOW_OUT = 0.05f;
	private static final float MELEE_SCALE = 0.62f;
	/** Where the guard is on the item's diagonal (item units from its middle). */
	private static final float MELEE_GUARD = 0.19f;
	private static final float MELEE_LENGTH = 0.3f;
	private static final float MELEE_TILT = 25f;
	private static final float BOW_SCALE = 0.6f;
	/** How far below the belt the bow's middle hangs. */
	private static final float BOW_DROP = 0.24f;
	private static final float QUIVER_BACK = 0.13f;
	private static final float QUIVER_LENGTH = 0.36f;
	private static final float QUIVER_WIDTH = 0.1f;
	private static final float QUIVER_DEPTH = 0.08f;
	/** How far the rim sticks up over the belt. */
	private static final float QUIVER_ABOVE = 0.1f;
	private static final float QUIVER_TILT = 20f;
	private static final int QUIVER_COLOR = 0xFF8A5A33;
	/** Arrows shown: one per this many carried, at most {@link #MAX_ARROWS}. */
	private static final int ARROWS_EACH = 16;
	private static final int MAX_ARROWS = 4;
	private static final float ARROW_SCALE = 0.42f;
	/** The arrows' middle below the rim's anchor, so the fletching sticks out. */
	private static final float ARROW_DEPTH = 0.06f;
	private static final float ARROW_SPREAD = 0.018f;

	// Texture quarters, {u0, v0, u1, v1}.
	private static final float[] SIDE_UV = cell(0f, 0f);
	private static final float[] TOP_UV = cell(0.5f, 0f);
	private static final float[] TIE_UV = cell(0f, 0.5f);
	private static final float[] BOTTOM_UV = cell(0.5f, 0.5f);

	BeltLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
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
		BeltFeature feature = BeltFeature.INSTANCE;
		List<BeltBundle> bundles = feature.bundlesFor(player);
		BeltGear gear = feature.gearFor(player);
		if (bundles.isEmpty() && gear.empty()) {
			return;
		}
		CharmPendulum[] pendulums = feature.pendulums(state.id);
		long now = System.nanoTime();
		// Each step makes the pouches bob a little; a hit squeezes them like the plushes.
		float speed = Math.min(1f, state.walkAnimationSpeed);
		float bob = 1f + 0.06f * Mth.cos(state.walkAnimationPos * 0.6662f * 2f) * speed;
		float squish = bob * PlushHits.squish(player, now);

		pose.pushPose();
		// Like vanilla layers: through the root first, which animated player models (Fresh Animations via EMF) move.
		getParentModel().root().translateAndRotate(pose);
		ModelPart body = getParentModel().body;
		Torso torso = Torso.of(body).inflated(Torso.worn(state, true));
		body.translateAndRotate(pose);
		float beltY = torso.bottom() - BELT_UP;
		submitBelt(pose, collector, light, torso, beltY);
		for (int i = 0; i < bundles.size(); i++) {
			BeltBundle bundle = bundles.get(i);
			// Right hip behind, left hip behind, then the same in front (the player's right is -x).
			float side = i % 2 == 0 ? -1f : 1f;
			boolean behind = i < 2;
			Vector3f anchor = new Vector3f(torso.centerX() + side * torso.halfWidth() * SIDE, beltY + BELT_HEIGHT / 2f,
					behind ? torso.back() + BELT_THICKNESS : torso.front() - BELT_THICKNESS);
			float fill = Mth.clamp(bundle.fill(), 0f, 1f);
			float height = NECK + Mth.lerp(fill, HEIGHT_EMPTY, HEIGHT_FULL);
			// Full ones hang heavier: a longer swing that dies down sooner.
			double damping = feature.damping() * (1.0 + 0.6 * fill);
			Vector3f down = pendulums[i].hang(pose.last().pose(), anchor, CORD + height / 2f, damping);
			if (down == null) {
				continue;
			}
			NameCharmRenderer.submitString(pose, collector, anchor, down, CORD, CORD_WIDTH, light);
			pose.pushPose();
			pose.translate(anchor.x, anchor.y, anchor.z);
			pose.mulPose(new Matrix4f().rotation(new Quaternionf().rotationTo(0f, 1f, 0f, down.x, down.y, down.z)));
			pose.translate(0f, CORD, 0f);
			float stretch = 1f / (float) Math.sqrt(Math.max(0.2f, squish));
			pose.scale(stretch, squish, stretch);
			int color = color(bundle);
			collector.submitCustomGeometry(pose, TYPE, (p, vc) -> pouch(vc, p, fill, color, light));
			pose.popPose();
		}
		submitGear(pose, collector, light, player, gear, torso, beltY, pendulums, feature.damping());
		pose.popPose();
	}

	/**
	 * The weapons: the melee weapon hangs on the left hip blade down, the bow or crossbow upright on the right with the
	 * quiver behind it. Each hangs from the belt and swings, stiffer than the pouches.
	 */
	private static void submitGear(PoseStack pose, SubmitNodeCollector collector, int light, LivingEntity holder, BeltGear gear,
			Torso torso, float beltY, CharmPendulum[] pendulums, double damping) {
		BeltFeature feature = BeltFeature.INSTANCE;
		float y = beltY + BELT_HEIGHT / 2f;
		float midZ = (torso.front() + torso.back()) / 2f;
		double stiff = damping * GEAR_DAMPING;
		if (gear.melee() != null) {
			Vector3f anchor = new Vector3f(torso.centerX() + torso.halfWidth() + GEAR_OUT, y, midZ + 0.02f);
			if (hang(pose, anchor, pendulums[MELEE], MELEE_LENGTH, stiff, MELEE_TILT)) {
				// The item's diagonal (grip bottom left, tip top right) turned to point down, flat against the hip.
				pose.rotateDegrees(Axis.XP, -45f);
				pose.rotateDegrees(Axis.YP, -90f);
				pose.scale(MELEE_SCALE, MELEE_SCALE, MELEE_SCALE);
				// The guard sits at the belt, the grip sticks out above it.
				pose.translate(MELEE_GUARD, MELEE_GUARD, 0f);
				submitItem(pose, collector, feature.stack(gear.melee()), holder, light);
				pose.popPose();
			}
		}
		if (gear.ranged() != null) {
			float side = torso.centerX() - torso.halfWidth() - GEAR_OUT;
			Vector3f quiverAnchor = new Vector3f(side, y, midZ + QUIVER_BACK);
			if (hang(pose, quiverAnchor, pendulums[QUIVER], QUIVER_LENGTH / 2f, stiff, QUIVER_TILT)) {
				collector.submitCustomGeometry(pose, TYPE, (p, vc) -> quiver(vc, p, light));
				int arrows = gear.arrow() == null || gear.arrows() <= 0 ? 0 : Math.min(MAX_ARROWS, 1 + (gear.arrows() - 1) / ARROWS_EACH);
				ItemStack arrow = arrows == 0 ? ItemStack.EMPTY : feature.stack(gear.arrow());
				for (int i = 0; i < arrows; i++) {
					pose.pushPose();
					// Spread a little and turned different ways, fletching out of the top.
					pose.translate(ARROW_SPREAD * ((i % 2) * 2 - 1), ARROW_DEPTH, ARROW_SPREAD * ((i / 2) * 2 - 1) * 0.6f);
					pose.rotateDegrees(Axis.YP, i * 55f);
					pose.rotateDegrees(Axis.XP, -45f);
					pose.rotateDegrees(Axis.YP, -90f);
					pose.scale(ARROW_SCALE, ARROW_SCALE, ARROW_SCALE);
					submitItem(pose, collector, arrow, holder, light);
					pose.popPose();
				}
				pose.popPose();
			}
			Vector3f bowAnchor = new Vector3f(side - BOW_OUT, y, midZ - 0.03f);
			if (hang(pose, bowAnchor, pendulums[BOW], BOW_DROP, stiff, 0f)) {
				pose.translate(0f, BOW_DROP, 0f);
				// The item's diagonal turned upright, flat against the hip.
				pose.rotateDegrees(Axis.XP, -45f);
				pose.rotateDegrees(Axis.YP, -90f);
				pose.scale(BOW_SCALE, BOW_SCALE, BOW_SCALE);
				submitItem(pose, collector, feature.stack(gear.ranged()), holder, light);
				pose.popPose();
			}
		}
	}

	/**
	 * Pushes a pose hanging from {@code anchor} along its pendulum, tipped {@code tilt} degrees backwards; false (and
	 * nothing pushed) if the body is squashed flat.
	 */
	private static boolean hang(PoseStack pose, Vector3f anchor, CharmPendulum pendulum, float length, double damping, float tilt) {
		Vector3f down = pendulum.hang(pose.last().pose(), anchor, length, damping);
		if (down == null) {
			return false;
		}
		pose.pushPose();
		pose.translate(anchor.x, anchor.y, anchor.z);
		pose.mulPose(new Matrix4f().rotation(new Quaternionf().rotationTo(0f, 1f, 0f, down.x, down.y, down.z)));
		pose.rotateDegrees(Axis.XP, tilt);
		return true;
	}

	/** Draws an item centred on the origin (its model as is, without a hand or display transform). */
	private static void submitItem(PoseStack pose, SubmitNodeCollector collector, ItemStack stack, LivingEntity holder, int light) {
		if (stack.isEmpty()) {
			return;
		}
		ItemStackRenderState state = new ItemStackRenderState();
		Minecraft.getInstance().getItemModelResolver().updateForLiving(state, stack, ItemDisplayContext.NONE, holder);
		state.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
	}

	/** A leather quiver hanging down from the origin, its rim a little above it. */
	private static void quiver(VertexConsumer vc, PoseStack.Pose p, int light) {
		float top = -QUIVER_ABOVE;
		float h = QUIVER_LENGTH / 2f;
		int rim = ARGB.scaleRGB(QUIVER_COLOR, 0.7f);
		box(vc, p, 0f, top + h, QUIVER_WIDTH / 2f, h, QUIVER_DEPTH / 2f, QUIVER_COLOR, light, SIDE_UV, TOP_UV, BOTTOM_UV);
		box(vc, p, 0f, top + 0.015f, QUIVER_WIDTH / 2f + 0.006f, 0.015f, QUIVER_DEPTH / 2f + 0.006f, rim, light, TIE_UV, TIE_UV, TIE_UV);
		box(vc, p, 0f, top + 2f * h - 0.02f, QUIVER_WIDTH / 2f + 0.004f, 0.012f, QUIVER_DEPTH / 2f + 0.004f, rim, light, TIE_UV, TIE_UV, TIE_UV);
	}

	/** A thin belt round the waist. */
	private static void submitBelt(PoseStack pose, SubmitNodeCollector collector, int light, Torso torso, float y) {
		float t = BELT_THICKNESS;
		float h = BELT_HEIGHT / 2f;
		float cy = y + h;
		float halfX = torso.halfWidth() + t;
		float midZ = (torso.front() + torso.back()) / 2f;
		float halfZ = (torso.back() - torso.front()) / 2f;
		NameCharmRenderer.submitStrap(pose, collector, new Vector3f(torso.centerX(), cy, torso.front() - t / 2f), new Vector3f(halfX, h, t / 2f), light);
		NameCharmRenderer.submitStrap(pose, collector, new Vector3f(torso.centerX(), cy, torso.back() + t / 2f), new Vector3f(halfX, h, t / 2f), light);
		for (int side = -1; side <= 1; side += 2) {
			NameCharmRenderer.submitStrap(pose, collector, new Vector3f(torso.centerX() + side * (torso.halfWidth() + t / 2f), cy, midZ),
					new Vector3f(t / 2f, h, halfZ), light);
		}
	}

	private static int color(BeltBundle bundle) {
		DyeColor dye = bundle.dye();
		return dye == null ? UNDYED : 0xFF000000 | dye.getTextureDiffuseColor();
	}

	/** The pouch hanging down from the origin: tie, gathered top, a body that puffs up with {@code fill}, rounded bottom. */
	private static void pouch(VertexConsumer vc, PoseStack.Pose p, float fill, int color, int light) {
		float w = Mth.lerp(fill, WIDTH_EMPTY, WIDTH_FULL) / 2f;
		float h = Mth.lerp(fill, HEIGHT_EMPTY, HEIGHT_FULL) / 2f;
		float d = Mth.lerp(fill, DEPTH_EMPTY, DEPTH_FULL) / 2f;
		int tie = ARGB.scaleRGB(color, 0.7f);
		box(vc, p, 0f, 0.015f, 0.032f, 0.015f, 0.024f, tie, light, TIE_UV, TIE_UV, TIE_UV);
		box(vc, p, 0f, 0.04f, w * 0.45f, 0.012f, d * 0.5f, color, light, TOP_UV, TOP_UV, TOP_UV);
		// Rounded shoulders and bottom: slightly smaller slabs above and below the body.
		box(vc, p, 0f, NECK - 0.004f, w * 0.8f, 0.006f, d * 0.8f, color, light, SIDE_UV, TOP_UV, BOTTOM_UV);
		box(vc, p, 0f, NECK + h, w, h, d, color, light, SIDE_UV, TOP_UV, BOTTOM_UV);
		box(vc, p, 0f, NECK + 2f * h + 0.008f, w * 0.8f, 0.008f, d * 0.8f, color, light, SIDE_UV, BOTTOM_UV, BOTTOM_UV);
	}

	/** An axis-aligned box centred on (x, y, 0) with half extents (hx, hy, hz); y points down. */
	private static void box(VertexConsumer vc, PoseStack.Pose p, float x, float y, float hx, float hy, float hz, int color, int light,
			float[] side, float[] top, float[] bottom) {
		float x0 = x - hx, x1 = x + hx, y0 = y - hy, y1 = y + hy, z0 = -hz, z1 = hz;
		// Front (-z) and back (+z).
		quad(vc, p, color, light, side, 0f, 0f, -1f, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
		quad(vc, p, color, light, side, 0f, 0f, 1f, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		// Sides.
		quad(vc, p, color, light, side, -1f, 0f, 0f, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(vc, p, color, light, side, 1f, 0f, 0f, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
		// Top (y0, facing up = -y) and bottom.
		quad(vc, p, color, light, top, 0f, -1f, 0f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(vc, p, color, light, bottom, 0f, 1f, 0f, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
	}

	/** A quad from its corners, top-left first, going round; {@code uv} is {u0, v0, u1, v1}. */
	private static void quad(VertexConsumer vc, PoseStack.Pose p, int color, int light, float[] uv, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
		vertex(vc, p, ax, ay, az, uv[0], uv[1], color, light, nx, ny, nz);
		vertex(vc, p, bx, by, bz, uv[2], uv[1], color, light, nx, ny, nz);
		vertex(vc, p, cx, cy, cz, uv[2], uv[3], color, light, nx, ny, nz);
		vertex(vc, p, dx, dy, dz, uv[0], uv[3], color, light, nx, ny, nz);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int color, int light,
			float nx, float ny, float nz) {
		vc.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	private static float[] cell(float u, float v) {
		return new float[] {u + 0.02f, v + 0.02f, u + 0.48f, v + 0.48f};
	}
}
