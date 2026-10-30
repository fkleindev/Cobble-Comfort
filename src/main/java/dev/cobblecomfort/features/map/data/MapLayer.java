package dev.cobblecomfort.features.map.data;

import dev.cobblecomfort.CobbleComfortClient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** All regions of one map layer (a dimension's surface, Nether roof or one cave layer), optionally saved to a folder. */
public final class MapLayer {
	/** Advanced by the map every tick; regions remember it when used, so unused ones can be unloaded. */
	private static int clock;

	private final Path folder;
	private final Map<Long, MapRegion> regions = new ConcurrentHashMap<>();
	private final Set<Long> missing = ConcurrentHashMap.newKeySet();

	/** @param folder where regions are saved, or null for a layer that only lives in memory */
	public MapLayer(Path folder) {
		this.folder = folder;
	}

	private static long key(int rx, int rz) {
		return (long) rx << 32 | (rz & 0xFFFFFFFFL);
	}

	/** The region, loading it from disk if needed; creates an empty one if {@code create}, otherwise may return null. */
	public MapRegion region(int rx, int rz, boolean create) {
		long key = key(rx, rz);
		MapRegion region = regions.get(key);
		if (region != null) {
			region.touch(clock);
			return region;
		}
		if (folder != null && !missing.contains(key)) {
			region = load(rx, rz);
			if (region != null) {
				region.touch(clock);
				regions.put(key, region);
				return region;
			}
			missing.add(key);
		}
		if (!create) {
			return null;
		}
		region = new MapRegion(rx, rz);
		region.touch(clock);
		regions.put(key, region);
		return region;
	}

	public static int clock() {
		return clock;
	}

	public static void tickClock() {
		clock++;
	}

	public Collection<MapRegion> loaded() {
		return regions.values();
	}

	/** Shaded colour at a block, 0 if unexplored. */
	public int colorAt(int bx, int bz, boolean shading) {
		MapRegion region = region(bx >> MapRegion.SHIFT, bz >> MapRegion.SHIFT, false);
		return region == null ? 0 : region.shadedColor(bx & (MapRegion.SIZE - 1), bz & (MapRegion.SIZE - 1), shading);
	}

	public int heightAt(int bx, int bz) {
		MapRegion region = region(bx >> MapRegion.SHIFT, bz >> MapRegion.SHIFT, false);
		return region == null ? Integer.MIN_VALUE : region.height(bx & (MapRegion.SIZE - 1), bz & (MapRegion.SIZE - 1));
	}

	public void clear() {
		regions.clear();
		missing.clear();
	}

	private Path file(int rx, int rz) {
		return folder.resolve("r." + rx + "." + rz + ".dat");
	}

	private MapRegion load(int rx, int rz) {
		Path file = file(rx, rz);
		if (!Files.exists(file)) {
			return null;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
			MapRegion region = new MapRegion(rx, rz);
			region.read(in);
			return region;
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.warn("Could not read map region {}", file, e);
			return null;
		}
	}

	/** Writes all regions changed since the last save. */
	public void save() {
		if (folder == null) {
			return;
		}
		for (MapRegion region : regions.values()) {
			if (!region.unsaved()) {
				continue;
			}
			Path file = file(region.x, region.z);
			try {
				Files.createDirectories(folder);
				Path temp = file.resolveSibling(file.getFileName() + ".tmp");
				try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))) {
					region.write(out);
				}
				Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				missing.remove(key(region.x, region.z));
			} catch (IOException e) {
				CobbleComfortClient.LOGGER.warn("Could not write map region {}", file, e);
			}
		}
	}

	/** Saves, then drops regions not used for {@code ticks} ticks; they are read again from disk when needed. */
	public void saveAndUnload(int ticks) {
		save();
		if (folder == null) {
			return;
		}
		int now = clock;
		regions.values().removeIf(region -> now - region.lastUsed > ticks && !region.unsaved());
	}
}
