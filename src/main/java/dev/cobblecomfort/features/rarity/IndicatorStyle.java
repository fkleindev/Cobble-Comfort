package dev.cobblecomfort.features.rarity;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum IndicatorStyle implements NameableEnum {
	/** Soft glow fading towards the slot edges. */
	GLOW,
	/** Evenly tinted slot background. */
	FILL,
	/** Coloured frame along the slot edge. */
	FRAME,
	/** Small triangle in the top left corner. */
	CORNER,
	/** Line along the bottom of the slot. */
	UNDERLINE;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.indicator_style." + name().toLowerCase(Locale.ROOT));
	}
}
