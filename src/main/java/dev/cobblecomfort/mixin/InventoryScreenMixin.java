package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.cobblecomfort.features.invtools.InventoryToolsFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The section view of the inventory: its size before the layout, its own background, the recipe button's place. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
	@Inject(method = "init", at = @At("HEAD"))
	private void cobblecomfort$sections(CallbackInfo ci) {
		InventoryToolsFeature.INSTANCE.beforeInventoryInit((InventoryScreen) (Object) this);
	}

	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void cobblecomfort$background(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y,
			float u, float v, int width, int height, int texWidth, int texHeight, Operation<Void> original) {
		if (!InventoryToolsFeature.INSTANCE.sections().drawBackground(this, g, pipeline, texture, x, y)) {
			original.call(g, pipeline, texture, x, y, u, v, width, height, texWidth, texHeight);
		}
	}

	@Inject(method = "getRecipeBookButtonPosition", at = @At("RETURN"), cancellable = true)
	private void cobblecomfort$recipeButton(CallbackInfoReturnable<ScreenPosition> cir) {
		ScreenPosition position = cir.getReturnValue();
		int y = InventoryToolsFeature.INSTANCE.sections().recipeButtonY(this, position.y());
		if (y != position.y()) {
			cir.setReturnValue(new ScreenPosition(position.x(), y));
		}
	}
}
