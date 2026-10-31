package dev.cobblecomfort.features.petplush;

import dev.cobblecomfort.features.plush.PlushPose;
import net.minecraft.client.model.animal.feline.BabyCatModel;
import net.minecraft.client.model.animal.wolf.BabyWolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.util.Mth;

/**
 * The baby wolf and cat models posed as a plush on a back. As a charm the layer stands the body up (head up,
 * belly out): the head is bent to look out and the legs hang down and a little forward. As a backpack it lies with
 * its belly on the player: the head looks out behind and the legs hug. Head, legs and tail
 * jiggle on top of that.
 */
final class PlushPetModels {
	/** Bends the head from looking up (the body stands upright) to looking out at the viewer. */
	private static final float HEAD_PITCH = 80f * Mth.DEG_TO_RAD;
	/** Legs hang down, a little forward. */
	private static final float LEG_PITCH = 65f * Mth.DEG_TO_RAD;
	/** As a backpack the body lies on its belly against the player: the head is raised to look out behind, the legs hug. */
	private static final float PACK_HEAD_PITCH = -75f * Mth.DEG_TO_RAD;
	private static final float PACK_LEG_PITCH = 25f * Mth.DEG_TO_RAD;

	private PlushPetModels() {
	}

	/** The jiggle of one plush, carried by its render state to the model. */
	record Jiggle(PlushPose limbs, float tailSwing, float tailLift, boolean pack) {
		static final Jiggle REST = new Jiggle(PlushPose.REST, 0f, 0f, false);
	}

	static final class WolfState extends WolfRenderState {
		Jiggle jiggle = Jiggle.REST;
	}

	static final class CatState extends CatRenderState {
		Jiggle jiggle = Jiggle.REST;
	}

	/** Hides the tail while the plush is measured, so a long tail doesn't make the plush smaller. */
	interface Tailed {
		void showTail(boolean shown);
	}

	static final class Wolf extends BabyWolfModel implements Tailed {
		Wolf(ModelPart root) {
			super(root);
		}

		@Override
		public void showTail(boolean shown) {
			tail.visible = shown;
		}

		@Override
		public void setupAnim(WolfRenderState state) {
			super.setupAnim(state);
			Jiggle j = state instanceof WolfState s ? s.jiggle : Jiggle.REST;
			pose(head, rightFrontLeg, leftFrontLeg, rightHindLeg, leftHindLeg, j);
			tail.xRot += j.tailLift();
			tail.yRot = j.tailSwing();
		}
	}

	static final class Cat extends BabyCatModel implements Tailed {
		Cat(ModelPart root) {
			super(root);
		}

		@Override
		public void showTail(boolean shown) {
			tail1.visible = shown;
			tail2.visible = shown;
		}

		@Override
		public void setupAnim(CatRenderState state) {
			super.setupAnim(state);
			Jiggle j = state instanceof CatState s ? s.jiggle : Jiggle.REST;
			pose(head, rightFrontLeg, leftFrontLeg, rightHindLeg, leftHindLeg, j);
			// The second tail segment follows the first a bit further, so the tail curls as it swings.
			tail1.xRot += j.tailLift();
			tail1.yRot = j.tailSwing();
			tail2.xRot += j.tailLift() * 0.6f;
			tail2.yRot = j.tailSwing() * 0.6f;
		}
	}

	private static void pose(ModelPart head, ModelPart rightFront, ModelPart leftFront, ModelPart rightHind, ModelPart leftHind,
			Jiggle j) {
		PlushPose limbs = j.limbs();
		float legPitch = j.pack() ? PACK_LEG_PITCH : LEG_PITCH;
		head.xRot = (j.pack() ? PACK_HEAD_PITCH : HEAD_PITCH) + limbs.headX();
		head.yRot = 0f;
		head.zRot = limbs.headZ();
		rightFront.xRot = legPitch + limbs.rightArmX();
		rightFront.zRot = limbs.rightArmZ() * 0.5f;
		leftFront.xRot = legPitch + limbs.leftArmX();
		leftFront.zRot = limbs.leftArmZ() * 0.5f;
		rightHind.xRot = legPitch + limbs.rightLegX();
		leftHind.xRot = legPitch + limbs.leftLegX();
	}
}
