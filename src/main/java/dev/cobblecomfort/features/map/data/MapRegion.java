package dev.cobblecomfort.features.map.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 512x512 blocks of the explored map: colour (0 = unexplored), surface height and whether the column is water.
 * Shading is applied when baking, from the heights, so changes to neighbours don't need a rescan. Stored per chunk and
 * only for explored chunks, so the many mostly empty cave layers cost little.
 * <p>
 * For the 3D map a column may also know that its top is a layer with air below (tree tops, overhangs, bridges): where
 * that layer ends and the floor under it. Kept only for chunks that have such a column.
 */
public final class MapRegion {
	public static final int SHIFT = 9;
	public static final int SIZE = 1 << SHIFT;
	private static final int AREA = SIZE * SIZE;
	private static final int CHUNKS = SIZE / 16;
	/** 1: every block in order. 2: only explored chunks, each with its index. 3: as 2, with the layers under the top. */
	private static final int FORMAT_DENSE = 1;
	private static final int FORMAT_CHUNKS = 2;
	private static final int FORMAT_LAYERS = 3;
	private static final float SHADE_STEP = 0.07f;
	private static final float SHADE_MIN = 0.7f;
	private static final float SHADE_MAX = 1.22f;
	/** The top of the column is solid down to the ground (or nothing more is known): no air below it. */
	public static final int SOLID = Short.MIN_VALUE;

	/** The 16x16 blocks of one chunk. */
	private static final class Chunk {
		final int[] colors = new int[256];
		final short[] heights = new short[256];
		final boolean[] water = new boolean[256];
		/** Null while every column is solid. */
		Layers layers;
		/** Bumped on every change to this chunk, so the 3D map rebuilds only the tiles over it. */
		int version;
	}

	/** Per column: the lowest block of the top layer (SOLID if no air below it), the floor under it and its colour (0 = none found). */
	private static final class Layers {
		final short[] bottoms = new short[256];
		final short[] floors = new short[256];
		final int[] floorColors = new int[256];

		Layers() {
			java.util.Arrays.fill(bottoms, (short) SOLID);
		}
	}

	public final int x;
	public final int z;
	private final Chunk[] chunks = new Chunk[CHUNKS * CHUNKS];
	/** Bumped on every change, so textures and caches know when to rebuild. */
	private volatile int version;
	private volatile boolean unsaved;
	/** {@link MapLayer#clock()} when last used, to unload regions nobody looks at (plain field: read for every map pixel). */
	int lastUsed;

	public MapRegion(int x, int z) {
		this.x = x;
		this.z = z;
	}

	void touch(int clock) {
		if (lastUsed != clock) {
			lastUsed = clock;
		}
	}

	private static int chunkIndex(int lx, int lz) {
		return (lz >> 4) * CHUNKS + (lx >> 4);
	}

	private static int inChunk(int lx, int lz) {
		return (lz & 15) << 4 | (lx & 15);
	}

	public void set(int lx, int lz, int color, int height, boolean isWater) {
		set(lx, lz, color, height, isWater, SOLID, 0, 0);
	}

	/** A column whose top layer ends at {@code bottom} (SOLID: it doesn't) above a floor at {@code floor} (colour 0: none). */
	public void set(int lx, int lz, int color, int height, boolean isWater, int bottom, int floor, int floorColor) {
		int ci = chunkIndex(lx, lz);
		Chunk chunk = chunks[ci];
		if (chunk == null) {
			if (color == 0) {
				return;
			}
			chunk = chunks[ci] = new Chunk();
		}
		int i = inChunk(lx, lz);
		Layers layers = chunk.layers;
		boolean sameLayers = layers == null ? bottom == SOLID
				: layers.bottoms[i] == bottom && (bottom == SOLID || layers.floors[i] == floor && layers.floorColors[i] == floorColor);
		if (chunk.colors[i] == color && chunk.heights[i] == height && chunk.water[i] == isWater && sameLayers) {
			return;
		}
		chunk.colors[i] = color;
		chunk.heights[i] = (short) height;
		chunk.water[i] = isWater;
		if (bottom != SOLID && layers == null) {
			layers = chunk.layers = new Layers();
		}
		if (layers != null) {
			layers.bottoms[i] = (short) bottom;
			layers.floors[i] = (short) (bottom == SOLID ? 0 : floor);
			layers.floorColors[i] = bottom == SOLID ? 0 : floorColor;
		}
		chunk.version++;
		version++;
		unsaved = true;
	}

	/**
	 * A number that changes whenever a block in the given range (region-local, inclusive) changes: built from the
	 * versions of the chunks it touches.
	 */
	public long stamp(int lx0, int lz0, int lx1, int lz1) {
		long stamp = 17;
		for (int cz = lz0 >> 4; cz <= lz1 >> 4; cz++) {
			for (int cx = lx0 >> 4; cx <= lx1 >> 4; cx++) {
				Chunk chunk = chunks[cz * CHUNKS + cx];
				stamp = stamp * 31 + (chunk == null ? -1 : chunk.version);
			}
		}
		return stamp;
	}

