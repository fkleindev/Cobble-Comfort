package dev.cobblecomfort.features.wardrobe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How totems of undying look: a plush of whoever holds them (also wearing their hat and neck piece), the game's own
 * totem, or a plush of a mob. The mob plushes use the game's own models and textures. The Christmas ones can only be
 * earned in December and January, then held all year. All colours of a kind are earned together. The order is the
 * skin's id: only ever append.
 */
public enum TotemSkin {
	PLAYER(Style.PLAYER, Hat.Unlock.free(), "player_head", null),
	MINI_ME(Style.MINI_ME, Hat.Unlock.free(), "leather_helmet", null),
	CLASSIC(Style.CLASSIC, Hat.Unlock.free(), "totem_of_undying", null),
	ALLAY(Style.ALLAY, Hat.Unlock.advancement("husbandry/allay_deliver_item_to_player"), "amethyst_shard", "allay/allay"),
	AXOLOTL_LUCY(Style.AXOLOTL, Hat.Unlock.advancement("husbandry/kill_axolotl_target"), "axolotl_bucket", "axolotl/axolotl_lucy_baby"),
	AXOLOTL_WILD(Style.AXOLOTL, Hat.Unlock.advancement("husbandry/kill_axolotl_target"), "axolotl_bucket", "axolotl/axolotl_wild_baby"),
	AXOLOTL_GOLD(Style.AXOLOTL, Hat.Unlock.advancement("husbandry/kill_axolotl_target"), "axolotl_bucket", "axolotl/axolotl_gold_baby"),
	AXOLOTL_CYAN(Style.AXOLOTL, Hat.Unlock.advancement("husbandry/kill_axolotl_target"), "axolotl_bucket", "axolotl/axolotl_cyan_baby"),
	AXOLOTL_BLUE(Style.AXOLOTL, Hat.Unlock.advancement("husbandry/kill_axolotl_target"), "axolotl_bucket", "axolotl/axolotl_blue_baby"),
	CREEPER(Style.CREEPER, Hat.Unlock.item("gunpowder"), "gunpowder", "creeper/creeper"),
	FROG_TEMPERATE(Style.FROG, Hat.Unlock.advancement("husbandry/tadpole_in_a_bucket"), "lily_pad", "frog/frog_temperate"),
	FROG_WARM(Style.FROG, Hat.Unlock.advancement("husbandry/tadpole_in_a_bucket"), "lily_pad", "frog/frog_warm"),
	FROG_COLD(Style.FROG, Hat.Unlock.advancement("husbandry/tadpole_in_a_bucket"), "lily_pad", "frog/frog_cold"),
	BEE(Style.BEE, Hat.Unlock.item("honey_bottle"), "honey_bottle", "bee/bee_baby"),
	DRAGON(Style.DRAGON, Hat.Unlock.advancement("end/kill_dragon"), "dragon_head", "enderdragon/dragon"),
	WARDEN(Style.WARDEN, Hat.Unlock.biome("deep_dark"), "sculk", "warden/warden"),
	SNOWMAN(Style.SNOWMAN, Hat.Unlock.free(), "snowball", "snow_golem/snow_golem"),
	GINGERBREAD(Style.GINGERBREAD, Hat.Unlock.free(), "cookie", null);

	/** The kind of totem, shown in the wardrobe as one tile that opens its colours. */
	public enum Style {
		PLAYER(Hat.Group.ALL_YEAR), MINI_ME(Hat.Group.ALL_YEAR), CLASSIC(Hat.Group.ALL_YEAR), ALLAY(Hat.Group.ALL_YEAR),
		AXOLOTL(Hat.Group.ALL_YEAR), CREEPER(Hat.Group.ALL_YEAR), FROG(Hat.Group.ALL_YEAR), BEE(Hat.Group.ALL_YEAR),
		DRAGON(Hat.Group.ALL_YEAR), WARDEN(Hat.Group.ALL_YEAR), SNOWMAN(Hat.Group.WINTER), GINGERBREAD(Hat.Group.WINTER);

		/** Seasonal kinds can only be earned in their months; once earned, they can be held all year. */
		public final Hat.Group group;

		Style(Hat.Group group) {
			this.group = group;
		}

		public Component title() {
			return Component.translatable("cobblecomfort.totem.style." + name().toLowerCase(Locale.ROOT));
		}
	}

	public final Style style;
	public final Hat.Unlock unlock;
	private final Identifier icon;
	/** The game's texture of the mob, or null where the plush isn't a mob. */
	public final Identifier texture;
	private ItemStack stack;

	TotemSkin(Style style, Hat.Unlock unlock, String icon, String texture) {
		this.style = style;
		this.unlock = unlock;
		this.icon = Identifier.withDefaultNamespace(icon);
		this.texture = texture == null ? null : Identifier.withDefaultNamespace("textures/entity/" + texture + ".png");
	}

	/** Yours from the start: free and not seasonal (those are earned by playing in their months). */
	public boolean free() {
		return unlock.kind() == Hat.Unlock.Kind.FREE && !style.group.seasonal();
	}

	/** Whether it can be earned now: seasonal ones only in their months. Once earned, they can be held all year. */
	public boolean inSeason() {
		return style.group.inSeason();
	}

	/** Whether it moves on its own (wings), so its picture can't be kept between frames. */
	public boolean animated() {
		return style == Style.ALLAY || style == Style.BEE || style == Style.DRAGON || style == Style.WARDEN;
	}

	/** Its colour, for the dot that stands for it under its kind's tile. */
	public int swatch() {
		return switch (this) {
			case AXOLOTL_LUCY -> 0xFFF2A0C8;
			case AXOLOTL_WILD -> 0xFF8A6A4A;
			case AXOLOTL_GOLD -> 0xFFF2C23A;
			case AXOLOTL_CYAN -> 0xFFC8F0F4;
			case AXOLOTL_BLUE -> 0xFF5A6AE8;
			case FROG_TEMPERATE -> 0xFFD8843A;
			case FROG_WARM -> 0xFFE8DCC0;
			case FROG_COLD -> 0xFF6AA05A;
			default -> 0xFFE8C040;
		};
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component title() {
		return Component.translatable("cobblecomfort.totem." + key());
	}

	/** An item that stands for it (the message when it is earned). */
	public ItemStack icon() {
		if (stack == null) {
			stack = new ItemStack(BuiltInRegistries.ITEM.getValue(icon));
		}
		return stack;
	}

	/** The colours of a kind, in their order. */
	public static List<TotemSkin> variants(Style style) {
		List<TotemSkin> out = new ArrayList<>();
		for (TotemSkin skin : values()) {
			if (skin.style == style) {
				out.add(skin);
			}
		}
		return out;
	}

	public static TotemSkin byId(int id) {
		TotemSkin[] all = values();
		return id >= 0 && id < all.length ? all[id] : null;
	}

	public static TotemSkin byKey(String key) {
		for (TotemSkin skin : values()) {
			if (skin.key().equals(key)) {
				return skin;
			}
		}
		return null;
	}
}
