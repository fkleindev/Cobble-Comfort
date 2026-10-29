package dev.cobblecomfort.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The trading screen's offer buttons (a private class): which visible row a button is. */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.MerchantScreen$TradeOfferButton")
public interface TradeOfferButtonAccessor {
	@Accessor("index")
	int cobblecomfort$index();
}
