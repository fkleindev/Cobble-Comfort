package dev.cobblecomfort.features.map;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Whether the minimap is flat or 3D. */
public enum MinimapView implements NameableEnum {
	/** Like the full map: 3D while its 3D view is switched on. */
	FOLLOW_MAP,
	ALWAYS_3D,
	ALWAYS_2D;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.minimap_view." + name().toLowerCase(Locale.ROOT));
	}
}
