package dev.cobblecomfort.features.petplush;

import dev.cobblecomfort.features.pets.PetEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;

/**
 * Which plush hangs on a player's back: a wolf or a cat, its variant id (e.g. "minecraft:ashen") and collar colour id
 * (-1 for none). This is all that is saved and shared; textures are looked up from it by each client.
 */
public record PetPlush(String type, String variant, int collar) {
	public static final String WOLF = "wolf";
	public static final String CAT = "cat";

	public boolean wolf() {
		return WOLF.equals(type);
	}

	public DyeColor collarColor() {
		return collar < 0 ? null : DyeColor.byId(collar);
	}

	/** The plush of a live wolf or cat, or null for other entities. */
	public static PetPlush of(Entity entity) {
		if (entity instanceof Wolf wolf) {
			return new PetPlush(WOLF, key(wolf.get(DataComponents.WOLF_VARIANT)), wolf.isTame() ? wolf.getCollarColor().getId() : -1);
		}
		if (entity instanceof Cat cat) {
			return new PetPlush(CAT, key(cat.get(DataComponents.CAT_VARIANT)), cat.isTame() ? cat.getCollarColor().getId() : -1);
		}
		return null;
	}

	/** The plush of a remembered pet, or null if it is no wolf or cat (or was remembered before variants were). */
	public static PetPlush of(PetEntry pet) {
		String type = switch (pet.type) {
			case "minecraft:wolf" -> WOLF;
			case "minecraft:cat" -> CAT;
			default -> null;
		};
		return type == null || pet.variant.isEmpty() ? null : new PetPlush(type, pet.variant, pet.collar);
	}

	private static String key(Holder<?> holder) {
		return holder == null ? "" : holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
	}
}
