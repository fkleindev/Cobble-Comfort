package dev.cobblecomfort.features.hud.anim;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** How an auto-hidden element appears and disappears. */
public enum ShowAnimation implements NameableEnum {
	NONE(false, false, false),
	FADE(true, false, false),
	SLIDE(false, true, false),
	SCALE(false, false, true),
	FADE_SLIDE(true, true, false),
	FADE_SCALE(true, false, true);

	public final boolean fade;
	public final boolean slide;
	public final boolean scale;

	ShowAnimation(boolean fade, boolean slide, boolean scale) {
		this.fade = fade;
		this.slide = slide;
		this.scale = scale;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.show_animation." + name().toLowerCase(Locale.ROOT));
	}
}
