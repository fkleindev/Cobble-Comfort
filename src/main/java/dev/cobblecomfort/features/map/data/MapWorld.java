package dev.cobblecomfort.features.map.data;

import dev.cobblecomfort.core.config.ConfigManager;
import dev.cobblecomfort.features.map.MapConfig;
import dev.cobblecomfort.features.tracker.TrackerStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * The map of the world or server the client is in. Per dimension: the surface, the Nether roof and a cave layer for
 * every 16 blocks of height, all saved. Like Xaero, a cave layer is only mapped while the player is in it, which keeps
 * scanning cheap. Fed by queues of chunks to scan (new chunks, plus a slow sweep around the player to pick up changes).
 */
public final class MapWorld {
	/** Layer ids besides the cave layers, which are their section Y (layer n = Y n*16 to n*16+15). */
	public static final int SURFACE = Integer.MAX_VALUE;
	public static final int ROOF = Integer.MAX_VALUE - 1;

	private static final long SCAN_BUDGET_NANOS = 2_000_000L;
	private static final int SWEEP_RADIUS = 3;
	private static final long SAVE_INTERVAL_MS = 60_000L;
	/** Regions unused this many ticks are saved and dropped from memory. */
	private static final int UNLOAD_TICKS = 60 * 20;
	private static final int UNDERGROUND_MARGIN = 4;
	/** Solid non-tree blocks above the player needed for the cave view (a house roof or a tree is not a cave). */
	private static final int ROCK_LAYERS = 3;

	/** The layers of one dimension. */
	private static final class Layers {
		final Path folder;
		final MapLayer surface;
		final MapLayer roof;
		final Map<Integer, MapLayer> caves = new ConcurrentHashMap<>();

		Layers(Path folder) {
			this.folder = folder;
			surface = new MapLayer(folder);
			roof = new MapLayer(folder == null ? null : folder.resolve("roof"));
		}

		MapLayer cave(int layer) {
			return caves.computeIfAbsent(layer, n -> new MapLayer(folder == null ? null : folder.resolve("caves").resolve(String.valueOf(n))));
		}
	}

