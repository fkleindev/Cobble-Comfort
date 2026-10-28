package dev.cobblecomfort.features.invtools;

import dev.cobblecomfort.features.shulker.ShulkerContents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Matches items against the search query: name, optionally tooltip and shulker contents; "@mod" matches the mod id.
 * Results are cached per stack instance until the query changes.
 */
public final class ItemSearch {
	private final Map<ItemStack, Boolean> cache = new WeakHashMap<>();
	private String query = "";
	/** True while tooltips are built for searching, so features that restyle tooltip text keep vanilla's lines. */
	private static boolean searching;

	public static boolean searching() {
		return searching;
	}

	public void setQuery(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (!normalized.equals(query)) {
			query = normalized;
			cache.clear();
		}
	}

	/** Vanilla's tooltip text of a stack, without the lines features add or replace (as searches see it). */
	public static List<Component> plainTooltip(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		searching = true;
		try {
			return stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
		} finally {
			searching = false;
		}
	}

	public String query() {
		return query;
	}

	public boolean active() {
		return !query.isEmpty();
	}

	public boolean matches(ItemStack stack, InventoryToolsConfig config) {
		if (stack.isEmpty()) {
			return false;
		}
		return cache.computeIfAbsent(stack, s -> compute(s, config, true));
	}

	private boolean compute(ItemStack stack, InventoryToolsConfig config, boolean nested) {
		if (query.startsWith("@")) {
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().contains(query.substring(1));
		}
		if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
			return true;
		}
		if (config.searchTooltip && tooltipContains(stack)) {
			return true;
		}
		if (nested && config.searchShulkers) {
			ShulkerContents contents = ShulkerContents.of(stack);
			if (contents != null) {
				for (ItemStack inner : contents.slots()) {
					if (!inner.isEmpty() && compute(inner, config, false)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean tooltipContains(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return false;
		}
		for (Component line : plainTooltip(stack)) {
			if (line.getString().toLowerCase(Locale.ROOT).contains(query)) {
				return true;
			}
		}
		return false;
	}
}
