package dev.cobblecomfort.features.wardrobe;

import dev.cobblecomfort.features.petplush.PetPlush;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A charm hanging on a string from the back (or from what is carried there) instead of a pet plush. All variants of
 * a kind are earned together. The order is the charm's id when shared with other players: only ever append.
 */
public enum Trinket {
	MINI_CREEPER(Style.MINI, Hat.Unlock.free(), "creeper_head",
			new int[]{0xFF5AB04A, 0xFF4A9A3C, 0xFF3A7A2E}, 0xFF1A2A18),
	MINI_AXOLOTL(Style.MINI, Hat.Unlock.free(), "axolotl_bucket",
			new int[]{0xFFF4A8C8, 0xFFE888B0, 0xFFC85A8A}, 0xFF2A1A22),
	MINI_BEE(Style.MINI, Hat.Unlock.free(), "honeycomb",
			new int[]{0xFFF6CE3A, 0xFF4A2A14, 0xFFE8F0FF}, 0xFF1A1A1A),
	MINI_SLIME(Style.MINI, Hat.Unlock.free(), "slime_ball",
			new int[]{0xFF8AD86A, 0xFF6AB84E, 0xFF4A9A3A}, 0xFF1E3A1A),
	LANTERN_FIRE(Style.LANTERN, Hat.Unlock.item("lantern"), "lantern",
			new int[]{0xFF3A3A42, 0xFF26262C}, 0xFFFFD86A),
	LANTERN_SOUL(Style.LANTERN, Hat.Unlock.item("lantern"), "soul_lantern",
			new int[]{0xFF3A3A42, 0xFF26262C}, 0xFF7AE8F0),
	LUCKY_CLOVER(Style.LUCKY, Hat.Unlock.item("rabbit_foot"), "rabbit_foot",
			new int[]{0xFF4AB84A, 0xFF3A9A3A}, 0xFF2E6A2E),
	LUCKY_HORSESHOE(Style.LUCKY, Hat.Unlock.item("rabbit_foot"), "rabbit_foot",
			new int[]{0xFFB0B4BC, 0xFF8A8E96}, 0xFF5A5E66),
	LUCKY_STAR(Style.LUCKY, Hat.Unlock.item("rabbit_foot"), "rabbit_foot",
			new int[]{0xFFF2D25A, 0xFFE8B030}, 0xFFFFF0A0),
	BELL_GOLD(Style.BELL, Hat.Unlock.item("bell"), "bell",
			new int[]{0xFFF2D25A, 0xFFC8A030}, 0xFF3A3A40),
	BELL_SILVER(Style.BELL, Hat.Unlock.item("bell"), "bell",
			new int[]{0xFFE4E8EE, 0xFFB0B6C0}, 0xFF3A3A40),
	PLUSH_WOLF_PALE(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFFD8D4CC, 0xFFD8D4CC, 0xFFD8D4CC}, 0),
	PLUSH_WOLF_WOODS(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFF8A6A4A, 0xFF8A6A4A, 0xFF8A6A4A}, 0),
	PLUSH_WOLF_ASHEN(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFFA8A8B0, 0xFFA8A8B0, 0xFFA8A8B0}, 0),
	PLUSH_WOLF_BLACK(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFF2A2626, 0xFF2A2626, 0xFF2A2626}, 0),
	PLUSH_WOLF_CHESTNUT(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFF8A5A3A, 0xFF8A5A3A, 0xFF8A5A3A}, 0),
	PLUSH_WOLF_RUSTY(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFFB0603A, 0xFFB0603A, 0xFFB0603A}, 0),
	PLUSH_WOLF_SNOWY(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0}, 0),
	PLUSH_WOLF_SPOTTED(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFFC8B090, 0xFFC8B090, 0xFFC8B090}, 0),
	PLUSH_WOLF_STRIPED(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFF9A8A6A, 0xFF9A8A6A, 0xFF9A8A6A}, 0),
	PLUSH_CAT_TABBY(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFF9A7A50, 0xFF9A7A50, 0xFF9A7A50}, 0),
	PLUSH_CAT_BLACK(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFF26262E, 0xFF26262E, 0xFF26262E}, 0),
	PLUSH_CAT_RED(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFE89A4A, 0xFFE89A4A, 0xFFE89A4A}, 0),
	PLUSH_CAT_SIAMESE(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFE8DCC4, 0xFFE8DCC4, 0xFFE8DCC4}, 0),
	PLUSH_CAT_BRITISH_SHORTHAIR(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFF8A8E96, 0xFF8A8E96, 0xFF8A8E96}, 0),
	PLUSH_CAT_CALICO(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFE8D0B0, 0xFFE8D0B0, 0xFFE8D0B0}, 0),
	PLUSH_CAT_PERSIAN(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFE8C88A, 0xFFE8C88A, 0xFFE8C88A}, 0),
	PLUSH_CAT_RAGDOLL(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFF0EADA, 0xFFF0EADA, 0xFFF0EADA}, 0),
	PLUSH_CAT_WHITE(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFF4F4F0, 0xFFF4F4F0, 0xFFF4F4F0}, 0),
	PLUSH_CAT_JELLIE(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFF6A6A6E, 0xFF6A6A6E, 0xFF6A6A6E}, 0),
	PLUSH_CAT_ALL_BLACK(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFF141418, 0xFF141418, 0xFF141418}, 0),
	HALLOWEEN_GHOST_WHITE(Style.GHOST, Hat.Unlock.free(), "white_wool",
			new int[]{0xFFF4F4F8, 0xFFDCDCE6, 0xFFFFFFFF}, 0xFF1A1A22),
	HALLOWEEN_GHOST_SOUL(Style.GHOST, Hat.Unlock.free(), "soul_lantern",
			new int[]{0xFFB8F0F4, 0xFF8AD8E0, 0xFFE0FCFF}, 0xFF1A3A44),
	HALLOWEEN_GHOST_LILAC(Style.GHOST, Hat.Unlock.free(), "purple_wool",
			new int[]{0xFFE0C8F4, 0xFFC8A8E8, 0xFFF4E8FF}, 0xFF2A1A3A),
	HALLOWEEN_JACK_FIRE(Style.JACK, Hat.Unlock.item("jack_o_lantern"), "jack_o_lantern",
			new int[]{0xFFE8862A, 0xFFC0661A, 0xFF4A7A2A}, 0xFFFFD84A),
	HALLOWEEN_JACK_SOUL(Style.JACK, Hat.Unlock.item("jack_o_lantern"), "soul_lantern",
			new int[]{0xFFE8862A, 0xFFC0661A, 0xFF4A7A2A}, 0xFF7AE8F0),
	AUTUMN_ACORN_LIGHT(Style.ACORN, Hat.Unlock.biome("dark_forest"), "oak_sapling",
			new int[]{0xFFC8904A, 0xFFA8743A, 0xFF6A4A2A}, 0xFF4A3420),
	AUTUMN_ACORN_DARK(Style.ACORN, Hat.Unlock.biome("dark_forest"), "dark_oak_sapling",
			new int[]{0xFF8A5A2E, 0xFF6A4220, 0xFF4A3420}, 0xFF2A1E14),
	AUTUMN_ACORN_GOLD(Style.ACORN, Hat.Unlock.biome("dark_forest"), "gold_nugget",
			new int[]{0xFFF2D25A, 0xFFC8A030, 0xFF8A6A2A}, 0xFF5A4420),
	HALLOWEEN_CANDY_RED(Style.CANDY, Hat.Unlock.item("sugar"), "sugar",
			new int[]{0xFFD82A3A, 0xFFF4F4F0, 0xFFB81E2E}, 0xFFFFB0B8),
	HALLOWEEN_CANDY_ORANGE(Style.CANDY, Hat.Unlock.item("sugar"), "sugar",
			new int[]{0xFFE8862A, 0xFF1A1A1E, 0xFFC0661A}, 0xFFFFD0A0),
	HALLOWEEN_CANDY_PURPLE(Style.CANDY, Hat.Unlock.item("sugar"), "sugar",
			new int[]{0xFF8A3AC8, 0xFF7AD84A, 0xFF6A2AA0}, 0xFFD8B8F4),
	WINTER_BAUBLE_RED(Style.BAUBLE, Hat.Unlock.free(), "red_dye",
			new int[]{0xFFD02A34, 0xFFE8C040, 0xFFFFB0B8}, 0xFFC8CCD4),
	WINTER_BAUBLE_GOLD(Style.BAUBLE, Hat.Unlock.free(), "gold_nugget",
			new int[]{0xFFE8C040, 0xFFF4F4F0, 0xFFFFF0B0}, 0xFFE8C040),
	WINTER_BAUBLE_BLUE(Style.BAUBLE, Hat.Unlock.free(), "blue_dye",
			new int[]{0xFF2A5AC8, 0xFFC8CCD4, 0xFFA8C8FF}, 0xFFC8CCD4),
	WINTER_GINGERBREAD_CLASSIC(Style.GINGERBREAD, Hat.Unlock.item("cookie"), "cookie",
			new int[]{0xFFB8743A, 0xFFF4F4F0, 0xFF3A2414}, 0xFFD82A3A),
	WINTER_GINGERBREAD_DARK(Style.GINGERBREAD, Hat.Unlock.item("cookie"), "cookie",
			new int[]{0xFF6A4228, 0xFFF4F4F0, 0xFF1E140C}, 0xFF3AA84A),
	WINTER_GINGERBREAD_HONEY(Style.GINGERBREAD, Hat.Unlock.item("cookie"), "honey_bottle",
			new int[]{0xFFD8A050, 0xFFF4C8D8, 0xFF5A3418}, 0xFF4A9AF2),
	WINTER_CANDY_CANE_RED(Style.CANDY_CANE, Hat.Unlock.item("sugar_cane"), "sugar_cane",
			new int[]{0xFFF4F4F0, 0xFFD02A34, 0xFFF4F4F0}, 0xFFFFFFFF),
	WINTER_CANDY_CANE_GREEN(Style.CANDY_CANE, Hat.Unlock.item("sugar_cane"), "sugar_cane",
			new int[]{0xFFF4F4F0, 0xFF2E8A3A, 0xFFF4F4F0}, 0xFFFFFFFF),
	WINTER_CANDY_CANE_MINT(Style.CANDY_CANE, Hat.Unlock.item("sugar_cane"), "sugar_cane",
			new int[]{0xFFD02A34, 0xFF2E8A3A, 0xFFF4F4F0}, 0xFFFFFFFF),
	WINTER_SNOWMAN_RED(Style.SNOWMAN, Hat.Unlock.item("snow_block"), "snow_block",
			new int[]{0xFFF4F6FA, 0xFFC8282A, 0xFFDCE2EC}, 0xFF1E1E22),
	WINTER_SNOWMAN_GREEN(Style.SNOWMAN, Hat.Unlock.item("snow_block"), "snow_block",
			new int[]{0xFFF4F6FA, 0xFF2E7A3A, 0xFFDCE2EC}, 0xFF1E1E22),
	WINTER_SNOWMAN_BLUE(Style.SNOWMAN, Hat.Unlock.item("snow_block"), "snow_block",
			new int[]{0xFFF4F6FA, 0xFF3A6AB8, 0xFFDCE2EC}, 0xFF1E1E22),
	END_CRYSTAL_CLASSIC(Style.END_CRYSTAL, Hat.Unlock.item("end_crystal"), "end_crystal",
			new int[]{0xFF35284A, 0xFFE8DCF0, 0xFFE04AD0}, 0xFFFFB8F4),
	END_CRYSTAL_VOID(Style.END_CRYSTAL, Hat.Unlock.item("end_crystal"), "end_crystal",
			new int[]{0xFF2A2C46, 0xFFB8E8F0, 0xFF3AC8D8}, 0xFFC8FFFF),
	COMPASS_IRON(Style.COMPASS, Hat.Unlock.item("compass"), "compass",
			new int[]{0xFFC8CCD4, 0xFF8A8E96, 0xFFF4F4F0}, 0xFFD82A2A),
	COMPASS_BRASS(Style.COMPASS, Hat.Unlock.item("compass"), "compass",
			new int[]{0xFFE0B04A, 0xFFA8802A, 0xFFF4EAD0}, 0xFFD82A2A);

	/** The kind of charm, shown in the wardrobe as one tile that opens its variants. */
	public enum Style {
		MINI(Hat.Group.ALL_YEAR), LANTERN(Hat.Group.ALL_YEAR), LUCKY(Hat.Group.ALL_YEAR), BELL(Hat.Group.ALL_YEAR),
		PLUSH_WOLF(Hat.Group.ALL_YEAR), PLUSH_CAT(Hat.Group.ALL_YEAR), END_CRYSTAL(Hat.Group.ALL_YEAR),
		COMPASS(Hat.Group.ALL_YEAR),
		GHOST(Hat.Group.SEASON), JACK(Hat.Group.SEASON), ACORN(Hat.Group.SEASON), CANDY(Hat.Group.SEASON),
		BAUBLE(Hat.Group.WINTER), GINGERBREAD(Hat.Group.WINTER), CANDY_CANE(Hat.Group.WINTER), SNOWMAN(Hat.Group.WINTER);

		/** Seasonal kinds can only be earned in their months; once earned, they can be worn all year. */
		public final Hat.Group group;

		Style(Hat.Group group) {
			this.group = group;
		}

		public Component title() {
			return Component.translatable("cobblecomfort.trinket.style." + name().toLowerCase(Locale.ROOT));
		}
	}

	public final Style style;
	public final Hat.Unlock unlock;
	private final Identifier icon;
	/** Its colours: the main ones (body, shade, detail), and one for eyes, light or a darker edge. */
	public final int[] main;
	public final int accent;
	private ItemStack stack;

	Trinket(Style style, Hat.Unlock unlock, String icon, int[] main, int accent) {
		this.style = style;
		this.unlock = unlock;
		this.icon = Identifier.withDefaultNamespace(icon);
		this.main = main;
		this.accent = accent;
	}

	/** Yours from the start: everything but the seasonal ones (those are earned by playing in their months). */
	public boolean free() {
		return !style.group.seasonal();
	}

	/** Whether it can be earned now: seasonal charms only in their months. Once earned, they can be worn all year. */
	public boolean inSeason() {
		return style.group.inSeason();
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component title() {
		if (plush() != null) {
			return Component.translatable("cobblecomfort.trinket." + style.name().toLowerCase(Locale.ROOT),
					Component.translatable("cobblecomfort.pet_variant." + variant()));
		}
		return Component.translatable("cobblecomfort.trinket." + key());
	}

	/** The plush of a wolf or cat this is (with a red collar), or null for the other kinds. */
	public PetPlush plush() {
		return switch (style) {
			case PLUSH_WOLF -> new PetPlush(PetPlush.WOLF, "minecraft:" + variant(), DyeColor.RED.getId());
			case PLUSH_CAT -> new PetPlush(PetPlush.CAT, "minecraft:" + variant(), DyeColor.RED.getId());
			default -> null;
		};
	}

	/** A plush's variant, from its name: "pale" for plush_wolf_pale. */
	private String variant() {
		return key().substring(key().indexOf('_', "plush_".length()) + 1);
	}

	/** An item that stands for it (the message when it is earned, the outfit list). */
	public ItemStack icon() {
		if (stack == null) {
			stack = new ItemStack(BuiltInRegistries.ITEM.getValue(icon));
		}
		return stack;
	}

	/** The variants of a kind, in their order. */
	public static List<Trinket> variants(Style style) {
		List<Trinket> out = new ArrayList<>();
		for (Trinket trinket : values()) {
			if (trinket.style == style) {
				out.add(trinket);
			}
		}
		return out;
	}

	public static Trinket byId(int id) {
		Trinket[] all = values();
		return id >= 0 && id < all.length ? all[id] : null;
	}

	public static Trinket byKey(String key) {
		for (Trinket trinket : values()) {
			if (trinket.key().equals(key)) {
				return trinket;
			}
		}
		return null;
	}
}
