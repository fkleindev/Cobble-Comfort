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
 * Something carried on the back, in one of its colours, instead of a plush backpack; a back charm hangs from it. All
 * colours of a kind are earned together. The order is the piece's id when shared with other players: only ever append.
 */
public enum BackPiece {
	HIKING_GREEN(Style.HIKING, Hat.Unlock.free(), "green_wool",
			new int[]{0xFF4A6A3A, 0xFF3A5430, 0xFF8A6A42}, 0xFFB8A070, 0xFFC0C0C8),
	HIKING_RED(Style.HIKING, Hat.Unlock.free(), "red_wool",
			new int[]{0xFFA83A2E, 0xFF862C22, 0xFF6A4A2E}, 0xFF5A7A9A, 0xFFC0C0C8),
	HIKING_BLUE(Style.HIKING, Hat.Unlock.free(), "blue_wool",
			new int[]{0xFF2E4A7A, 0xFF22385E, 0xFF6A4A2E}, 0xFFC86A3A, 0xFFC0C0C8),
	WINGS_ANGEL(Style.WINGS, Hat.Unlock.item("phantom_membrane"), "feather",
			new int[]{0xFFF6F6F2, 0xFFDCDCE4, 0xFFC4C6D2}, 0xFFE8C040, 0),
	WINGS_FAIRY(Style.WINGS, Hat.Unlock.item("phantom_membrane"), "glass_pane",
			new int[]{0xFFC8F0F4, 0xFFF4C8E8, 0xFF9AD8E8}, 0xFFF4F4FF, 0),
	WINGS_BAT(Style.WINGS, Hat.Unlock.item("phantom_membrane"), "phantom_membrane",
			new int[]{0xFF3A2E3A, 0xFF2A1E2A, 0xFF5A4A5A}, 0xFF1A141A, 0),
	SWORD_IRON(Style.SWORD, Hat.Unlock.advancement("adventure/kill_a_mob"), "iron_sword",
			new int[]{0xFF5A3C24, 0xFF3E2A1A}, 0xFFD8DCE2, 0xFF8A8E96),
	SWORD_GOLD(Style.SWORD, Hat.Unlock.advancement("adventure/kill_a_mob"), "golden_sword",
			new int[]{0xFF6A2A2A, 0xFF4A1E1E}, 0xFFF2D25A, 0xFFC8A030),
	SWORD_DIAMOND(Style.SWORD, Hat.Unlock.advancement("adventure/kill_a_mob"), "diamond_sword",
			new int[]{0xFF2A2A3A, 0xFF1C1C28}, 0xFF7AE8E0, 0xFF3AB8B0),
	SWORD_NETHERITE(Style.SWORD, Hat.Unlock.advancement("adventure/kill_a_mob"), "netherite_sword",
			new int[]{0xFF3A2A22, 0xFF261C16}, 0xFF5A5258, 0xFF3A3238),
	GUITAR_NATURAL(Style.GUITAR, Hat.Unlock.item("note_block"), "note_block",
			new int[]{0xFFD8A86A, 0xFFB8884A, 0xFF5A3A22}, 0xFF2A2A2A, 0xFFE8E4DA),
	GUITAR_RED(Style.GUITAR, Hat.Unlock.item("note_block"), "note_block",
			new int[]{0xFFC02A2A, 0xFF8A1E1E, 0xFF3A2A22}, 0xFFF4F0E6, 0xFFE8E4DA),
	GUITAR_BLACK(Style.GUITAR, Hat.Unlock.item("note_block"), "note_block",
			new int[]{0xFF26262A, 0xFF1A1A1E, 0xFF5A3A22}, 0xFFF4F0E6, 0xFFE8E4DA),
	SHELL_TURTLE(Style.SHELL, Hat.Unlock.item("turtle_scute"), "turtle_scute",
			new int[]{0xFF4A8A3A, 0xFF3A6E2E, 0xFF2A4E22}, 0xFFC8B87A, 0xFF6A4A2E),
	SHELL_MOSSY(Style.SHELL, Hat.Unlock.item("turtle_scute"), "turtle_scute",
			new int[]{0xFF3A5A3A, 0xFF2A4A2E, 0xFF1E3422}, 0xFF7AA84A, 0xFF4A3A2A),
	SHELL_SAND(Style.SHELL, Hat.Unlock.item("turtle_scute"), "turtle_scute",
			new int[]{0xFFD8C08A, 0xFFB8A06A, 0xFF8A7448}, 0xFF6A8A4A, 0xFF6A4A2E),
	PLUSH_WOLF_PALE(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFFD8D4CC, 0xFFD8D4CC, 0xFFD8D4CC}, 0, 0),
	PLUSH_WOLF_WOODS(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFF8A6A4A, 0xFF8A6A4A, 0xFF8A6A4A}, 0, 0),
	PLUSH_WOLF_ASHEN(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFFA8A8B0, 0xFFA8A8B0, 0xFFA8A8B0}, 0, 0),
	PLUSH_WOLF_BLACK(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFF2A2626, 0xFF2A2626, 0xFF2A2626}, 0, 0),
	PLUSH_WOLF_CHESTNUT(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFF8A5A3A, 0xFF8A5A3A, 0xFF8A5A3A}, 0, 0),
	PLUSH_WOLF_RUSTY(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFFB0603A, 0xFFB0603A, 0xFFB0603A}, 0, 0),
	PLUSH_WOLF_SNOWY(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFFF0F0F0, 0xFFF0F0F0, 0xFFF0F0F0}, 0, 0),
	PLUSH_WOLF_SPOTTED(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFFC8B090, 0xFFC8B090, 0xFFC8B090}, 0, 0),
	PLUSH_WOLF_STRIPED(Style.PLUSH_WOLF, Hat.Unlock.tamed("wolf"), "bone",
			new int[]{0xFF9A8A6A, 0xFF9A8A6A, 0xFF9A8A6A}, 0, 0),
	PLUSH_CAT_TABBY(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFF9A7A50, 0xFF9A7A50, 0xFF9A7A50}, 0, 0),
	PLUSH_CAT_BLACK(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFF26262E, 0xFF26262E, 0xFF26262E}, 0, 0),
	PLUSH_CAT_RED(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFE89A4A, 0xFFE89A4A, 0xFFE89A4A}, 0, 0),
	PLUSH_CAT_SIAMESE(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFE8DCC4, 0xFFE8DCC4, 0xFFE8DCC4}, 0, 0),
	PLUSH_CAT_BRITISH_SHORTHAIR(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFF8A8E96, 0xFF8A8E96, 0xFF8A8E96}, 0, 0),
	PLUSH_CAT_CALICO(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFE8D0B0, 0xFFE8D0B0, 0xFFE8D0B0}, 0, 0),
	PLUSH_CAT_PERSIAN(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFE8C88A, 0xFFE8C88A, 0xFFE8C88A}, 0, 0),
	PLUSH_CAT_RAGDOLL(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFF0EADA, 0xFFF0EADA, 0xFFF0EADA}, 0, 0),
	PLUSH_CAT_WHITE(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFFF4F4F0, 0xFFF4F4F0, 0xFFF4F4F0}, 0, 0),
	PLUSH_CAT_JELLIE(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFF6A6A6E, 0xFF6A6A6E, 0xFF6A6A6E}, 0, 0),
	PLUSH_CAT_ALL_BLACK(Style.PLUSH_CAT, Hat.Unlock.tamed("cat"), "string",
			new int[]{0xFF141418, 0xFF141418, 0xFF141418}, 0, 0),
	HALLOWEEN_BATWINGS_DUSK(Style.BAT_WINGS, Hat.Unlock.biome("dripstone_caves"), "bat_spawn_egg",
			new int[]{0xFF4A3A3E, 0xFF3A2C30, 0xFF241A1E}, 0xFFE0D8C8, 0),
	HALLOWEEN_BATWINGS_VAMPIRE(Style.BAT_WINGS, Hat.Unlock.biome("dripstone_caves"), "bat_spawn_egg",
			new int[]{0xFF8A1A22, 0xFF6A1218, 0xFF1A1A1E}, 0xFFE8E8E8, 0),
	HALLOWEEN_BATWINGS_GHOST(Style.BAT_WINGS, Hat.Unlock.biome("dripstone_caves"), "bat_spawn_egg",
			new int[]{0xFFD8D8E2, 0xFFB8B8C6, 0xFF7A7A88}, 0xFF2A2A32, 0),
	HALLOWEEN_BROOM_OAK(Style.BROOM, Hat.Unlock.item("wheat"), "wheat",
			new int[]{0xFF8A6A42, 0xFF6A4E2E, 0xFFD8B860}, 0xFF6A2A8A, 0xFFB8963E),
	HALLOWEEN_BROOM_DARK(Style.BROOM, Hat.Unlock.item("wheat"), "wheat",
			new int[]{0xFF4A3420, 0xFF36261A, 0xFF9A8A52}, 0xFF3A7A3A, 0xFF7A6A3A),
	HALLOWEEN_BROOM_BIRCH(Style.BROOM, Hat.Unlock.item("wheat"), "wheat",
			new int[]{0xFFE0D8C0, 0xFF3A3A36, 0xFFE8C860}, 0xFFE8862A, 0xFFC8A040),
	HALLOWEEN_PUMPKIN_PACK_FIRE(Style.PUMPKIN_PACK, Hat.Unlock.item("carved_pumpkin"), "carved_pumpkin",
			new int[]{0xFFE8862A, 0xFFC0661A, 0xFF5A3A22}, 0xFFFFD84A, 0xFF4A7A2A),
	HALLOWEEN_PUMPKIN_PACK_SOUL(Style.PUMPKIN_PACK, Hat.Unlock.item("carved_pumpkin"), "soul_lantern",
			new int[]{0xFFE8862A, 0xFFC0661A, 0xFF2A2A30}, 0xFF7AE8F0, 0xFF4A7A2A),
	HALLOWEEN_PUMPKIN_PACK_WHITE(Style.PUMPKIN_PACK, Hat.Unlock.item("carved_pumpkin"), "bone_meal",
			new int[]{0xFFF0ECE0, 0xFFD0CABA, 0xFF5A3A22}, 0xFFFFB84A, 0xFF5A8A3A),
	HALLOWEEN_SCYTHE_IRON(Style.SCYTHE, Hat.Unlock.biome("soul_sand_valley"), "iron_hoe",
			new int[]{0xFF4A3420, 0xFF36261A, 0xFFB0B4BC}, 0xFFE8ECF2, 0xFF6A1E22),
	HALLOWEEN_SCYTHE_SOUL(Style.SCYTHE, Hat.Unlock.biome("soul_sand_valley"), "soul_torch",
			new int[]{0xFF2A2A30, 0xFF1A1A1E, 0xFF2E4A58}, 0xFF7AE8F0, 0xFF4A3A2A),
	HALLOWEEN_SCYTHE_GOLD(Style.SCYTHE, Hat.Unlock.biome("soul_sand_valley"), "golden_hoe",
			new int[]{0xFF2A1A1A, 0xFF1A1010, 0xFFF2D25A}, 0xFFFFF0A0, 0xFF6A2A8A),
	WINTER_SACK_BURLAP(Style.GIFT_SACK, Hat.Unlock.free(), "bundle",
			new int[]{0xFFB8925A, 0xFF967444, 0xFFE8D8B0}, 0xFFC8282A, 0xFF2E7A3A),
	WINTER_SACK_RED(Style.GIFT_SACK, Hat.Unlock.free(), "bundle",
			new int[]{0xFFB8282A, 0xFF961E22, 0xFFE8C040}, 0xFF2E7A3A, 0xFF3A6AB8),
	WINTER_SACK_GREEN(Style.GIFT_SACK, Hat.Unlock.free(), "bundle",
			new int[]{0xFF2E6A3A, 0xFF22542C, 0xFFE8C040}, 0xFFC8282A, 0xFFF4F4F0),
	WINTER_ICEWINGS_FROST(Style.ICE_WINGS, Hat.Unlock.biome("ice_spikes"), "packed_ice",
			new int[]{0xFFE8F6FF, 0xFFB8E0F8, 0xFF7AB8E8}, 0xFFFFFFFF, 0),
	WINTER_ICEWINGS_AURORA(Style.ICE_WINGS, Hat.Unlock.biome("ice_spikes"), "packed_ice",
			new int[]{0xFFC8F8E8, 0xFF8AE8C8, 0xFF8A7AE8}, 0xFFE0FFF4, 0),
	WINTER_ICEWINGS_DEEP(Style.ICE_WINGS, Hat.Unlock.biome("ice_spikes"), "blue_ice",
			new int[]{0xFFA8D0F8, 0xFF5A8AE0, 0xFF2A4AA8}, 0xFFD8ECFF, 0),
	WINTER_TREE_CLASSIC(Style.TREE, Hat.Unlock.item("spruce_sapling"), "spruce_sapling",
			new int[]{0xFF2E7A3A, 0xFF1E5A2A, 0xFF6A4A2A}, 0xFFF2D23A, 0xFFD82A3A),
	WINTER_TREE_SNOWY(Style.TREE, Hat.Unlock.item("spruce_sapling"), "snowball",
			new int[]{0xFF2E6A42, 0xFFF4F8FF, 0xFF6A4A2A}, 0xFFE8ECF4, 0xFF3A6AB8),
	WINTER_TREE_GOLD(Style.TREE, Hat.Unlock.item("spruce_sapling"), "gold_nugget",
			new int[]{0xFF2E7A3A, 0xFF1E5A2A, 0xFF6A4A2A}, 0xFFF2D23A, 0xFFE8C040),
	WINTER_ROCKETS_RED(Style.ROCKETS, Hat.Unlock.item("firework_rocket"), "firework_rocket",
			new int[]{0xFFD02A2A, 0xFFF4F4F0, 0xFFE8C040}, 0xFF3A3A40, 0xFF5A3A22),
	WINTER_ROCKETS_BLUE(Style.ROCKETS, Hat.Unlock.item("firework_rocket"), "firework_rocket",
			new int[]{0xFF2A5AC8, 0xFFC8CCD4, 0xFFF4F4F0}, 0xFFC8CCD4, 0xFF3A3A40),
	WINTER_ROCKETS_GOLD(Style.ROCKETS, Hat.Unlock.item("firework_rocket"), "firework_rocket",
			new int[]{0xFFE8C040, 0xFF1E1E22, 0xFFD02A2A}, 0xFF1E1E22, 0xFF3A3A40),
	ROD_COD(Style.ROD, Hat.Unlock.item("fishing_rod"), "cod",
			new int[]{0xFF8A6438, 0xFFC8A070, 0xFFE8E8EC}, 0xFFB0B4BC, 0xFFC8282A),
	ROD_SALMON(Style.ROD, Hat.Unlock.item("fishing_rod"), "salmon",
			new int[]{0xFF4A3420, 0xFFB8946A, 0xFFE8E8EC}, 0xFFD8B04A, 0xFF2E7A3A),
	ROD_PUFFERFISH(Style.ROD, Hat.Unlock.item("fishing_rod"), "pufferfish",
			new int[]{0xFFC0B85A, 0xFFD8C890, 0xFFE8E8EC}, 0xFFB0B4BC, 0xFF2A5AC8),
	BEEHIVE_OAK(Style.BEEHIVE, Hat.Unlock.item("honeycomb"), "beehive",
			new int[]{0xFFB8925A, 0xFF8A6A3A, 0xFF6A4E2A}, 0xFFF2B22A, 0xFF9A7442),
	BEEHIVE_NEST(Style.BEEHIVE, Hat.Unlock.item("honeycomb"), "bee_nest",
			new int[]{0xFFE8C46A, 0xFFC89A3A, 0xFFA87A2A}, 0xFFF6C83A, 0xFFD8A848),
	BEEHIVE_DARK(Style.BEEHIVE, Hat.Unlock.item("honeycomb"), "honeycomb",
			new int[]{0xFF5A3E22, 0xFF3E2A16, 0xFF2A1C0E}, 0xFFF2B22A, 0xFF4A3018);

	/** The kind of piece, shown in the wardrobe as one tile that opens its colours. */
	public enum Style {
		HIKING(Hat.Group.ALL_YEAR), WINGS(Hat.Group.ALL_YEAR), SWORD(Hat.Group.ALL_YEAR), GUITAR(Hat.Group.ALL_YEAR),
		SHELL(Hat.Group.ALL_YEAR), PLUSH_WOLF(Hat.Group.ALL_YEAR), PLUSH_CAT(Hat.Group.ALL_YEAR), ROD(Hat.Group.ALL_YEAR),
		BEEHIVE(Hat.Group.ALL_YEAR),
		BAT_WINGS(Hat.Group.SEASON), BROOM(Hat.Group.SEASON), PUMPKIN_PACK(Hat.Group.SEASON), SCYTHE(Hat.Group.SEASON),
		GIFT_SACK(Hat.Group.WINTER), ICE_WINGS(Hat.Group.WINTER), TREE(Hat.Group.WINTER), ROCKETS(Hat.Group.WINTER);

		/** Seasonal kinds can only be earned in their months; once earned, they can be worn all year. */
		public final Hat.Group group;

		Style(Hat.Group group) {
			this.group = group;
		}

		public Component title() {
			return Component.translatable("cobblecomfort.back.style." + name().toLowerCase(Locale.ROOT));
		}
	}

	public final Style style;
	public final Hat.Unlock unlock;
	private final Identifier icon;
	/**
	 * Its colours, by style. Hiking pack: cloth, shade, straps; bedroll, buckles. Wings: light, middle, shade; edge.
	 * Sword: scabbard, shade; blade metal, metal shade. Guitar: body, edge, neck; pickguard, strings. Shell: plates,
	 * shade, lines; rim, straps. Bat wings: membrane, shade, bones; claws. Broom: handle, handle detail, bristles; band,
	 * bristle shade. Pumpkin pack: pumpkin, shade, straps; glow, stem. Scythe: handle, shade, blade; edge, grip. Gift
	 * sack: sack, shade, rope; two gifts. Ice wings: light, middle, deep; glow. Tree: needles, shade (or snow), trunk;
	 * star, baubles. Rockets: tubes, stripes, tips; fins, harness. Fishing rod: blank, cork, line; reel,
	 * thread wraps. Beehive: wood, frame, plank lines; honey, roof.
	 */
	public final int[] main;
	public final int accent;
	public final int second;
	private ItemStack stack;

	BackPiece(Style style, Hat.Unlock unlock, String icon, int[] main, int accent, int second) {
		this.style = style;
		this.unlock = unlock;
		this.icon = Identifier.withDefaultNamespace(icon);
		this.main = main;
		this.accent = accent;
		this.second = second;
	}

	/** Yours from the start: everything but the seasonal ones (those are earned by playing in their months). */
	public boolean free() {
		return !style.group.seasonal();
	}

	/** Whether it can be earned now: seasonal pieces only in their months. Once earned, they can be worn all year. */
	public boolean inSeason() {
		return style.group.inSeason();
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component title() {
		if (plush() != null) {
			return Component.translatable("cobblecomfort.back." + style.name().toLowerCase(Locale.ROOT),
					Component.translatable("cobblecomfort.pet_variant." + variant()));
		}
		return Component.translatable("cobblecomfort.back." + key());
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

	/** The colours of a kind, in their order. */
	public static List<BackPiece> variants(Style style) {
		List<BackPiece> out = new ArrayList<>();
		for (BackPiece piece : values()) {
			if (piece.style == style) {
				out.add(piece);
			}
		}
		return out;
	}

	public static BackPiece byId(int id) {
		BackPiece[] all = values();
		return id >= 0 && id < all.length ? all[id] : null;
	}

	public static BackPiece byKey(String key) {
		for (BackPiece piece : values()) {
			if (piece.key().equals(key)) {
				return piece;
			}
		}
		return null;
	}
}
