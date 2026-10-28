package dev.cobblecomfort.features.invtools;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum SortOrder implements NameableEnum {
	/** Like the creative inventory: building blocks, then tools, food, ... */
	CREATIVE,
	NAME,
	/** Biggest stacks first. */
	COUNT;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.sort_order." + name().toLowerCase(Locale.ROOT));
	}
}
