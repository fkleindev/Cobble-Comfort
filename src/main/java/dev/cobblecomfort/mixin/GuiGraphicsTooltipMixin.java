package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.rarity.RarityTooltips;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Tracks which item a deferred tooltip belongs to, for the rarity-coloured tooltip frame. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsTooltipMixin {
	private static final String ITEM_TOOLTIP = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;"
			+ "Lnet/minecraft/world/item/ItemStack;II)V";
	private static final String DEFER = "setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;II"
			+ "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;"
			+ "Lnet/minecraft/resources/Identifier;ZZ)V";

	@Unique
	private ItemStack cobblecomfort$tooltipStack;

	@Inject(method = ITEM_TOOLTIP, at = @At("HEAD"))
	private void cobblecomfort$itemTooltip(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
		RarityTooltips.pending = stack;
	}

	@Inject(method = ITEM_TOOLTIP, at = @At("RETURN"))
	private void cobblecomfort$itemTooltipDone(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
		RarityTooltips.pending = null;
	}

	@Inject(method = DEFER, at = @At("HEAD"))
	private void cobblecomfort$deferTooltip(Font font, List<ClientTooltipComponent> lines, int x, int y,
			ClientTooltipPositioner positioner, Identifier style, boolean focused, boolean replace, CallbackInfo ci) {
		cobblecomfort$tooltipStack = RarityTooltips.pending;
	}

	@Inject(method = "tooltip", at = @At("HEAD"))
	private void cobblecomfort$beginTooltip(CallbackInfo ci) {
		RarityTooltips.active = cobblecomfort$tooltipStack;
		cobblecomfort$tooltipStack = null;
	}

	@Inject(method = "tooltip", at = @At("RETURN"))
	private void cobblecomfort$endTooltip(CallbackInfo ci) {
		RarityTooltips.active = null;
	}
}
