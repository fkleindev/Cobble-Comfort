package dev.cobblecomfort.features.wardrobe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Something worn on the face, in one of its colours: one piece over the eyes, one on the nose and mouth and paint on the skin, hidden
 * under a helmet. The seasonal ones can only be earned in their months, then worn all year. All colours of a kind are
 * earned together. The order is the piece's id when shared with other players: only ever append.
 */
public enum Face {
	GLASSES_GOLD(Style.GLASSES, Hat.Unlock.free(), "gold_nugget",
			new int[]{0xFFE8C040, 0xFFB08A28}, 0xFFF4F8FF, 0),
	GLASSES_SILVER(Style.GLASSES, Hat.Unlock.free(), "iron_nugget",
			new int[]{0xFFD0D4DC, 0xFF9AA0AA}, 0xFFF4F8FF, 0),
	GLASSES_BLACK(Style.GLASSES, Hat.Unlock.free(), "black_dye",
			new int[]{0xFF26262C, 0xFF16161A}, 0xFFF4F8FF, 0),
	SUNGLASSES_BLACK(Style.SUNGLASSES, Hat.Unlock.free(), "tinted_glass",
			new int[]{0xFF1E1E22, 0xFF2A2A34, 0xFF50505E}, 0xFFE8ECF4, 0),
	SUNGLASSES_AVIATOR(Style.SUNGLASSES, Hat.Unlock.free(), "gold_ingot",
			new int[]{0xFFE8C040, 0xFF34402A, 0xFF5E7048}, 0xFFE8ECF4, 0),
	SUNGLASSES_PINK(Style.SUNGLASSES, Hat.Unlock.free(), "pink_dye",
			new int[]{0xFFF48AC0, 0xFF4E2442, 0xFF86487A}, 0xFFFFE0F0, 0),
	EYEPATCH_BLACK(Style.EYEPATCH, Hat.Unlock.free(), "black_dye",
			new int[]{0xFF1E1E22, 0xFF141418, 0xFF2E2E34}, 0xFFE8E4D8, 0),
	EYEPATCH_LEATHER(Style.EYEPATCH, Hat.Unlock.free(), "leather",
			new int[]{0xFF7A4A2A, 0xFF5A3418, 0xFF4A3020}, 0xFFC8A070, 0),
	MASQUERADE_GOLD(Style.MASQUERADE, Hat.Unlock.free(), "gold_ingot",
			new int[]{0xFFE8C040, 0xFFB08A28}, 0xFFF4F0E0, 0xFFF4F4F0),
	MASQUERADE_PURPLE(Style.MASQUERADE, Hat.Unlock.free(), "amethyst_shard",
			new int[]{0xFF6A2A9A, 0xFF4E1E74}, 0xFFE8C040, 0xFFF48AC0),
	MASQUERADE_BLACK(Style.MASQUERADE, Hat.Unlock.free(), "black_dye",
			new int[]{0xFF1E1E22, 0xFF121214}, 0xFFC8CCD4, 0xFFD82A3A),
	BANDANA_RED(Style.BANDANA, Hat.Unlock.free(), "red_dye",
			new int[]{0xFFB82A2A, 0xFF8A1E1E}, 0xFFF0EEE6, 0),
	BANDANA_BLUE(Style.BANDANA, Hat.Unlock.free(), "blue_dye",
			new int[]{0xFF2A4A8A, 0xFF1E3668}, 0xFFF0EEE6, 0),
	BANDANA_BLACK(Style.BANDANA, Hat.Unlock.free(), "black_dye",
			new int[]{0xFF26262A, 0xFF1A1A1E}, 0xFFE8E8E8, 0),
	MUSTACHE_CURLED_BROWN(Style.MUSTACHE, Hat.Unlock.free(), "brown_dye",
			new int[]{0xFF5A3A22, 0xFF3E2814}, 0, 0),
	MUSTACHE_CURLED_BLACK(Style.MUSTACHE, Hat.Unlock.free(), "black_dye",
			new int[]{0xFF2C2626, 0xFF181414}, 0, 0),
	MUSTACHE_WALRUS_GREY(Style.MUSTACHE, Hat.Unlock.free(), "light_gray_dye",
			new int[]{0xFFB8B8B4, 0xFF8A8A86}, 0, 0),
	MUSTACHE_WALRUS_GINGER(Style.MUSTACHE, Hat.Unlock.free(), "orange_dye",
			new int[]{0xFFC0602A, 0xFF8E4418}, 0, 0),
	HALLOWEEN_MASK_PUMPKIN(Style.HALLOWEEN_MASK, Hat.Unlock.free(), "carved_pumpkin",
			new int[]{0xFFE8862A, 0xFFC0661A, 0xFF1E1E22}, 0xFFFFD84A, 0xFF4A7A2A),
	HALLOWEEN_MASK_SKULL(Style.HALLOWEEN_MASK, Hat.Unlock.free(), "bone",
			new int[]{0xFFEDE8D8, 0xFFC8C2AE, 0xFF1E1E22}, 0xFF1A1A1A, 0xFF8A8478),
	WINTER_REINDEER_NOSE(Style.REINDEER_NOSE, Hat.Unlock.free(), "redstone",
			new int[]{0xFFE02A2A, 0xFFB01E1E}, 0xFFFFC8C8, 0),
	CARNIVAL_CLOWN_NOSE(Style.CLOWN_NOSE, Hat.Unlock.free(), "red_dye",
			new int[]{0xFFE8242A, 0xFFB01A1E}, 0xFFFFFFFF, 0xFF1E1E22),
	GOGGLES_BRASS(Style.GOGGLES, Hat.Unlock.free(), "gold_nugget",
			new int[]{0xFFD8A848, 0xFF9A7228, 0xFF6A4424, 0xFFE89A3A}, 0xFFFFF0C8, 0xFF8AD0C8),
	GOGGLES_COPPER(Style.GOGGLES, Hat.Unlock.free(), "copper_ingot",
			new int[]{0xFFD8784A, 0xFF9E4E2E, 0xFF3E2A20, 0xFF5AB08A}, 0xFFE8FFF0, 0xFFE8B04A),
	GOGGLES_IRON(Style.GOGGLES, Hat.Unlock.free(), "iron_ingot",
			new int[]{0xFF5A5A62, 0xFF3A3A40, 0xFF2A2420, 0xFF4A8AD8}, 0xFFE0F0FF, 0xFFD84A3A),
	PIPE_WOOD(Style.PIPE, Hat.Unlock.free(), "stick",
			new int[]{0xFF8A5430, 0xFF5E361C, 0xFF2A1E18}, 0xFFC8A060, 0xFFE8862A),
	PIPE_CORNCOB(Style.PIPE, Hat.Unlock.free(), "wheat",
			new int[]{0xFFE8D088, 0xFFC0A458, 0xFF2A1E18}, 0xFF8A6A3A, 0xFFE8862A),
	PIPE_DARK(Style.PIPE, Hat.Unlock.free(), "charcoal",
			new int[]{0xFF3A2A24, 0xFF221814, 0xFF141010}, 0xFFC8B060, 0xFFE8862A),
	BLUSH_PINK(Style.BLUSH, Hat.Unlock.free(), "pink_petals",
			new int[]{0xFFF4A0B8, 0xFFE07898}, 0xFFFFD0DC, 0),
	BLUSH_ROSE(Style.BLUSH, Hat.Unlock.free(), "poppy",
			new int[]{0xFFE06070, 0xFFC04050}, 0xFFF4A0A8, 0),
	BLUSH_PEACH(Style.BLUSH, Hat.Unlock.free(), "orange_tulip",
			new int[]{0xFFF4B088, 0xFFE09068}, 0xFFFFD8C0, 0),
	FRECKLES_BROWN(Style.FRECKLES, Hat.Unlock.free(), "cocoa_beans",
			new int[]{0xFF9A6440, 0xFF7A4A2C}, 0, 0),
	FRECKLES_GINGER(Style.FRECKLES, Hat.Unlock.free(), "orange_dye",
			new int[]{0xFFC87A44, 0xFFA85E30}, 0, 0),
	FRECKLES_DARK(Style.FRECKLES, Hat.Unlock.free(), "brown_dye",
			new int[]{0xFF5A3A26, 0xFF422A1A}, 0, 0),
	WAR_PAINT_RED(Style.WAR_PAINT, Hat.Unlock.free(), "red_dye",
			new int[]{0xFFC02A2A, 0xFF8A1A1A}, 0xFFF0EEE6, 0),
	WAR_PAINT_BLACK(Style.WAR_PAINT, Hat.Unlock.free(), "black_dye",
			new int[]{0xFF1E1E22, 0xFF101012}, 0xFFF0EEE6, 0),
	WAR_PAINT_BLUE(Style.WAR_PAINT, Hat.Unlock.free(), "lapis_lazuli",
			new int[]{0xFF2A5AB0, 0xFF1A3A80}, 0xFFE8F0FF, 0);

