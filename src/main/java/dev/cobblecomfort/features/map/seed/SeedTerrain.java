package dev.cobblecomfort.features.map.seed;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.config.ConfigManager;
import dev.cobblecomfort.features.map.data.BlockColorCache;
import dev.cobblecomfort.features.map.data.MapRegion;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Terrain of unexplored areas, calculated from the seed in the background at 1 sample per 4x4 blocks: ground height,
 * water and a colour from the biome. Regions are cached on disk, so each is only calculated once.
 */
public final class SeedTerrain {
	public static final int STEP = 4;
	public static final int SAMPLES = MapRegion.SIZE / STEP;
	private static final int WORKERS = 2;
	private static final int MAX_QUEUE = 48;
	private static final float SHADE_STEP = 0.05f;

	/** One region's colours (ARGB, 0 = nothing/void), SAMPLES x SAMPLES. */
	public static final class Region {
		public final int x, z;
		public final int[] colors = new int[SAMPLES * SAMPLES];

		Region(int x, int z) {
			this.x = x;
			this.z = z;
		}

		public int color(int lx, int lz) {
			return colors[lz * SAMPLES + lx];
		}
	}

	private final SeedGenerator generator;
	private final Path folder;
	private final Map<Long, Region> regions = new ConcurrentHashMap<>();
	private final Set<Long> pending = ConcurrentHashMap.newKeySet();
	private final LinkedBlockingDeque<Long> queue = new LinkedBlockingDeque<>();
	private final AtomicInteger finished = new AtomicInteger();
	private volatile boolean closed;

	public SeedTerrain(SeedGenerator generator, Path worldFolder) {
		this.generator = generator;
		this.folder = worldFolder.resolve("seed_" + generator.dimension.identifier().getPath() + "_" + generator.seed);
		for (int i = 0; i < WORKERS; i++) {
			Thread thread = new Thread(this::work, "Cobble Comfort seed terrain " + i);
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			thread.start();
		}
	}

	public SeedGenerator generator() {
		return generator;
	}

	/** Grows whenever a region finishes, so views know to redraw. */
	public int version() {
		return finished.get();
	}

	private static long key(int rx, int rz) {
		return (long) rx << 32 | (rz & 0xFFFFFFFFL);
	}

	/** The region if ready; otherwise queues it (newest requests first) and returns null. */
	public Region region(int rx, int rz) {
		long key = key(rx, rz);
		Region region = regions.get(key);
		if (region == null && pending.add(key)) {
			queue.addFirst(key);
			while (queue.size() > MAX_QUEUE) {
				Long dropped = queue.pollLast();
				if (dropped != null) {
					pending.remove(dropped);
				}
			}
		}
		return region;
	}

	/** Colour of an unexplored block, 0 if not calculated (yet). */
	public int colorAt(int bx, int bz) {
		Region region = region(bx >> MapRegion.SHIFT, bz >> MapRegion.SHIFT);
		if (region == null) {
			return 0;
		}
		return region.color((bx & (MapRegion.SIZE - 1)) / STEP, (bz & (MapRegion.SIZE - 1)) / STEP);
	}

	public void close() {
		closed = true;
		queue.clear();
	}

	private void work() {
		while (!closed) {
			try {
				Long key = queue.takeFirst();
				int rx = (int) (key >> 32);
				int rz = (int) (long) key;
				Region region = load(rx, rz);
				if (region == null) {
					region = compute(rx, rz);
					save(region);
				}
				regions.put(key, region);
				pending.remove(key);
				finished.incrementAndGet();
			} catch (InterruptedException e) {
				return;
			} catch (RuntimeException e) {
				CobbleComfortClient.LOGGER.warn("Seed terrain failed", e);
			}
		}
	}

	private Region compute(int rx, int rz) {
		Region region = new Region(rx, rz);
		BiomeResolver resolver = generator.resolver();
		int[] heights = new int[SAMPLES * SAMPLES];
		boolean[] water = new boolean[SAMPLES * SAMPLES];
		int baseX = rx * MapRegion.SIZE;
		int baseZ = rz * MapRegion.SIZE;
		boolean nether = generator.dimension == Level.NETHER;
		boolean end = generator.dimension == Level.END;
		int minY = generator.heightAccessor.getMinY();
		for (int sz = 0; sz < SAMPLES && !closed; sz++) {
			for (int sx = 0; sx < SAMPLES; sx++) {
				int x = baseX + sx * STEP + STEP / 2;
				int z = baseZ + sz * STEP + STEP / 2;
				int i = sz * SAMPLES + sx;
				if (nether) {
					region.colors[i] = SeedColors.nether(generator.biome(resolver, x, 64, z));
					heights[i] = 64;
					continue;
				}
				int floor = generator.floorHeight(x, z);
				heights[i] = floor;
				if (end) {
					region.colors[i] = floor <= minY + 1 ? 0 : SeedColors.END_STONE;
					continue;
				}
				Holder<Biome> biome = generator.biome(resolver, x, Math.max(floor, generator.seaLevel), z);
				if (floor < generator.seaLevel - 1) {
					water[i] = true;
					region.colors[i] = SeedColors.water(biome, generator.seaLevel - 1 - floor);
				} else {
					region.colors[i] = SeedColors.land(biome, x, z, floor);
				}
			}
		}
		// Height shading against the sample to the north-west, like the explored map.
		for (int sz = SAMPLES - 1; sz >= 0; sz--) {
			for (int sx = SAMPLES - 1; sx >= 0; sx--) {
				int i = sz * SAMPLES + sx;
				if (water[i] || region.colors[i] == 0 || nether) {
					continue;
				}
				int north = sz > 0 ? heights[i - SAMPLES] : heights[i];
				int west = sx > 0 ? heights[i - 1] : heights[i];
				float diff = (heights[i] - north) + (heights[i] - west) * 0.5f;
				region.colors[i] = BlockColorCache.scale(region.colors[i], Math.clamp(1f + diff * SHADE_STEP, 0.7f, 1.2f));
			}
		}
		return region;
	}

	private Path file(int rx, int rz) {
		return folder.resolve("r." + rx + "." + rz + ".dat");
	}

	private Region load(int rx, int rz) {
		Path file = file(rx, rz);
		if (!Files.exists(file)) {
			return null;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
			Region region = new Region(rx, rz);
			for (int i = 0; i < region.colors.length; i++) {
				region.colors[i] = in.readInt();
			}
			return region;
		} catch (IOException e) {
			return null;
		}
	}

	private void save(Region region) {
		Path file = file(region.x, region.z);
		try {
			Files.createDirectories(folder);
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(file))))) {
				for (int color : region.colors) {
					out.writeInt(color);
				}
			}
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.warn("Could not write seed terrain {}", file, e);
		}
	}

	/** The folder for a world's map data (same place as the explored map). */
	public static Path worldFolder(String worldKey) {
		return ConfigManager.dir().resolve("map").resolve(worldKey);
	}

	public static boolean sameAs(SeedTerrain terrain, long seed, ResourceKey<Level> dimension) {
		return terrain != null && terrain.generator.seed == seed && terrain.generator.dimension == dimension;
	}
}
