package dev.cobblecomfort.features.lightoverlay;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Whether the light at a position lets monsters spawn, following the vanilla rule: the block light must not exceed
 * the dimension's limit, and the combined light (with the sky darkened at night) must pass the dimension's light test.
 */
public enum SpawnLight {
	/** Dark enough at any time of day. */
	ALWAYS,
	/** Only dark enough at night or in a thunderstorm. */
	NIGHT,
	/** Too bright for monsters. */
	SAFE;

	/** How much the sky light drops at midnight. */
	private static final int NIGHT_SKY_DARKEN = 11;

	public static SpawnLight at(Level level, BlockPos pos) {
		DimensionType dimension = level.dimensionType();
		int block = level.getBrightness(LightLayer.BLOCK, pos);
		if (block > dimension.monsterSpawnBlockLightLimit()) {
			return SAFE;
		}
		int sky = dimension.hasSkyLight() ? level.getBrightness(LightLayer.SKY, pos) : 0;
		int darkest = dimension.monsterSpawnLightTest().maxInclusive();
		if (Math.max(block, sky) <= darkest) {
			return ALWAYS;
		}
		return Math.max(block, sky - NIGHT_SKY_DARKEN) <= darkest ? NIGHT : SAFE;
	}
}
