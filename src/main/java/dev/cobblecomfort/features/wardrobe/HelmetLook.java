package dev.cobblecomfort.features.wardrobe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

/**
 * The helmet under a hat, as the hat's helmet part shows it: two colours woven or tiled in a pattern, whether it glints
 * (enchanted), and the colour of its armour trim (or 0).
 */
public record HelmetLook(Pattern pattern, int light, int dark, boolean glint, int trim) {
	public enum Pattern {
		/** Even, the top row lighter (metal). */
		PLAIN,
		/** Stitches: every other tile of the lower row darker (leather). */
		STITCHED,
		/** Checkered links (chainmail) or scutes (turtle shell). */
		CHECKER,
		/** Sparkling: every third tile the light colour (diamond). */
		SPARKLE
	}

	/** The helmet a player wears on their head, or null if none (or something that isn't drawn as armour). */
	public static HelmetLook of(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot() != EquipmentSlot.HEAD || equippable.assetId().isEmpty()) {
			return null;
		}
		ResourceKey<EquipmentAsset> asset = equippable.assetId().get();
		ArmorTrim armorTrim = stack.get(DataComponents.TRIM);
		int trim = 0;
		if (armorTrim != null) {
			TextColor color = armorTrim.material().value().description().getStyle().getColor();
			trim = color == null ? 0xFFE0E0E0 : 0xFF000000 | color.getValue();
		}
		boolean glint = stack.hasFoil();
		if (asset == EquipmentAssets.LEATHER) {
			int dye = 0xFF000000 | DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR);
			return new HelmetLook(Pattern.STITCHED, dye, ARGB.scaleRGB(dye, 0.62f), glint, trim);
		}
		if (asset == EquipmentAssets.CHAINMAIL) {
			return new HelmetLook(Pattern.CHECKER, 0xFFB4B8C0, 0xFF5A5E66, glint, trim);
		}
		if (asset == EquipmentAssets.IRON) {
			return new HelmetLook(Pattern.PLAIN, 0xFFF0F0F0, 0xFFBCBCBC, glint, trim);
		}
		if (asset == EquipmentAssets.GOLD) {
			return new HelmetLook(Pattern.PLAIN, 0xFFFFF26A, 0xFFE8B524, glint, trim);
		}
		if (asset == EquipmentAssets.DIAMOND) {
			return new HelmetLook(Pattern.SPARKLE, 0xFFC8FFF8, 0xFF3CD6C8, glint, trim);
		}
		if (asset == EquipmentAssets.NETHERITE) {
			return new HelmetLook(Pattern.PLAIN, 0xFF6A5E62, 0xFF3A3236, glint, trim);
		}
		if (asset == EquipmentAssets.COPPER) {
			return new HelmetLook(Pattern.PLAIN, 0xFFF0A07A, 0xFFC0643C, glint, trim);
		}
		if (asset == EquipmentAssets.TURTLE_SCUTE) {
			return new HelmetLook(Pattern.CHECKER, 0xFF5CB040, 0xFF2E7A2A, glint, trim);
		}
		// Helmets from other mods: a neutral grey.
		return new HelmetLook(Pattern.PLAIN, 0xFFC8C8C8, 0xFF8A8A8A, glint, trim);
	}

	/** The colour of a tile of the helmet part: {@code i} along it, {@code row} 0 at the top. */
	public int tile(int i, int row) {
		return switch (pattern) {
			case PLAIN -> row == 0 ? light : dark;
			case STITCHED -> row == 0 || i % 2 == 1 ? light : dark;
			case CHECKER -> (i + row) % 2 == 0 ? light : dark;
			case SPARKLE -> i % 3 == row ? light : dark;
		};
	}
}
