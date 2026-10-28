package dev.cobblecomfort.features.storage;

import dev.cobblecomfort.features.shulker.ShulkerContents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Counting and formatting helpers for remembered contents. */
public final class StorageItems {
	private static final long MINUTE = 60_000L;
	private static final long HOUR = 60 * MINUTE;
	private static final long DAY = 24 * HOUR;

	/** An item and how many of it there are in total. */
	public record Entry(ItemStack stack, int total) {
	}

	private StorageItems() {
	}

	/** The stacks of a container, optionally with the contents of shulker boxes inside it. */
	public static List<ItemStack> flatten(List<ItemStack> items, boolean shulkers) {
		if (!shulkers) {
			return items;
		}
		List<ItemStack> list = new ArrayList<>(items);
		for (ItemStack stack : items) {
			ShulkerContents contents = ShulkerContents.of(stack);
			if (contents != null) {
				for (ItemStack inner : contents.slots()) {
					if (!inner.isEmpty()) {
						list.add(inner);
					}
				}
			}
		}
		return list;
	}

	/** Sums equal stacks; most first. */
	public static List<Entry> summary(List<ItemStack> items) {
		List<Entry> entries = new ArrayList<>();
		for (ItemStack stack : items) {
			add(entries, stack, stack.getCount());
		}
		entries.sort(Comparator.comparingInt((Entry e) -> -e.total()));
		return entries;
	}

	static void add(List<Entry> entries, ItemStack stack, int count) {
		if (stack.isEmpty()) {
			return;
		}
		for (int i = 0; i < entries.size(); i++) {
			Entry entry = entries.get(i);
			if (ItemStack.isSameItemSameComponents(entry.stack(), stack)) {
				entries.set(i, new Entry(entry.stack(), entry.total() + count));
				return;
			}
		}
		entries.add(new Entry(stack.copyWithCount(1), count));
	}

	/** Short count for item cells: 64, 999, 1.2k, 12k. */
	public static String shortCount(int count) {
		if (count < 1000) {
			return String.valueOf(count);
		}
		if (count < 10_000) {
			return String.format(Locale.ROOT, "%.1fk", count / 1000f);
		}
		return count / 1000 + "k";
	}

	/** "just now", "5 min ago", "3 h ago", "2 days ago". */
	public static Component age(long seen) {
		long ago = Math.max(0, System.currentTimeMillis() - seen);
		if (ago < MINUTE) {
			return Component.translatable("cobblecomfort.storage.age.now");
		}
		if (ago < HOUR) {
			return Component.translatable("cobblecomfort.storage.age.minutes", ago / MINUTE);
		}
		if (ago < DAY) {
			return Component.translatable("cobblecomfort.storage.age.hours", ago / HOUR);
		}
		return Component.translatable("cobblecomfort.storage.age.days", ago / DAY);
	}

	public static boolean stale(long seen, int staleDays) {
		return System.currentTimeMillis() - seen > staleDays * DAY;
	}
}
