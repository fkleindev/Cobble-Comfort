package dev.cobblecomfort.features.invtools;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sorts a group of slots with ordinary clicks, so it works on any server: first stacks of the same item are
 * merged, then the stacks are swapped into order. Each step is one small action (2-3 clicks) and is worked out from
 * the slots' current contents, so the job can be spread over several ticks.
 */
final class Sorter {
	private static final int MAX_STEPS = 2000;
	private static Map<Item, Integer> creativeOrder;

	private final AbstractContainerMenu menu;
	private final List<Slot> slots;
	private final Comparator<ItemStack> order;
	private int steps;

	Sorter(AbstractContainerMenu menu, List<Slot> slots, SortOrder sortOrder) {
		this.menu = menu;
		this.slots = slots;
		this.order = comparator(sortOrder);
	}

	List<Slot> slots() {
		return slots;
	}

	AbstractContainerMenu menu() {
		return menu;
	}

	/** Does one action; returns false when the slots are sorted (or sorting has to stop). */
	boolean step() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.containerMenu != menu || !menu.getCarried().isEmpty() || ++steps > MAX_STEPS) {
			return false;
		}
		return merge() || reorder();
	}

	/** Merges one later stack into an earlier, not yet full stack of the same item. */
	private boolean merge() {
		for (int a = 0; a < slots.size(); a++) {
			ItemStack target = slots.get(a).getItem();
			if (target.isEmpty() || target.getCount() >= slots.get(a).getMaxStackSize(target)) {
				continue;
			}
			for (int b = a + 1; b < slots.size(); b++) {
				if (ItemStack.isSameItemSameComponents(target, slots.get(b).getItem())) {
					click(slots.get(b));
					click(slots.get(a));
					if (!menu.getCarried().isEmpty()) {
						click(slots.get(b));
					}
					return true;
				}
			}
		}
		return false;
	}

	/** Swaps the first slot that holds the wrong stack with the slot holding the right one. */
	private boolean reorder() {
		List<ItemStack> wanted = new ArrayList<>();
		for (Slot slot : slots) {
			if (slot.hasItem()) {
				wanted.add(slot.getItem());
			}
		}
		wanted.sort(order);
		for (int i = 0; i < slots.size(); i++) {
			if (i >= wanted.size()) {
				// Every stack already sits in the first slots, the rest is empty.
				return false;
			}
			ItemStack current = slots.get(i).getItem();
			ItemStack want = wanted.get(i);
			if (same(current, want)) {
				continue;
			}
			for (int j = i + 1; j < slots.size(); j++) {
				if (same(slots.get(j).getItem(), want)) {
					return swap(slots.get(i), slots.get(j));
				}
			}
			return false;
		}
		return false;
	}

	/** Swaps two slots; {@code to} gets the stack of {@code from}. */
	private boolean swap(Slot to, Slot from) {
		if (to.hasItem() && (holdsItems(to.getItem()) || holdsItems(from.getItem()))) {
			// Clicking a stack onto a bundle (or a bundle onto a stack) puts it inside instead of swapping,
			// so go through an empty slot: every click then lands on an empty slot.
			Slot spare = spareSlot();
			if (spare == null) {
				return false;
			}
			click(to);
			click(spare);
			click(from);
			click(to);
			click(spare);
			click(from);
			return true;
		}
		click(from);
		click(to);
		if (!menu.getCarried().isEmpty()) {
			click(from);
		}
		return true;
	}

	private static boolean holdsItems(ItemStack stack) {
		return stack.has(DataComponents.BUNDLE_CONTENTS);
	}

	/** An empty slot to park a stack in: one of the sorted slots, otherwise one of the player's inventory. */
	private Slot spareSlot() {
		for (Slot slot : slots) {
			if (!slot.hasItem()) {
				return slot;
			}
		}
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory && slot.getContainerSlot() < Inventory.INVENTORY_SIZE && !slot.hasItem()) {
				return slot;
			}
		}
		return null;
	}

	private static boolean same(ItemStack a, ItemStack b) {
		return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
	}

	private void click(Slot slot) {
		Minecraft mc = Minecraft.getInstance();
		mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
	}

	// ---------------------------------------------------------------- order

	private static Comparator<ItemStack> comparator(SortOrder sortOrder) {
		Comparator<ItemStack> primary = switch (sortOrder) {
			case CREATIVE -> Comparator.comparingInt(Sorter::creativeIndex);
			case NAME -> Comparator.comparing(stack -> stack.getHoverName().getString());
			case COUNT -> Comparator.comparingInt((ItemStack stack) -> -stack.getCount());
		};
		return primary
				.thenComparingInt(stack -> BuiltInRegistries.ITEM.getId(stack.getItem()))
				.thenComparing(stack -> stack.getHoverName().getString())
				.thenComparingInt(stack -> -stack.getCount());
	}

	private static int creativeIndex(ItemStack stack) {
		Map<Item, Integer> map = creativeOrder();
		Integer index = map.get(stack.getItem());
		return index != null ? index : map.size() + BuiltInRegistries.ITEM.getId(stack.getItem());
	}

	/** Position of every item in the creative tabs, built once (the tabs are filled on demand if needed). */
	private static Map<Item, Integer> creativeOrder() {
		if (creativeOrder != null) {
			return creativeOrder;
		}
		Minecraft mc = Minecraft.getInstance();
		Map<Item, Integer> map = new HashMap<>();
		if (mc.player != null && mc.level != null) {
			CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
			for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
				if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
					continue;
				}
				for (ItemStack stack : tab.getDisplayItems()) {
					map.putIfAbsent(stack.getItem(), map.size());
				}
			}
		}
		if (!map.isEmpty()) {
			creativeOrder = map;
		}
		return map;
	}
}
