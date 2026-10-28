package dev.cobblecomfort.mixin;

import dev.cobblecomfort.features.inventoryfx.InventoryFxFeature;
import dev.cobblecomfort.features.invtools.InventoryToolsFeature;
import dev.cobblecomfort.features.shulker.ShulkerPreviewFeature;
import dev.cobblecomfort.features.smoothgui.SmoothGuiFeature;
import dev.cobblecomfort.features.rarity.RarityFeature;
import dev.cobblecomfort.features.rarity.RarityTooltips;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	protected Slot hoveredSlot;
	@Shadow
	@Final
	protected AbstractContainerMenu menu;
	@Shadow
	protected int leftPos;
	@Shadow
	protected int topPos;
	@Shadow
	protected boolean isQuickCrafting;
	@Shadow
	@Final
	protected Set<Slot> quickCraftSlots;
	@Shadow
	private int quickCraftingRemainder;

	@Shadow
	private void extractFloatingItem(GuiGraphicsExtractor g, ItemStack stack, int x, int y, String text) {
		throw new AssertionError("shadowed");
	}

	// ---- slots: bounce transform around everything drawn for the slot (declared first so it wraps the rest)

	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void cobblecomfort$beginSlotAnimation(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		InventoryFxFeature.INSTANCE.beginSlot(g, slot);
		SmoothGuiFeature.INSTANCE.slotTransform(g, this, slot);
	}

	/** Rarity indicator behind the slot's item (the item is drawn later in the same method). */
	@Inject(method = "extractSlot", at = @At("HEAD"))
	private void cobblecomfort$rarityIndicator(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		RarityFeature.Place place = (Object) this instanceof CreativeModeInventoryScreen
				? RarityFeature.Place.CREATIVE
				: RarityFeature.Place.INVENTORY;
		RarityFeature.INSTANCE.drawSlot(g, slot.getItem(), slot.x, slot.y, slot == hoveredSlot, place);
	}

	@Inject(method = "extractSlot", at = @At("RETURN"))
	private void cobblecomfort$endSlotAnimation(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
		ShulkerPreviewFeature.INSTANCE.drawFillBar(g, slot.getItem(), slot.x, slot.y, false);
		InventoryToolsFeature.INSTANCE.decorateSlot(g, this, slot);
		InventoryFxFeature.INSTANCE.endSlot(g, slot);
	}

	// ---- carried item: springy follow, tilt, pop and particles

	/** Replaces vanilla's carried item rendering; the count text logic mirrors vanilla. */
	@Inject(method = "extractCarriedItem", at = @At("HEAD"), cancellable = true)
	private void cobblecomfort$carriedItem(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		ItemStack carried = menu.getCarried();
		boolean handled = InventoryFxFeature.INSTANCE.frame(g, this, mouseX, mouseY, carried, hoveredSlot,
				leftPos, topPos, quickCraftSlots, (x, y) -> {
					ItemStack shown = carried;
					String text = null;
					if (isQuickCrafting && quickCraftSlots.size() > 1) {
						shown = carried.copyWithCount(quickCraftingRemainder);
						if (shown.isEmpty()) {
							text = ChatFormatting.YELLOW + "0";
						}
					}
					extractFloatingItem(g, shown, x, y, text);
				});
		if (handled) {
			ci.cancel();
		}
	}

	// ---- tooltip item tracking for the rarity tooltip frame

	@Inject(method = "extractTooltip", at = @At("HEAD"))
	private void cobblecomfort$rememberTooltipItem(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		RarityTooltips.pending = hoveredSlot != null && hoveredSlot.hasItem() ? hoveredSlot.getItem() : null;
	}

	@Inject(method = "extractTooltip", at = @At("RETURN"))
	private void cobblecomfort$forgetTooltipItem(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
		RarityTooltips.pending = null;
	}
}
