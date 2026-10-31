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
 * The autumn and Halloween ones can only be worn in October and November. All colours of a kind are earned together,
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
			new int[]{0xFF2E2E32, 0xFF1E1E22}, 0xFFE8C040, 0, 0, true);

	/** The kind of hat: the shape it is made in, shown in the wardrobe as one tile that opens its colours. */
	public enum Style {
		CROWN(Group.ALL_YEAR, false), WREATH(Group.ALL_YEAR, false), AVIATOR(Group.ALL_YEAR, true), MINER(Group.ALL_YEAR, true),
		PIRATE(Group.ALL_YEAR, true), HALO(Group.ALL_YEAR, true), STRAW(Group.ALL_YEAR, true), FISHER(Group.ALL_YEAR, true),
		CHEF(Group.ALL_YEAR, true), LEAVES(Group.SEASON, true), ACORN(Group.SEASON, true), MUSHROOM(Group.SEASON, true),
		WITCH(Group.SEASON, true), PUMPKIN(Group.SEASON, true), HORNS(Group.SEASON, true), BAT(Group.SEASON, true);

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

	/** The wardrobe's groups of hats. Seasonal ones can only be worn (and earned) in their months. */
	public enum Group {
		ALL_YEAR(0, 0), SEASON(10, 11);

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
			return !seasonal() || month >= firstMonth && month <= lastMonth;
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

	/** How a hat is earned: always worn, an advancement, an item once in the inventory, or a biome once entered. */
	public record Unlock(Kind kind, Identifier id) {
		public enum Kind {
			FREE, ADVANCEMENT, ITEM, BIOME
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
	 * Halo: light, shade. Straw: straw, dark; ribbon. Fisher: cloth, brim; band. Chef: cloth, folds; badge.
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

	/** Whether it can be worn now: seasonal hats only in their months. */
	public boolean inSeason() {
		return style.group.inSeason();
	}

	public boolean free() {
		return unlock.kind() == Unlock.Kind.FREE;
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
