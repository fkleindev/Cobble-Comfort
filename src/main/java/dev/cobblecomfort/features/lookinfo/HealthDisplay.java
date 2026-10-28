package dev.cobblecomfort.features.lookinfo;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum HealthDisplay implements NameableEnum {
	HEARTS, BAR, NUMBER, OFF;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.health_display." + name().toLowerCase(Locale.ROOT));
	}
}
