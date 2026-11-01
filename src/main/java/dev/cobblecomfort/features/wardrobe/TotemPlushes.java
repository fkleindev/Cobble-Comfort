package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.cobblecomfort.core.VanillaAssets;
import dev.cobblecomfort.mixin.ModelPartAccessor;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The totems that are not a plush of a player: mobs as plushes, from the game's own models and textures (their heads a
 * little bigger, like a soft toy), and a dragon and a gingerbread man built from voxels. Each stands in the item's unit
 * cube like the player plush, feet at the bottom, as big as fits. Wings flap.
 */
public final class TotemPlushes {
	private static final float SIZE = 0.95f;
	private static final float V = HatLayer.V;
	private static final Identifier WARDEN_GLOW = Identifier.withDefaultNamespace("textures/entity/warden/warden_bioluminescent_layer.png");
	private static final Identifier WARDEN_HEART = Identifier.withDefaultNamespace("textures/entity/warden/warden_heart.png");
	private static final Identifier DRAGON = Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");

	/** A mob's model as a plush: its parts, how far it reaches (blocks, y down) and its wings. */
	private record Mob(ModelPart root, float[] bounds, List<ModelPart> wings) {
	}

	private static final Map<TotemSkin.Style, Mob> MOBS = new EnumMap<>(TotemSkin.Style.class);
	private static Map<ModelLayerLocation, LayerDefinition> layers;
	private static ModelPart wardenGlow;
	private static ModelPart wardenHeart;
	private static ModelPart dragonHead;
	private static float[] dragonHeadBounds;

	private TotemPlushes() {
	}

	/** Draws a totem skin that is not a player plush, in the item's unit cube already centred on x and z. */
	public static void submit(TotemSkin skin, PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
		float seconds = System.nanoTime() % 1_000_000_000_000L / 1e9f;
		switch (skin.style) {
			case DRAGON -> dragon(pose, collector, light, seconds);
			case GINGERBREAD -> gingerbread(pose, collector, light);
			default -> {
				Mob mob = mob(skin.style);
				if (mob == null) {
					return;
				}
				flap(skin.style, mob, seconds);
				pose.pushPose();
				place(pose, mob.bounds);
				collector.submitModelPart(mob.root, pose, RenderTypes.entityTranslucent(VanillaAssets.texture(skin.texture)), light,
						overlay, null);
				if (skin.style == TotemSkin.Style.WARDEN) {
					// The glowing spots, and the heart beating.
					collector.submitModelPart(wardenGlow, pose, RenderTypes.entityTranslucentEmissive(VanillaAssets.texture(WARDEN_GLOW)),
							light, overlay, null);
					int beat = (int) (Math.max(0f, Mth.sin(seconds * 6f)) * 255f);
					collector.submitModelPart(wardenHeart, pose, RenderTypes.entityTranslucentEmissive(VanillaAssets.texture(WARDEN_HEART)),
							light, overlay, null, ARGB.color(beat, 0xFFFFFF));
				}
				if (skin.style == TotemSkin.Style.SNOWMAN) {
					carrot(pose, collector, mob.root, light);
				}
				pose.popPose();
			}
		}
	}

	/**
	 * Dresses a player plush in a hat and neck piece (Mini-Me: what its holder wears; either may be null). The pose is
	 * the plush model's, its parts posed; {@code bodyTop} is how far above its pivot the body's top is, in the body's own
	 * (scaled) space, in pixels.
	 */
	public static void dress(PoseStack pose, SubmitNodeCollector collector, ModelPart root, ModelPart head, ModelPart body,
			float bodyTop, Hat hat, Neck neck, int light) {
		if (hat != null) {
			pose.pushPose();
			root.translateAndRotate(pose);
			head.translateAndRotate(pose);
			HatLayer.draw(pose, collector, hat, light);
			pose.popPose();
		}
		if (neck != null) {
			pose.pushPose();
			root.translateAndRotate(pose);
			body.translateAndRotate(pose);
			pose.translate(0f, -bodyTop / 16f, 0f);
			NeckLayer.draw(pose, collector, neck, light);
			pose.popPose();
		}
	}

	/** Scales a model reaching {@code b} (blocks, y down) to fit, stands it upright on the bottom and faces it out. */
	private static void place(PoseStack pose, float[] b) {
		float s = SIZE / Math.max(b[3] - b[2], Math.max(b[1] - b[0], b[5] - b[4]));
		pose.scale(s, s, s);
		pose.rotateDegrees(Axis.XP, 180);
		pose.translate(-(b[0] + b[1]) / 2f, -b[3], -(b[4] + b[5]) / 2f);
	}

