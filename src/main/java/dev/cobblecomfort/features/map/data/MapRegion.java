package dev.cobblecomfort.features.map.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 512x512 blocks of the explored map: colour (0 = unexplored), surface height and whether the column is water.
 * Shading is applied when baking, from the heights, so changes to neighbours don't need a rescan.
 */
public final class MapRegion {
	public static final int SHIFT = 9;
	public static final int SIZE = 1 << SHIFT;
	private static final int AREA = SIZE * SIZE;
	private static final int FORMAT = 1;
	private static final float SHADE_STEP = 0.07f;
	private static final float SHADE_MIN = 0.7f;
	private static final float SHADE_MAX = 1.22f;

	public final int x;
	public final int z;
	private final int[] colors = new int[AREA];
	private final short[] heights = new short[AREA];
	private final boolean[] water = new boolean[AREA];
	/** Bumped on every change, so textures and caches know when to rebuild. */
	private volatile int version;
	private boolean unsaved;

	public MapRegion(int x, int z) {
		this.x = x;
		this.z = z;
	}

	private static int index(int lx, int lz) {
		return lz << SHIFT | lx;
	}

	public void set(int lx, int lz, int color, int height, boolean isWater) {
		int i = index(lx, lz);
		if (colors[i] == color && heights[i] == height && water[i] == isWater) {
			return;
		}
		colors[i] = color;
		heights[i] = (short) height;
		water[i] = isWater;
		version++;
		unsaved = true;
	}

	public boolean explored(int lx, int lz) {
		return colors[index(lx, lz)] != 0;
	}

	public int rawColor(int lx, int lz) {
		return colors[index(lx, lz)];
	}

	public int height(int lx, int lz) {
		return heights[index(lx, lz)];
	}

	/** Colour with height shading against the block to the north-west (inside this region). */
	public int shadedColor(int lx, int lz, boolean shading) {
		int i = index(lx, lz);
		int color = colors[i];
		if (color == 0 || !shading || water[i]) {
			return color;
		}
		int north = lz > 0 && colors[i - SIZE] != 0 ? heights[i - SIZE] : heights[i];
		int west = lx > 0 && colors[i - 1] != 0 ? heights[i - 1] : heights[i];
		float diff = (heights[i] - north) + (heights[i] - west) * 0.5f;
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
		out.writeInt(FORMAT);
		for (int i = 0; i < AREA; i++) {
			out.writeInt(colors[i]);
			out.writeShort(heights[i]);
			out.writeBoolean(water[i]);
		}
		unsaved = false;
	}

	public void read(DataInputStream in) throws IOException {
		if (in.readInt() != FORMAT) {
			return;
		}
		for (int i = 0; i < AREA; i++) {
			colors[i] = in.readInt();
			heights[i] = in.readShort();
			water[i] = in.readBoolean();
		}
		version++;
		unsaved = false;
	}
}
