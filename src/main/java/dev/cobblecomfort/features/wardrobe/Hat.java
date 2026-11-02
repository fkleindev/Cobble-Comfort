package dev.cobblecomfort.features.wardrobe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A head covering in one of its colours. Worn instead of the helmet: its band or ring shows which helmet is under it.
 * The autumn and Halloween ones can only be earned in October and November, the Christmas and New Year ones in December
 * and January; once earned they can be worn all year. All colours of a kind are earned together,
 * except for crowns and wreaths. The order is the hat's id when shared with other players: only ever append.
 */
public enum Hat {
	CROWN_PAPER(Style.CROWN, Unlock.free(), "paper",
			new int[]{0xFFF4D35E, 0xFFE0B83C, 0xFFB08A28}, 0xFFE8574A, 0xFF4A90D9, 0xFFFFFFFF, false),
	CROWN_GOLD(Style.CROWN, Unlock.advancement("adventure/hero_of_the_village"), "gold_ingot",
			new int[]{0xFFFFE27A, 0xFFF2B92E, 0xFFB07A14}, 0xFFD81E3A, 0xFF9E1B2C, 0xFFF4F0E6, true),
	CROWN_SILVER(Style.CROWN, Unlock.item("heart_of_the_sea"), "heart_of_the_sea",
			new int[]{0xFFF2F4F8, 0xFFC4CAD4, 0xFF828A98}, 0xFF2F6FE0, 0xFF1E3A78, 0xFFE8F4FF, true),
	CROWN_COPPER(Style.CROWN, Unlock.item("emerald"), "emerald",
			new int[]{0xFFF2A07A, 0xFFD9774C, 0xFF9A4E2C}, 0xFF2CC85A, 0xFF1E5A34, 0xFFE8FFF0, true),
	CROWN_AMETHYST(Style.CROWN, Unlock.item("amethyst_shard"), "amethyst_shard",
			new int[]{0xFFF6D8C8, 0xFFDDB09C, 0xFFA87A6A}, 0xFFA66CE8, 0xFF5A2E8C, 0xFFF4E8FF, true),
	WREATH_MEADOW(Style.WREATH, Unlock.free(), "poppy",
			new int[]{0xFFE0302C, 0xFFF4F4F0}, 0xFFFFD23A, 0xFF5C9E3A, 0xFF3E7A2A, true),
	WREATH_CORNFLOWER(Style.WREATH, Unlock.biome("flower_forest"), "cornflower",
			new int[]{0xFF4A6CE8, 0xFFF8F8FF}, 0xFF2A3A8C, 0xFF5C9E3A, 0xFF3E7A2A, true),
	WREATH_CHERRY(Style.WREATH, Unlock.biome("cherry_grove"), "pink_petals",
			new int[]{0xFFF7B8D2, 0xFFFFDCEA}, 0xFFE5739F, 0xFF7A5A48, 0xFF5A3E32, true),
	WREATH_SUNFLOWER(Style.WREATH, Unlock.biome("sunflower_plains"), "sunflower",
			new int[]{0xFFFFD02A, 0xFFF7952A}, 0xFF6A4220, 0xFF6AA83E, 0xFF487A2A, true),
	WREATH_WITHER(Style.WREATH, Unlock.item("wither_rose"), "wither_rose",
			new int[]{0xFF2A2024, 0xFF5A1E22}, 0xFF0E0A0C, 0xFF3E4A2C, 0xFF262E1C, true),
	AUTUMN_LEAVES(Style.LEAVES, Unlock.free(), "orange_dye",
			new int[]{0xFFC8341E, 0xFFE8761E, 0xFFE8B21E}, 0xFF6A4424, 0xFFD8902A, 0xFF8A3418, true),
	AUTUMN_ACORN(Style.ACORN, Unlock.biome("dark_forest"), "dark_oak_sapling",
			new int[]{0xFF8A5E34, 0xFF6A4424, 0xFF7A522C}, 0xFF4A3018, 0xFFB98448, 0, true),
	AUTUMN_MUSHROOM(Style.MUSHROOM, Unlock.item("red_mushroom"), "red_mushroom",
			new int[]{0xFFD8282A, 0xFFA81C20}, 0xFFF4F0E8, 0xFFE8DCC4, 0xFFCDBBA0, true),
	HALLOWEEN_WITCH(Style.WITCH, Unlock.free(), "cauldron",
			new int[]{0xFF2E2438, 0xFF1E1826, 0xFF40344E}, 0xFF7A3EA8, 0xFFE8C040, 0xFF141014, true),
	HALLOWEEN_PUMPKIN(Style.PUMPKIN, Unlock.item("jack_o_lantern"), "jack_o_lantern",
			new int[]{0xFFE8861E, 0xFFC0661A, 0xFFF4A23A}, 0xFFFFE070, 0xFF5A6A2A, 0xFF6AA03A, true),
	HALLOWEEN_HORNS(Style.HORNS, Unlock.advancement("story/enter_the_nether"), "nether_wart",
			new int[]{0xFFD02424, 0xFFA01818, 0xFF5A0C0C}, 0xFF1E1A1E, 0, 0, true),
	HALLOWEEN_BAT(Style.BAT, Unlock.biome("dripstone_caves"), "bat_spawn_egg",
			new int[]{0xFF3A2E2A, 0xFF2A201E}, 0xFFF0D040, 0xFFA0A6AE, 0xFF2A2A30, true),
	AUTUMN_LEAVES_GOLD(Style.LEAVES, Unlock.free(), "yellow_dye",
			new int[]{0xFFF2C230, 0xFFE89A1E, 0xFFF6DA6A}, 0xFF6A4424, 0xFFE8B84A, 0xFF9A6A1C, true),
	AUTUMN_LEAVES_RUST(Style.LEAVES, Unlock.free(), "red_dye",
			new int[]{0xFF8E2A1A, 0xFFB0481E, 0xFF6E3A1E}, 0xFF4A3018, 0xFFA0522A, 0xFF5A2414, true),
	AUTUMN_ACORN_LIGHT(Style.ACORN, Unlock.biome("dark_forest"), "oak_sapling",
			new int[]{0xFFB8925E, 0xFF96703E, 0xFFA8804C}, 0xFF6A4A28, 0xFFD8B078, 0, true),
	AUTUMN_ACORN_DARK(Style.ACORN, Unlock.biome("dark_forest"), "dark_oak_sapling",
			new int[]{0xFF5A3C22, 0xFF3E2814, 0xFF4C321C}, 0xFF2E1E10, 0xFF8E6234, 0, true),
	AUTUMN_MUSHROOM_BROWN(Style.MUSHROOM, Unlock.item("red_mushroom"), "brown_mushroom",
			new int[]{0xFF8A5A30, 0xFF6A4220}, 0xFFE8D8B8, 0xFFE8DCC4, 0xFFD8C8A8, true),
	AUTUMN_MUSHROOM_PURPLE(Style.MUSHROOM, Unlock.item("red_mushroom"), "purple_dye",
			new int[]{0xFF7A3AB8, 0xFF5A2A8A}, 0xFFF0E0FF, 0xFFE0D8F0, 0xFFC8B8E0, true),
	HALLOWEEN_WITCH_GREEN(Style.WITCH, Unlock.free(), "cauldron",
			new int[]{0xFF1E3A24, 0xFF14281A, 0xFF2C4C32}, 0xFF7AC84A, 0xFFC8C8D0, 0xFF141014, true),
	HALLOWEEN_WITCH_NIGHT(Style.WITCH, Unlock.free(), "cauldron",
			new int[]{0xFF1C2448, 0xFF141A34, 0xFF2A3460}, 0xFFE8C040, 0xFFE8E8F0, 0xFF141014, true),
	HALLOWEEN_PUMPKIN_SOUL(Style.PUMPKIN, Unlock.item("jack_o_lantern"), "soul_lantern",
			new int[]{0xFFE8E4D8, 0xFFC8C2B0, 0xFFF6F2E8}, 0xFF6AE8F0, 0xFF5A6A2A, 0xFF6AA03A, true),
	HALLOWEEN_PUMPKIN_GREEN(Style.PUMPKIN, Unlock.item("jack_o_lantern"), "jack_o_lantern",
			new int[]{0xFF4E7A2E, 0xFF3A5E20, 0xFF6A9440}, 0xFFFFC040, 0xFF5A4A2A, 0xFF8AB04A, true),
	HALLOWEEN_HORNS_BLACK(Style.HORNS, Unlock.advancement("story/enter_the_nether"), "nether_wart",
			new int[]{0xFF2A2428, 0xFF1A1618, 0xFF5A5058}, 0xFF8A1414, 0, 0, true),
	HALLOWEEN_HORNS_PURPLE(Style.HORNS, Unlock.advancement("story/enter_the_nether"), "nether_wart",
			new int[]{0xFF8A3AC8, 0xFF6A2A9A, 0xFF3A1458}, 0xFF1E1A1E, 0, 0, true),
	HALLOWEEN_BAT_BLACK(Style.BAT, Unlock.biome("dripstone_caves"), "bat_spawn_egg",
			new int[]{0xFF1E1A1C, 0xFF141012}, 0xFFE02828, 0xFFA0A6AE, 0xFF2A2A30, true),
	HALLOWEEN_BAT_WHITE(Style.BAT, Unlock.biome("dripstone_caves"), "bat_spawn_egg",
			new int[]{0xFFE8E8EC, 0xFFC8C8D0}, 0xFF4AA8E8, 0xFFA0A6AE, 0xFF2A2A30, true),
	AVIATOR_BROWN(Style.AVIATOR, Unlock.item("elytra"), "elytra",
			new int[]{0xFF7A4A2A, 0xFF5A3418, 0xFFE8DCC0}, 0xFF8AC8E8, 0xFFB08A3A, 0, true),
	AVIATOR_BLACK(Style.AVIATOR, Unlock.item("elytra"), "elytra",
			new int[]{0xFF2E2A2C, 0xFF1C1A1C, 0xFFD8D0C8}, 0xFFE8A040, 0xFF9AA0A8, 0, true),
	AVIATOR_OLIVE(Style.AVIATOR, Unlock.item("elytra"), "elytra",
			new int[]{0xFF5A6A3A, 0xFF404C28, 0xFFE8DCC0}, 0xFF7AE0A0, 0xFF6A5A3A, 0, true),
	MINER_YELLOW(Style.MINER, Unlock.advancement("story/mine_diamond"), "diamond_pickaxe",
			new int[]{0xFFF2C21E, 0xFFC89A12}, 0xFFFFF4C0, 0xFF4A4A4E, 0, true),
	MINER_WHITE(Style.MINER, Unlock.advancement("story/mine_diamond"), "diamond_pickaxe",
			new int[]{0xFFEDEDED, 0xFFC0C0C4}, 0xFFFFF4C0, 0xFF4A4A4E, 0, true),
	MINER_ORANGE(Style.MINER, Unlock.advancement("story/mine_diamond"), "diamond_pickaxe",
			new int[]{0xFFE8701E, 0xFFB85212}, 0xFFC8F0FF, 0xFF3A3A3E, 0, true),
	PIRATE_BLACK(Style.PIRATE, Unlock.biome("deep_ocean"), "nautilus_shell",
			new int[]{0xFF22201E, 0xFF161412}, 0xFFF0F0EC, 0xFFD8B040, 0, true),
	PIRATE_RED(Style.PIRATE, Unlock.biome("deep_ocean"), "nautilus_shell",
			new int[]{0xFF8A1E1E, 0xFF5E1212}, 0xFF242424, 0xFFD8B040, 0, true),
	PIRATE_NAVY(Style.PIRATE, Unlock.biome("deep_ocean"), "nautilus_shell",
			new int[]{0xFF1E2A4A, 0xFF121A30}, 0xFFE8C040, 0xFFE8E8E8, 0, true),
	HALO_GOLD(Style.HALO, Unlock.advancement("adventure/totem_of_undying"), "totem_of_undying",
			new int[]{0xFFFFE070, 0xFFE8B830}, 0, 0, 0, true),
	HALO_WHITE(Style.HALO, Unlock.advancement("adventure/totem_of_undying"), "totem_of_undying",
			new int[]{0xFFFFFFFF, 0xFFD0E0F8}, 0, 0, 0, true),
	HALO_RAINBOW(Style.HALO, Unlock.advancement("adventure/totem_of_undying"), "totem_of_undying",
			new int[]{0xFFFF7AC8, 0xFF7AC8FF}, 0, 0, 0, true),
	STRAW_NATURAL(Style.STRAW, Unlock.advancement("husbandry/plant_seed"), "wheat",
			new int[]{0xFFE8C878, 0xFFC8A458}, 0xFFC8302A, 0, 0, true),
	STRAW_PALE(Style.STRAW, Unlock.advancement("husbandry/plant_seed"), "wheat",
			new int[]{0xFFF2E2B0, 0xFFD8C690}, 0xFF3A6AC8, 0, 0, true),
	STRAW_DARK(Style.STRAW, Unlock.advancement("husbandry/plant_seed"), "wheat",
			new int[]{0xFFB89048, 0xFF967032}, 0xFF2A2A2A, 0, 0, true),
	FISHER_KHAKI(Style.FISHER, Unlock.advancement("husbandry/fishy_business"), "fishing_rod",
			new int[]{0xFFB8A878, 0xFF968858}, 0xFF5A4A2A, 0, 0, true),
	FISHER_GREEN(Style.FISHER, Unlock.advancement("husbandry/fishy_business"), "fishing_rod",
			new int[]{0xFF5A7A4A, 0xFF46603A}, 0xFF2E2E22, 0, 0, true),
	FISHER_BLUE(Style.FISHER, Unlock.advancement("husbandry/fishy_business"), "fishing_rod",
			new int[]{0xFF4A6A9A, 0xFF3A5478}, 0xFFE8E0C8, 0, 0, true),
	CHEF_WHITE(Style.CHEF, Unlock.item("cake"), "cake",
			new int[]{0xFFF6F6F2, 0xFFDCDCD6}, 0xFFC8302A, 0, 0, true),
	CHEF_CREAM(Style.CHEF, Unlock.item("cake"), "cake",
			new int[]{0xFFF2E6C8, 0xFFD8CAA8}, 0xFF3A6AC8, 0, 0, true),
	CHEF_BLACK(Style.CHEF, Unlock.item("cake"), "cake",
			new int[]{0xFF2E2E32, 0xFF1E1E22}, 0xFFE8C040, 0, 0, true),
	BEANIE_BEE(Style.BEANIE, Unlock.advancement("husbandry/safely_harvest_honey"), "honeycomb",
			new int[]{0xFF5A3420, 0xFF4A2A18, 0xFF64402A}, 0xFFF0A020, 0xFFF0EEE6, 0xFFF2C81E, true),
	BEANIE_RED(Style.BEANIE, Unlock.advancement("husbandry/safely_harvest_honey"), "red_wool",
			new int[]{0xFFB82A2A, 0xFF962020, 0xFFA82626}, 0xFFF4F4F0, 0xFFF4F4F0, 0, true),
	BEANIE_GREY(Style.BEANIE, Unlock.advancement("husbandry/safely_harvest_honey"), "gray_wool",
			new int[]{0xFF8A8C90, 0xFF707276, 0xFF7C7E82}, 0xFF4A6AA8, 0xFF4A6AA8, 0, true),
	CAP_BLUE(Style.CAP, Unlock.advancement("adventure/shoot_arrow"), "blue_wool",
			new int[]{0xFF2A4A8A, 0xFF1E3668}, 0xFFF0F0EC, 0, 0, true),
	CAP_RED(Style.CAP, Unlock.advancement("adventure/shoot_arrow"), "red_wool",
			new int[]{0xFFB8262A, 0xFF8A1A1E}, 0xFFF0F0EC, 0, 0, true),
	CAP_CREEPER(Style.CAP, Unlock.advancement("adventure/shoot_arrow"), "creeper_head",
			new int[]{0xFF4E9A3A, 0xFF3A7A2A}, 0xFF7ACC5A, 0xFF1A2A18, 0, true),
	NIGHTCAP_BLUE(Style.NIGHTCAP, Unlock.advancement("adventure/sleep_in_bed"), "white_bed",
			new int[]{0xFF3A5AA8, 0xFFE8ECF4, 0xFFE8ECF4}, 0xFFF4F4F0, 0, 0, true),
	NIGHTCAP_RED(Style.NIGHTCAP, Unlock.advancement("adventure/sleep_in_bed"), "red_bed",
			new int[]{0xFFB8302C, 0xFFF4EEE6, 0xFFF4EEE6}, 0xFFF4F4F0, 0, 0, true),
	NIGHTCAP_GREEN(Style.NIGHTCAP, Unlock.advancement("adventure/sleep_in_bed"), "green_bed",
			new int[]{0xFF4A7A3A, 0xFFF2E8CC, 0xFFF2E8CC}, 0xFFF2C81E, 0, 0, true),
	PROPELLER_CLASSIC(Style.PROPELLER, Unlock.advancement("adventure/fall_from_world_height"), "feather",
			new int[]{0xFFD83A30, 0xFFF2C21E, 0xFF3A6AC8}, 0xFF4AA83A, 0, 0, true),
	PROPELLER_PASTEL(Style.PROPELLER, Unlock.advancement("adventure/fall_from_world_height"), "feather",
			new int[]{0xFFF4A8C8, 0xFFA8D8F4, 0xFFF4E8A0}, 0xFFB8E8B0, 0, 0, true),
	PROPELLER_DARK(Style.PROPELLER, Unlock.advancement("adventure/fall_from_world_height"), "feather",
			new int[]{0xFF2E2E34, 0xFF6A2A9A, 0xFF1E5A6A}, 0xFFE8C040, 0, 0, true),
	BUNNY_WHITE(Style.BUNNY, Unlock.advancement("husbandry/breed_an_animal"), "rabbit_foot",
			new int[]{0xFFF4F4F0, 0xFFD8D8D4, 0xFFE8E8E4}, 0xFFF4A8C0, 0, 0, true),
	BUNNY_BROWN(Style.BUNNY, Unlock.advancement("husbandry/breed_an_animal"), "rabbit_foot",
			new int[]{0xFF9A6A42, 0xFF7A5232, 0xFF5A3C24}, 0xFFE8C8A8, 0, 0, true),
	BUNNY_BLACK(Style.BUNNY, Unlock.advancement("husbandry/breed_an_animal"), "rabbit_foot",
			new int[]{0xFF2A2A2E, 0xFF1C1C20, 0xFF3A3A40}, 0xFF8A8A92, 0, 0, true),
	MORTARBOARD_BLACK(Style.MORTARBOARD, Unlock.advancement("story/enchant_item"), "enchanted_book",
			new int[]{0xFF22222A, 0xFF16161C}, 0xFFE8C040, 0xFFE8C040, 0, true),
	MORTARBOARD_NAVY(Style.MORTARBOARD, Unlock.advancement("story/enchant_item"), "enchanted_book",
			new int[]{0xFF1E2A4A, 0xFF141E36}, 0xFFF0F0EC, 0xFFC0C0C8, 0, true),
	MORTARBOARD_RED(Style.MORTARBOARD, Unlock.advancement("story/enchant_item"), "enchanted_book",
			new int[]{0xFF8A1E24, 0xFF6A161A}, 0xFFE8C040, 0xFFE8C040, 0, true),
	TOPHAT_BLACK(Style.TOPHAT, Unlock.advancement("adventure/trade"), "emerald",
			new int[]{0xFF1E1E22, 0xFF141418}, 0xFF8A1E24, 0xFFF4F4F0, 0, true),
	TOPHAT_GREY(Style.TOPHAT, Unlock.advancement("adventure/trade"), "emerald",
			new int[]{0xFF7A7C82, 0xFF5E6066}, 0xFF1E1E22, 0xFF4A7AC8, 0, true),
	TOPHAT_PURPLE(Style.TOPHAT, Unlock.advancement("adventure/trade"), "emerald",
			new int[]{0xFF4A2A6A, 0xFF361E4E}, 0xFF1E1E22, 0xFFE8C040, 0, true),
	COWBOY_BROWN(Style.COWBOY, Unlock.advancement("husbandry/tame_an_animal"), "saddle",
			new int[]{0xFF8A5A32, 0xFF74482A}, 0xFF3A2414, 0xFFC0C0C8, 0, true),
	COWBOY_BLACK(Style.COWBOY, Unlock.advancement("husbandry/tame_an_animal"), "saddle",
			new int[]{0xFF26221E, 0xFF1C1814}, 0xFFC0C0C8, 0xFFC0C0C8, 0, true),
	COWBOY_TAN(Style.COWBOY, Unlock.advancement("husbandry/tame_an_animal"), "saddle",
			new int[]{0xFFD8BC8A, 0xFFC4A672}, 0xFF6A4424, 0xFFE8C040, 0, true),
	HEADPHONES_BLACK(Style.HEADPHONES, Unlock.musicDisc(), "music_disc_cat",
			new int[]{0xFF26262A, 0xFF4A4A50}, 0xFFD83A30, 0, 0, true),
	HEADPHONES_WHITE(Style.HEADPHONES, Unlock.musicDisc(), "music_disc_cat",
			new int[]{0xFFF0F0EC, 0xFFB8B8BC}, 0xFF3A6AC8, 0, 0, true),
	HEADPHONES_PINK(Style.HEADPHONES, Unlock.musicDisc(), "music_disc_cat",
			new int[]{0xFFF4A8C8, 0xFFE8E0E4}, 0xFFF4F4F0, 0, 0, true),
	PARTY_PINK(Style.PARTY, Unlock.item("firework_rocket"), "firework_rocket",
			new int[]{0xFFF47AB8, 0xFFF2D23A}, 0, 0xFF4AA8E8, 0, true),
	PARTY_BLUE(Style.PARTY, Unlock.item("firework_rocket"), "firework_rocket",
			new int[]{0xFF3A6AD8, 0xFFF4F4F0}, 0, 0xFFE83A3A, 0, true),
	PARTY_GREEN(Style.PARTY, Unlock.item("firework_rocket"), "firework_rocket",
			new int[]{0xFF4AB84A, 0xFFF2902A}, 0, 0xFFF2D23A, 0, true),
	SOMBRERO_STRAW(Style.SOMBRERO, Unlock.biome("desert"), "dead_bush",
			new int[]{0xFFE0C080, 0xFFD0AE6C}, 0xFFC8302A, 0xFF3A8A3A, 0, true),
	SOMBRERO_BLACK(Style.SOMBRERO, Unlock.biome("desert"), "dead_bush",
			new int[]{0xFF242024, 0xFF1A161A}, 0xFFE8C040, 0xFFE8E4DC, 0, true),
	SOMBRERO_RED(Style.SOMBRERO, Unlock.biome("desert"), "dead_bush",
			new int[]{0xFFA8262A, 0xFF8A1C20}, 0xFFF4F0E6, 0xFFE8C040, 0, true),
	BERET_BLACK(Style.BERET, Unlock.item("painting"), "painting",
			new int[]{0xFF222226, 0xFF18181C}, 0xFFD83A30, 0, 0, true),
	BERET_RED(Style.BERET, Unlock.item("painting"), "painting",
			new int[]{0xFFB02A2E, 0xFF8A1E22}, 0xFF3A6AD8, 0, 0, true),
	BERET_NAVY(Style.BERET, Unlock.item("painting"), "painting",
			new int[]{0xFF223260, 0xFF18244A}, 0xFFF2D23A, 0, 0, true),
	TYROLEAN_GREEN(Style.TYROLEAN, Unlock.biome("meadow"), "fern",
			new int[]{0xFF3E5A34, 0xFF324A2A, 0xFFC8302A}, 0xFF3A3A40, 0xFFE8E8EC, 0, true),
	TYROLEAN_GREY(Style.TYROLEAN, Unlock.biome("meadow"), "fern",
			new int[]{0xFF6E7076, 0xFF5A5C62, 0xFF3A6A3A}, 0xFFE8E8EC, 0xFFE8C040, 0, true),
	TYROLEAN_BROWN(Style.TYROLEAN, Unlock.biome("meadow"), "fern",
			new int[]{0xFF6A4A2E, 0xFF563C24, 0xFF2E5A2E}, 0xFF8A2A2A, 0xFFE8E8EC, 0, true),
	CAPTAIN_WHITE(Style.CAPTAIN, Unlock.item("spyglass"), "spyglass",
			new int[]{0xFFF2F2EE, 0xFF1E2A4A, 0xFF141418}, 0xFFE8C040, 0xFFE8C040, 0, true),
	CAPTAIN_NAVY(Style.CAPTAIN, Unlock.item("spyglass"), "spyglass",
			new int[]{0xFF1E2A4A, 0xFF141E36, 0xFF101014}, 0xFFE8C040, 0xFFF0F0EC, 0, true),
	CAPTAIN_BLACK(Style.CAPTAIN, Unlock.item("spyglass"), "spyglass",
			new int[]{0xFF222226, 0xFF18181C, 0xFF0E0E10}, 0xFFE8C040, 0xFFD8D8DC, 0, true),
	USHANKA_BROWN(Style.USHANKA, Unlock.biome("snowy_taiga"), "rabbit_hide",
			new int[]{0xFF5A3E28, 0xFF7A5A3E, 0xFF9A7A5A}, 0xFF3A2A1E, 0xFFD83A30, 0, true),
	USHANKA_GREY(Style.USHANKA, Unlock.biome("snowy_taiga"), "rabbit_hide",
			new int[]{0xFF2A3450, 0xFF8A8A8E, 0xFFB0B0B4}, 0xFF2A2A2E, 0xFFE8C040, 0, true),
	USHANKA_WHITE(Style.USHANKA, Unlock.biome("snowy_taiga"), "rabbit_hide",
			new int[]{0xFFA8262A, 0xFFE8E8E4, 0xFFFFFFFF}, 0xFFC8C8C4, 0xFFE8C040, 0, true),
	TIARA_SILVER(Style.TIARA, Unlock.item("diamond"), "diamond",
			new int[]{0xFFE4E8EE, 0xFFB0B6C0}, 0xFF3A6AE8, 0xFF9AD0F8, 0, true),
	TIARA_GOLD(Style.TIARA, Unlock.item("diamond"), "diamond",
			new int[]{0xFFF2D25A, 0xFFC8A030}, 0xFFD82A3A, 0xFF3AC86A, 0, true),
	TIARA_ROSE(Style.TIARA, Unlock.item("diamond"), "diamond",
			new int[]{0xFFF0C0B0, 0xFFD09A88}, 0xFFE87AB8, 0xFFF4E8FF, 0, true),
	NINJA_NAVY(Style.NINJA, Unlock.biome("bamboo_jungle"), "bamboo",
			new int[]{0xFF223260, 0xFF18244A}, 0, 0, 0, true),
	NINJA_RED(Style.NINJA, Unlock.biome("bamboo_jungle"), "bamboo",
			new int[]{0xFFB02A2E, 0xFF8A1E22}, 0, 0, 0, true),
	NINJA_BLACK(Style.NINJA, Unlock.biome("bamboo_jungle"), "bamboo",
			new int[]{0xFF26262A, 0xFF1A1A1E}, 0, 0, 0, true),
	WINTER_SANTA_RED(Style.SANTA, Unlock.free(), "red_wool",
			new int[]{0xFFC8282A, 0xFFA01E22, 0xFFF4F4F0}, 0xFFF4F4F0, 0, 0, true),
	WINTER_SANTA_GREEN(Style.SANTA, Unlock.free(), "green_wool",
			new int[]{0xFF2E7A3A, 0xFF22602C, 0xFFF4F4F0}, 0xFFF4F4F0, 0, 0, true),
	WINTER_SANTA_FROST(Style.SANTA, Unlock.free(), "light_blue_wool",
			new int[]{0xFF3A6AB8, 0xFF2A5090, 0xFFF4F4F0}, 0xFFE8F4FF, 0, 0, true),
	WINTER_REINDEER_BROWN(Style.REINDEER, Unlock.biome("snowy_taiga"), "spruce_sapling",
			new int[]{0xFF8A6A42, 0xFFB08A5A, 0xFF5A3C24}, 0xFF9A7A52, 0xFFC8282A, 0xFFE8C040, true),
	WINTER_REINDEER_SNOW(Style.REINDEER, Unlock.biome("snowy_taiga"), "snowball",
			new int[]{0xFFE8E8EC, 0xFFFFFFFF, 0xFF3A6AB8}, 0xFFD8D8DC, 0xFF3A6AB8, 0xFFC8CCD4, true),
	WINTER_REINDEER_GOLD(Style.REINDEER, Unlock.biome("snowy_taiga"), "gold_nugget",
			new int[]{0xFFE8C040, 0xFFFFE27A, 0xFF2E7A3A}, 0xFF9A7A52, 0xFF2E7A3A, 0xFFE8C040, true),
	WINTER_ELF_GREEN(Style.ELF, Unlock.item("cake"), "cake",
			new int[]{0xFF3A8A3A, 0xFF2E7030, 0xFFC8282A}, 0xFFE8C040, 0, 0, true),
	WINTER_ELF_RED(Style.ELF, Unlock.item("cake"), "cake",
			new int[]{0xFFC8282A, 0xFFA01E22, 0xFF3A8A3A}, 0xFFE8C040, 0, 0, true),
	WINTER_ELF_STRIPED(Style.ELF, Unlock.item("cake"), "cake",
			new int[]{0xFFC8282A, 0xFFF4F4F0, 0xFF3A8A3A}, 0xFFC8CCD4, 0, 0, true),
	WINTER_SPARKLE_GOLD(Style.SPARKLE, Unlock.item("firework_star"), "firework_star",
			new int[]{0xFFE8C040, 0xFFC09A28}, 0xFF1E1E22, 0xFFFFF4B0, 0, false),
	WINTER_SPARKLE_SILVER(Style.SPARKLE, Unlock.item("firework_star"), "firework_star",
			new int[]{0xFFC8CCD4, 0xFF9AA0AA}, 0xFF3A6AB8, 0xFFFFFFFF, 0, false),
	WINTER_SPARKLE_BLACK(Style.SPARKLE, Unlock.item("firework_star"), "firework_star",
			new int[]{0xFF1E1E22, 0xFF141418}, 0xFFE8C040, 0xFFFFF4B0, 0, false),
	AXOLOTL_LUCY(Style.AXOLOTL, Unlock.item("axolotl_bucket"), "axolotl_bucket",
			new int[]{0xFFF6B2CE, 0xFFE494B6, 0xFFFDDCE8}, 0xFFD8467A, 0xFF2A1A22, 0xFFF27AA4, true),
	AXOLOTL_WILD(Style.AXOLOTL, Unlock.item("axolotl_bucket"), "axolotl_bucket",
			new int[]{0xFF8E6E4E, 0xFF72563C, 0xFFB89870}, 0xFF6A3A4E, 0xFF1E140E, 0xFFC08A7A, true),
	AXOLOTL_GOLD(Style.AXOLOTL, Unlock.item("axolotl_bucket"), "axolotl_bucket",
			new int[]{0xFFF4C840, 0xFFDCA42C, 0xFFFCE48E}, 0xFFE8802A, 0xFF2A1E10, 0xFFF4A05A, true),
	AXOLOTL_CYAN(Style.AXOLOTL, Unlock.item("axolotl_bucket"), "axolotl_bucket",
			new int[]{0xFFDCF2F6, 0xFFB4D8E2, 0xFFF6FDFF}, 0xFFE87AAA, 0xFF1E2A30, 0xFFF4A8C8, true),
	AXOLOTL_BLUE(Style.AXOLOTL, Unlock.item("axolotl_bucket"), "axolotl_bucket",
			new int[]{0xFF5E6EE8, 0xFF4652C4, 0xFF8E9CF6}, 0xFFF2D04A, 0xFF141838, 0xFF9AA8FF, true),
	FOX_RED(Style.FOX, Unlock.item("sweet_berries"), "sweet_berries",
			new int[]{0xFFE27A2C, 0xFFBA5A1C, 0xFFF6F0E6}, 0xFF2A1E1A, 0xFF1E140E, 0xFFF2C8B4, true),
	FOX_SNOW(Style.FOX, Unlock.item("sweet_berries"), "snowball",
			new int[]{0xFFF4F4F2, 0xFFD4D8E0, 0xFFFFFFFF}, 0xFF3A3A42, 0xFF1E2A30, 0xFFE8D4CC, true);

