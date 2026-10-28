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

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The map of the world or server the client is in: one saved surface layer per dimension plus a temporary cave layer,
 * fed by a queue of chunks to scan (new chunks, plus a slow sweep around the player to pick up changes).
 */
public final class MapWorld {
	private static final long SCAN_BUDGET_NANOS = 2_000_000L;
	private static final int SWEEP_RADIUS = 3;
	private static final long SAVE_INTERVAL_MS = 60_000L;
	private static final int CAVE_SLICE_STEP = 3;
	private static final int UNDERGROUND_MARGIN = 4;
	/** Solid non-tree blocks above the player needed for the cave view (a house roof or a tree is not a cave). */
	private static final int ROCK_LAYERS = 3;

	private final ExecutorService saver = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "Cobble Comfort map saver");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<ResourceKey<Level>, MapLayer> surfaces = new HashMap<>();
	private final MapLayer cave = new MapLayer(null);
	private final ArrayDeque<Long> surfaceQueue = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();
	private final ArrayDeque<Long> caveQueue = new ArrayDeque<>();
	private String worldKey;
	private Path folder;
	private ResourceKey<Level> dimension;
	private int caveSlice = Integer.MIN_VALUE;
	private boolean caveActive;
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
		surfaces.clear();
		cave.clear();
		surfaceQueue.clear();
		queued.clear();
		caveQueue.clear();
		dimension = null;
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

	public MapLayer surface(ResourceKey<Level> dim) {
		return surfaces.computeIfAbsent(dim, d -> new MapLayer(folder == null ? null
				: folder.resolve(d.identifier().getNamespace() + "_" + d.identifier().getPath().replace('/', '_').toLowerCase(Locale.ROOT))));
	}

	/** The layer to draw right now: the cave slice while underground (if enabled), else the surface. */
	public MapLayer active(ClientLevel level) {
		return caveActive ? cave : surface(level.dimension());
	}

	/** Grows with every scanned chunk, so views know when to redraw. */
	public int scanCount() {
		return scanCount;
	}

	public boolean caveActive() {
		return caveActive;
	}

	public void chunkLoaded(LevelChunk chunk) {
		enqueue(chunk.getPos().pack());
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
		if (level.dimension() != dimension) {
			dimension = level.dimension();
			caveSlice = Integer.MIN_VALUE;
			cave.clear();
		}
		updateCave(level, player, config);
		sweep(player);
		long deadline = System.nanoTime() + SCAN_BUDGET_NANOS;
		MapLayer surface = surface(level.dimension());
		boolean underRoof = config.netherUnderRoof && level.dimensionType().hasCeiling();
		while (System.nanoTime() < deadline && (!surfaceQueue.isEmpty() || !caveQueue.isEmpty())) {
			if (!caveQueue.isEmpty()) {
				LevelChunk chunk = loadedChunk(level, caveQueue.poll());
				if (chunk != null) {
					ChunkScanner.scanCave(level, chunk, cave, caveSlice);
					scanCount++;
				}
				continue;
			}
			long pos = surfaceQueue.poll();
			queued.remove(pos);
			LevelChunk chunk = loadedChunk(level, pos);
			if (chunk != null) {
				ChunkScanner.scanSurface(level, chunk, surface, underRoof);
				scanCount++;
				if (scanListener != null) {
					scanListener.accept(chunk, level);
				}
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
		if (caveActive && caveQueue.isEmpty()) {
			caveQueue.add(ChunkPos.pack(cx, cz));
		}
	}

	private void updateCave(ClientLevel level, LocalPlayer player, MapConfig config) {
		boolean underground = false;
		if (config.caveMode && !level.dimensionType().hasCeiling()) {
			int surfaceHeight = surface(level.dimension()).heightAt(player.getBlockX(), player.getBlockZ());
			underground = !level.canSeeSky(player.blockPosition().above())
					&& surfaceHeight != Integer.MIN_VALUE && player.getBlockY() < surfaceHeight - UNDERGROUND_MARGIN
					&& hasRockAbove(level, player, surfaceHeight);
		}
		caveActive = underground;
		if (!underground) {
			return;
		}
		int slice = player.getBlockY();
		if (Math.abs(slice - caveSlice) >= CAVE_SLICE_STEP) {
			caveSlice = slice;
			cave.clear();
			caveQueue.clear();
			int radius = Minecraft.getInstance().options.getEffectiveRenderDistance();
			ChunkPos center = player.chunkPosition();
			for (int r = 0; r <= radius; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
							caveQueue.add(ChunkPos.pack(center.x() + dx, center.z() + dz));
						}
					}
				}
			}
		}
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

	public void saveAsync() {
		lastSaveMs = System.currentTimeMillis();
		for (MapLayer layer : surfaces.values()) {
			saver.execute(layer::save);
		}
	}
}
