package dev.cobblecomfort.features.fullbright;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum FullbrightMethod implements NameableEnum {
	/** Lightmap without shaders, client-side night vision while an Iris shader pack is active. */
	AUTO,
	/** Brightens the vanilla lightmap only; no effect on shader packs. */
	LIGHTMAP,
	/** An invisible client-side night vision effect; also read by shader packs. */
	NIGHT_VISION;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.fullbright_method." + name().toLowerCase(Locale.ROOT));
	}
}
