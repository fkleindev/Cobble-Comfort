package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.smoothgui.RecipePageSlide;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Recipe book pages slide in when turned. */
@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageMixin {
	@Shadow
	private int currentPage;
	@Shadow
	private List<RecipeCollection> recipeCollections;
	@Shadow
	@Final
	private List<RecipeButton> buttons;

	@Unique
	private int cobblecomfort$lastPage = -1;
	@Unique
	private List<RecipeCollection> cobblecomfort$lastCollections;
	@Unique
	private long cobblecomfort$start;
	@Unique
	private int cobblecomfort$direction;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void cobblecomfort$pageTurned(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		// Only a turned page slides; a new tab or search shows its first page at once.
		if (recipeCollections == cobblecomfort$lastCollections && cobblecomfort$lastPage >= 0 && currentPage != cobblecomfort$lastPage) {
			cobblecomfort$direction = Integer.signum(currentPage - cobblecomfort$lastPage);
			cobblecomfort$start = System.nanoTime();
		} else if (recipeCollections != cobblecomfort$lastCollections) {
			cobblecomfort$direction = 0;
		}
		cobblecomfort$lastPage = currentPage;
		cobblecomfort$lastCollections = recipeCollections;
	}

	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeButton;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
	private void cobblecomfort$slideButton(RecipeButton button, GuiGraphicsExtractor g, int mouseX, int mouseY, float delta,
			Operation<Void> original) {
		RecipePageSlide.button(g, buttons, cobblecomfort$direction, RecipePageSlide.progress(cobblecomfort$start),
				() -> original.call(button, g, mouseX, mouseY, delta));
	}
}
