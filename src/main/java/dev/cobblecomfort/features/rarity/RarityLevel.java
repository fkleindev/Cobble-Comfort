package dev.cobblecomfort.features.rarity;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Rarity;

import java.util.Locale;

/** Mirror of vanilla {@link Rarity} that can be shown in the config screen. */
public enum RarityLevel implements NameableEnum {
	COMMON, UNCOMMON, RARE, EPIC;

	public static RarityLevel of(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> COMMON;
			case UNCOMMON -> UNCOMMON;
			case RARE -> RARE;
			case EPIC -> EPIC;
		};
	}

	public boolean atLeast(RarityLevel other) {
		return ordinal() >= other.ordinal();
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.rarity." + name().toLowerCase(Locale.ROOT));
	}
}
