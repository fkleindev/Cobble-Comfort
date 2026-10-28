package dev.cobblecomfort.features.shulker;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The items of a shulker box item, as the full slot list or summed up per item type. */
public record ShulkerContents(List<ItemStack> slots, int color) {
	public static final int SLOTS = 27;
	/** Colour of the undyed (purple) shulker box. */
	private static final int UNDYED_COLOR = 0xFF9A6C9A;

	/** One entry of the compact summary: an item and how many of it the box holds in total. */
	public record Entry(ItemStack stack, int total) {
	}

	public static boolean isShulkerBox(ItemStack stack) {
		return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock;
	}

	/** The contents of a shulker box item, or null if it isn't one or is empty. */
	public static ShulkerContents of(ItemStack stack) {
		if (!isShulkerBox(stack)) {
			return null;
		}
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null) {
			return null;
		}
		List<ItemStack> slots = new ArrayList<>(contents.itemCopies().toList());
		if (slots.stream().allMatch(ItemStack::isEmpty)) {
			return null;
		}
		while (slots.size() < SLOTS) {
			slots.add(ItemStack.EMPTY);
		}
		DyeColor dye = ((ShulkerBoxBlock) ((BlockItem) stack.getItem()).getBlock()).getColor();
		int color = dye == null ? UNDYED_COLOR : 0xFF000000 | dye.getTextureDiffuseColor();
		return new ShulkerContents(slots, color);
	}

	public List<Entry> summary(boolean sortByCount) {
		List<Entry> entries = new ArrayList<>();
		for (ItemStack stack : slots) {
			if (stack.isEmpty()) {
				continue;
			}
			int index = -1;
			for (int i = 0; i < entries.size(); i++) {
				if (ItemStack.isSameItemSameComponents(entries.get(i).stack(), stack)) {
					index = i;
					break;
				}
			}
			if (index < 0) {
				entries.add(new Entry(stack.copyWithCount(1), stack.getCount()));
			} else {
				Entry old = entries.get(index);
				entries.set(index, new Entry(old.stack(), old.total() + stack.getCount()));
			}
		}
		if (sortByCount) {
			entries.sort(Comparator.comparingInt((Entry e) -> -e.total()));
		}
		return entries;
	}

	/** How full the box is (0..1), by occupied slots or by item count. */
	public float fill(FillMode mode) {
		if (mode == FillMode.SLOTS) {
			return slots.stream().filter(s -> !s.isEmpty()).count() / (float) SLOTS;
		}
		float used = 0;
		for (ItemStack stack : slots) {
			if (!stack.isEmpty()) {
				used += stack.getCount() / (float) stack.getMaxStackSize();
			}
		}
		return used / SLOTS;
	}
}
