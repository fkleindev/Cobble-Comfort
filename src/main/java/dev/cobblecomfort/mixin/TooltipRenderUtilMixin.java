package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.cobblecomfort.features.rarity.RarityFeature;
import dev.cobblecomfort.features.rarity.RarityTooltips;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilMixin {
	/** The second sprite drawn is the frame; swap it for a rarity-coloured one (only for the default style). */
	@WrapOperation(method = "extractTooltipBackground", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
			ordinal = 1))
	private static void cobblecomfort$rarityFrame(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite,
			int x, int y, int width, int height, Operation<Void> original, GuiGraphicsExtractor graphics,
			int tooltipX, int tooltipY, int tooltipWidth, int tooltipHeight, Identifier style) {
		if (style != null || !RarityFeature.INSTANCE.drawTooltipFrame(g, RarityTooltips.active, x, y, width, height)) {
			original.call(g, pipeline, sprite, x, y, width, height);
		}
	}
}
