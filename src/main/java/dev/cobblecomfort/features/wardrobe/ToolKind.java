package dev.cobblecomfort.features.wardrobe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * The kinds of weapons and tools a charm can be chosen for, each with where on the item the charm is tied. The order
 * is the kind's id when shared with other players: only ever append.
 */
public enum ToolKind {
	SWORD(Items.DIAMOND_SWORD, 4.5f, 4.8f, 9.5f, 9.5f),
	AXE(Items.IRON_AXE, 6.5f, 6.5f, 8.5f, 8.5f),
	PICKAXE(Items.IRON_PICKAXE, 9.5f, 9.5f, 10.5f, 10.5f),
	SHOVEL(Items.IRON_SHOVEL, 8f, 8f, 9.5f, 9.5f),
	HOE(Items.IRON_HOE, 9.5f, 9.5f, 10.5f, 10.5f),
	SPEAR(Items.IRON_SPEAR, 9f, 9f, 11.5f, 11.5f),
	MACE(Items.MACE, 5.5f, 5.5f, 8f, 8f),
	TRIDENT(Items.TRIDENT, 8f, 8f, 9.5f, 9.5f),
	BOW(Items.BOW, 5f, 12.5f, 8.5f, 14.5f),
	CROSSBOW(Items.CROSSBOW, 5.5f, 10.5f, 6.5f, 11f),
	FISHING_ROD(Items.FISHING_ROD, 10f, 10f, 11.5f, 11.5f);

	/** The item that stands for the kind (the wardrobe's tiles and buttons). */
	private final Item item;
	private ItemStack icon;
	/**
	 * Where the charm is tied, as a share of a flat 16 × 16 item from its bottom left (given in its pixels): where the
	 * handle meets the head, blade or guard, not at the end of the handle in the fist.
	 */
	public final float anchorX;
	public final float anchorY;
	/**
	 * Where it is tied in first person (also in Punchy's arm): the item is big there and only its upper part is in view,
	 * so further up the blade or the head, where the charm doesn't hang below the screen.
	 */
	public final float nearX;
	public final float nearY;

	ToolKind(Item icon, float anchorX, float anchorY, float nearX, float nearY) {
		this.item = icon;
		this.anchorX = anchorX / 16f;
		this.anchorY = anchorY / 16f;
		this.nearX = nearX / 16f;
		this.nearY = nearY / 16f;
	}

	/**
	 * Where the charm is tied unless moved in the charm editor: in item pixels across and up a flat 16 × 16 item from its
	 * bottom left, and towards its front from the middle.
	 */
	public Vector3f defaultSpot(boolean firstPerson) {
		return firstPerson ? new Vector3f(nearX * 16f, nearY * 16f, 0f) : new Vector3f(anchorX * 16f, anchorY * 16f, 0f);
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Component title() {
		return Component.translatable("cobblecomfort.tool_kind." + key());
	}

	public ItemStack icon() {
		if (icon == null) {
			icon = new ItemStack(item);
		}
		return icon;
	}

	/** The kind of weapon or tool a stack is, or null: by its tags, else by its name, so modded tools count too. */
	public static ToolKind of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		if (stack.is(ItemTags.SWORDS)) {
			return SWORD;
		}
		if (stack.is(ItemTags.AXES)) {
			return AXE;
		}
		if (stack.is(ItemTags.PICKAXES)) {
			return PICKAXE;
		}
		if (stack.is(ItemTags.SHOVELS)) {
			return SHOVEL;
		}
		if (stack.is(ItemTags.HOES)) {
			return HOE;
		}
		if (stack.is(ItemTags.SPEARS)) {
			return SPEAR;
		}
		if (stack.is(Items.MACE)) {
			return MACE;
		}
		if (stack.is(Items.TRIDENT)) {
			return TRIDENT;
		}
		if (stack.is(Items.BOW)) {
			return BOW;
		}
		if (stack.is(Items.CROSSBOW)) {
			return CROSSBOW;
		}
		if (stack.is(Items.FISHING_ROD)) {
			return FISHING_ROD;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (ToolKind kind : values()) {
			if (path.endsWith("_" + kind.key())) {
				return kind;
			}
		}
		return null;
	}

	public static ToolKind byId(int id) {
		ToolKind[] all = values();
		return id >= 0 && id < all.length ? all[id] : null;
	}
}
