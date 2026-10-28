package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.enchantdesc.EnchantDescFeature;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Enchantment descriptions: decorates each enchantment line and adds detail lines right after it. */
@Mixin(ItemEnchantments.class)
public abstract class ItemEnchantmentsMixin {
	@Inject(method = "addToTooltip", at = @At("HEAD"))
	private void cobblecomfort$begin(Item.TooltipContext context, Consumer<Component> out, TooltipFlag flag,
			DataComponentGetter components, CallbackInfo ci) {
		EnchantDescFeature.INSTANCE.begin((ItemEnchantments) (Object) this, components);
	}

	@WrapOperation(method = "addToTooltip", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/Enchantment;getFullname(Lnet/minecraft/core/Holder;I)Lnet/minecraft/network/chat/Component;"))
	private Component cobblecomfort$name(Holder<Enchantment> holder, int level, Operation<Component> original) {
		return EnchantDescFeature.INSTANCE.decorateName(holder, level, original.call(holder, level));
	}

	@SuppressWarnings("unchecked")
	@WrapOperation(method = "addToTooltip", at = @At(value = "INVOKE",
			target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"))
	private void cobblecomfort$details(Consumer<Object> out, Object line, Operation<Void> original) {
		original.call(out, line);
		EnchantDescFeature.INSTANCE.appendDetails((Consumer<Component>) (Consumer<?>) out);
	}

	@Inject(method = "addToTooltip", at = @At("RETURN"))
	private void cobblecomfort$end(Item.TooltipContext context, Consumer<Component> out, TooltipFlag flag,
			DataComponentGetter components, CallbackInfo ci) {
		EnchantDescFeature.INSTANCE.end(out);
	}
}
