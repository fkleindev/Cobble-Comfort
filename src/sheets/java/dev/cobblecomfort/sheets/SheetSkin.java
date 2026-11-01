package dev.cobblecomfort.sheets;

import net.minecraft.world.entity.player.PlayerSkin;

/** The skin every player wears while the cosmetic sheets are drawn (null: their own). */
public final class SheetSkin {
	public static volatile PlayerSkin skin;
	static volatile boolean slim;

	private SheetSkin() {
	}
}
