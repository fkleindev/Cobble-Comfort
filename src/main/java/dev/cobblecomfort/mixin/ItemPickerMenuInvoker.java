package dev.cobblecomfort.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public interface ItemPickerMenuInvoker {
	@Invoker("getRowIndexForScroll")
	int cobblecomfort$rowForScroll(float scroll);
}
