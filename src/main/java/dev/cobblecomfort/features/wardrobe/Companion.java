package dev.cobblecomfort.features.wardrobe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A little companion that follows its player around, only drawn (no entity: nothing collides with it, nothing can hit
 * it, blocks go where it stands). All variants of a kind are earned together. The order is its id when shared with
 * other players: only ever append.
 */
public enum Companion {
	MUSHROOM_RED(Style.MUSHROOM, "red_mushroom", 0xFFD8302A, 0xFFF4F0E4, 0xFFF0E6CC, 0xFF2A1E1A),
	MUSHROOM_BROWN(Style.MUSHROOM, "brown_mushroom", 0xFF9A6A48, 0xFFC8A47A, 0xFFE8DCC4, 0xFF2A1E1A),
	MUSHROOM_CRIMSON(Style.MUSHROOM, "crimson_fungus", 0xFF9A1E2A, 0xFFF0A040, 0xFFC8686A, 0xFF2A1014),
	MUSHROOM_WARPED(Style.MUSHROOM, "warped_fungus", 0xFF1E8A80, 0xFFF0A040, 0xFF6ACAC0, 0xFF0E2A28),
	CLOUD_WHITE(Style.CLOUD, "white_wool", 0xFFF6F8FC, 0xFFDCE4EE, 0xFF3A3A42, 0xFF1E1E22),
	CLOUD_RAIN(Style.CLOUD, "gray_wool", 0xFFA8B0BC, 0xFF8A929E, 0xFF2A2A30, 0xFF141418),
	CLOUD_SUNSET(Style.CLOUD, "pink_wool", 0xFFF8D0D8, 0xFFF0A8B8, 0xFF5A3A48, 0xFF2A1A22),
	DRAGON_GREEN(Style.DRAGON, "lime_dye", 0xFF4AA84A, 0xFFE8D890, 0xFF2E7A3A, 0xFFF0D040),
	DRAGON_RED(Style.DRAGON, "red_dye", 0xFFC8342A, 0xFFF0C880, 0xFF8A1E1A, 0xFFF0D040),
	DRAGON_ENDER(Style.DRAGON, "dragon_breath", 0xFF26222E, 0xFF4A3A5A, 0xFF16141C, 0xFFE04AF0),
	DRAGON_GOLD(Style.DRAGON, "gold_ingot", 0xFFE8B83A, 0xFFF8E8A0, 0xFFB8862A, 0xFF3AA8F0),
	OWL_BROWN(Style.OWL, "feather", 0xFF8A6A48, 0xFFD8C3A0, 0xFF5A4028, 0xFFF0C030),
	OWL_SNOWY(Style.OWL, "snowball", 0xFFF2F2EE, 0xFFFFFFFF, 0xFF6A6A66, 0xFFF0C030),
	OWL_GREY(Style.OWL, "gray_dye", 0xFF8A8A8E, 0xFFC8C8CC, 0xFF4A4A50, 0xFFF08A30),
	GOLEM_COBBLE(Style.GOLEM, "cobblestone", 0xFF8A8A8A, 0xFF6E6E6E, 0xFFA4A4A4, 0xFF5FD3F5),
	GOLEM_MOSSY(Style.GOLEM, "mossy_cobblestone", 0xFF7E847A, 0xFF5E7A44, 0xFF9AA090, 0xFF8AF06A),
	GOLEM_DEEPSLATE(Style.GOLEM, "cobbled_deepslate", 0xFF4A4A50, 0xFF36363C, 0xFF62626A, 0xFFF0A040),
	PUMPKIN_FIRE(Style.PUMPKIN, "jack_o_lantern", 0xFFE8862A, 0xFFC0661A, 0xFF4A7A2A, 0xFFFFD84A),
	PUMPKIN_SOUL(Style.PUMPKIN, "soul_lantern", 0xFFE8862A, 0xFFC0661A, 0xFF4A7A2A, 0xFF7AE8F0),
	SNOWMAN_RED(Style.SNOWMAN, "snowball", 0xFFF4F6FA, 0xFFDCE2EC, 0xFFC8282A, 0xFFE8862A),
	SNOWMAN_GREEN(Style.SNOWMAN, "snowball", 0xFFF4F6FA, 0xFFDCE2EC, 0xFF2E7A3A, 0xFFE8862A),
	SNOWMAN_BLUE(Style.SNOWMAN, "snowball", 0xFFF4F6FA, 0xFFDCE2EC, 0xFF3A6AB8, 0xFFE8862A),
	BUNNY_WHITE(Style.BUNNY, "rabbit_foot", 0xFFF2F0EC, 0xFFF4C8D0, 0xFF6AB8F0, 0xFFF0D040),
	BUNNY_BROWN(Style.BUNNY, "rabbit_hide", 0xFF9A7454, 0xFFE8C8B0, 0xFFE87AA8, 0xFF8AD86A),
	BUNNY_GREY(Style.BUNNY, "rabbit_foot", 0xFF9A9A9E, 0xFFE8D0D4, 0xFFF0C040, 0xFFA87AE8),
	RED_PANDA_CLASSIC(Style.RED_PANDA, "bamboo", 0xFFC8552A, 0xFF3A1E14, 0xFFF4EEE4, 0xFF8A3418),
	RED_PANDA_DARK(Style.RED_PANDA, "cocoa_beans", 0xFF8A3A1E, 0xFF26140E, 0xFFE8DCCC, 0xFF4E1E10),
	RED_PANDA_SNOW(Style.RED_PANDA, "snowball", 0xFFF0E2CC, 0xFF9A8676, 0xFFFFFFFF, 0xFFD2B898),
	RED_PANDA_AUTUMN(Style.RED_PANDA, "sweet_berries", 0xFFE8962A, 0xFF5A2E14, 0xFFF8F0DC, 0xFFC04A1E),
	TURTLE_GREEN(Style.TURTLE, "turtle_scute", 0xFF7CC46A, 0xFF3E8A4A, 0xFF5CAA5A, 0xFFE8D89A),
	TURTLE_DESERT(Style.TURTLE, "cactus", 0xFFC8A878, 0xFF6A4A2A, 0xFF9A7040, 0xFFEAD8A8),
	TURTLE_MOSSY(Style.TURTLE, "moss_block", 0xFF8AA86A, 0xFF4A5A3A, 0xFF62704A, 0xFFD8D0A0),
	TURTLE_CORAL(Style.TURTLE, "brain_coral", 0xFF8AD0C8, 0xFFE87A8A, 0xFFF4A0AA, 0xFFF8E8D8),
	KITTEN_GINGER(Style.KITTEN, "cod", 0xFFE8963A, 0xFFC06A20, 0xFFFFF0DC, 0xFF6AB83A),
	KITTEN_BLACK(Style.KITTEN, "ink_sac", 0xFF2A2A30, 0xFF1A1A1E, 0xFF3E3E46, 0xFFF0C030),
	KITTEN_WHITE(Style.KITTEN, "milk_bucket", 0xFFF6F4F0, 0xFFE4DED6, 0xFFFFFFFF, 0xFF5AA8F0),
	KITTEN_CALICO(Style.KITTEN, "salmon", 0xFFF6F0E6, 0xFFE8963A, 0xFF2A2A30, 0xFFC8A030),
	SHADOW_WOLF_NIGHT(Style.SHADOW_WOLF, "echo_shard", 0xFF424052, 0xFF262532, 0xFF726E8C, 0xFFF4E89A),
	SHADOW_WOLF_VOID(Style.SHADOW_WOLF, "ender_eye", 0xFF3E3050, 0xFF20182C, 0xFF7E50AA, 0xFFE85AF8),
	SHADOW_WOLF_EMBER(Style.SHADOW_WOLF, "blaze_powder", 0xFF463630, 0xFF241A16, 0xFF8E5638, 0xFFFF8A2A),
	SHADOW_WOLF_SOUL(Style.SHADOW_WOLF, "soul_torch", 0xFF32464E, 0xFF18282E, 0xFF50868E, 0xFF5AF0E0),
	HEDGEHOG_BROWN(Style.HEDGEHOG, "sweet_berries", 0xFF8A6A4A, 0xFFE8D4B4, 0xFF3A2A1E, 0xFFD8302A),
	HEDGEHOG_SNOW(Style.HEDGEHOG, "white_wool", 0xFFEEE6DA, 0xFFFFF6EE, 0xFFC4B8A8, 0xFF6AB0E8),
	HEDGEHOG_DARK(Style.HEDGEHOG, "coal", 0xFF4E423A, 0xFFCBB79C, 0xFF1E1814, 0xFF6AA83A),
	HEDGEHOG_AUTUMN(Style.HEDGEHOG, "apple", 0xFFB07A48, 0xFFF0DCC0, 0xFF5A3A20, 0xFFE87A2A),
	PENGUIN_EMPEROR(Style.PENGUIN, "ice", 0xFFA8ACB6, 0xFF26262C, 0xFFF6F6F8, 0xFF3A3A42),
	PENGUIN_KING(Style.PENGUIN, "brown_wool", 0xFF8A6A52, 0xFF4A3A2E, 0xFFE6D8C2, 0xFF2E2A28),
	PENGUIN_ADELIE(Style.PENGUIN, "gray_wool", 0xFF6C6C76, 0xFF34343C, 0xFFDCDCE2, 0xFFE89AA8),
	PENGUIN_FAIRY(Style.PENGUIN, "blue_ice", 0xFF7C9CBC, 0xFF4A6A8E, 0xFFF0F4F8, 0xFF2E2E36),
	MECH_STEEL(Style.MECH, "iron_ingot", 0xFFB8BCC4, 0xFFC8743A, 0xFF5AE6F4, 0xFFF0C030),
	MECH_GUNMETAL(Style.MECH, "netherite_scrap", 0xFF5C6068, 0xFFC8743A, 0xFFFF4A3A, 0xFFF08A2A),
	MECH_ARCTIC(Style.MECH, "quartz", 0xFFE4E7EC, 0xFFC8743A, 0xFF8AF06A, 0xFF3A8AE8),
	MECH_COPPER(Style.MECH, "copper_ingot", 0xFFC8743A, 0xFFB0603A, 0xFFF0C84A, 0xFF2E2E34),
	SALAMANDER_LAVA(Style.SALAMANDER, "magma_cream", 0xFF2C2828, 0xFF3E302A, 0xFF1C1818, 0xFFFF7A1A),
	SALAMANDER_MAGMA(Style.SALAMANDER, "magma_block", 0xFF3A2422, 0xFF4A2A24, 0xFF241414, 0xFFFF3A1A),
	SALAMANDER_SOUL(Style.SALAMANDER, "soul_soil", 0xFF2A3036, 0xFF343A40, 0xFF181C20, 0xFF4AE8F0),
	SALAMANDER_GILDED(Style.SALAMANDER, "gilded_blackstone", 0xFF2E2A30, 0xFF3A343A, 0xFF1A181C, 0xFFF0C040);

