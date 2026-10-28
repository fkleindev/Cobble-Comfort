package dev.cobblecomfort.features.effects;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum EffectStyle implements NameableEnum {
	/** One row per effect: icon, name, time and a progress bar. */
	LIST,
	/** Icons with a ring that runs out, time below. */
	RINGS,
	/** Small icons with time and level below, like vanilla. */
	COMPACT;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.effect_style." + name().toLowerCase(Locale.ROOT));
	}
}
