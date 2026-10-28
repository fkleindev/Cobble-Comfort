package dev.cobblecomfort.features.shulker;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** The modifier key that switches the tooltip from the compact summary to the full grid. */
public enum PreviewKey implements NameableEnum {
	SHIFT,
	CONTROL;

	public boolean isDown() {
		Minecraft mc = Minecraft.getInstance();
		return this == SHIFT ? mc.hasShiftDown() : mc.hasControlDown();
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.preview_key." + name().toLowerCase(Locale.ROOT));
	}
}
