package dev.cobblecomfort.features.trade;

import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One remembered offer: what it costs (as seen, with discounts), what it gives and how often it could still be used. */
public final class TradeOffer {
	/** Saved forms of the stacks; costB is null when the offer has a single cost. */
	JsonElement costA, costB, result;
	public int uses;
	public int maxUses;

	transient ItemStack costAStack = ItemStack.EMPTY;
	transient ItemStack costBStack = ItemStack.EMPTY;
	transient ItemStack resultStack = ItemStack.EMPTY;

	public ItemStack costA() {
		return costAStack;
	}

	public ItemStack costB() {
		return costBStack;
	}

	public ItemStack result() {
		return resultStack;
	}

	public boolean outOfStock() {
		return uses >= maxUses;
	}

	/** Whether the villager pays emeralds for items (rather than selling something). */
	public boolean buying() {
		return resultStack.is(Items.EMERALD) && !costAStack.is(Items.EMERALD);
	}

	/** For sorting by price: how many items the offer costs in total. */
	public int price() {
		return costAStack.getCount() + costBStack.getCount();
	}
}
