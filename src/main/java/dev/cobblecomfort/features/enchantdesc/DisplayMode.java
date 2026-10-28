package dev.cobblecomfort.features.enchantdesc;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum DisplayMode implements NameableEnum {
	/** Details only while Shift is held. */
	SHIFT,
	/** Details on enchanted books always, on other items only with Shift. */
	BOOKS_ALWAYS,
	/** Details always. */
	ALWAYS;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.enchant_display." + name().toLowerCase(Locale.ROOT));
	}
}