	private final ExecutorService saver = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "Cobble Comfort map saver");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<ResourceKey<Level>, Layers> dims = new ConcurrentHashMap<>();
	private final ArrayDeque<Long> surfaceQueue = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();
	/** Chunks to scan in the cave layer the player is in (like Xaero, only that layer is mapped). */
	private final ArrayDeque<Long> layerQueue = new ArrayDeque<>();
	private String worldKey;
	private Path folder;
	private ResourceKey<Level> dimension;
	/** The layer the player is in, as the map shows it when following them. */
	private int autoLayer = SURFACE;
	private int sweepIndex;
	private long lastSaveMs = System.currentTimeMillis();
	private int scanCount;
	private java.util.function.BiConsumer<LevelChunk, ClientLevel> scanListener;

	/** Called after each surface scan of a chunk (used for structure detection). */
	public void setScanListener(java.util.function.BiConsumer<LevelChunk, ClientLevel> listener) {
		scanListener = listener;
	}

	public void join() {
		worldKey = TrackerStorage.currentWorldKey();
		folder = ConfigManager.dir().resolve("map").resolve(worldKey);
		dims.clear();
		surfaceQueue.clear();
		queued.clear();
		layerQueue.clear();
		dimension = null;
		autoLayer = SURFACE;
	}

	public void leave() {
		saveAsync();
		worldKey = null;
		folder = null;
	}

	public String worldKey() {
		return worldKey;
	}

	public boolean ready() {
		return worldKey != null;
	}

	private Layers dim(ResourceKey<Level> dim) {
		return dims.computeIfAbsent(dim, d -> new Layers(folder == null ? null
				: folder.resolve(d.identifier().getNamespace() + "_" + d.identifier().getPath().replace('/', '_').toLowerCase(Locale.ROOT))));
	}

	public MapLayer surface(ResourceKey<Level> dim) {
		return dim(dim).surface;
	}

	/** A layer of a dimension: {@link #SURFACE}, {@link #ROOF} or a cave layer. */
	public MapLayer layer(ResourceKey<Level> dim, int id) {
		Layers layers = dim(dim);
		return id == SURFACE ? layers.surface : id == ROOF ? layers.roof : layers.cave(id);
	}

	public static boolean isCave(int id) {
		return id != SURFACE && id != ROOF;
	}

	/** The layer to draw when following the player: their cave layer while underground (if enabled), the Nether roof on top of it, else the surface. */
	public MapLayer active(ClientLevel level) {
		return layer(level.dimension(), autoLayer);
	}

	public int autoLayer() {
		return autoLayer;
	}

	public boolean caveActive() {
		return isCave(autoLayer);
	}

	/**
	 * The layers a dimension can show, top to bottom: the roof (dimensions with a ceiling), the surface and the cave
	 * layers. For the player's dimension all of its height, for others the ones mapped so far.
	 */
	public List<Integer> layerIds(ResourceKey<Level> dim, ClientLevel level) {
		List<Integer> ids = new ArrayList<>();
		TreeSet<Integer> caves = new TreeSet<>(dim(dim).caves.keySet());
		boolean roof;
		if (level != null && level.dimension() == dim) {
			roof = level.dimensionType().hasCeiling();
			for (int n = level.getMinSectionY(); n <= maxCaveLayer(level); n++) {
				caves.add(n);
			}
		} else {
			Path dimFolder = dim(dim).folder;
			roof = dimFolder != null && Files.isDirectory(dimFolder.resolve("roof"));
			if (dimFolder != null && Files.isDirectory(dimFolder.resolve("caves"))) {
				try (Stream<Path> list = Files.list(dimFolder.resolve("caves"))) {
					list.forEach(p -> {
						try {
							caves.add(Integer.parseInt(p.getFileName().toString()));
						} catch (NumberFormatException ignored) {
						}
					});
				} catch (IOException ignored) {
				}
			}
		}
		if (roof) {
			ids.add(ROOF);
		}
		ids.add(SURFACE);
		ids.addAll(caves.descendingSet());
		return ids;
	}

	/** The highest cave layer: below the roof in dimensions with a ceiling, else the top of the world. */
	private static int maxCaveLayer(ClientLevel level) {
		if (level.dimensionType().hasCeiling()) {
			return (level.getMinY() + level.dimensionType().logicalHeight() - 1) >> 4;
		}
		return level.getMaxSectionY();
	}

	/** Grows with every scanned chunk, so views know when to redraw. */
	public int scanCount() {
		return scanCount;
	}

	public void chunkLoaded(LevelChunk chunk) {
		long pos = chunk.getPos().pack();
		enqueue(pos);
		if (isCave(autoLayer)) {
			layerQueue.add(pos);
		}
	}

	private void enqueue(long pos) {
		if (queued.add(pos)) {
			surfaceQueue.add(pos);
		}
	}

	public void tick(Minecraft mc, MapConfig config) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (!ready() || level == null || player == null) {
			return;
		}
		MapLayer.tickClock();
		if (level.dimension() != dimension) {
			dimension = level.dimension();
			autoLayer = SURFACE;
			layerQueue.clear();
		}
		updateAutoLayer(level, player, config);
		sweep(player);
		long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
		Layers layers = dim(level.dimension());
		boolean ceiling = level.dimensionType().hasCeiling();
		boolean underRoof = config.netherUnderRoof && ceiling;
		while (System.nanoTime() < deadline && (!surfaceQueue.isEmpty() || !layerQueue.isEmpty())) {
			if (!surfaceQueue.isEmpty()) {
				long pos = surfaceQueue.poll();
				queued.remove(pos);
				LevelChunk chunk = loadedChunk(level, pos);
				if (chunk != null) {
					ChunkScanner.scanSurface(level, chunk, layers.surface, underRoof);
					if (ceiling) {
						ChunkScanner.scanSurface(level, chunk, layers.roof, false);
					}
					scanCount++;
					if (scanListener != null) {
						scanListener.accept(chunk, level);
					}
				}
				continue;
			}
			LevelChunk chunk = loadedChunk(level, layerQueue.poll());
			if (chunk != null && isCave(autoLayer)) {
				ChunkScanner.scanCaves(level, chunk, layers::cave, autoLayer, autoLayer);
				scanCount++;
			}
		}
		if (System.currentTimeMillis() - lastSaveMs > SAVE_INTERVAL_MS) {
			saveAsync();
		}
	}

	/** Re-queues chunks around the player one at a time, so block changes show up on the map. */
	private void sweep(LocalPlayer player) {
		int side = SWEEP_RADIUS * 2 + 1;
		int index = sweepIndex++ % (side * side);
		int cx = player.chunkPosition().x() + index % side - SWEEP_RADIUS;
		int cz = player.chunkPosition().z() + index / side - SWEEP_RADIUS;
		enqueue(ChunkPos.pack(cx, cz));
		if (isCave(autoLayer) && layerQueue.isEmpty()) {
			layerQueue.add(ChunkPos.pack(cx, cz));
		}
	}

	private void updateAutoLayer(ClientLevel level, LocalPlayer player, MapConfig config) {
		int layer = SURFACE;
		boolean ceiling = level.dimensionType().hasCeiling();
		if (ceiling && player.getBlockY() >= level.getMinY() + level.dimensionType().logicalHeight()) {
			layer = ROOF;
		} else if (config.caveMode && underground(level, player, ceiling)) {
			layer = Math.clamp(player.getBlockY() >> 4, level.getMinSectionY(), maxCaveLayer(level));
		}
		if (layer == autoLayer) {
			return;
		}
		autoLayer = layer;
		layerQueue.clear();
		if (!isCave(layer)) {
			return;
		}
		// Entering a layer: refresh it around the player first, nearest chunks first.
		int radius = Minecraft.getInstance().options.getEffectiveRenderDistance();
		ChunkPos center = player.chunkPosition();
		for (int r = 0; r <= radius; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
						layerQueue.add(ChunkPos.pack(center.x() + dx, center.z() + dz));
					}
				}
			}
		}
	}

	private boolean underground(ClientLevel level, LocalPlayer player, boolean ceiling) {
		int surfaceHeight = surface(level.dimension()).heightAt(player.getBlockX(), player.getBlockZ());
		return (ceiling || !level.canSeeSky(player.blockPosition().above()))
				&& surfaceHeight != Integer.MIN_VALUE && player.getBlockY() < surfaceHeight - UNDERGROUND_MARGIN
				&& hasRockAbove(level, player, surfaceHeight);
	}

	/**
	 * Whether there are several solid layers above the player, not counting trees: standing under leaves (or a thin
	 * roof) should not switch the map to the cave view.
	 */
	private static boolean hasRockAbove(ClientLevel level, LocalPlayer player, int surfaceHeight) {
		BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
		int solid = 0;
		for (int y = player.getBlockY() + 2; y <= surfaceHeight; y++) {
			BlockState state = level.getBlockState(pos.setY(y));
			if (state.canOcclude() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && ++solid >= ROCK_LAYERS) {
				return true;
			}
		}
		return false;
	}

	private static LevelChunk loadedChunk(ClientLevel level, long pos) {
		return (LevelChunk) level.getChunkSource().getChunk(ChunkPos.getX(pos), ChunkPos.getZ(pos), ChunkStatus.FULL, false);
	}

	/** Saves changed regions in the background and drops the ones unused for a while from memory. */
	public void saveAsync() {
		lastSaveMs = System.currentTimeMillis();
		for (Layers layers : dims.values()) {
			saver.execute(() -> layers.surface.saveAndUnload(UNLOAD_TICKS));
			saver.execute(() -> layers.roof.saveAndUnload(UNLOAD_TICKS));
			for (MapLayer cave : layers.caves.values()) {
				saver.execute(() -> cave.saveAndUnload(UNLOAD_TICKS));
			}
		}
	}
}
