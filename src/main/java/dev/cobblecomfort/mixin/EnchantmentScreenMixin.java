package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.cobblecomfort.features.enchanting.EnchantingFeature;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Enchanting table: readable slot text and extra lines in the slot tooltip. */
@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin {
	/** The slot loop variable {@code i} is the sixth int local (after mouseX, mouseY, xo, yo, goldCount). */
	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/EnchantmentNames;getRandomName(Lnet/minecraft/client/gui/Font;I)Lnet/minecraft/network/chat/FormattedText;"))
	private FormattedText cobblecomfort$readableRunes(EnchantmentNames names, Font font, int width,
			Operation<FormattedText> original, @Local(ordinal = 5) int slot) {
		FormattedText random = original.call(names, font, width);
		FormattedText readable = EnchantingFeature.INSTANCE.readableRunes(((EnchantmentScreen) (Object) this).getMenu(), slot);
		return readable != null ? readable : random;
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"))
	private void cobblecomfort$slotTooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, int x, int y,
			Operation<Void> original) {
		EnchantingFeature.INSTANCE.decorateSlotTooltip((EnchantmentScreen) (Object) this, lines, x, y);
		original.call(g, font, lines, x, y);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void cobblecomfort$overlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		EnchantingFeature.INSTANCE.drawOverlay((EnchantmentScreen) (Object) this, g, mouseX, mouseY);
	}
}
