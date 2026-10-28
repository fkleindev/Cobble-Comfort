package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.itembars.ItemBarsFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Durability bar and cooldown ring in place of vanilla's item decorations. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemBarsMixin {
	@Inject(method = "itemBar", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$durability(ItemStack stack, int x, int y, CallbackInfo ci) {
		if (ItemBarsFeature.INSTANCE.drawBar((GuiGraphicsExtractor) (Object) this, stack, x, y)) {
			ci.cancel();
		}
	}

	@Inject(method = "itemCooldown", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$cooldown(ItemStack stack, int x, int y, CallbackInfo ci) {
		if (ItemBarsFeature.INSTANCE.drawCooldown((GuiGraphicsExtractor) (Object) this, stack, x, y)) {
			ci.cancel();
		}
	}
}
