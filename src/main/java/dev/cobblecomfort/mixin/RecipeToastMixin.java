package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.toasts.ToastsFeature;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Recipe toasts: a "×3" when one toast holds several recipes, or none at all if switched off. */
@Mixin(RecipeToast.class)
public abstract class RecipeToastMixin {
	@Shadow
	@Final
	private List<?> recipeItems;

	@Inject(method = "addOrUpdate", at = @At("HEAD"), cancellable = true)
	private static void cobblecomfort$hide(ToastManager manager, RecipeDisplay recipe, CallbackInfo ci) {
		if (ToastsFeature.INSTANCE.hideRecipes()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void cobblecomfort$count(GuiGraphicsExtractor g, Font font, long visibleTime, CallbackInfo ci) {
		if (ToastsFeature.INSTANCE.stack() && recipeItems.size() > 1) {
			ToastsFeature.INSTANCE.drawBadge(g, "×" + recipeItems.size());
		}
	}
}
