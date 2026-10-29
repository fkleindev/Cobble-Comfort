package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.cobblecomfort.features.inventoryfx.InventoryFxFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Furnace flame and arrow follow the progress smoothly, with the next pixel partly drawn. */
@Mixin(AbstractFurnaceScreen.class)
public abstract class AbstractFurnaceScreenMixin {
	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/AbstractFurnaceMenu;getLitProgress()F"))
	private float cobblecomfort$litProgress(AbstractFurnaceMenu menu, Operation<Float> original) {
		return InventoryFxFeature.INSTANCE.stations().litProgress(this, original.call(menu));
	}

	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/AbstractFurnaceMenu;getBurnProgress()F"))
	private float cobblecomfort$burnProgress(AbstractFurnaceMenu menu, Operation<Float> original) {
		return InventoryFxFeature.INSTANCE.stations().burnProgress(this, original.call(menu));
	}

	/** The flame (first) and the arrow (second) sprite. */
	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"))
	private void cobblecomfort$progressSprite(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int texW, int texH,
			int u, int v, int x, int y, int w, int h, Operation<Void> original) {
		boolean flame = texW == 14;
		// The flame grows upwards from the bottom of its 14 pixel box.
		int top = flame ? y + h - 14 : y;
		boolean drawn = InventoryFxFeature.INSTANCE.stations().drawProgress(g, flame,
				(du, dv, dx, dy, dw, dh, color) -> g.blitSprite(pipeline, sprite, texW, texH, du, dv, dx, dy, dw, dh, color), x, top);
		if (!drawn) {
			original.call(g, pipeline, sprite, texW, texH, u, v, x, y, w, h);
		}
	}
}