	// ---------------------------------------------------------------- mobs

	private static Mob mob(TotemSkin.Style style) {
		return MOBS.computeIfAbsent(style, s -> {
			ModelLayerLocation layer = switch (s) {
				case ALLAY -> ModelLayers.ALLAY;
				case AXOLOTL -> ModelLayers.AXOLOTL_BABY;
				case CREEPER -> ModelLayers.CREEPER;
				case FROG -> ModelLayers.FROG;
				case BEE -> ModelLayers.BEE_BABY;
				case WARDEN -> ModelLayers.WARDEN;
				case SNOWMAN -> ModelLayers.SNOW_GOLEM;
				default -> null;
			};
			if (layer == null) {
				return null;
			}
			// A plush has a big head.
			float head = switch (s) {
				case CREEPER, WARDEN -> 1.35f;
				case SNOWMAN -> 1.25f;
				case FROG, ALLAY -> 1.15f;
				default -> 1f;
			};
			ModelPart root = bake(layer, head);
			if (s == TotemSkin.Style.AXOLOTL) {
				// Sitting up on its tail like a soft toy, rather than lying flat and tiny.
				root.xRot = -45f * Mth.DEG_TO_RAD;
			}
			if (s == TotemSkin.Style.WARDEN) {
				wardenGlow = bake(ModelLayers.WARDEN_BIOLUMINESCENT, head);
				wardenHeart = bake(ModelLayers.WARDEN_HEART, head);
			}
			List<ModelPart> wings = new ArrayList<>();
			collect(root, "wing", wings);
			return new Mob(root, bounds(root), wings);
		});
	}

	/**
	 * The game's own model, not the baked one: mods like EMF swap those for resource pack models with their own pivots
	 * and animations, which a plush can't know. Its head scaled by {@code head}.
	 */
	private static ModelPart bake(ModelLayerLocation layer, float head) {
		if (layers == null) {
			layers = LayerDefinitions.createRoots();
		}
		ModelPart root = layers.get(layer).bakeRoot();
		ModelPart part = find(root, "head");
		if (part != null && head != 1f) {
			part.xScale = head;
			part.yScale = head;
			part.zScale = head;
		}
		return root;
	}

	private static float[] bounds(ModelPart root) {
		float[] b = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
		root.getExtentsForGui(new PoseStack(), v -> {
			b[0] = Math.min(b[0], v.x());
			b[1] = Math.max(b[1], v.x());
			b[2] = Math.min(b[2], v.y());
			b[3] = Math.max(b[3], v.y());
			b[4] = Math.min(b[4], v.z());
			b[5] = Math.max(b[5], v.z());
		});
		return b;
	}

	private static Map<String, ModelPart> children(ModelPart part) {
		return ((ModelPartAccessor) (Object) part).cobblecomfort$children();
	}