	/** The kind of hat: the shape it is made in, shown in the wardrobe as one tile that opens its colours. */
	public enum Style {
		CROWN(Group.ALL_YEAR, false), WREATH(Group.ALL_YEAR, false), AVIATOR(Group.ALL_YEAR, true),
		MINER(Group.ALL_YEAR, true), PIRATE(Group.ALL_YEAR, true), HALO(Group.ALL_YEAR, true), STRAW(Group.ALL_YEAR, true),
		FISHER(Group.ALL_YEAR, true), CHEF(Group.ALL_YEAR, true), BEANIE(Group.ALL_YEAR, true), CAP(Group.ALL_YEAR, true),
		NIGHTCAP(Group.ALL_YEAR, true), PROPELLER(Group.ALL_YEAR, true), BUNNY(Group.ALL_YEAR, true),
		MORTARBOARD(Group.ALL_YEAR, true), TOPHAT(Group.ALL_YEAR, true), COWBOY(Group.ALL_YEAR, true),
		HEADPHONES(Group.ALL_YEAR, true), PARTY(Group.ALL_YEAR, true), SOMBRERO(Group.ALL_YEAR, true),
		BERET(Group.ALL_YEAR, true), TYROLEAN(Group.ALL_YEAR, true), CAPTAIN(Group.ALL_YEAR, true),
		USHANKA(Group.ALL_YEAR, true), TIARA(Group.ALL_YEAR, true), NINJA(Group.ALL_YEAR, true),
		AXOLOTL(Group.ALL_YEAR, true), FOX(Group.ALL_YEAR, true),
		LEAVES(Group.SEASON, true), ACORN(Group.SEASON, true), MUSHROOM(Group.SEASON, true), WITCH(Group.SEASON, true),
		PUMPKIN(Group.SEASON, true), HORNS(Group.SEASON, true), BAT(Group.SEASON, true),
		SANTA(Group.WINTER, true), REINDEER(Group.WINTER, true), ELF(Group.WINTER, true), SPARKLE(Group.WINTER, true);

