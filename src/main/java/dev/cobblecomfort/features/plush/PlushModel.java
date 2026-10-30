package dev.cobblecomfort.features.plush;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * A chibi plush made from the player skin layout: every part keeps its skin-sized box (so the skin maps as usual),
 * and the part is scaled down instead, leaving a big head on a short, round body with stubby limbs. Model space is
 * the entity one: pixels, y pointing down, feet at 0. Head, arms and legs pivot where they are sewn on, so the
 * jiggle from {@link PlushPose} swings them like stuffed limbs.
 */
final class PlushModel extends Model<PlushPose> {
	private static final CubeDeformation HAT = new CubeDeformation(0.5f);
	private static final CubeDeformation OUTER = new CubeDeformation(0.25f);

	private static final float LEG_SCALE_XZ = 0.85f;
	private static final float LEG_SCALE_Y = 0.34f;
	private static final float BODY_SCALE_X = 0.9f;
	private static final float BODY_SCALE_Y = 0.5f;
	private static final float BODY_SCALE_Z = 0.95f;
	private static final float ARM_SCALE_XZ = 0.85f;
	private static final float ARM_SCALE_Y = 0.45f;
	/** How far the head sinks into the body, like a stuffed head sewn on. */
	private static final float NECK_OVERLAP = 0.6f;
	private static final float ARM_SPREAD = (float) Math.toRadians(14);

	private static final float LEG_HEIGHT = 12 * LEG_SCALE_Y;
	private static final float BODY_HEIGHT = 12 * BODY_SCALE_Y;

	/** Height of the whole plush in pixels. */
	static final float HEIGHT = LEG_HEIGHT + BODY_HEIGHT + 8 - NECK_OVERLAP;

	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	private PlushModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		head = root.getChild("head");
		rightArm = root.getChild("right_arm");
		leftArm = root.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	@Override
	public void setupAnim(PlushPose pose) {
		resetPose();
		head.xRot += pose.headX();
		head.zRot += pose.headZ();
		rightArm.xRot += pose.rightArmX();
		rightArm.zRot += pose.rightArmZ();
		leftArm.xRot += pose.leftArmX();
		leftArm.zRot += pose.leftArmZ();
		rightLeg.xRot += pose.rightLegX();
		leftLeg.xRot += pose.leftLegX();
	}

	static PlushModel create(boolean slim) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float shoulder = -(LEG_HEIGHT + BODY_HEIGHT);

		// The head pivots at the neck, so it nods and wobbles on top of the body.
		root.addOrReplaceChild("head", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8)
						.texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, HAT),
				PartPose.offset(0, shoulder + NECK_OVERLAP, 0));
		root.addOrReplaceChild("body", CubeListBuilder.create()
						.texOffs(16, 16).addBox(-4, -6, -2, 8, 12, 4)
						.texOffs(16, 32).addBox(-4, -6, -2, 8, 12, 4, OUTER),
				scaled(0, -LEG_HEIGHT - BODY_HEIGHT / 2, 0, BODY_SCALE_X, BODY_SCALE_Y, BODY_SCALE_Z));

		int armWidth = slim ? 3 : 4;
		float armX = 4 * BODY_SCALE_X + armWidth * ARM_SCALE_XZ / 2;
		// Arms hang from the shoulder: the box starts at the pivot and runs down.
		root.addOrReplaceChild("right_arm", CubeListBuilder.create()
						.texOffs(40, 16).addBox(-armWidth / 2f, 0, -2, armWidth, 12, 4)
						.texOffs(40, 32).addBox(-armWidth / 2f, 0, -2, armWidth, 12, 4, OUTER),
				limb(-armX, shoulder + 0.4f, ARM_SPREAD, ARM_SCALE_XZ, ARM_SCALE_Y));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create()
						.texOffs(32, 48).addBox(-armWidth / 2f, 0, -2, armWidth, 12, 4)
						.texOffs(48, 48).addBox(-armWidth / 2f, 0, -2, armWidth, 12, 4, OUTER),
				limb(armX, shoulder + 0.4f, -ARM_SPREAD, ARM_SCALE_XZ, ARM_SCALE_Y));

		// Legs hang from the hip the same way.
		float legX = 2 * LEG_SCALE_XZ;
		root.addOrReplaceChild("right_leg", CubeListBuilder.create()
						.texOffs(0, 16).addBox(-2, 0, -2, 4, 12, 4)
						.texOffs(0, 32).addBox(-2, 0, -2, 4, 12, 4, OUTER),
				limb(-legX, -LEG_HEIGHT, 0, LEG_SCALE_XZ, LEG_SCALE_Y));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create()
						.texOffs(16, 48).addBox(-2, 0, -2, 4, 12, 4)
						.texOffs(0, 48).addBox(-2, 0, -2, 4, 12, 4, OUTER),
				limb(legX, -LEG_HEIGHT, 0, LEG_SCALE_XZ, LEG_SCALE_Y));
		return new PlushModel(LayerDefinition.create(mesh, 64, 64).bakeRoot());
	}

	private static PartPose scaled(float x, float y, float z, float sx, float sy, float sz) {
		return new PartPose(x, y, z, 0, 0, 0, sx, sy, sz);
	}

	private static PartPose limb(float x, float y, float zRot, float scaleXZ, float scaleY) {
		return new PartPose(x, y, 0, 0, 0, zRot, scaleXZ, scaleY, scaleXZ);
	}
}