	/** The kind of companion, shown in the wardrobe as one tile that opens its variants. */
	public enum Style {
		MUSHROOM(Hat.Group.ALL_YEAR, Movement.WALK), CLOUD(Hat.Group.ALL_YEAR, Movement.HOVER),
		DRAGON(Hat.Group.ALL_YEAR, Movement.FLY), OWL(Hat.Group.ALL_YEAR, Movement.FLY),
		GOLEM(Hat.Group.ALL_YEAR, Movement.WALK), RED_PANDA(Hat.Group.ALL_YEAR, Movement.WALK),
		TURTLE(Hat.Group.ALL_YEAR, Movement.WALK), KITTEN(Hat.Group.ALL_YEAR, Movement.HOP),
		SHADOW_WOLF(Hat.Group.ALL_YEAR, Movement.WALK), HEDGEHOG(Hat.Group.ALL_YEAR, Movement.WALK),
		PENGUIN(Hat.Group.ALL_YEAR, Movement.WALK), MECH(Hat.Group.ALL_YEAR, Movement.WALK),
		SALAMANDER(Hat.Group.ALL_YEAR, Movement.WALK),
		PUMPKIN(Hat.Group.SEASON, Movement.WALK), SNOWMAN(Hat.Group.WINTER, Movement.HOP),
		BUNNY(Hat.Group.EASTER, Movement.HOP);

