package dev.cobblecomfort.features.tracker;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.HashMap;
import java.util.Map;

/** Counts items by registry id, optionally looking into shulker boxes and bundles. */
public final class ItemCounter {
	/** Shulker boxes can contain bundles, so two levels of nesting are enough. */
	private static final int MAX_DEPTH = 2;

	private ItemCounter() {
	}

	public static Map<String, Integer> countInventory(Inventory inventory, TrackerConfig cfg) {
		Map<String, Integer> counts = new HashMap<>();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			add(counts, inventory.getItem(i), cfg, 0);
		}
		return counts;
	}

	public static void add(Map<String, Integer> counts, ItemStack stack, TrackerConfig cfg, int depth) {
		if (stack.isEmpty()) {
			return;
		}
		counts.merge(id(stack), stack.getCount(), Integer::sum);
		if (depth >= MAX_DEPTH) {
			return;
		}
		if (cfg.countShulkers) {
			ItemContainerContents container = stack.get(DataComponents.CONTAINER);
			if (container != null) {
				container.nonEmptyItemCopyStream().forEach(inner -> add(counts, inner, cfg, depth + 1));
			}
		}
		if (cfg.countBundles) {
			BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
			if (bundle != null) {
				bundle.itemCopies().forEach(inner -> add(counts, inner, cfg, depth + 1));
			}
		}
	}

	public static String id(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}
}
