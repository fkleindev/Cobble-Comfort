package dev.cobblecomfort.features.infohud;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum InfoStyle implements NameableEnum {
	/** Dark rounded panel with item icons. */
	PANEL,
	/** Text with shadow only, no background. */
	TEXT,
	/** Everything in one line with separators. */
	COMPACT;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.info_style." + name().toLowerCase(Locale.ROOT));
	}
}
