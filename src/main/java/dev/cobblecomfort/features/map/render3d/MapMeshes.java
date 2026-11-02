package dev.cobblecomfort.features.map.render3d;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.cobblecomfort.features.map.data.BlockColorCache;
import dev.cobblecomfort.features.map.data.MapLayer;
import dev.cobblecomfort.features.map.data.MapRegion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The explored terrain as blocks, in tiles of 64 x 64 cells. A cell is 1, 2, 4 or 8 blocks wide (the tile 64 to 512
 * blocks), so far away tiles stay cheap; a 512 tile is a whole region. Each cell is a column of its highest block's
 * colour, or, where the map knows there is air under its top (a tree's crown, an overhang), that top layer floating
 * over the floor below. Walls are drawn where the neighbours leave a side open, with a short skirt at the edge of what
 * is explored. Built once and kept on the GPU until the region changes or the tile goes unused.
 */
public final class MapMeshes {
	public static final int CELLS = 64;
	/** Cell widths in blocks, finest first. */
	public static final int[] STEPS = {1, 2, 4, 8};
	private static final int UNUSED_FRAMES = 600;
	private static final int MAX_MESHES = 1200;
	/** Skirt below the edge of the explored land, in cells. */
	private static final int SKIRT = 3;
	/** Faces lit from the north-west: top, then north, south, west, east. */
	private static final float TOP = 1f;
	private static final float NORTH = 0.74f;
	private static final float SOUTH = 0.86f;
	private static final float WEST = 0.92f;
	private static final float EAST = 0.7f;
	/** Walls darken towards their foot. */
	private static final float FOOT = 0.78f;

	public record Key(MapLayer layer, int x, int z, int step) {
	}

	public static final class Mesh {
		final GpuBuffer buffer;
		public final int indexCount;
		final long stamp;
		int lastUsed;

		Mesh(GpuBuffer buffer, int indexCount, long stamp) {
			this.buffer = buffer;
			this.indexCount = indexCount;
			this.stamp = stamp;
		}

		public GpuBuffer buffer() {
			return buffer;
		}
	}

	private static final Map<Key, Mesh> MESHES = new HashMap<>();
	private static int frame;

	private MapMeshes() {
	}

	public static void beginFrame() {
		frame++;
		if (frame % 60 != 0 && MESHES.size() < MAX_MESHES) {
			return;
		}
		Iterator<Mesh> it = MESHES.values().iterator();
		while (it.hasNext()) {
			Mesh mesh = it.next();
			if (frame - mesh.lastUsed > UNUSED_FRAMES || MESHES.size() > MAX_MESHES && frame - mesh.lastUsed > 2) {
				close(mesh);
				it.remove();
			}
		}
	}

	public static void clear() {
		MESHES.values().forEach(MapMeshes::close);
		MESHES.clear();
	}

	private static void close(Mesh mesh) {
		if (mesh.buffer != null) {
			mesh.buffer.close();
		}
	}

	/**
	 * The mesh of a tile, built now if {@code build} allows it (else the last one, even if outdated, or null). Tiles
	 * are at multiples of their size: (x, z) * 64 * step blocks.
	 */
	public static Mesh get(Key key, boolean build) {
		Mesh mesh = MESHES.get(key);
		long stamp = stamp(key);
		if (mesh != null && (mesh.stamp == stamp || !build)) {
			mesh.lastUsed = frame;
			return mesh;
		}
		if (!build) {
			return null;
		}
		Mesh built = build(key, stamp);
		if (mesh != null) {
			close(mesh);
		}
		built.lastUsed = frame;
		MESHES.put(key, built);
		return built;
	}

	/**
	 * The versions of the chunks a tile reads from (its own area and its one-cell border), so a change elsewhere in the
	 * region doesn't rebuild it.
	 */
	private static long stamp(Key key) {
		int step = key.step();
		int size = CELLS * step;
		int x0 = key.x() * size - step;
		int z0 = key.z() * size - step;
		int x1 = x0 + size + 2 * step - 1;
		int z1 = z0 + size + 2 * step - 1;
		long stamp = 1;
		for (int rz = z0 >> MapRegion.SHIFT; rz <= z1 >> MapRegion.SHIFT; rz++) {
			for (int rx = x0 >> MapRegion.SHIFT; rx <= x1 >> MapRegion.SHIFT; rx++) {
				MapRegion region = key.layer().region(rx, rz, false);
				if (region == null) {
					stamp = stamp * 31 - 1;
					continue;
				}
				int base = MapRegion.SIZE;
				int lx0 = Math.max(x0 - rx * base, 0);
				int lz0 = Math.max(z0 - rz * base, 0);
				int lx1 = Math.min(x1 - rx * base, base - 1);
				int lz1 = Math.min(z1 - rz * base, base - 1);
				stamp = stamp * 31 + region.stamp(lx0, lz0, lx1, lz1);
			}
		}
		return stamp;
	}

	/** Whether any block of the tile is explored, without building it. */
	public static boolean explored(MapLayer layer, int x, int z, int step) {
		int size = CELLS * step;
		int x0 = x * size;
		int z0 = z * size;
		for (int rz = z0 >> MapRegion.SHIFT; rz <= (z0 + size - 1) >> MapRegion.SHIFT; rz++) {
			for (int rx = x0 >> MapRegion.SHIFT; rx <= (x0 + size - 1) >> MapRegion.SHIFT; rx++) {
				if (layer.region(rx, rz, false) != null) {
					return true;
				}
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- building

	/** Below any world: stands for "solid all the way down". */
	private static final float DEEP = -100000f;
	/** Undersides of overhangs and tree tops are in shadow. */
	private static final float UNDER = 0.5f;

	/**
	 * The cells of a tile and a one-cell border (for walls against the neighbours). Per cell: the highest column's
	 * height and colour (0 = unexplored), whether it is water, and where its top layer ends (SOLID: solid down to the
	 * ground) above which floor and floor colour (0 = none).
	 */
	private static final class Cells {
		final int n;
		final int[] height;
		final int[] color;
		final boolean[] water;
		final int[] bottom;
		final int[] floor;
		final int[] floorColor;

		Cells(int n) {
			this.n = n;
			height = new int[n * n];
			color = new int[n * n];
			water = new boolean[n * n];
			bottom = new int[n * n];
			floor = new int[n * n];
			floorColor = new int[n * n];
		}

		/**
		 * The solid parts of a cell from bottom to top, as (low, high) block faces into {@code spans} and their colours
		 * into {@code colors}; returns how many (0 to 2). The lowest reaches down to {@link #DEEP} unless it floats.
		 */
		int spans(int i, float[] spans, int[] colors) {
			if (color[i] == 0) {
				return 0;
			}
			float top = height[i] + 1;
			if (bottom[i] == MapRegion.SOLID) {
				spans[0] = DEEP;
				spans[1] = top;
				colors[0] = color[i];
				return 1;
			}
			int count = 0;
			if (floorColor[i] != 0) {
				spans[0] = DEEP;
				spans[1] = floor[i] + 1;
				colors[0] = floorColor[i];
				count = 1;
			}
			spans[count * 2] = bottom[i];
			spans[count * 2 + 1] = top;
			colors[count] = color[i];
			return count + 1;
		}
	}

	/** Reads the cells of a tile plus a one-cell border. A wider cell takes its highest column. */
	private static Cells read(Key key) {
		int step = key.step();
		int n = CELLS + 2;
		Cells cells = new Cells(n);
		int x0 = key.x() * CELLS * step - step;
		int z0 = key.z() * CELLS * step - step;
		MapLayer layer = key.layer();
		MapRegion region = null;
		int regionX = Integer.MIN_VALUE;
		int regionZ = Integer.MIN_VALUE;
		for (int cz = 0; cz < n; cz++) {
			for (int cx = 0; cx < n; cx++) {
				int best = Integer.MIN_VALUE;
				MapRegion bestRegion = null;
				int bestLx = 0;
				int bestLz = 0;
				int bx0 = x0 + cx * step;
				int bz0 = z0 + cz * step;
				for (int dz = 0; dz < step; dz++) {
					for (int dx = 0; dx < step; dx++) {
						int bx = bx0 + dx;
						int bz = bz0 + dz;
						int rx = bx >> MapRegion.SHIFT;
						int rz = bz >> MapRegion.SHIFT;
						if (rx != regionX || rz != regionZ) {
							region = layer.region(rx, rz, false);
							regionX = rx;
							regionZ = rz;
						}
						if (region == null) {
							continue;
						}
						int lx = bx & (MapRegion.SIZE - 1);
						int lz = bz & (MapRegion.SIZE - 1);
						if (region.rawColor(lx, lz) == 0) {
							continue;
						}
						int h = region.height(lx, lz);
						if (h > best) {
							best = h;
							bestRegion = region;
							bestLx = lx;
							bestLz = lz;
						}
					}
				}
				int i = cz * n + cx;
				if (bestRegion == null) {
					continue;
				}
				cells.height[i] = best;
				cells.color[i] = bestRegion.rawColor(bestLx, bestLz);
				cells.water[i] = bestRegion.water(bestLx, bestLz);
				cells.bottom[i] = bestRegion.bottom(bestLx, bestLz);
				cells.floor[i] = bestRegion.floor(bestLx, bestLz);
				cells.floorColor[i] = bestRegion.floorColor(bestLx, bestLz);
			}
		}
		closeHiddenGaps(cells);
		return cells;
	}

	/**
	 * Air under a column's top is only kept where it can be seen: open to the side next to a lower column (a tree's
	 * crown, the edge of an overhang or a floating island), or joined to such air through neighbouring gaps. Caves
	 * under the ground are filled in, so they cost no faces.
	 */
	private static void closeHiddenGaps(Cells cells) {
		int n = cells.n;
		boolean[] open = new boolean[n * n];
		int[] queue = new int[n * n];
		int head = 0;
		int tail = 0;
		for (int i = 0; i < n * n; i++) {
			if (!hasGap(cells, i)) {
				continue;
			}
			int x = i % n;
			int z = i / n;
			for (int d = 0; d < 4; d++) {
				int j = neighbour(n, x, z, d);
				if (j >= 0 && cells.color[j] != 0 && !hasGap(cells, j) && cells.height[j] + 1 < cells.bottom[i]) {
					open[i] = true;
					queue[tail++] = i;
					break;
				}
			}
		}
		while (head < tail) {
			int i = queue[head++];
			int x = i % n;
			int z = i / n;
			float low = gapLow(cells, i);
			for (int d = 0; d < 4; d++) {
				int j = neighbour(n, x, z, d);
				if (j >= 0 && !open[j] && hasGap(cells, j) && gapLow(cells, j) < cells.bottom[i] && low < cells.bottom[j]) {
					open[j] = true;
					queue[tail++] = j;
				}
			}
		}
		for (int i = 0; i < n * n; i++) {
			if (hasGap(cells, i) && !open[i]) {
				cells.bottom[i] = MapRegion.SOLID;
			}
		}
	}

	private static boolean hasGap(Cells cells, int i) {
		return cells.color[i] != 0 && cells.bottom[i] != MapRegion.SOLID;
	}

	/** The lowest air of a gap: just above its floor, or the bottom of the world if it has none. */
	private static float gapLow(Cells cells, int i) {
		return cells.floorColor[i] != 0 ? cells.floor[i] + 1 : DEEP;
	}

	/** The cell next to (x, z) towards north, south, west or east, or -1 outside the grid. */
	private static int neighbour(int n, int x, int z, int d) {
		int nx = x + (d == 2 ? -1 : d == 3 ? 1 : 0);
		int nz = z + (d == 0 ? -1 : d == 1 ? 1 : 0);
		return nx < 0 || nz < 0 || nx >= n || nz >= n ? -1 : nz * n + nx;
	}

	private static Mesh build(Key key, long stamp) {
		Cells cells = read(key);
		int step = key.step();
		int n = cells.n;
		int quads = 0;
		for (int i = 0; i < n * n; i++) {
			if (cells.color[i] != 0) {
				quads += cells.bottom[i] == MapRegion.SOLID ? 5 : 11;
			}
		}
		if (quads == 0) {
			return new Mesh(null, 0, stamp);
		}
		int vertexSize = DefaultVertexFormat.POSITION_COLOR.getVertexSize();
		try (ByteBufferBuilder bytes = new ByteBufferBuilder(quads * 4 * vertexSize)) {
			BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
			Faces faces = new Faces(builder, cells, step);
			for (int cz = 1; cz < n - 1; cz++) {
				for (int cx = 1; cx < n - 1; cx++) {
					faces.cell(cx, cz, key.x() * CELLS + cx, key.z() * CELLS + cz);
				}
			}
			if (faces.quads == 0) {
				return new Mesh(null, 0, stamp);
			}
			try (MeshData mesh = builder.buildOrThrow()) {
				GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Cobble Comfort 3D map tile", GpuBuffer.USAGE_VERTEX,
						mesh.vertexBuffer());
				return new Mesh(buffer, faces.quads * 6, stamp);
			}
		}
	}

	/** Writes the faces of the cells of one tile; reuses its scratch arrays from cell to cell. */
	private static final class Faces {
		final BufferBuilder builder;
		final Cells cells;
		final int step;
		final float skirt;
		int quads;
		final float[] own = new float[4];
		final int[] ownColors = new int[2];
		final float[] other = new float[4];
		final int[] otherColors = new int[2];
		/** Pieces of a wall still open after cutting away the neighbour's solid parts: (low, high) pairs. */
		final float[] open = new float[8];
		private final float[] kept = new float[8];

		Faces(BufferBuilder builder, Cells cells, int step) {
			this.builder = builder;
			this.cells = cells;
			this.step = step;
			this.skirt = SKIRT * step + 2;
		}

		/** One cell: the tops of its solid parts, the underside of a top layer with air below, and its open walls. */
		void cell(int cx, int cz, int worldCx, int worldCz) {
			int n = cells.n;
			int i = cz * n + cx;
			int count = cells.spans(i, own, ownColors);
			if (count == 0) {
				return;
			}
			// Positions are relative to the tile's corner; the tile's cells start at border cell 1.
			float x0 = (cx - 1) * step;
			float z0 = (cz - 1) * step;
			float x1 = x0 + step;
			float z1 = z0 + step;
			for (int s = 0; s < count; s++) {
				float low = own[s * 2];
				float high = own[s * 2 + 1];
				int color = ownColors[s];
				boolean water = s == count - 1 && cells.water[i];
				int top = shade(water ? color : vary(color, worldCx, worldCz, step + s), TOP);
				quad(x0, high, z0, x0, high, z1, x1, high, z1, x1, high, z0, top, top);
				if (low != DEEP) {
					int under = shade(color, UNDER);
					quad(x0, low, z0, x1, low, z0, x1, low, z1, x0, low, z1, under, under);
				}
				// North (-z), south (+z), west (-x), east (+x).
				// On the tile's edge the neighbour may be drawn coarser or finer: a skirt hides the seam between them.
				wall(i - n, low, high, color, NORTH, x0, z0, x1, z0, cz == 1);
				wall(i + n, low, high, color, SOUTH, x1, z1, x0, z1, cz == n - 2);
				wall(i - 1, low, high, color, WEST, x0, z1, x0, z0, cx == 1);
				wall(i + 1, low, high, color, EAST, x1, z0, x1, z1, cx == n - 2);
			}
		}

		/** The parts of the side of a solid span (low, high) that the neighbour's solid parts don't cover. */
		private void wall(int neighbour, float low, float high, int color, float light, float ax, float az, float bx, float bz,
				boolean edge) {
			int upper = shade(color, light);
			int lower = shade(color, light * FOOT);
			float skirtLow = Math.max(low, high - skirt);
			int pieces;
			int others = cells.spans(neighbour, other, otherColors);
			if (others == 0) {
				// The edge of what is explored: a short skirt instead of a wall down to the bottom of the world.
				open[0] = skirtLow;
				open[1] = high;
				pieces = 1;
			} else {
				open[0] = low;
				open[1] = high;
				pieces = 1;
				for (int o = 0; o < others; o++) {
					pieces = cut(pieces, other[o * 2], other[o * 2 + 1]);
				}
				if (edge) {
					// Always a skirt at the tile's edge, plus whatever wall goes further down.
					pieces = cut(pieces, skirtLow, high);
					// Evenly lit, so a wall carrying on below it joins without a seam in its shading.
					quad(ax, high, az, ax, skirtLow, az, bx, skirtLow, bz, bx, high, bz, upper, upper);
				}
			}
			for (int p = 0; p < pieces; p++) {
				float top = open[p * 2 + 1];
				// Open down to the bottom of the world (the neighbour floats): a skirt as at the edge.
				float bottom = open[p * 2] == DEEP ? top - skirt : open[p * 2];
				if (top - bottom > 0.001f) {
					quad(ax, top, az, ax, bottom, az, bx, bottom, bz, bx, top, bz, upper, lower);
				}
			}
		}

		/** Removes (low, high) from the open pieces; returns how many are left. */
		private int cut(int pieces, float low, float high) {
			int count = 0;
			for (int p = 0; p < pieces; p++) {
				float a = open[p * 2];
				float b = open[p * 2 + 1];
				if (high <= a || low >= b) {
					kept[count * 2] = a;
					kept[count * 2 + 1] = b;
					count++;
					continue;
				}
				if (a < low && count < 4) {
					kept[count * 2] = a;
					kept[count * 2 + 1] = low;
					count++;
				}
				if (high < b && count < 4) {
					kept[count * 2] = high;
					kept[count * 2 + 1] = b;
					count++;
				}
			}
			System.arraycopy(kept, 0, open, 0, count * 2);
			return count;
		}

		/** A quad of corners a, b, c, d: a and d take the {@code upper} colour, b and c the {@code lower} one. */
		private void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
				float dx, float dy, float dz, int upper, int lower) {
			builder.addVertex(ax, ay, az).setColor(upper);
			builder.addVertex(bx, by, bz).setColor(lower);
			builder.addVertex(cx, cy, cz).setColor(lower);
			builder.addVertex(dx, dy, dz).setColor(upper);
			quads++;
		}
	}

	private static int shade(int color, float factor) {
		return 0xFF000000 | BlockColorCache.scale(color, factor) & 0xFFFFFF;
	}

	/** A little brightness noise per block, so flat ground reads as blocks (as in voxel games) and not as a smooth sheet. */
	private static int vary(int color, int cx, int cz, int salt) {
		int hash = (cx * 73856093) ^ (cz * 19349663) ^ (salt * 83492791);
		hash ^= hash >>> 13;
		hash *= 0x5bd1e995;
		hash ^= hash >>> 15;
		float factor = 0.96f + (hash & 0xFF) / 255f * 0.08f;
		return BlockColorCache.scale(color, factor);
	}
}