		/** Seasonal kinds can only be earned in their months; once earned, they come along all year. */
		public final Hat.Group group;
		public final Movement movement;

		Style(Hat.Group group, Movement movement) {
			this.group = group;
			this.movement = movement;
		}

		public Component title() {
			return Component.translatable("cobblecomfort.companion.style." + name().toLowerCase(Locale.ROOT));
		}
	}

	/** How it gets about. */
	public enum Movement {
		/** Walks on the ground, hops up steps. */
		WALK,
		/** Hops along on the ground. */
		HOP,
		/** Floats a little above the ground. */
		HOVER,
		/** Flies at shoulder height, lands to rest. */
		FLY
	}

	/** Something small a companion wears; bows and collars come in the 16 dye colours. */
	public enum Accessory {
		NONE, BOW, COLLAR, FLOWER, CROWN;

		public boolean dyed() {
			return this == BOW || this == COLLAR;
		}

		public Component title() {
			return Component.translatable("cobblecomfort.companion.accessory." + name().toLowerCase(Locale.ROOT));
		}

		public static Accessory byId(int id) {
			Accessory[] all = values();
			return id >= 0 && id < all.length ? all[id] : NONE;
		}

		public static Accessory byKey(String key) {
			for (Accessory accessory : values()) {
				if (accessory.name().toLowerCase(Locale.ROOT).equals(key)) {
					return accessory;
				}
			}
			return NONE;
		}
	}

