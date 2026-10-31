package dev.cobblecomfort.features.wardrobe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Something worn round the neck, in one of its colours, over a chestplate if there is one. The autumn and Halloween
 * ones can only be worn in October and November. All colours of a kind are earned together. The order is the piece's id when shared with other players: only ever append.
 */
public enum Neck {
	SCARF_STRIPED(Style.SCARF, Hat.Unlock.biome("snowy_plains"), "red_wool",
			new int[]{0xFFC8302A, 0xFFF0EEE6, 0xFFF0EEE6}, 0, 0),
	SCARF_GREEN(Style.SCARF, Hat.Unlock.biome("snowy_plains"), "green_wool",
			new int[]{0xFF3A7A3A, 0xFF2E642E, 0xFF8AC86A}, 0, 0),
	SCARF_PLAID(Style.SCARF, Hat.Unlock.biome("snowy_plains"), "blue_wool",
			new int[]{0xFF2A4A8A, 0xFF1A1A22, 0xFFC8302A}, 0, 0),
	NECKLACE_HEART(Style.NECKLACE, Hat.Unlock.item("gold_ingot"), "gold_nugget",
			new int[]{0xFFF2D25A, 0xFFC8A030}, 0xFFD82A3A, 0xFFF47A8A),
	NECKLACE_EMERALD(Style.NECKLACE, Hat.Unlock.item("gold_ingot"), "emerald",
			new int[]{0xFFE4E8EE, 0xFFB0B6C0}, 0xFF2CC85A, 0xFFA8F0C0),
	NECKLACE_PEARL(Style.NECKLACE, Hat.Unlock.item("gold_ingot"), "ender_pearl",
			new int[]{0xFFB0B6C0, 0xFF80868F}, 0xFF1E6A5E, 0xFF3AB8A0),
	NECKLACE_TOTEM(Style.NECKLACE, Hat.Unlock.item("gold_ingot"), "totem_of_undying",
			new int[]{0xFFF2D25A, 0xFFC8A030}, 0xFFE8C040, 0xFF3A8A3A),
	NECKLACE_COMPASS(Style.NECKLACE, Hat.Unlock.item("gold_ingot"), "compass",
			new int[]{0xFFE4E8EE, 0xFFB0B6C0}, 0xFF8A8A92, 0xFFD82A2A),
	BOWTIE_BLACK(Style.BOWTIE, Hat.Unlock.free(), "black_dye",
			new int[]{0xFF1E1E22, 0xFF141418}, 0, 0),
	BOWTIE_RED(Style.BOWTIE, Hat.Unlock.free(), "red_dye",
			new int[]{0xFFC02A2E, 0xFF961E22}, 0, 0),
	BOWTIE_DOTS(Style.BOWTIE, Hat.Unlock.free(), "blue_dye",
			new int[]{0xFF2A4A9A, 0xFF1E3678}, 0xFFF0F0EC, 0),
	TIE_RED(Style.TIE, Hat.Unlock.free(), "red_dye",
			new int[]{0xFFB82A2E, 0xFF8A1E22}, 0, 0),
	TIE_BLUE(Style.TIE, Hat.Unlock.free(), "blue_dye",
			new int[]{0xFF2A4A8A, 0xFFC0C8D8}, 0, 0),
	TIE_GREEN(Style.TIE, Hat.Unlock.free(), "green_dye",
			new int[]{0xFF2E5A34, 0xFFE8C040}, 0, 0),
	BANDANA_RED(Style.BANDANA, Hat.Unlock.biome("badlands"), "red_dye",
			new int[]{0xFFB82A2A, 0xFF8A1E1E}, 0xFFF0EEE6, 0),
	BANDANA_BLUE(Style.BANDANA, Hat.Unlock.biome("badlands"), "blue_dye",
			new int[]{0xFF2A4A8A, 0xFF1E3668}, 0xFFF0EEE6, 0),
	BANDANA_BLACK(Style.BANDANA, Hat.Unlock.biome("badlands"), "black_dye",
			new int[]{0xFF26262A, 0xFF1A1A1E}, 0xFFE8E8E8, 0),
	LEI_TROPICAL(Style.LEI, Hat.Unlock.biome("beach"), "pink_tulip",
			new int[]{0xFFF47AB8, 0xFFF2D23A, 0xFFF4F4F0}, 0xFFF2902A, 0),
	LEI_ORCHID(Style.LEI, Hat.Unlock.biome("beach"), "allium",
			new int[]{0xFFF4F4F0, 0xFFC88AE8, 0xFFE8D0F4}, 0xFFF2D23A, 0),
	LEI_SUNSET(Style.LEI, Hat.Unlock.biome("beach"), "orange_tulip",
			new int[]{0xFFE83A3A, 0xFFF2902A, 0xFFF2C23A}, 0xFFF4F4F0, 0),
	BELL_BROWN(Style.BELL, Hat.Unlock.item("bell"), "bell",
			new int[]{0xFF7A4A2A, 0xFF5A3418}, 0xFFE8C040, 0xFFC0C0C8),
	BELL_RED(Style.BELL, Hat.Unlock.item("bell"), "bell",
			new int[]{0xFFB02A2E, 0xFF8A1E22}, 0xFFD0D4DC, 0xFFE8C040),
	BELL_BLUE(Style.BELL, Hat.Unlock.item("bell"), "bell",
			new int[]{0xFF2A4A8A, 0xFF1E3668}, 0xFFE8C040, 0xFFC0C0C8),
	BOA_PINK(Style.BOA, Hat.Unlock.item("feather"), "feather",
			new int[]{0xFFF48AC0, 0xFFD86AA0, 0xFFFFB8DC}, 0, 0),
	BOA_WHITE(Style.BOA, Hat.Unlock.item("feather"), "feather",
			new int[]{0xFFF4F4F0, 0xFFD8D8D4, 0xFFFFFFFF}, 0, 0),
	BOA_PURPLE(Style.BOA, Hat.Unlock.item("feather"), "feather",
			new int[]{0xFF7A3AB8, 0xFF5A2A8A, 0xFFA870E0}, 0, 0),
	AUTUMN_SCARF_RUST(Style.LEAF_SCARF, Hat.Unlock.free(), "orange_wool",
			new int[]{0xFFB8562A, 0xFF96441E, 0xFFF2C23A}, 0xFFD8302A, 0xFF7A4A22),
	AUTUMN_SCARF_MOSS(Style.LEAF_SCARF, Hat.Unlock.free(), "green_wool",
			new int[]{0xFF5A6A32, 0xFF465426, 0xFFF2902A}, 0xFFD8302A, 0xFFF2C23A),
	AUTUMN_SCARF_CREAM(Style.LEAF_SCARF, Hat.Unlock.free(), "white_wool",
			new int[]{0xFFE8DCC0, 0xFFCCBE9E, 0xFFD8302A}, 0xFFF2902A, 0xFF8A5A2A),
	HALLOWEEN_PUMPKINS_FIRE(Style.PUMPKIN_CHAIN, Hat.Unlock.item("pumpkin_seeds"), "jack_o_lantern",
			new int[]{0xFFE8862A, 0xFFC0661A, 0xFF4A3420}, 0xFFFFD84A, 0xFF4A7A2A),
	HALLOWEEN_PUMPKINS_SOUL(Style.PUMPKIN_CHAIN, Hat.Unlock.item("pumpkin_seeds"), "soul_lantern",
			new int[]{0xFFE8862A, 0xFFC0661A, 0xFF2A2A30}, 0xFF7AE8F0, 0xFF4A7A2A),
	HALLOWEEN_PUMPKINS_WHITE(Style.PUMPKIN_CHAIN, Hat.Unlock.item("pumpkin_seeds"), "bone_meal",
			new int[]{0xFFF0ECE0, 0xFFD0CABA, 0xFF4A3420}, 0xFFFFB84A, 0xFF5A8A3A),
	HALLOWEEN_VAMPIRE_CLASSIC(Style.VAMPIRE, Hat.Unlock.free(), "redstone",
			new int[]{0xFF1A1A1E, 0xFF2E2E36, 0xFFA8181E}, 0xFFE8C040, 0xFFD8182A),
	HALLOWEEN_VAMPIRE_VIOLET(Style.VAMPIRE, Hat.Unlock.free(), "amethyst_shard",
			new int[]{0xFF3A1A4A, 0xFF52306A, 0xFF141418}, 0xFFC8CCD4, 0xFFB86AF0),
	HALLOWEEN_VAMPIRE_BLOOD(Style.VAMPIRE, Hat.Unlock.free(), "red_dye",
			new int[]{0xFF7A1418, 0xFF9A2228, 0xFF141418}, 0xFF2A2A30, 0xFFE8E8E8),
	HALLOWEEN_BONES_WHITE(Style.BONES, Hat.Unlock.item("bone"), "bone",
			new int[]{0xFFEDE8D8, 0xFFC8C2AE, 0xFF4A3A2A}, 0xFFEDE8D8, 0xFF1A1A1A),
	HALLOWEEN_BONES_WITHER(Style.BONES, Hat.Unlock.item("bone"), "wither_skeleton_skull",
			new int[]{0xFF4A4A50, 0xFF333338, 0xFF1A1A1E}, 0xFF2A2A2E, 0xFF8A1E1E),
	HALLOWEEN_BONES_GOLD(Style.BONES, Hat.Unlock.item("bone"), "gold_nugget",
			new int[]{0xFFF2D25A, 0xFFC8A030, 0xFF3A2A1A}, 0xFFF2D25A, 0xFF1A1A1A);