	/** Where on the face a piece is worn: one piece each (paint on the skin goes under the others). */
	public enum Place {
		EYES, MOUTH, SKIN;

		public Component title() {
			return Component.translatable("cobblecomfort.wardrobe.face_" + name().toLowerCase(Locale.ROOT));
		}
	}

	/** The kind of piece, shown in the wardrobe as one tile that opens its colours. */
	public enum Style {
		GLASSES(Place.EYES, Hat.Group.ALL_YEAR), SUNGLASSES(Place.EYES, Hat.Group.ALL_YEAR),
		MASQUERADE(Place.EYES, Hat.Group.ALL_YEAR), EYEPATCH(Place.EYES, Hat.Group.ALL_YEAR),
		BANDANA(Place.MOUTH, Hat.Group.ALL_YEAR), MUSTACHE(Place.MOUTH, Hat.Group.ALL_YEAR),
		HALLOWEEN_MASK(Place.EYES, Hat.Group.SEASON), REINDEER_NOSE(Place.MOUTH, Hat.Group.WINTER),
		CLOWN_NOSE(Place.MOUTH, Hat.Group.CARNIVAL), GOGGLES(Place.EYES, Hat.Group.ALL_YEAR), PIPE(Place.MOUTH, Hat.Group.ALL_YEAR),
		BLUSH(Place.SKIN, Hat.Group.ALL_YEAR), FRECKLES(Place.SKIN, Hat.Group.ALL_YEAR), WAR_PAINT(Place.SKIN, Hat.Group.ALL_YEAR);

