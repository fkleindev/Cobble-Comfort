package dev.cobblecomfort.features.shulker;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum FillMode implements NameableEnum {
	/** Occupied slots out of all slots. */
	SLOTS,
	/** Items out of what the occupied and free slots could hold. */
	ITEMS;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.fill_mode." + name().toLowerCase(Locale.ROOT));
	}
}
