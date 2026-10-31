package dev.cobblecomfort.features.petplush;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The torso box in the body part's own space (blocks, y down, -z in front), as it is drawn: animated player models
 * (Fresh Animations) move and resize it, so things worn on it are placed from this instead of from vanilla's numbers.
 */
public record Torso(float top, float bottom, float front, float back, float centerX, float halfWidth) {
	public static final Torso VANILLA = new Torso(0f, 0.75f, -0.125f, 0.125f, 0f, 0.25f);
	/** How far the skin's jacket layer, chestplates and leggings stand out from the torso, in pixels. */
	private static final float JACKET = 0.25f;
	private static final float CHESTPLATE = 1f;
	private static final float LEGGINGS = 0.5f;

	/** This box grown by {@code pixels} on every side. */
	public Torso inflated(float pixels) {
		float d = pixels / 16f;
		return new Torso(top - d, bottom + d, front - d, back + d, centerX, halfWidth + d);
	}

	/**
	 * How far what is worn on the torso stands out from it, in pixels, so things hung on it sit on the armour instead of
	 * in it. {@code waist}: around the waist, where leggings cover the torso too.
	 */
	public static float worn(HumanoidRenderState state, boolean waist) {
		if (isArmor(state.chestEquipment, EquipmentSlot.CHEST)) {
			return CHESTPLATE;
		}
		if (waist && isArmor(state.legsEquipment, EquipmentSlot.LEGS)) {
			return LEGGINGS;
		}
		return JACKET;
	}

	private static boolean isArmor(ItemStack stack, EquipmentSlot slot) {
		Equippable equippable = stack == null ? null : stack.get(DataComponents.EQUIPPABLE);
		// Elytras are worn on the chest but drawn as wings.
		return equippable != null && equippable.slot() == slot && equippable.assetId().isPresent() && !stack.is(Items.ELYTRA);
	}

	/** Measured from the body part as posed now; vanilla's box if it has none. */
	public static Torso of(ModelPart body) {
		PoseStack own = new PoseStack();
		body.translateAndRotate(own);
		Matrix4f toLocal = new Matrix4f(own.last().pose()).invert();
		float[] b = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
		Vector3f local = new Vector3f();
		body.getExtentsForGui(new PoseStack(), v -> {
			toLocal.transformPosition(v.x(), v.y(), v.z(), local);
			b[0] = Math.min(b[0], local.x);
			b[1] = Math.max(b[1], local.x);
			b[2] = Math.min(b[2], local.y);
			b[3] = Math.max(b[3], local.y);
			b[4] = Math.min(b[4], local.z);
			b[5] = Math.max(b[5], local.z);
		});
		return b[0] > b[1] ? VANILLA : new Torso(b[2], b[3], b[4], b[5], (b[0] + b[1]) / 2f, (b[1] - b[0]) / 2f);
	}
}
