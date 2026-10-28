package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.effects.EffectHudFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Effect HUD: the effect list next to inventories in the same look, with description tooltips. */
@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {
	@Shadow
	@Final
	private AbstractContainerScreen<?> screen;

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$effectList(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		if (EffectHudFeature.INSTANCE.drawInventory(screen, g, mouseX, mouseY)) {
			ci.cancel();
		}
	}
}
