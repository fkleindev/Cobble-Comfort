package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.food.FoodHudFeature;
import dev.cobblecomfort.features.preview.ItemPreviewFeature;
import dev.cobblecomfort.features.shulker.ShulkerPreviewFeature;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Consumer;

/** Shulker, map and book previews: tooltip images, replacing vanilla's text lists where they show the same. */
@Mixin(ItemStack.class)
public abstract class ItemStackShulkerMixin {
	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$shulkerPreview(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		Optional<TooltipComponent> image = ShulkerPreviewFeature.INSTANCE.tooltipImage((ItemStack) (Object) this);
		if (image.isEmpty()) {
			image = ItemPreviewFeature.INSTANCE.tooltipImage((ItemStack) (Object) this);
		}
		if (image.isPresent()) {
			cir.setReturnValue(image);
		}
	}

	/** Food HUD: food without a tooltip image of its own shows its hunger and saturation values. */
	@Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
	private void cobblecomfort$foodValues(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (cir.getReturnValue().isEmpty()) {
			Optional<TooltipComponent> food = FoodHudFeature.INSTANCE.tooltipImage((ItemStack) (Object) this);
			if (food.isPresent()) {
				cir.setReturnValue(food);
			}
		}
	}

	@Inject(method = "addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/component/TooltipProvider$Getter;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V",
			at = @At("HEAD"), cancellable = true)
	private <T> void cobblecomfort$hideContentLines(DataComponentType<T> type, TooltipProvider.Getter<T> getter,
			Item.TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag, CallbackInfo ci) {
		if (type == DataComponents.CONTAINER && ShulkerPreviewFeature.INSTANCE.replacesTooltip(this)
				|| type == DataComponents.STORED_ENCHANTMENTS && ItemPreviewFeature.INSTANCE.replacesEnchantLines(this)) {
			ci.cancel();
		}
	}
}
