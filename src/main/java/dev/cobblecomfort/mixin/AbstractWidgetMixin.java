package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.inventoryfx.InventoryFxFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The trading screen's offer buttons slide in together with their row's items (see {@link MerchantScreenMixin}). */
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {
	@Unique
	private boolean cobblecomfort$slid;

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void cobblecomfort$slideOffer(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		cobblecomfort$slid = false;
		if ((Object) this instanceof TradeOfferButtonAccessor button
				&& Minecraft.getInstance().gui.screen() instanceof MerchantScreenAccessor screen) {
			float offset = InventoryFxFeature.INSTANCE.stations().slideOffset(screen.cobblecomfort$scrollOff() + button.cobblecomfort$index());
			if (offset != 0f) {
				cobblecomfort$slid = true;
				InventoryFxFeature.INSTANCE.stations().beginSlide(g, (ContainerScreenAccessor) screen, offset);
			}
		}
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void cobblecomfort$endSlide(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (cobblecomfort$slid) {
			cobblecomfort$slid = false;
			InventoryFxFeature.INSTANCE.stations().endSlide(g);
		}
	}
}
