package dev.cobblecomfort.features.wardrobe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * A pin worn on the chest. Free ones can always be worn; the others are earned with a vanilla advancement. The order is
 * the badge's id when shared with other players and its place in {@code textures/misc/badges.png}: only ever append.
 * A pin has two layers: its disc and a symbol in front, a vanilla item where one shows it, otherwise its own pixel art.
 */
public enum Badge {
	HEART(null, null),
	STAR(null, "nether_star"),
	FLOWER(null, "poppy"),
	MUSHROOM(null, "red_mushroom"),
	CREEPER(null, null),
	PAW(null, null),
	MOON(null, null),
	LIGHTNING(null, null),
	SMILE(null, null),
	NOTE(null, null),
	DIAMOND("story/mine_diamond", "diamond"),
	NETHER("story/enter_the_nether", null),
	END("story/enter_the_end", "ender_eye"),
	DRAGON("end/kill_dragon", null),
	WITHER("nether/summon_wither", null),
	ELYTRA("end/elytra", "elytra"),
	TOTEM("adventure/totem_of_undying", "totem_of_undying"),
	NETHERITE("nether/netherite_armor", "netherite_ingot"),
	HERO("adventure/hero_of_the_village", "emerald"),
	BEACON("nether/create_beacon", null),
	HUNTER("adventure/kill_a_mob", "iron_sword"),
	TAME("husbandry/tame_an_animal", "bone");

	/** How many pins fit on the chest. */
	public static final int MAX_WORN = 3;
	public static final int CELL = 12;
	public static final int COLUMNS = 6;
	public static final int ATLAS_WIDTH = 72;
	public static final int ATLAS_HEIGHT = 96;
	/** The discs fill the top half of the atlas, the symbols (8x8 in the middle of their cell) the bottom half. */
	public static final int SYMBOL_ROWS = 4;
	/** How many pin pixels a vanilla item symbol spans: all inside the rim, as its sprite has a transparent margin. */
	public static final int ITEM_PIXELS = 10;
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("cobblecomfort", "textures/misc/badges.png");

	private final Identifier advancement;
	private final Identifier item;
	private ItemStack stack;

	Badge(String advancement, String item) {
		this.advancement = advancement == null ? null : Identifier.withDefaultNamespace(advancement);
		this.item = item == null ? null : Identifier.withDefaultNamespace(item);
	}

	/** The vanilla item shown as its symbol, or empty if it has its own. */
	public ItemStack item() {
		if (item == null) {
			return ItemStack.EMPTY;
		}
		if (stack == null) {
			stack = new ItemStack(BuiltInRegistries.ITEM.getValue(item));
		}
		return stack;
	}

	/** The advancement that earns it, or null if it is free. */
	public Identifier advancement() {
		return advancement;
	}

	public boolean free() {
		return advancement == null;
	}

	public String key() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public Component title() {
		return Component.translatable("cobblecomfort.badge." + key());
	}

	/** Its disc's cell in the atlas, in pixels. */
	public int u() {
		return ordinal() % COLUMNS * CELL;
	}

	public int v() {
		return ordinal() / COLUMNS * CELL;
	}

	/** Where its own symbol is: right below the discs. */
	public int symbolV() {
		return v() + SYMBOL_ROWS * CELL;
	}

	public static Badge byId(int id) {
		Badge[] all = values();
		return id >= 0 && id < all.length ? all[id] : null;
	}

	public static Badge byKey(String key) {
		for (Badge badge : values()) {
			if (badge.key().equals(key)) {
				return badge;
			}
		}
		return null;
	}
}
