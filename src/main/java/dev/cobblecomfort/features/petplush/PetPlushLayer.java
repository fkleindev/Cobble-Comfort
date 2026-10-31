package dev.cobblecomfort.features.petplush;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.cobblecomfort.features.namecharm.CharmPendulum;
import dev.cobblecomfort.features.namecharm.NameCharmRenderer;
import dev.cobblecomfort.features.plush.PlushHits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.feline.CatVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.item.DyeColor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws a player's plushes: the baby model of the wolf or cat with its own variant texture and collar (so resource
 * packs apply). A charm hangs small on a short string and swings with {@link CharmPendulum}; a backpack is worn
 * bigger with straps over the shoulders, and the charm then hangs from its lower side.
 */
final class PetPlushLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final Identifier WOLF_COLLAR = Identifier.withDefaultNamespace("textures/entity/wolf/wolf_collar_baby.png");
	private static final Identifier CAT_COLLAR = Identifier.withDefaultNamespace("textures/entity/cat/cat_collar_baby.png");
	private static final Identifier WOLF_DEFAULT = Identifier.withDefaultNamespace("textures/entity/wolf/wolf_tame_baby.png");
	private static final Identifier CAT_DEFAULT = Identifier.withDefaultNamespace("textures/entity/cat/cat_tabby_baby.png");

	// Everything below is in the body's space (blocks, y down, +z is behind the player), relative to the torso as it is
	// drawn: its top, its back and its front. Animated player models (Fresh Animations) move and resize it.
	/** Where the charm's string is tied without a backpack: right shoulder blade (x, below the top, behind the back). */
	private static final Vector3f CHARM_ANCHOR = new Vector3f(-0.13f, 0.16f, 0.035f);
	private static final float STRING = 0.09f;
	private static final float STRING_WIDTH = 0.012f;
	/** Height of the charm (head to bottom, as it hangs upright). */
	private static final float CHARM_HEIGHT = 0.3f;
	/** Height of the backpack (head to bottom), how far its head sticks up over the shoulders, and where its belly is. */
	private static final float PACK_HEIGHT = 0.6f;
	private static final float PACK_TOP = -0.12f;
	private static final float PACK_BACK = 0.015f;
	/** How far the chest straps run down from the top. */
	private static final float STRAP_LENGTH = 0.4f;
	/** The shoulder straps: distance from the middle, width and thickness. */
	private static final float STRAP_X = 0.13f;
	private static final float STRAP_WIDTH = 0.055f;
	private static final float STRAP_THICKNESS = 0.016f;

	private final PlushPetModels.Wolf wolf;
	private final PlushPetModels.Cat cat;
	private final PlushPetModels.Cat catCollar;
	private final Bounds wolfCharm;
	private final Bounds catCharm;
	private final Bounds wolfPack;
	private final Bounds catPack;

	/** The posed model's size in blocks: top (y down), height, middle, width, back (side towards the player), depth. */
	private record Bounds(float top, float height, float centerX, float width, float back, float depth) {
		static Bounds of(EntityModel<?> model, Consumer<PoseStack> posing) {
			float[] b = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
			PoseStack posed = new PoseStack();
			posing.accept(posed);
			// Measured without the tail, so a long tail doesn't make the plush smaller.
			if (model instanceof PlushPetModels.Tailed tailed) {
				tailed.showTail(false);
			}
			model.root().getExtentsForGui(posed, v -> {
				b[0] = Math.min(b[0], v.x());
				b[1] = Math.max(b[1], v.x());
				b[2] = Math.min(b[2], v.y());
				b[3] = Math.max(b[3], v.y());
				b[4] = Math.min(b[4], v.z());
				b[5] = Math.max(b[5], v.z());
			});
			if (model instanceof PlushPetModels.Tailed tailed) {
				tailed.showTail(true);
			}
			return new Bounds(b[2], Math.max(0.01f, b[3] - b[2]), (b[0] + b[1]) / 2f, b[1] - b[0], b[4], b[5] - b[4]);
		}
	}

	PetPlushLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
		// Vanilla's models, not the baked ones: mods like EMF swap those for resource pack models with their own pivots
		// and animations (Fresh Animations), which the plush posing can't know. Packs keep vanilla's texture layout.
		Map<ModelLayerLocation, LayerDefinition> vanilla = LayerDefinitions.createRoots();
		wolf = new PlushPetModels.Wolf(vanilla.get(ModelLayers.WOLF_BABY).bakeRoot());
		cat = new PlushPetModels.Cat(vanilla.get(ModelLayers.CAT_BABY).bakeRoot());
		catCollar = new PlushPetModels.Cat(vanilla.get(ModelLayers.CAT_BABY_COLLAR).bakeRoot());
		wolfCharm = Bounds.of(wolf, PetPlushLayer::asCharm);
		catCharm = Bounds.of(cat, PetPlushLayer::asCharm);
		wolfPack = Bounds.of(wolf, PetPlushLayer::asPack);
		catPack = Bounds.of(cat, PetPlushLayer::asPack);
	}

	/** Stands the four-legged model up: head up, belly away from the player, back towards them. */
	private static void asCharm(PoseStack pose) {
		pose.rotateDegrees(Axis.YP, 180f);
		pose.rotateDegrees(Axis.XP, -90f);
	}

	/** Stands it up the other way round: belly on the player, back out (the model faces -z with its back up). */
	private static void asPack(PoseStack pose) {
		pose.rotateDegrees(Axis.XP, -90f);
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
		PetPlushFeature feature = PetPlushFeature.INSTANCE;
		PlushLook look = feature.lookFor(player);
		if (look == null || !look.hasPets()) {
			return;
		}
		PetPlushFeature.Motion motion = feature.motion(state.id);
		motion.updateJiggle(player, System.nanoTime());
		float squish = feature.config().hurtSquish ? PlushHits.squish(player, System.nanoTime()) : 1f;

		pose.pushPose();
		// Like vanilla layers: through the root first, which animated player models (Fresh Animations via EMF) move.
		getParentModel().root().translateAndRotate(pose);
		ModelPart body = getParentModel().body;
		Torso torso = Torso.of(body).inflated(Torso.worn(state, false));
		body.translateAndRotate(pose);
		Vector3f charmAnchor = new Vector3f(torso.centerX() + CHARM_ANCHOR.x, torso.top() + CHARM_ANCHOR.y, torso.back() + CHARM_ANCHOR.z);
		if (look.pack() != null) {
			Bounds bounds = look.pack().wolf() ? wolfPack : catPack;
			float scale = PACK_HEIGHT / bounds.height();
			submitPack(pose, collector, light, look.pack(), torso, bounds, scale, squish, jiggle(motion, true));
			// The charm hangs from the backpack's lower right side.
			charmAnchor = new Vector3f(torso.centerX() - bounds.width() * scale * 0.4f, torso.top() + PACK_TOP + PACK_HEIGHT * 0.8f,
					torso.back() + PACK_BACK + bounds.depth() * scale * 0.5f);
		}
		if (look.charm() != null) {
			submitCharm(pose, collector, light, look.charm(), charmAnchor, squish, motion, feature.damping());
		}
		pose.popPose();
	}

	/** Squash and stretch along y (down) around {@code pivotY}, keeping the volume roughly the same. */
	private static void squash(PoseStack pose, float squish, float pivotY) {
		if (squish == 1f) {
			return;
		}
		float stretch = 1f / (float) Math.sqrt(Math.max(0.2f, squish));
		pose.translate(0f, pivotY, 0f);
		pose.scale(stretch, squish, stretch);
		pose.translate(0f, -pivotY, 0f);
	}

	/** The plush faces away from its holder, so its left and right are mirrored. */
	private static PlushPetModels.Jiggle jiggle(PetPlushFeature.Motion motion, boolean pack) {
		return new PlushPetModels.Jiggle(motion.jiggle.pose(true), motion.jiggle.tailSwing(true), motion.jiggle.tailLift(), pack);
	}

	private void submitPack(PoseStack pose, SubmitNodeCollector collector, int light, PetPlush plush, Torso torso, Bounds bounds, float scale,
			float squish, PlushPetModels.Jiggle jiggle) {
		// Straps over both shoulders, down the chest.
		for (int side = -1; side <= 1; side += 2) {
			float x = torso.centerX() + side * STRAP_X;
			float front = torso.front() - STRAP_THICKNESS;
			float back = torso.back() + PACK_BACK;
			NameCharmRenderer.submitStrap(pose, collector, new Vector3f(x, torso.top() - STRAP_THICKNESS / 2f, (front + back) / 2f),
					new Vector3f(STRAP_WIDTH / 2f, STRAP_THICKNESS / 2f, (back - front) / 2f), light);
			NameCharmRenderer.submitStrap(pose, collector, new Vector3f(x, torso.top() + STRAP_LENGTH / 2f, front + STRAP_THICKNESS / 2f),
					new Vector3f(STRAP_WIDTH / 2f, STRAP_LENGTH / 2f, STRAP_THICKNESS / 2f), light);
		}
		pose.pushPose();
		pose.translate(torso.centerX(), torso.top() + PACK_TOP, torso.back() + PACK_BACK);
		// Squeezed towards its middle; straps and charm stay where they are.
		squash(pose, squish, PACK_HEIGHT / 2f);
		pose.scale(scale, scale, scale);
		pose.translate(-bounds.centerX(), -bounds.top(), -bounds.back());
		asPack(pose);
		submitModel(pose, collector, light, plush, jiggle);
		pose.popPose();
	}

	private void submitCharm(PoseStack pose, SubmitNodeCollector collector, int light, PetPlush plush, Vector3f anchor,
			float squish, PetPlushFeature.Motion motion, double damping) {
		Vector3f down = motion.pendulum.hang(pose.last().pose(), anchor, STRING + CHARM_HEIGHT / 2f, damping);
		if (down == null) {
			return;
		}
		NameCharmRenderer.submitString(pose, collector, anchor, down, STRING, STRING_WIDTH, light);
		pose.pushPose();
		pose.translate(anchor.x, anchor.y, anchor.z);
		pose.mulPose(new Matrix4f().rotation(new Quaternionf().rotationTo(0f, 1f, 0f, down.x, down.y, down.z)));
		pose.translate(0f, STRING, 0f);
		// Squeezed up towards the string.
		squash(pose, squish, 0f);
		Bounds bounds = plush.wolf() ? wolfCharm : catCharm;
		float scale = CHARM_HEIGHT / bounds.height();
		pose.scale(scale, scale, scale);
		// Its back against the player (or the backpack), top of the head at the string.
		pose.translate(-bounds.centerX(), -bounds.top(), -bounds.back());
		asCharm(pose);
		submitModel(pose, collector, light, plush, jiggle(motion, false));
		pose.popPose();
	}

	private void submitModel(PoseStack pose, SubmitNodeCollector collector, int light, PetPlush plush, PlushPetModels.Jiggle jiggle) {
		Identifier texture = texture(plush);
		DyeColor collar = plush.collarColor();
		if (plush.wolf()) {
			PlushPetModels.WolfState s = new PlushPetModels.WolfState();
			s.jiggle = jiggle;
			s.isBaby = true;
			s.tailAngle = (float) Math.PI / 5f;
			s.collarColor = collar;
			submit(wolf, s, pose, collector, texture, light, -1);
			if (collar != null) {
				submit(wolf, s, pose, collector, WOLF_COLLAR, light, collar.getTextureDiffuseColor());
			}
		} else {
			PlushPetModels.CatState s = new PlushPetModels.CatState();
			s.jiggle = jiggle;
			s.isBaby = true;
			s.collarColor = collar;
			submit(cat, s, pose, collector, texture, light, -1);
			if (collar != null) {
				submit(catCollar, s, pose, collector, CAT_COLLAR, light, collar.getTextureDiffuseColor());
			}
		}
	}

	private static <S extends LivingEntityRenderState> void submit(EntityModel<S> model, S state, PoseStack pose,
			SubmitNodeCollector collector, Identifier texture, int light, int color) {
		collector.submitModel(model, state, pose, RenderTypes.entityCutout(texture), light, OverlayTexture.NO_OVERLAY, color, null, 0);
	}

	/** The baby texture of the variant, looked up in the synced registry; a default one if it is unknown here. */
	private static Identifier texture(PetPlush plush) {
		Minecraft mc = Minecraft.getInstance();
		Identifier id = Identifier.tryParse(plush.variant());
		if (mc.level != null && id != null) {
			if (plush.wolf()) {
				WolfVariant variant = mc.level.registryAccess().lookup(Registries.WOLF_VARIANT).flatMap(r -> r.getOptional(id)).orElse(null);
				if (variant != null) {
					return variant.babyInfo().tame().texturePath();
				}
			} else {
				CatVariant variant = mc.level.registryAccess().lookup(Registries.CAT_VARIANT).flatMap(r -> r.getOptional(id)).orElse(null);
				if (variant != null) {
					return variant.babyAssetInfo().texturePath();
				}
			}
		}
		return plush.wolf() ? WOLF_DEFAULT : CAT_DEFAULT;
	}
}
