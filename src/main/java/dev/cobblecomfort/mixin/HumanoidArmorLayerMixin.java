package dev.cobblecomfort.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.features.wardrobe.WardrobeFeature;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's hat from the wardrobe takes the place of their helmet. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
	@Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$hat(PoseStack pose, SubmitNodeCollector collector, ItemStack stack, EquipmentSlot slot, int light,
			HumanoidRenderState state, CallbackInfo ci) {
		if (slot == EquipmentSlot.HEAD && state instanceof AvatarRenderState avatar && WardrobeFeature.INSTANCE.hidesHelmet(avatar)) {
			ci.cancel();
		}
	}
}
