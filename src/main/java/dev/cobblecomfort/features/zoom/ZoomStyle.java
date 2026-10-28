package dev.cobblecomfort.features.zoom;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum ZoomStyle implements NameableEnum {
	/** Plain zoom. */
	NORMAL,
	/** Black letterbox bars slide in at the top and bottom of the screen. */
	CINEMATIC;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.zoom_style." + name().toLowerCase(Locale.ROOT));
	}
}