	/** The first part named {@code name} under {@code part}, depth first, or null. */
	private static ModelPart find(ModelPart part, String name) {
		for (Map.Entry<String, ModelPart> child : children(part).entrySet()) {
			if (child.getKey().equals(name)) {
				return child.getValue();
			}
			ModelPart found = find(child.getValue(), name);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** Every part under {@code part} whose name contains {@code word}, the right ones first. */
	private static void collect(ModelPart part, String word, List<ModelPart> out) {
		for (Map.Entry<String, ModelPart> child : children(part).entrySet()) {
			if (child.getKey().contains(word)) {
				if (child.getKey().contains("right")) {
					out.addFirst(child.getValue());
				} else {
					out.add(child.getValue());
				}
			}
			collect(child.getValue(), word, out);
		}
	}

	/**
	 * Applies the parts under {@code part} down to the one named {@code name} to the pose, each pushed; how many were
	 * pushed (none if there is no such part).
	 */
	private static int into(PoseStack pose, ModelPart part, String name) {
		for (Map.Entry<String, ModelPart> child : children(part).entrySet()) {
			pose.pushPose();
			child.getValue().translateAndRotate(pose);
			if (child.getKey().equals(name)) {
				return 1;
			}
			int depth = into(pose, child.getValue(), name);
			if (depth > 0) {
				return depth + 1;
			}
			pose.popPose();
		}
		return 0;
	}

	/** Flaps the wings: an allay's slowly, a bee's in a buzz. */
	private static void flap(TotemSkin.Style style, Mob mob, float seconds) {
		for (int i = 0; i < mob.wings.size(); i++) {
			ModelPart wing = mob.wings.get(i);
			wing.resetPose();
			float side = i == 0 ? 1f : -1f;
			if (style == TotemSkin.Style.BEE) {
				wing.zRot = side * Mth.cos(seconds * 40f) * 0.47f;
			} else if (style == TotemSkin.Style.ALLAY) {
				wing.yRot += side * (0.25f + 0.25f * Mth.sin(seconds * 9f));
			}
		}
	}

	/** A carrot for the snowman's nose, on the front of its head. */
	private static void carrot(PoseStack pose, SubmitNodeCollector collector, ModelPart root, int light) {
		pose.pushPose();
		root.translateAndRotate(pose);
		int depth = into(pose, root, "head");
		if (depth > 0) {
			List<HatLayer.Box> nose = List.of(HatLayer.raw(-1f, -9f, -11f, 1f, -7f, -7f, 0xFFF08A1E),
					HatLayer.raw(-0.6f, -8.6f, -12.5f, 0.6f, -7.4f, -11f, 0xFFD0661A));
			HatLayer.submit(pose, collector, nose, null, light);
		}
		for (int i = 0; i < depth; i++) {
			pose.popPose();
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- built ones

	private static final int DRAGON_SCALES = 0xFF1E1A26;
	private static final int DRAGON_BELLY = 0xFF3A3248;
	private static final int DRAGON_MEMBRANE = 0xFF4A2E5E;
	private static final int DRAGON_BONE = 0xFF141018;
	private static final int DRAGON_SPIKE = 0xFF5A5468;

	/**
	 * A chubby dragon sitting up: a round body with a pale belly, paws, a tail with spikes curled round behind, the
	 * dragon's own head on a short neck, and wings that flap.
	 */
	private static void dragon(PoseStack pose, SubmitNodeCollector collector, int light, float seconds) {
		pose.pushPose();
		place(pose, new float[]{-19 * V, 19 * V, -31 * V, 0f, -15 * V, 19 * V});
		List<HatLayer.Box> body = new ArrayList<>(List.of(
				HatLayer.raw(-7f, -14f, -6f, 7f, 0f, 8f, DRAGON_SCALES),
				HatLayer.raw(-5f, -12f, -6.5f, 5f, -1f, -6f, DRAGON_BELLY),
				HatLayer.raw(-6f, -3f, -9f, -2f, 0f, -6f, DRAGON_SCALES),
				HatLayer.raw(2f, -3f, -9f, 6f, 0f, -6f, DRAGON_SCALES),
				HatLayer.raw(-2.5f, -4f, 8f, 2.5f, 0f, 15f, DRAGON_SCALES),
				HatLayer.raw(-1.5f, -3f, 15f, 1.5f, -1f, 19f, DRAGON_SCALES),
				HatLayer.raw(-3.5f, -18f, -4f, 3.5f, -13f, 1f, DRAGON_SCALES)));
		for (float z : new float[]{-1f, 2.5f, 6f}) {
			body.add(HatLayer.raw(-0.75f, -16f, z, 0.75f, -14f, z + 2f, DRAGON_SPIKE));
		}
		body.add(HatLayer.raw(-0.5f, -5.5f, 10f, 0.5f, -4f, 12f, DRAGON_SPIKE));
		body.add(HatLayer.raw(-0.5f, -4.5f, 14f, 0.5f, -3f, 16f, DRAGON_SPIKE));
		HatLayer.submit(pose, collector, body, null, light);
		// The wings, beating from the shoulders.
		float beat = 0.35f + 0.35f * Mth.sin(seconds * 5f);
		for (int side = -1; side <= 1; side += 2) {
			pose.pushPose();
			pose.translate(side * 7f * V, -12f * V, 1f * V);
			pose.rotateDegrees(Axis.ZP, side * -beat * Mth.RAD_TO_DEG);
			List<HatLayer.Box> wing = new ArrayList<>();
			wing.add(wingBox(side, 0f, -1f, 12f, 0f, DRAGON_BONE));
			wing.add(wingBox(side, 0f, 0f, 12f, 6f, DRAGON_MEMBRANE));
			wing.add(wingBox(side, 0f, 6f, 4f, 8f, DRAGON_MEMBRANE));
			wing.add(wingBox(side, 5f, 6f, 8f, 7.5f, DRAGON_MEMBRANE));
			wing.add(wingBox(side, 9f, 6f, 12f, 9f, DRAGON_MEMBRANE));
			wing.add(wingBox(side, 11f, -1f, 12f, 9f, DRAGON_BONE));
			HatLayer.submit(pose, collector, wing, null, light);
			pose.popPose();
		}
		// The dragon's own head, small, on the neck.
		if (dragonHead == null) {
			if (layers == null) {
				layers = LayerDefinitions.createRoots();
			}
			dragonHead = layers.get(ModelLayers.DRAGON_SKULL).bakeRoot();
			dragonHeadBounds = bounds(dragonHead);
		}
		float[] b = dragonHeadBounds;
		float k = 16f * V / (b[1] - b[0]);
		pose.translate(0f, -17f * V, -4f * V);
		pose.scale(k, k, k);
		pose.translate(-(b[0] + b[1]) / 2f, -b[3], -(b[4] + b[5]) / 2f);
		collector.submitModelPart(dragonHead, pose, RenderTypes.entityCutout(VanillaAssets.texture(DRAGON)), light,
				net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, null);
		pose.popPose();
	}

	/** A box of a wing reaching out from the shoulder: {@code side} mirrors it to the left. */
	private static HatLayer.Box wingBox(int side, float out0, float y0, float out1, float y1, int color) {
		return side > 0 ? HatLayer.raw(out0, y0, 1.75f, out1, y1, 2.25f, color) : HatLayer.raw(-out1, y0, 1.75f, -out0, y1, 2.25f, color);
	}

	private static final int GINGER = 0xFFB8743A;
	private static final int ICING = 0xFFF8F4EE;

	/** A gingerbread man: a round head, arms out, icing round his hands and feet, a smile and gumdrop buttons. */
	private static void gingerbread(PoseStack pose, SubmitNodeCollector collector, int light) {
		pose.pushPose();
		place(pose, new float[]{-10 * V, 10 * V, -30 * V, 0f, -4 * V, 3.5f * V});
		List<HatLayer.Box> out = new ArrayList<>(List.of(
				HatLayer.raw(-5f, -8f, -2.5f, -1f, 0f, 2.5f, GINGER),
				HatLayer.raw(1f, -8f, -2.5f, 5f, 0f, 2.5f, GINGER),
				HatLayer.raw(-6f, -18f, -3f, 6f, -7f, 3f, GINGER),
				HatLayer.raw(-10f, -17.5f, -2.5f, -6f, -13f, 2.5f, GINGER),
				HatLayer.raw(6f, -17.5f, -2.5f, 10f, -13f, 2.5f, GINGER),
				HatLayer.raw(-5f, -29f, -3.5f, 5f, -18f, 3.5f, GINGER),
				HatLayer.raw(-6f, -28f, -3.5f, 6f, -19f, 3.5f, GINGER),
				// Icing: hands, feet, eyes, a smile, gumdrop buttons.
				HatLayer.raw(-10f, -17.5f, -2.8f, -9f, -13f, -2.5f, ICING),
				HatLayer.raw(9f, -17.5f, -2.8f, 10f, -13f, -2.5f, ICING),
				HatLayer.raw(-5f, -2f, -2.8f, -1f, -1.25f, -2.5f, ICING),
				HatLayer.raw(1f, -2f, -2.8f, 5f, -1.25f, -2.5f, ICING),
				HatLayer.raw(-3f, -26f, -3.8f, -1.5f, -24.5f, -3.5f, 0xFF2A1A12),
				HatLayer.raw(1.5f, -26f, -3.8f, 3f, -24.5f, -3.5f, 0xFF2A1A12),
				HatLayer.raw(-2.5f, -22f, -3.8f, 2.5f, -21.25f, -3.5f, ICING),
				HatLayer.raw(-3.5f, -23f, -3.8f, -2.5f, -22f, -3.5f, ICING),
				HatLayer.raw(2.5f, -23f, -3.8f, 3.5f, -22f, -3.5f, ICING),
				HatLayer.raw(-1f, -16.5f, -4f, 1f, -14.5f, -3f, 0xFFE0303A),
				HatLayer.raw(-1f, -12.5f, -4f, 1f, -10.5f, -3f, 0xFF3AB04A)));
		HatLayer.submit(pose, collector, out, null, light);
		pose.popPose();
	}
}
