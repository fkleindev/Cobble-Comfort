package dev.cobblecomfort.features.map.seed;

import dev.cobblecomfort.features.map.data.BlockColorCache;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Approximate surface colours for calculated terrain, from the biome (sand, snow, grass, forest, ...) and height. */
final class SeedColors {
	static final int END_STONE = 0xFFDBDC9E;
	private static final int SAND = 0xFFDBD3A0;
	private static final int RED_SAND = 0xFFB5653A;
	private static final int SNOW = 0xFFEFF4F7;
	private static final int STONE = 0xFF858585;
	private static final int MYCELIUM = 0xFF857784;
	private static final int GRASS_BASE = 0xFF9C9C9C;
	private static final int LEAVES_BASE = 0xFF7C7C7C;
	private static final int WATER_BASE = 0xFFB4B4B4;
	private static final int MOUNTAIN_STONE_HEIGHT = 170;
	private static final float WATER_DEPTH_DARKEN = 0.03f;

	private SeedColors() {
	}

	private static String path(Holder<Biome> biome) {
		return biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
	}

	static int land(Holder<Biome> holder, int x, int z, int height) {
		String path = path(holder);
		Biome biome = holder.value();
		if (path.contains("badlands")) {
			return RED_SAND;
		}
		if (path.contains("desert") || path.equals("beach")) {
			return SAND;
		}
		if (path.contains("snow") || path.contains("frozen") || path.contains("ice") || path.equals("grove")
				|| path.equals("jagged_peaks")) {
			return SNOW;
		}
		if (path.contains("stony") || height > MOUNTAIN_STONE_HEIGHT) {
			return STONE;
		}
		if (path.contains("mushroom")) {
			return MYCELIUM;
		}
		if (path.contains("forest") || path.contains("taiga") || path.contains("jungle") || path.contains("grove")
				|| path.contains("swamp") || path.contains("garden") || path.contains("wooded")) {
			return BlockColorCache.multiply(LEAVES_BASE, 0xFF000000 | biome.getFoliageColor());
		}
		return BlockColorCache.multiply(GRASS_BASE, 0xFF000000 | biome.getGrassColor(x, z));
	}

	static int water(Holder<Biome> biome, int depth) {
		int color = BlockColorCache.multiply(WATER_BASE, 0xFF000000 | biome.value().getSpecialEffects().waterColor());
		return BlockColorCache.scale(color, Math.max(0.5f, 1f - depth * WATER_DEPTH_DARKEN));
	}

	static int nether(Holder<Biome> biome) {
		return switch (path(biome)) {
			case "crimson_forest" -> 0xFF8E1F1F;
			case "warped_forest" -> 0xFF1A7C7C;
			case "soul_sand_valley" -> 0xFF5B4A3C;
			case "basalt_deltas" -> 0xFF4C4C52;
			default -> 0xFF6E2E2E;
		};
	}
}
