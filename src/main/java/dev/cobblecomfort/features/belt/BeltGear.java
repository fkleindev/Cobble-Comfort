package dev.cobblecomfort.features.belt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * The weapons on a belt: a melee weapon on the left hip, a bow or crossbow on the right with a quiver of the arrows it
 * would shoot (and how many arrows there are). Any of them may be null.
 */
public record BeltGear(GearItem melee, GearItem ranged, GearItem arrow, int arrows) {
	public static final BeltGear NONE = new BeltGear(null, null, null, 0);

	public boolean empty() {
		return melee == null && ranged == null;
	}

	/** What this player carries in their hotbar, apart from what is in their hand. */
	public static BeltGear of(Player player) {
		Inventory inventory = player.getInventory();
		ItemStack melee = ItemStack.EMPTY;
		int meleeRank = Integer.MAX_VALUE;
		ItemStack ranged = ItemStack.EMPTY;
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (slot == inventory.getSelectedSlot()) {
				continue;
			}
			ItemStack stack = inventory.getItem(slot);
			int rank = meleeRank(stack);
			if (rank < meleeRank) {
				melee = stack;
				meleeRank = rank;
			}
			if (ranged.isEmpty() && stack.getItem() instanceof ProjectileWeaponItem) {
				ranged = stack;
			}
		}
		GearItem arrow = null;
		int arrows = 0;
		if (!ranged.isEmpty()) {
			ItemStack ammo = player.getProjectile(ranged);
			if (ammo.getItem() instanceof ArrowItem) {
				arrow = GearItem.of(ammo);
			}
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (stack.getItem() instanceof ArrowItem) {
					arrows += stack.getCount();
				}
			}
		}
		return new BeltGear(GearItem.of(melee), GearItem.of(ranged), arrow, arrows);
	}

	/** Swords first, then spears, maces and axes; anything else isn't worn. */
	private static int meleeRank(ItemStack stack) {
		if (stack.is(ItemTags.SWORDS)) {
			return 0;
		}
		if (stack.is(ItemTags.SPEARS)) {
			return 1;
		}
		if (stack.is(Items.MACE)) {
			return 2;
		}
		if (stack.is(ItemTags.AXES)) {
			return 3;
		}
		return Integer.MAX_VALUE;
	}

	public void write(FriendlyByteBuf buf) {
		GearItem.write(buf, melee);
		GearItem.write(buf, ranged);
		GearItem.write(buf, arrow);
		buf.writeVarInt(arrows);
	}

	public static BeltGear read(FriendlyByteBuf buf) {
		if (!buf.isReadable()) {
			return NONE;
		}
		return new BeltGear(GearItem.read(buf), GearItem.read(buf), GearItem.read(buf), buf.readVarInt());
	}
}
