package dev.cobblecomfort.features.rarity;

import net.minecraft.world.item.ItemStack;

/**
 * Carries the item a tooltip belongs to from where it is requested to where its frame is drawn.
 * Tooltips are deferred to the end of the frame, so the stack passes through two stages.
 */
public final class RarityTooltips {
	/** Set while a screen requests an item tooltip. */
	public static ItemStack pending;
	/** Set while the deferred tooltip of that item is being drawn. */
	public static ItemStack active;

	private RarityTooltips() {
	}
}
