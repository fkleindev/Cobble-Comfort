package dev.cobblecomfort.features.map.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Which structures are shown, with an item icon and a readable name each. */
public final class StructureIcons {
	private static final Map<String, ItemStack> ICONS = new HashMap<>();

	private StructureIcons() {
	}

	/** Icon for a structure type, or null if the type is not shown (mineshafts, buried treasure, ...). */
	public static ItemStack icon(String type) {
		return ICONS.computeIfAbsent(type, t -> {
			Item item = itemFor(t);
			return item == null ? ItemStack.EMPTY : new ItemStack(item);
		});
	}

	public static boolean shown(String type) {
		return !icon(type).isEmpty();
	}

	private static Item itemFor(String type) {
		if (type.startsWith("village")) {
			return Items.BELL;
		}
		if (type.startsWith("ruined_portal")) {
			return Items.CRYING_OBSIDIAN;
		}
		if (type.startsWith("shipwreck")) {
			return Items.OAK_BOAT;
		}
		return switch (type) {
			case "pillager_outpost" -> Items.CROSSBOW;
			case "desert_pyramid" -> Items.CHISELED_SANDSTONE;
			case "jungle_pyramid" -> Items.MOSSY_COBBLESTONE;
			case "swamp_hut" -> Items.CAULDRON;
			case "igloo" -> Items.SNOW_BLOCK;
			case "monument" -> Items.PRISMARINE_SHARD;
			case "mansion" -> Items.TOTEM_OF_UNDYING;
			case "stronghold" -> Items.ENDER_EYE;
			case "ancient_city" -> Items.SCULK_SHRIEKER;
			case "trail_ruins" -> Items.BRUSH;
			case "trial_chambers" -> Items.TRIAL_KEY;
			case "fortress" -> Items.NETHER_BRICK;
			case "bastion_remnant" -> Items.GILDED_BLACKSTONE;
			case "end_city" -> Items.PURPUR_BLOCK;
			case "dungeon" -> Items.SPAWNER;
			default -> null;
		};
	}

	public static Component name(String type) {
		String key = type.startsWith("village") ? "village" : type.startsWith("ruined_portal") ? "ruined_portal"
				: type.startsWith("shipwreck") ? "shipwreck" : type;
		String translationKey = "cobblecomfort.structure." + key;
		Component translated = Component.translatable(translationKey);
		if (!translated.getString().equals(translationKey)) {
			return translated;
		}
		String pretty = key.replace('_', ' ');
		return Component.literal(pretty.substring(0, 1).toUpperCase(Locale.ROOT) + pretty.substring(1));
	}
}
