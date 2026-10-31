package dev.cobblecomfort.core.config;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.core.FeatureManager;
import dev.isxander.yacl3.api.ConfigCategory;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class CobbleConfigScreen {
	/** Sidebar icon of each feature's settings. */
	private static final Map<String, Item> ICONS = Map.ofEntries(
			Map.entry("armor_hud", Items.IRON_CHESTPLATE), Map.entry("chat", Items.WRITABLE_BOOK),
			Map.entry("crosshair", Items.ARROW), Map.entry("effect_hud", Items.POTION),
			Map.entry("enchant_desc", Items.ENCHANTED_BOOK), Map.entry("enchanting", Items.ENCHANTING_TABLE),
			Map.entry("food_hud", Items.COOKED_BEEF), Map.entry("fullbright", Items.GLOWSTONE_DUST),
			Map.entry("furnace", Items.FURNACE), Map.entry("hotbar", Items.IRON_SWORD),
			Map.entry("hud", Items.PAINTING), Map.entry("hud_anim", Items.EXPERIENCE_BOTTLE),
			Map.entry("info_hud", Items.CLOCK), Map.entry("inventory_fx", Items.BUNDLE),
			Map.entry("inventory_tools", Items.HOPPER), Map.entry("item_bars", Items.DIAMOND_PICKAXE),
			Map.entry("light_overlay", Items.TORCH), Map.entry("look_info", Items.ENDER_EYE),
			Map.entry("map", Items.FILLED_MAP), Map.entry("pets", Items.BONE),
			Map.entry("phantom", Items.PHANTOM_MEMBRANE), Map.entry("pickup_log", Items.PAPER),
			Map.entry("plush", Items.TOTEM_OF_UNDYING), Map.entry("name_charm", Items.NAME_TAG), Map.entry("pet_plush", Items.LEAD), Map.entry("belt", Items.BUNDLE), Map.entry("plant_sway", Items.SHORT_GRASS), Map.entry("soft_doors", Items.OAK_DOOR), Map.entry("hanging", Items.LANTERN), Map.entry("stuck_arrows", Items.ARROW), Map.entry("wardrobe", Items.LEATHER_CHESTPLATE),
			Map.entry("item_preview", Items.MAP),
			Map.entry("rarity", Items.AMETHYST_SHARD), Map.entry("shulker_preview", Items.SHULKER_BOX),
			Map.entry("smooth_gui", Items.FEATHER), Map.entry("storage", Items.CHEST),
			Map.entry("toasts", Items.BELL), Map.entry("tracker", Items.LODESTONE),
			Map.entry("trade", Items.EMERALD), Map.entry("transitions", Items.ENDER_PEARL),
			Map.entry("zoom", Items.SPYGLASS));

	private CobbleConfigScreen() {
	}

	public static Screen create(Screen parent) {
		GeneralConfig general = FeatureManager.general();
		List<SettingsScreen.Section> sections = new ArrayList<>();
		for (Feature<?> feature : FeatureManager.features()) {
			Item icon = ICONS.getOrDefault(feature.id(), Items.BOOK);
			sections.add(new SettingsScreen.Section(feature.name(), icon, feature.buildCategory(),
					() -> feature.config().enabled));
		}
		sections.sort(Comparator.comparing(section -> section.name().getString()));
		sections.addFirst(new SettingsScreen.Section(Component.translatable("cobblecomfort.category.general"),
				Items.COMPARATOR,
				ConfigCategory.createBuilder()
						.name(Component.translatable("cobblecomfort.category.general"))
						.option(Opts.bool("general.enabled", true, () -> general.enabled, v -> general.enabled = v))
						.build(),
				() -> general.enabled));
		return new SettingsScreen(parent, Component.translatable("cobblecomfort.config.title"), sections, FeatureManager::saveAll);
	}
}
