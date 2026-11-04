package dev.cobblecomfort.features.invtools;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The kind of an item, from the creative tab it is listed in; the sections of the inventory's section view. */
public enum ItemCategory {
	BLOCKS,
	FUNCTIONAL,
	TOOLS,
	COMBAT,
	FOOD,
	MATERIALS,
	OTHER,
	EMPTY;

	/** Creative tabs, most telling first: an axe listed under tools and combat counts as a tool. */
	private static final List<Map.Entry<String, ItemCategory>> TABS = List.of(
			Map.entry("tools_and_utilities", TOOLS),
			Map.entry("combat", COMBAT),
			Map.entry("food_and_drinks", FOOD),
			Map.entry("ingredients", MATERIALS),
			Map.entry("functional_blocks", FUNCTIONAL),
			Map.entry("redstone_blocks", FUNCTIONAL),
			Map.entry("building_blocks", BLOCKS),
			Map.entry("colored_blocks", BLOCKS),
			Map.entry("natural_blocks", BLOCKS));
	private static Map<Item, ItemCategory> byItem;

	public Component label() {
		return Component.translatable("cobblecomfort.invtools.section." + name().toLowerCase(Locale.ROOT));
	}

	/** A small picture for the section label when the name does not fit. */
	public ItemStack icon() {
		return new ItemStack(switch (this) {
			case BLOCKS -> Items.GRASS_BLOCK;
			case FUNCTIONAL -> Items.CRAFTING_TABLE;
			case TOOLS -> Items.IRON_PICKAXE;
			case COMBAT -> Items.IRON_SWORD;
			case FOOD -> Items.BREAD;
			case MATERIALS -> Items.IRON_INGOT;
			case OTHER -> Items.PAPER;
			case EMPTY -> Items.AIR;
		});
	}

	public static ItemCategory of(ItemStack stack) {
		if (stack.isEmpty()) {
			return EMPTY;
		}
		ItemCategory category = byItem().get(stack.getItem());
		if (category != null) {
			return category;
		}
		// Not in the vanilla tabs (e.g. modded items): guess from what the item does.
		if (stack.has(DataComponents.FOOD)) {
			return FOOD;
		}
		if (stack.has(DataComponents.TOOL)) {
			return TOOLS;
		}
		if (stack.has(DataComponents.WEAPON)) {
			return COMBAT;
		}
		return stack.getItem() instanceof BlockItem ? BLOCKS : OTHER;
	}

	/** Every item of the vanilla creative tabs with its kind, built once (the tabs are filled on demand if needed). */
	private static Map<Item, ItemCategory> byItem() {
		if (byItem != null) {
			return byItem;
		}
		Minecraft mc = Minecraft.getInstance();
		Map<Item, ItemCategory> map = new HashMap<>();
		if (mc.player != null && mc.level != null) {
			CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
			for (Map.Entry<String, ItemCategory> entry : TABS) {
				CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.withDefaultNamespace(entry.getKey()));
				if (tab == null) {
					continue;
				}
				for (ItemStack stack : tab.getDisplayItems()) {
					map.putIfAbsent(stack.getItem(), entry.getValue());
				}
			}
		}
		if (!map.isEmpty()) {
			byItem = map;
		}
		return map;
	}
}
