package dev.cobblecomfort.features.map.data;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.function.IntFunction;

/** Turns loaded chunks into map pixels: the surface (or, in the Nether, the ground under the roof) and cave layers. */
public final class ChunkScanner {
	private static final int MAX_WATER_DEPTH = 24;
	private static final int CAVE_SEARCH = 48;
	private static final int CAVE_SOLID = 0xFF262626;
	private static final float WATER_DEPTH_DARKEN = 0.035f;
	private static final float WATER_MIN_BRIGHTNESS = 0.55f;
	private static final float WATER_FLOOR_BLEND = 0.25f;

	private ChunkScanner() {
	}

	/** Scans the top of every column. {@code underRoof} looks below a ceiling (Nether) instead of drawing the roof. */
	public static void scanSurface(ClientLevel level, LevelChunk chunk, MapLayer layer, boolean underRoof) {
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		MapRegion region = layer.region(baseX >> MapRegion.SHIFT, baseZ >> MapRegion.SHIFT, true);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int bx = baseX + lx;
				int bz = baseZ + lz;
				// getHeight already returns the highest block (the heightmap minus one).
				int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
				int start = top;
				if (underRoof) {
					start = belowRoof(chunk, pos, bx, bz, Math.min(top, level.dimensionType().logicalHeight() - 1 + minY), minY);
				}
				column(level, chunk, region, pos, bx, bz, start, minY);
			}
		}
	}

	/**
	 * Scans cave layers {@code fromLayer..toLayer} (16 blocks each, layer n = Y n*16 to n*16+15). Per column and layer:
	 * the floor below the highest open block in the layer, or dark rock if the layer is solid there. Layers above the
	 * ground show the ground, like the surface.
	 */
	public static void scanCaves(ClientLevel level, LevelChunk chunk, IntFunction<MapLayer> layers, int fromLayer, int toLayer) {
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		int count = toLayer - fromLayer + 1;
		MapRegion[] regions = new MapRegion[count];
		boolean[] empty = new boolean[count];
		for (int i = 0; i < count; i++) {
			int layer = fromLayer + i;
			regions[i] = layers.apply(layer).region(baseX >> MapRegion.SHIFT, baseZ >> MapRegion.SHIFT, true);
			int section = chunk.getSectionIndexFromSectionY(layer);
			empty[i] = section < 0 || section >= chunk.getSectionsCount() || chunk.getSection(section).hasOnlyAir();
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int minY = level.getMinY();
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int bx = baseX + lx;
				int bz = baseZ + lz;
				int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
				for (int i = 0; i < count; i++) {
					int layerTop = (fromLayer + i) * 16 + 15;
					int layerBottom = layerTop - 15;
					// Open sky above the ground in this layer: it shows the ground.
					if (layerTop > top) {
						column(level, chunk, regions[i], pos, bx, bz, top, minY);
						continue;
					}
					int open = Integer.MIN_VALUE;
					if (empty[i]) {
						open = layerTop;
					} else {
						for (int y = layerTop; y >= layerBottom; y--) {
							if (isOpen(level, chunk.getBlockState(pos.set(bx, y, bz)), pos)) {
								open = y;
								break;
							}
						}
					}
					if (open == Integer.MIN_VALUE) {
						regions[i].set(bx & (MapRegion.SIZE - 1), bz & (MapRegion.SIZE - 1), CAVE_SOLID, layerTop + 1, false);
					} else {
						column(level, chunk, regions[i], pos, bx, bz, open, Math.max(minY, open - CAVE_SEARCH));
					}
				}
			}
		}
	}

	/** Air, fluids and blocks the map does not draw: where a cave can be seen from above. */
	private static boolean isOpen(ClientLevel level, BlockState state, BlockPos pos) {
		return state.isAir() || !state.getFluidState().isEmpty() || BlockColorCache.color(level, state, pos) == 0;
	}

	/** First air below the solid roof at the top of a column, or the top itself if there is no gap. */
	private static int belowRoof(LevelChunk chunk, BlockPos.MutableBlockPos pos, int bx, int bz, int top, int minY) {
		int y = top;
		while (y > minY && !chunk.getBlockState(pos.set(bx, y, bz)).isAir()) {
			y--;
		}
		return y > minY ? y : top;
	}

	private static void column(ClientLevel level, LevelChunk chunk, MapRegion region, BlockPos.MutableBlockPos pos,
			int bx, int bz, int start, int bottom) {
		int lx = bx & (MapRegion.SIZE - 1);
		int lz = bz & (MapRegion.SIZE - 1);
		for (int y = start; y >= bottom; y--) {
			BlockState state = chunk.getBlockState(pos.set(bx, y, bz));
			int color = BlockColorCache.color(level, state, pos);
			if (color == 0) {
				continue;
			}
			if (BlockColorCache.isWater(state)) {
				int depth = 1;
				int floor = 0;
				for (int d = y - 1; d >= bottom && depth < MAX_WATER_DEPTH; d--) {
					BlockState below = chunk.getBlockState(pos.set(bx, d, bz));
					if (!BlockColorCache.isWater(below)) {
						floor = BlockColorCache.color(level, below, pos);
						break;
					}
					depth++;
				}
				float brightness = Math.max(WATER_MIN_BRIGHTNESS, 1f - depth * WATER_DEPTH_DARKEN);
				int water = BlockColorCache.scale(color, brightness);
				if (floor != 0 && depth <= 3) {
					water = blend(water, floor, WATER_FLOOR_BLEND);
				}
				region.set(lx, lz, water, y, true);
				return;
			}
			region.set(lx, lz, color, y, false);
			return;
		}
	}

	private static int blend(int a, int b, float t) {
		int r = Math.round((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t);
		int g = Math.round((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}
}
