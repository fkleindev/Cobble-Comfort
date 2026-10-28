package dev.cobblecomfort.features.lookinfo;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum LookLayout implements NameableEnum {
	/** Rounded box with icon, name, detail lines and mod name, like Jade. */
	PANEL,
	/** A single line without a box. */
	COMPACT;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.look_layout." + name().toLowerCase(Locale.ROOT));
	}
}
