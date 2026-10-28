package dev.cobblecomfort.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cobblecomfort.features.smoothgui.CreativeScroll;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth scrolling in the creative inventory: slides the item grid. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerSlotScrollMixin {
	@Inject(method = "extractSlots", at = @At("HEAD"))
	private void cobblecomfort$beginSlots(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		if ((Object) this instanceof CreativeModeInventoryScreen creative) {
			CreativeScroll.beginFade();
			CreativeScroll.beginSlots(creative.getMenu().slots, CreativeScreenAccessor.cobblecomfort$grid());
		}
	}

	@WrapOperation(method = "extractSlots", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V"))
	private void cobblecomfort$slideSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY,
			Operation<Void> original) {
		if (screen instanceof CreativeModeInventoryScreen) {
			CreativeScroll.slot(g, slot, CreativeScreenAccessor.cobblecomfort$grid(), () -> original.call(screen, g, slot, mouseX, mouseY));
		} else {
			original.call(screen, g, slot, mouseX, mouseY);
		}
	}

	@Inject(method = "extractSlots", at = @At("RETURN"))
	private void cobblecomfort$endSlots(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		if ((Object) this instanceof CreativeModeInventoryScreen creative) {
			CreativeModeInventoryScreen.ItemPickerMenu menu = creative.getMenu();
			int row = ((ItemPickerMenuInvoker) menu).cobblecomfort$rowForScroll(((CreativeScreenAccessor) creative).cobblecomfort$scrollOffs());
			CreativeScroll.endSlots(g, menu.items, row);
			CreativeScroll.endFade();
		}
	}
}
