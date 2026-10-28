package dev.cobblecomfort.features.hotbar;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum SlotNumberMode implements NameableEnum {
	OFF,
	/** Numbers fade in on a slot change and fade out again after a moment. */
	WHILE_SWITCHING,
	ALWAYS;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.slot_numbers." + name().toLowerCase(Locale.ROOT));
	}
}
