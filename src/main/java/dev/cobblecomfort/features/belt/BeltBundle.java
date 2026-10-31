package dev.cobblecomfort.features.belt;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

/** A bundle on the belt: its dye colour id (-1 for an undyed one) and how full it is (0 to 1). */
public record BeltBundle(int color, float fill) {
	/** At most this many hang on a belt. */
	public static final int MAX = 4;

	/** The bundle this stack is, or null if it is none. */
	public static BeltBundle of(ItemStack stack) {
		if (stack.isEmpty() || !stack.has(DataComponents.BUNDLE_CONTENTS)) {
			return null;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		DyeColor dye = path.endsWith("_bundle") ? DyeColor.byName(path.substring(0, path.length() - "_bundle".length()), null) : null;
		return new BeltBundle(dye == null ? -1 : dye.getId(), BundleItem.getFullnessDisplay(stack));
	}

	public DyeColor dye() {
		return color < 0 ? null : DyeColor.byId(color);
	}
}