		public final Place place;
		/** Seasonal kinds can only be earned in their months; once earned, they can be worn all year. */
		public final Hat.Group group;

		Style(Place place, Hat.Group group) {
			this.place = place;
			this.group = group;
		}

		public Component title() {
			return Component.translatable("cobblecomfort.face.style." + name().toLowerCase(Locale.ROOT));
		}

		/** The kinds worn at a place, in their order. */
		public static Style[] at(Place place) {
			return Arrays.stream(values()).filter(style -> style.place == place).toArray(Style[]::new);
		}
	}

	public final Style style;
	public final Hat.Unlock unlock;
	private final Identifier icon;
	/**
	 * Its colours, by style. Glasses: frame, shade; glint. Sunglasses: frame, lens, lens light; glint. Eye patch: patch,
	 * shade, strap; mark. Masquerade mask: mask, shade; trim, feather. Bandana: cloth, shade; pattern. Moustache: hair,
	 * shade. Halloween mask: face, shade, strap; eyes (the pumpkin's glow, the skull's sockets), stalk or cracks.
	 * Reindeer nose: nose, shade; shine. Clown nose: nose, shade; shine, elastic. Goggles: frame, shade, strap, lens; glint,
	 * the loupe's lens. Pipe: bowl, shade, stem; rim, ember. Blush: blush, shade; glow. Freckles: freckle, darker one. War
	 * paint: paint, shade; the light stripes.
	 */
	public final int[] main;
	public final int accent;
	public final int second;
	private ItemStack stack;

	Face(Style style, Hat.Unlock unlock, String icon, int[] main, int accent, int second) {
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
		return Component.translatable("cobblecomfort.face." + key());
	}

	/** An item that stands for it (the message when it is earned, the outfit list). */
	public ItemStack icon() {
		if (stack == null) {
			stack = new ItemStack(BuiltInRegistries.ITEM.getValue(icon));
		}
		return stack;
	}

	/** The colours of a kind, in their order. */
	public static List<Face> variants(Style style) {
		List<Face> out = new ArrayList<>();
		for (Face face : values()) {
			if (face.style == style) {
				out.add(face);
			}
		}
		return out;
	}

	public static Face byId(int id) {
		Face[] all = values();
		return id >= 0 && id < all.length ? all[id] : null;
	}

	public static Face byKey(String key) {
		for (Face face : values()) {
			if (face.key().equals(key)) {
				return face;
			}
		}
		return null;
	}
}
