package dev.cobblecomfort.features.plush;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/**
 * Draws the totem plush with the skin and animation handed over by {@link PlushFeature}. The plush stands in the
 * item's unit cube, centred, feet on the bottom, facing the same way as a player head item.
 */
public final class PlushRenderer implements SpecialModelRenderer<PlushRenderer.Args> {
	/** Plush height relative to the unit cube. */
	private static final float SIZE = 0.95f;
	/** Punchy's arm sits closer to the camera and lower than the vanilla hand. */
	private static final float PUNCHY_SCALE = 0.8f;
	private static final float PUNCHY_LIFT = 0.4f;

	/**
	 * What to draw: whose skin, and how squashed and tilted the plush and its limbs are right now. Part of the item's
	 * identity, so plushes with different skins never share a cached GUI image.
	 */
	public record Args(Identifier texture, boolean slim, float squish, float tiltZ, float tiltX, float upright,
			PlushPose limbs) {
	}

	private final PlushModel wide = PlushModel.create(false);
	private final PlushModel slim = PlushModel.create(true);

	@Override
	public void submit(Args args, PoseStack pose, SubmitNodeCollector collector, int light, int overlay,
			boolean foil, int outlineColor) {
		if (args == null) {
			return;
		}
		pose.pushPose();
		pose.translate(0.5f, 0f, 0.5f);
		if (args.upright() != 0f) {
			// Stands the plush up, facing you, where another mod holds items differently (see PlushFeature#begin).
			pose.rotateAround(Axis.XP.rotationDegrees(args.upright()), 0f, 0.5f, 0f);
			pose.rotateAround(Axis.YP.rotationDegrees(180), 0f, 0.5f, 0f);
			pose.translate(0f, 0.5f, 0f);
			pose.scale(PUNCHY_SCALE, PUNCHY_SCALE, PUNCHY_SCALE);
			pose.translate(0f, PUNCHY_LIFT - 0.5f, 0f);
		}
		// The body sways on its bottom like a soft toy; head and limbs swing in the model (PlushModel#setupAnim).
		pose.rotateDegrees(Axis.ZP, args.tiltZ());
		pose.rotateDegrees(Axis.XP, args.tiltX());
		// Squash and stretch keep the volume roughly the same.
		float stretch = 1f / (float) Math.sqrt(Math.max(0.2f, args.squish()));
		pose.scale(stretch, args.squish(), stretch);
		place(pose);
		collector.submitModel(args.slim() ? slim : wide, args.limbs(), pose, RenderTypes.entityTranslucent(args.texture()),
				light, overlay, -1, null, outlineColor);
		pose.popPose();
	}

	/** From the unit cube to model space: scaled to fit, flipped upright (model y points down) and facing front. */
	private static void place(PoseStack pose) {
		float scale = SIZE * 16f / PlushModel.HEIGHT;
		pose.scale(scale, scale, scale);
		pose.rotateDegrees(Axis.XP, 180);
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		PoseStack pose = new PoseStack();
		pose.translate(0.5f, 0f, 0.5f);
		place(pose);
		wide.root().getExtentsForGui(pose, output);
	}

	@Override
	public Args extractArgument(ItemStack stack) {
		return PlushFeature.INSTANCE.pendingArgs();
	}

	public record Unbaked() implements SpecialModelRenderer.Unbaked<Args> {
		public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(Unbaked::new);

		@Override
		public PlushRenderer bake(SpecialModelRenderer.BakingContext context) {
			return new PlushRenderer();
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