	/** Longest name a companion can be given. */
	public static final int MAX_NAME = 16;

	public final Style style;
	private final Identifier icon;
	/**
	 * Its colours by kind. Mushroom: cap, dots, stem, face. Cloud: puffs, underside, face, (unused). Dragon: scales, belly, wings,
	 * eyes. Owl: feathers, face and belly, dark, eyes. Golem: stone, patches, light stone, eyes. Pumpkin: orange, ribs,
	 * vine, glow. Snowman: snow, shade, scarf, nose. Bunny: fur, inner ears, egg, egg stripe. Red panda: fur, legs and
	 * belly, mask, tail rings. Turtle: skin, shell, shell plates, belly.
	 * Kitten: fur, stripes (calico: orange patch), muzzle and paws (calico: black patch), eyes.
	 * Shadow wolf: fur, dark fur, smoke, eyes and glowing marks.
	 * Hedgehog: spines, face and belly, spine tips, what it carries (berry, flower, leaf). Penguin chick: down, head, face
	 * mask, beak and feet. Mini mech: metal, copper (going green with time), visor light, paint and warning stripes.
	 * Lava salamander: basalt scales, belly, crest, the glow in its cracks and eyes.
	 */
	public final int main, second, third, accent;
	private ItemStack stack;

	Companion(Style style, String icon, int main, int second, int third, int accent) {
		this.style = style;
		this.icon = Identifier.withDefaultNamespace(icon);
		this.main = main;
		this.second = second;
		this.third = third;
		this.accent = accent;
	}

	/** Yours from the start: everything but the seasonal ones (those are earned by playing in their months). */
	public boolean free() {
		return !style.group.seasonal();
	}

	/** Whether it can be earned now: seasonal ones only in their months. Once earned, they come along all year. */
	public boolean inSeason() {
		return style.group.inSeason();
	}

	/** Earned by playing in its months (seasonal) or yours from the start. */
	public Hat.Unlock unlock() {
		return Hat.Unlock.free();
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component title() {
		return Component.translatable("cobblecomfort.companion." + key());
	}

	/** An item that stands for it (the message when it is earned, the outfit list). */
	public ItemStack icon() {
		if (stack == null) {
			stack = new ItemStack(BuiltInRegistries.ITEM.getValue(icon));
		}
		return stack;
	}

	/** The variants of a kind, in their order. */
	public static List<Companion> variants(Style style) {
		List<Companion> out = new ArrayList<>();
		for (Companion companion : values()) {
			if (companion.style == style) {
				out.add(companion);
			}
		}
		return out;
	}

	public static Companion byId(int id) {
		Companion[] all = values();
		return id >= 0 && id < all.length ? all[id] : null;
	}

	public static Companion byKey(String key) {
		for (Companion companion : values()) {
			if (companion.key().equals(key)) {
				return companion;
			}
		}
		return null;
	}

	/** A name as it may be shown: no formatting codes or control characters, trimmed, at most {@link #MAX_NAME} long. */
	public static String cleanName(String name) {
		if (name == null) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < name.length() && out.length() < MAX_NAME; i++) {
			char ch = name.charAt(i);
			if (ch == '§') {
				i++;
			} else if (!Character.isISOControl(ch)) {
				out.append(ch);
			}
		}
		return out.toString().strip();
	}

	/** What a player's companion looks like: the kind, its name ("" none), and what it wears in which dye colour. */
	public record Look(Companion companion, String name, Accessory accessory, int color) {
	}
}
