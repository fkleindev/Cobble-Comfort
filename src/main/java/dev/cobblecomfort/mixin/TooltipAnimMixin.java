package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.smoothgui.TooltipAnimation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tooltips fade and slide in, follow the mouse softly and glide from one to the next. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class TooltipAnimMixin {
	@Inject(method = "tooltip", at = @At("HEAD"))
	private void cobblecomfort$beginTooltip(CallbackInfo ci) {
		TooltipAnimation.begin();
	}

	@WrapOperation(method = "tooltip", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
	private Vector2ic cobblecomfort$position(ClientTooltipPositioner positioner, int screenWidth, int screenHeight, int mouseX, int mouseY,
			int width, int height, Operation<Vector2ic> original) {
		return TooltipAnimation.position(original.call(positioner, screenWidth, screenHeight, mouseX, mouseY, width, height), width, height);
	}

	@WrapOperation(method = "tooltip", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/TooltipRenderUtil;extractTooltipBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/resources/Identifier;)V"))
	private void cobblecomfort$background(GuiGraphicsExtractor g, int x, int y, int width, int height, Identifier style,
			Operation<Void> original) {
		original.call(g, x, y, TooltipAnimation.width(width), TooltipAnimation.height(height), style);
		TooltipAnimation.clip(g, x, y, width, height);
	}

	@Inject(method = "tooltip", at = @At("RETURN"))
	private void cobblecomfort$endTooltip(CallbackInfo ci) {
		TooltipAnimation.end((GuiGraphicsExtractor) (Object) this);
	}
}
