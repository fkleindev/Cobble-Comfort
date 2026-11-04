package dev.cobblecomfort.features.wardrobe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A charm tied to a weapon or tool, chosen for each {@link ToolKind}. The name tag shows the name of renamed items
 * (and nothing on the others); the rest hang on every item of their kind. The order is the charm's id when shared with
 * other players: only ever append.
 */
public enum ToolCharm {
	NAME_TAG(Style.NAME_TAG, Hat.Unlock.free(), "name_tag", new int[]{0xFFE8DCC0}, 0xFF3B2A1C),
	TASSEL_RED(Style.TASSEL, Hat.Unlock.free(), "red_wool",
			new int[]{0xFFC8282A, 0xFFA01E22}, 0xFFE8C040),
	TASSEL_GOLD(Style.TASSEL, Hat.Unlock.free(), "yellow_wool",
			new int[]{0xFFF2C84A, 0xFFD8A830}, 0xFF8A5A2A),
	TASSEL_BLACK(Style.TASSEL, Hat.Unlock.free(), "black_wool",
			new int[]{0xFF2A2A30, 0xFF1A1A1E}, 0xFFC8282A),
	TASSEL_WHITE(Style.TASSEL, Hat.Unlock.free(), "white_wool",
			new int[]{0xFFF4F4F0, 0xFFDCDCE0}, 0xFF3A6AC8),
	ORE_DIAMOND(Style.ORE, Hat.Unlock.free(), "diamond",
			new int[]{0xFF5DECF5, 0xFF2CB8C8}, 0xFFD8FFFF),
	ORE_EMERALD(Style.ORE, Hat.Unlock.free(), "emerald",
			new int[]{0xFF41F384, 0xFF17A84A}, 0xFFC8FFD8),
	ORE_AMETHYST(Style.ORE, Hat.Unlock.free(), "amethyst_shard",
			new int[]{0xFFB48AF0, 0xFF7A52C8}, 0xFFF0E0FF),
	ORE_LAPIS(Style.ORE, Hat.Unlock.free(), "lapis_lazuli",
			new int[]{0xFF3A62D8, 0xFF1E3A9A}, 0xFFA8C0FF),
	FEATHERS_CHICKEN(Style.FEATHERS, Hat.Unlock.free(), "feather",
			new int[]{0xFFF4F4F0, 0xFFD8D8DC, 0xFFB0B0B4}, 0xFF8A5A2A),
	FEATHERS_PARROT_RED(Style.FEATHERS, Hat.Unlock.free(), "feather",
			new int[]{0xFFE8302A, 0xFF2A6AC8, 0xFFF2D25A}, 0xFF8A5A2A),
	FEATHERS_PARROT_BLUE(Style.FEATHERS, Hat.Unlock.free(), "feather",
			new int[]{0xFF2A7AE8, 0xFFF2D25A, 0xFF2AC8C8}, 0xFF8A5A2A),
	LURE_SALMON(Style.LURE, Hat.Unlock.free(), "salmon",
			new int[]{0xFFA83A32, 0xFFE88A6A, 0xFF6A2A26}, 0xFF1A1A1A),
	LURE_COD(Style.LURE, Hat.Unlock.free(), "cod",
			new int[]{0xFFC8A878, 0xFFE8DCC0, 0xFF8A7050}, 0xFF1A1A1A),
	LURE_TROPICAL(Style.LURE, Hat.Unlock.free(), "tropical_fish",
			new int[]{0xFFE8862A, 0xFFF4F4F0, 0xFF2A6AC8}, 0xFF1A1A1A),
	BELL_COPPER(Style.BELL, Hat.Unlock.free(), "bell",
			new int[]{0xFFD87A48, 0xFFA8522E}, 0xFF3A3A40),
	BELL_GOLD(Style.BELL, Hat.Unlock.free(), "bell",
			new int[]{0xFFF2D25A, 0xFFC8A030}, 0xFF3A3A40),
	GLOW_BERRY(Style.GLOW_BERRY, Hat.Unlock.free(), "glow_berries",
			new int[]{0xFF4A8A2E, 0xFF3A6A24}, 0xFFFFB84A);

	/** The kind of charm, shown in the wardrobe as one tile that opens its variants. */
	public enum Style {
		NAME_TAG(ToolKind.SWORD), TASSEL(ToolKind.SWORD), ORE(ToolKind.PICKAXE), FEATHERS(ToolKind.BOW),
		LURE(ToolKind.FISHING_ROD), BELL(ToolKind.MACE), GLOW_BERRY(ToolKind.PICKAXE);

		/** Seasonal kinds can only be earned in their months; once earned, they can be worn all year. */
		public final Hat.Group group;
		/** The weapon or tool it is shown on where no kind is picked (the cosmetic sheets). */
		public final ToolKind showcase;

		Style(ToolKind showcase) {
			this.group = Hat.Group.ALL_YEAR;
			this.showcase = showcase;
		}

		public Component title() {
			return Component.translatable("cobblecomfort.tool_charm.style." + name().toLowerCase(Locale.ROOT));
		}
	}

	public final Style style;
	public final Hat.Unlock unlock;
	private final Identifier icon;
	/** Its colours: the main ones (body, shade, detail), and one for a binding, eyes or light. */
	public final int[] main;
	public final int accent;
	private ItemStack stack;

	ToolCharm(Style style, Hat.Unlock unlock, String icon, int[] main, int accent) {
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
		return Component.translatable("cobblecomfort.tool_charm." + key());
	}

	/** An item that stands for it (the message when it is earned). */
	public ItemStack icon() {
		if (stack == null) {
			stack = new ItemStack(BuiltInRegistries.ITEM.getValue(icon));
		}
		return stack;
	}

	/** The variants of a kind, in their order. */
	public static List<ToolCharm> variants(Style style) {
		List<ToolCharm> out = new ArrayList<>();
		for (ToolCharm charm : values()) {
			if (charm.style == style) {
				out.add(charm);
			}
		}
		return out;
	}

	public static ToolCharm byId(int id) {
		ToolCharm[] all = values();
		return id >= 0 && id < all.length ? all[id] : null;
	}

	public static ToolCharm byKey(String key) {
		for (ToolCharm charm : values()) {
			if (charm.key().equals(key)) {
				return charm;
			}
		}
		return null;
	}
}
