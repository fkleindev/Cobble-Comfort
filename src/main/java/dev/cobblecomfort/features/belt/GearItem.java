package dev.cobblecomfort.features.belt;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.List;
import java.util.Optional;

/**
 * An item worn on the belt, reduced to what its look needs and what can be shared cheaply: the item, a custom model
 * (resource packs, the {@code item_model} component) if it has one, whether it glints, and a potion colour (tipped
 * arrows, -1 for none).
 */
public record GearItem(Identifier item, Identifier model, boolean glint, int color) {
	public static GearItem of(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		if (model != null && model.equals(stack.getPrototype().get(DataComponents.ITEM_MODEL))) {
			model = null;
		}
		PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
		int color = potion == null || potion.equals(PotionContents.EMPTY) ? -1 : potion.getColor();
		return new GearItem(BuiltInRegistries.ITEM.getKey(stack.getItem()), model, stack.hasFoil(), color);
	}

	/** A stack that looks like the worn one; empty if the item is unknown here. */
	public ItemStack toStack() {
		Item type = BuiltInRegistries.ITEM.getValue(item);
		if (type == Items.AIR) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(type);
		if (model != null) {
			stack.set(DataComponents.ITEM_MODEL, model);
		}
		if (glint) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		if (color != -1) {
			stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(color), List.of(), Optional.empty()));
		}
		return stack;
	}

	static void write(FriendlyByteBuf buf, GearItem gear) {
		buf.writeBoolean(gear != null);
		if (gear != null) {
			buf.writeIdentifier(gear.item);
			buf.writeBoolean(gear.model != null);
			if (gear.model != null) {
				buf.writeIdentifier(gear.model);
			}
			buf.writeBoolean(gear.glint);
			buf.writeInt(gear.color);
		}
	}

	static GearItem read(FriendlyByteBuf buf) {
		if (!buf.readBoolean()) {
			return null;
		}
		Identifier item = buf.readIdentifier();
		Identifier model = buf.readBoolean() ? buf.readIdentifier() : null;
		return new GearItem(item, model, buf.readBoolean(), buf.readInt());
	}
}
