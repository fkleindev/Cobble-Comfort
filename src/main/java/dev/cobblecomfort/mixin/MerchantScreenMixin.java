package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.inventoryfx.InventoryFxFeature;
import dev.cobblecomfort.features.inventoryfx.StationFx;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trading screen: new offers slide into the list one after another (their items here, their buttons in
 * {@link AbstractWidgetMixin}), and the trader's level bar fills smoothly.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin {
	@Shadow
	private int scrollOff;

	/** The offer row being drawn, counted from the first visible one, and whether it is sliding. */
	@Unique
	private int cobblecomfort$row;
	@Unique
	private boolean cobblecomfort$slid;

	@Inject(method = "extractContents", at = @At("HEAD"))
	private void cobblecomfort$beginRows(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		cobblecomfort$row = 0;
		MerchantMenu menu = ((MerchantScreen) (Object) this).getMenu();
		InventoryFxFeature.INSTANCE.stations().merchantFrame(this, menu.getOffers().size());
	}

	@Inject(method = "extractContents", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/MerchantScreen;extractAndDecorateCostA(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;II)V"))
	private void cobblecomfort$slideRow(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		StationFx stations = InventoryFxFeature.INSTANCE.stations();
		float offset = stations.slideOffset(scrollOff + cobblecomfort$row);
		cobblecomfort$slid = offset != 0f;
		if (cobblecomfort$slid) {
			stations.beginSlide(g, (ContainerScreenAccessor) this, offset);
		}
	}

	/** After the result item of the row (the second decorations call) the row is done. */
	@Inject(method = "extractContents", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",
			ordinal = 1, shift = At.Shift.AFTER))
	private void cobblecomfort$endRow(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (cobblecomfort$slid) {
			InventoryFxFeature.INSTANCE.stations().endSlide(g);
			cobblecomfort$slid = false;
		}
		cobblecomfort$row++;
	}

	@WrapOperation(method = "extractProgressBar", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/MerchantMenu;getTraderLevel()I"))
	private int cobblecomfort$barLevel(MerchantMenu menu, Operation<Integer> original) {
		return InventoryFxFeature.INSTANCE.stations().barLevel(menu, original.call(menu));
	}

	@WrapOperation(method = "extractProgressBar", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/MerchantMenu;getTraderXp()I"))
	private int cobblecomfort$barXp(MerchantMenu menu, Operation<Integer> original) {
		return InventoryFxFeature.INSTANCE.stations().barXp(original.call(menu));
	}
}