	/** The lowest block of the column's top layer, or SOLID if there is no air below it. */
	public int bottom(int lx, int lz) {
		Chunk chunk = chunks[chunkIndex(lx, lz)];
		return chunk == null || chunk.layers == null ? SOLID : chunk.layers.bottoms[inChunk(lx, lz)];
	}

	/** The floor under the top layer (only meaningful when {@link #bottom} isn't SOLID). */
	public int floor(int lx, int lz) {
		Chunk chunk = chunks[chunkIndex(lx, lz)];
		return chunk == null || chunk.layers == null ? 0 : chunk.layers.floors[inChunk(lx, lz)];
	}

	/** The floor's colour, 0 if none was found under the top layer. */
	public int floorColor(int lx, int lz) {
		Chunk chunk = chunks[chunkIndex(lx, lz)];
		return chunk == null || chunk.layers == null ? 0 : chunk.layers.floorColors[inChunk(lx, lz)];
	}

	public boolean explored(int lx, int lz) {
		return rawColor(lx, lz) != 0;
	}

	public int rawColor(int lx, int lz) {
		Chunk chunk = chunks[chunkIndex(lx, lz)];
		return chunk == null ? 0 : chunk.colors[inChunk(lx, lz)];
	}

	public int height(int lx, int lz) {
		Chunk chunk = chunks[chunkIndex(lx, lz)];
		return chunk == null ? 0 : chunk.heights[inChunk(lx, lz)];
	}

	public boolean water(int lx, int lz) {
		Chunk chunk = chunks[chunkIndex(lx, lz)];
		return chunk != null && chunk.water[inChunk(lx, lz)];
	}

	/** Colour with height shading against the block to the north-west (inside this region). */
	public int shadedColor(int lx, int lz, boolean shading) {
		Chunk chunk = chunks[chunkIndex(lx, lz)];
		if (chunk == null) {
			return 0;
		}
		int i = inChunk(lx, lz);
		int color = chunk.colors[i];
		if (color == 0 || !shading || chunk.water[i]) {
			return color;
		}
		int h = chunk.heights[i];
		int north = lz > 0 && explored(lx, lz - 1) ? height(lx, lz - 1) : h;
		int west = lx > 0 && explored(lx - 1, lz) ? height(lx - 1, lz) : h;
		float diff = (h - north) + (h - west) * 0.5f;
		float factor = Math.clamp(1f + diff * SHADE_STEP, SHADE_MIN, SHADE_MAX);
		return BlockColorCache.scale(color, factor);
	}

	public int version() {
		return version;
	}

	public boolean unsaved() {
		return unsaved;
	}

	public void write(DataOutputStream out) throws IOException {
		unsaved = false;
		out.writeInt(FORMAT_LAYERS);
		int count = 0;
		for (Chunk chunk : chunks) {
			count += chunk == null ? 0 : 1;
		}
		out.writeShort(count);
		for (int c = 0; c < chunks.length; c++) {
			Chunk chunk = chunks[c];
			if (chunk == null) {
				continue;
			}
			out.writeShort(c);
			for (int i = 0; i < 256; i++) {
				out.writeInt(chunk.colors[i]);
				out.writeShort(chunk.heights[i]);
				out.writeBoolean(chunk.water[i]);
			}
			Layers layers = chunk.layers;
			out.writeBoolean(layers != null);
			if (layers != null) {
				for (int i = 0; i < 256; i++) {
					out.writeShort(layers.bottoms[i]);
					out.writeShort(layers.floors[i]);
					out.writeInt(layers.floorColors[i]);
				}
			}
		}
	}

	public void read(DataInputStream in) throws IOException {
		int format = in.readInt();
		if (format == FORMAT_DENSE) {
			for (int i = 0; i < AREA; i++) {
				int color = in.readInt();
				short height = in.readShort();
				boolean isWater = in.readBoolean();
				set(i & (SIZE - 1), i >> SHIFT, color, height, isWater);
			}
		} else if (format == FORMAT_CHUNKS || format == FORMAT_LAYERS) {
			int count = in.readShort();
			for (int n = 0; n < count; n++) {
				Chunk chunk = new Chunk();
				chunks[in.readShort()] = chunk;
				for (int i = 0; i < 256; i++) {
					chunk.colors[i] = in.readInt();
					chunk.heights[i] = in.readShort();
					chunk.water[i] = in.readBoolean();
				}
				// Older files know no layers: every column counts as solid until it is scanned again.
				if (format == FORMAT_LAYERS && in.readBoolean()) {
					Layers layers = chunk.layers = new Layers();
					for (int i = 0; i < 256; i++) {
						layers.bottoms[i] = in.readShort();
						layers.floors[i] = in.readShort();
						layers.floorColors[i] = in.readInt();
					}
				}
			}
		} else {
			return;
		}
		version++;
		// Old files are rewritten in the new format on the next save.
		unsaved = format == FORMAT_DENSE;
	}
}
