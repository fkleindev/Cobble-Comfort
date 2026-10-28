package dev.cobblecomfort.features.armorhud;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum DurabilityDisplay implements NameableEnum {
	/** "97%" */
	PERCENT,
	/** "512" */
	REMAINING,
	/** "512/528" */
	FULL,
	/** A small bar under the icon. */
	BAR,
	NONE;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.durability_display." + name().toLowerCase(Locale.ROOT));
	}
}