	/** The kind of piece, shown in the wardrobe as one tile that opens its colours. */
	public enum Style {
		SCARF(Hat.Group.ALL_YEAR), NECKLACE(Hat.Group.ALL_YEAR), BOWTIE(Hat.Group.ALL_YEAR), TIE(Hat.Group.ALL_YEAR),
		BANDANA(Hat.Group.ALL_YEAR), LEI(Hat.Group.ALL_YEAR), BELL(Hat.Group.ALL_YEAR), BOA(Hat.Group.ALL_YEAR),
		LEAF_SCARF(Hat.Group.SEASON), PUMPKIN_CHAIN(Hat.Group.SEASON), VAMPIRE(Hat.Group.SEASON), BONES(Hat.Group.SEASON);

		/** Seasonal kinds can only be worn (and earned) in their months. */
		public final Hat.Group group;

		Style(Hat.Group group) {
			this.group = group;
		}

		public Component title() {
			return Component.translatable("cobblecomfort.neck.style." + name().toLowerCase(Locale.ROOT));
		}
	}

	public final Style style;
	public final Hat.Unlock unlock;
	private final Identifier icon;
	/**
	 * Its colours, by style. Scarf: two stripes (or cloth and shade, or plaid), fringe. Necklace: chain light and dark;
	 * pendant, pendant detail. Bow tie: cloth, shade; dots. Tie: cloth, stripes. Bandana: cloth, shade; pattern. Lei:
	 * three flowers; centres. Bell: collar, shade; bell, buckle. Boa: fluff, shade, light. Leaf scarf: knit, shade,
	 * leaf; two more leaves. Pumpkin chain: pumpkin, shade, cord; glow, stems. Vampire collar: outside, edge, lining;
	 * brooch, gem. Bones: bone, shade, cord; skull, eyes.
	 */
	public final int[] main;
	public final int accent;
	public final int second;
	private ItemStack stack;