		public final Group group;
		/** Whether all its colours are earned together (crowns and wreaths each have their own way). */
		public final boolean together;

		Style(Group group, boolean together) {
			this.group = group;
			this.together = together;
		}

		public Component title() {
			return Component.translatable("cobblecomfort.hat.style." + name().toLowerCase(Locale.ROOT));
		}
	}

	/** The wardrobe's groups of hats. Seasonal ones can only be earned in their months, then worn all year. */
	public enum Group {
		ALL_YEAR(0, 0), SEASON(10, 11), WINTER(12, 1);

		private final int firstMonth;
		private final int lastMonth;

		Group(int firstMonth, int lastMonth) {
			this.firstMonth = firstMonth;
			this.lastMonth = lastMonth;
		}

		public boolean seasonal() {
			return firstMonth != 0;
		}

		public boolean inSeason() {
			int month = LocalDate.now().getMonthValue();
			if (!seasonal()) {
				return true;
			}
			// A season may run over the turn of the year (December to January).
			return firstMonth <= lastMonth ? month >= firstMonth && month <= lastMonth : month >= firstMonth || month <= lastMonth;
		}

		public Component title() {
			return Component.translatable("cobblecomfort.hat.group." + name().toLowerCase(Locale.ROOT));
		}

		/** When it can be worn, e.g. "October and November". */
		public Component season() {
			return Component.translatable("cobblecomfort.hat.season", Component.translatable("cobblecomfort.month." + firstMonth),
					Component.translatable("cobblecomfort.month." + lastMonth));
		}
	}

