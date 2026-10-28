package dev.cobblecomfort.features.hud;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** One of nine reference points on the screen (and on an element), expressed as fractions of width/height. */
public enum Anchor implements NameableEnum {
	TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
	CENTER_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), CENTER_RIGHT(1f, 0.5f),
	BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f);

	public final float fx;
	public final float fy;

	Anchor(float fx, float fy) {
		this.fx = fx;
		this.fy = fy;
	}

	/** Picks the anchor whose screen third contains the given point. */
	public static Anchor forPoint(float x, float y, float width, float height) {
		int col = x < width / 3f ? 0 : x > width * 2f / 3f ? 2 : 1;
		int row = y < height / 3f ? 0 : y > height * 2f / 3f ? 2 : 1;
		return values()[row * 3 + col];
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("cobblecomfort.enum.anchor." + name().toLowerCase(Locale.ROOT));
	}
}
