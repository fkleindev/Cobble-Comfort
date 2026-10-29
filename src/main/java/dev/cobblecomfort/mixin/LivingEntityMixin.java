package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.hotbar.HotbarFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** The local player's item broke (the server's break event, before the slot is emptied): it shatters in the hotbar. */
	@Inject(method = "breakItem", at = @At("HEAD"))
	private void cobblecomfort$itemBroken(ItemStack stack, CallbackInfo ci) {
		if ((Object) this instanceof Player player && player == Minecraft.getInstance().player) {
			HotbarFeature.INSTANCE.onItemBroken(player, stack);
		}
	}
}