	/**
	 * How a hat is earned: always worn, an advancement, an item (or any music disc) once in the inventory, a biome
	 * once entered, or an animal tamed.
	 */
	public record Unlock(Kind kind, Identifier id) {
		public enum Kind {
			FREE, ADVANCEMENT, ITEM, BIOME, MUSIC_DISC, TAMED
		}

		static Unlock free() {
			return new Unlock(Kind.FREE, null);
		}

		static Unlock advancement(String path) {
			return new Unlock(Kind.ADVANCEMENT, Identifier.withDefaultNamespace(path));
		}

		static Unlock item(String path) {
			return new Unlock(Kind.ITEM, Identifier.withDefaultNamespace(path));
		}

		/** Earned by taming an animal of this type (a wolf, a cat). */
		static Unlock tamed(String type) {
			return new Unlock(Kind.TAMED, Identifier.withDefaultNamespace(type));
		}

		static Unlock musicDisc() {
			return new Unlock(Kind.MUSIC_DISC, null);
		}

		static Unlock biome(String path) {
			return new Unlock(Kind.BIOME, Identifier.withDefaultNamespace(path));
		}
	}

	public final Style style;
	public final Unlock unlock;
	private final Identifier icon;
	/**
	 * Its colours, by style. Crown: metal light, middle, dark; gem, (unused), pearl. Wreath: the two petal colours; flower
	 * centre, leaf light and dark (autumn: three leaf colours; acorn cap, leaf light and dark). Acorn: scales light and
	 * dark, top; stalk, rim. Fly agaric: cap, brim; dots, stem, gills. Witch: cone, brim, tip; band, buckle, spider.
	 * Pumpkin: orange, ribs, light; glow, stem, leaf. Horns: red, dark, tips; band. Bat: body, wings; eyes, spring, band.
	 * Aviator: leather, dark, lining; lenses, frame. Miner: shell, ridge; lamp, housing. Pirate: felt, brim; feather, trim.
	 * Halo: light, shade. Straw: straw, dark; ribbon. Fisher: cloth, brim; band. Chef: cloth, folds; badge. Beanie: knit,
	 * ribs, cuff; patch, pompom (bee: daisy), daisy centre. Cap: cloth, seams; logo (creeper: face). Nightcap: stripes,
	 * rim; pompom. Propeller: segments; hub. Bunny: fur, shade, band; inner ear. Mortarboard: cloth, band; cord, cap.
	 * Top hat and cowboy hat: felt, brim; band, feather or concho. Headphones: cups, cushions; caps. Party: stripes;
	 * (unused), pompom. Sombrero: crown, brim; pompom and embroidery, embroidery. Beret: cloth, rim; paint. Tyrolean:
	 * felt, brim, cord; feather, badge. Captain: top, band, visor; cord, anchor. Ushanka: cloth, fur, light fur; ties,
	 * badge. Tiara: metal, setting; stone, small stones. Ninja: band, shade. Santa: cloth, shade, fur; pompom. Reindeer:
	 * antlers, antler light, band; ears, bow, bell. Elf: cone, stripe or shade, cuff; bell. Sparkle (New Year's top
	 * hat): felt, brim; band, sparkles. Axolotl: plush, shade, belly; gills, eyes, cheeks. Fox: fur, shade,
	 * white; ear tips and nose, eyes, inner ears.
	 */
	public final int[] main;
	public final int accent;
	public final int second;
	public final int third;
	/** A crown with pearls (the paper one has plain points); wreaths with hanging strands. */
	public final boolean fancy;
	private ItemStack stack;

