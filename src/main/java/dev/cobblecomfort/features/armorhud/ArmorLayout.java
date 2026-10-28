package dev.cobblecomfort.features.armorhud;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum ArmorLayout implements NameableEnum {
	/** A row with the durability below each icon. */
	HORIZONTAL,
	/** A column with the durability to the right of each icon. */
	VERTICAL;

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.armor_layout." + name().toLowerCase(Locale.ROOT));
	}
}
