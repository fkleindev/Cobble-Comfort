package dev.cobblecomfort.features.inventoryfx;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum ParticleStyle implements NameableEnum {
	/** Pixels in the colours of the item texture that crumble off and fall. */
	CRUMBS,
	/** Twinkling sparks in the item's rarity colour. */
	SPARKS,
	/** Crumbs for every item, plus sparks for rare items. */
	BOTH;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.particle_style." + name().toLowerCase(Locale.ROOT));
	}
}