	Hat(Style style, Unlock unlock, String icon, int[] main, int accent, int second, int third, boolean fancy) {
		this.style = style;
		this.unlock = unlock;
		this.icon = Identifier.withDefaultNamespace(icon);
		this.main = main;
		this.accent = accent;
		this.second = second;
		this.third = third;
		this.fancy = fancy;
	}

	/** Whether it can be earned now: seasonal hats only in their months. Once earned, they can be worn all year. */
	public boolean inSeason() {
		return style.group.inSeason();
	}

	/** Yours from the start: everything but the seasonal ones (those are earned by playing in their months). */
	public boolean free() {
		return !style.group.seasonal();
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component title() {
		return Component.translatable("cobblecomfort.hat." + key());
	}

	/** An item that stands for it (the wardrobe, the message when it is earned). */
	public ItemStack icon() {
		if (stack == null) {
			stack = new ItemStack(BuiltInRegistries.ITEM.getValue(icon));
		}
		return stack;
	}

	/** The colours of a kind of hat, in their order. */
	public static List<Hat> variants(Style style) {
		List<Hat> out = new ArrayList<>();
		for (Hat hat : values()) {
			if (hat.style == style) {
				out.add(hat);
			}
		}
		return out;
	}

	public static Hat byId(int id) {
		Hat[] all = values();
		return id >= 0 && id < all.length ? all[id] : null;
	}

	public static Hat byKey(String key) {
		for (Hat hat : values()) {
			if (hat.key().equals(key)) {
				return hat;
			}
		}
		return null;
	}
}