	Neck(Style style, Hat.Unlock unlock, String icon, int[] main, int accent, int second) {
		this.style = style;
		this.unlock = unlock;
		this.icon = Identifier.withDefaultNamespace(icon);
		this.main = main;
		this.accent = accent;
		this.second = second;
	}

	public boolean free() {
		return unlock.kind() == Hat.Unlock.Kind.FREE;
	}

	/** Whether it can be worn now: seasonal pieces only in their months. */
	public boolean inSeason() {
		return style.group.inSeason();
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component title() {
		return Component.translatable("cobblecomfort.neck." + key());
	}

	/** An item that stands for it (the message when it is earned, the outfit list). */
	public ItemStack icon() {
		if (stack == null) {
			stack = new ItemStack(BuiltInRegistries.ITEM.getValue(icon));
		}
		return stack;
	}

	/** The colours of a kind, in their order. */
	public static List<Neck> variants(Style style) {
		List<Neck> out = new ArrayList<>();
		for (Neck neck : values()) {
			if (neck.style == style) {
				out.add(neck);
			}
		}
		return out;
	}

	public static Neck byId(int id) {
		Neck[] all = values();
		return id >= 0 && id < all.length ? all[id] : null;
	}

	public static Neck byKey(String key) {
		for (Neck neck : values()) {
			if (neck.key().equals(key)) {
				return neck;
			}
		}
		return null;
	}
}
