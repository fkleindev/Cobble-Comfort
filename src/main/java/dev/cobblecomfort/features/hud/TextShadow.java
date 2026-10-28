package dev.cobblecomfort.features.hud;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum TextShadow implements NameableEnum {
	VANILLA, ON, OFF;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.text_shadow." + name().toLowerCase(Locale.ROOT));
	}
}
