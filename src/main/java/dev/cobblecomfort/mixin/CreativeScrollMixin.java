package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.smoothgui.CreativeScroll;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Smooth scrolling in the creative inventory: notices how many rows the wheel moved the grid. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScrollMixin {
	@Shadow
	private float scrollOffs;
	@Shadow
	private static CreativeModeTab selectedTab;

	@Unique
	private int cobblecomfort$rowBefore;

	@Unique
	private int cobblecomfort$row() {
		CreativeModeInventoryScreen screen = (CreativeModeInventoryScreen) (Object) this;
		return ((ItemPickerMenuInvoker) screen.getMenu()).cobblecomfort$rowForScroll(scrollOffs);
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"))
	private void cobblecomfort$beforeScroll(double x, double y, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		cobblecomfort$rowBefore = cobblecomfort$row();
	}

	@Inject(method = "mouseScrolled", at = @At("RETURN"))
	private void cobblecomfort$afterScroll(double x, double y, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
		CreativeScroll.scrolled(cobblecomfort$row() - cobblecomfort$rowBefore);
	}

	@Inject(method = "selectTab", at = @At("HEAD"))
	private void cobblecomfort$tabChanged(CreativeModeTab tab, CallbackInfo ci) {
		if (tab != selectedTab) {
			CreativeScroll.tabChanged();
		}
	}

	/** The newly selected tab rises out of the panel. */
	@Inject(method = "extractTabButton", at = @At("HEAD"))
	private void cobblecomfort$beginTab(GuiGraphicsExtractor g, int mouseX, int mouseY, CreativeModeTab tab, CallbackInfo ci) {
		g.pose().pushMatrix();
		if (tab == selectedTab) {
			g.pose().translate(0f, CreativeScroll.tabOffset(tab.row() == CreativeModeTab.Row.TOP));
		}
	}

	@Inject(method = "extractTabButton", at = @At("RETURN"))
	private void cobblecomfort$endTab(GuiGraphicsExtractor g, int mouseX, int mouseY, CreativeModeTab tab, CallbackInfo ci) {
		g.pose().popMatrix();
	}
}
