package dev.cobblecomfort.features.map.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 512x512 blocks of the explored map: colour (0 = unexplored), surface height and whether the column is water.
 * Shading is applied when baking, from the heights, so changes to neighbours don't need a rescan. Stored per chunk and
 * only for explored chunks, so the many mostly empty cave layers cost little.
 */
public final class MapRegion {
	public static final int SHIFT = 9;
	public static final int SIZE = 1 << SHIFT;
	private static final int AREA = SIZE * SIZE;
	private static final int CHUNKS = SIZE / 16;
	/** 1: every block in order. 2: only explored chunks, each with its index. */
	private static final int FORMAT_DENSE = 1;
	private static final int FORMAT_CHUNKS = 2;
	private static final float SHADE_STEP = 0.07f;
	private static final float SHADE_MIN = 0.7f;
	private static final float SHADE_MAX = 1.22f;

	/** The 16x16 blocks of one chunk. */
	private static final class Chunk {
		final int[] colors = new int[256];
		final short[] heights = new short[256];
		final boolean[] water = new boolean[256];
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
		int ci = chunkIndex(lx, lz);
		Chunk chunk = chunks[ci];
		if (chunk == null) {
			if (color == 0) {
				return;
			}
			chunk = chunks[ci] = new Chunk();
		}
		int i = inChunk(lx, lz);
		if (chunk.colors[i] == color && chunk.heights[i] == height && chunk.water[i] == isWater) {
			return;
		}
		chunk.colors[i] = color;
		chunk.heights[i] = (short) height;
		chunk.water[i] = isWater;
		version++;
		unsaved = true;
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
		out.writeInt(FORMAT_CHUNKS);
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
		} else if (format == FORMAT_CHUNKS) {
			int count = in.readShort();
			for (int n = 0; n < count; n++) {
				Chunk chunk = new Chunk();
				chunks[in.readShort()] = chunk;
				for (int i = 0; i < 256; i++) {
					chunk.colors[i] = in.readInt();
					chunk.heights[i] = in.readShort();
					chunk.water[i] = in.readBoolean();
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
