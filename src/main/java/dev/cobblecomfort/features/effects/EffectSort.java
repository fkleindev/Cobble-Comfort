package dev.cobblecomfort.features.effects;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum EffectSort implements NameableEnum {
	LONGEST_FIRST,
	SHORTEST_FIRST,
	NAME;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.effect_sort." + name().toLowerCase(Locale.ROOT));
	}
}
