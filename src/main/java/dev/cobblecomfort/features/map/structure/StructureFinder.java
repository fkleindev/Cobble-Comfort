package dev.cobblecomfort.features.map.structure;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.features.map.seed.SeedGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Finds structures around a point: in singleplayer exactly (the integrated server checks each candidate), otherwise
 * from the seed (placement maths plus a biome check, a good estimate). Runs in the background.
 */
public final class StructureFinder {
	private static final int SERVER_BATCH = 24;
	/** Biome check heights for structures that sit underground. */
	private static final int ANCIENT_CITY_Y = -27;
	private static final int TRIAL_CHAMBERS_Y = -30;

	private volatile List<StructureMarker> markers = List.of();
	private volatile boolean running;
	private int centerX, centerZ;
	private String dimension = "";
	private long seed;

	public List<StructureMarker> markers() {
		return markers;
	}

	/** Whether a new search is due (moved far, other dimension or seed) and none is running. */
	public boolean needsUpdate(int x, int z, String dim, long currentSeed, int radius) {
		if (running) {
			return false;
		}
		return !dim.equals(dimension) || currentSeed != seed || Math.abs(x - centerX) > radius / 4 || Math.abs(z - centerZ) > radius / 4;
	}

	public void clear() {
		markers = List.of();
		dimension = "";
	}

	/** Exact search through the integrated server (singleplayer). */
	public void startExact(MinecraftServer server, ServerLevel level, int x, int z, int radius, String dim) {
		begin(x, z, dim, level.getSeed());
		Thread thread = new Thread(() -> {
			try {
				markers = findExact(server, level, x, z, radius);
			} catch (RuntimeException e) {
				CobbleComfortClient.LOGGER.warn("Structure search failed", e);
			} finally {
				running = false;
			}
		}, "Cobble Comfort structures");
		thread.setDaemon(true);
		thread.start();
	}

	/** Estimated search from the seed (servers). */
	public void startSeed(SeedGenerator generator, int x, int z, int radius, String dim) {
		begin(x, z, dim, generator.seed);
		Thread thread = new Thread(() -> {
			try {
				markers = findFromSeed(generator, x, z, radius);
			} catch (RuntimeException e) {
				CobbleComfortClient.LOGGER.warn("Structure search failed", e);
			} finally {
				running = false;
			}
		}, "Cobble Comfort structures");
		thread.setDaemon(true);
		thread.start();
	}

	private void begin(int x, int z, String dim, long currentSeed) {
		running = true;
		centerX = x;
		centerZ = z;
		dimension = dim;
		seed = currentSeed;
	}

	/** A structure that might start in a chunk. */
	private record Candidate(ChunkPos chunk, StructureSet set) {
	}

	private static List<Candidate> candidates(ChunkGeneratorStructureState state, Iterable<Holder<StructureSet>> sets,
			long seed, int x, int z, int radius) {
		List<Candidate> result = new ArrayList<>();
		int minCx = (x - radius) >> 4;
		int maxCx = (x + radius) >> 4;
		int minCz = (z - radius) >> 4;
		int maxCz = (z + radius) >> 4;
		for (Holder<StructureSet> holder : sets) {
			StructureSet set = holder.value();
			if (set.structures().stream().noneMatch(e -> StructureIcons.shown(path(e.structure())))) {
				continue;
			}
			StructurePlacement placement = set.placement();
			if (placement instanceof RandomSpreadStructurePlacement spread) {
				int spacing = spread.spacing();
				for (int gx = Math.floorDiv(minCx, spacing); gx <= Math.floorDiv(maxCx, spacing); gx++) {
					for (int gz = Math.floorDiv(minCz, spacing); gz <= Math.floorDiv(maxCz, spacing); gz++) {
						ChunkPos chunk = spread.getPotentialStructureChunk(seed, gx, gz);
						if (chunk.x() < minCx || chunk.x() > maxCx || chunk.z() < minCz || chunk.z() > maxCz) {
							continue;
						}
						if (placement.isStructureChunk(state, chunk.x(), chunk.z())) {
							result.add(new Candidate(chunk, set));
						}
					}
				}
			} else if (placement instanceof ConcentricRingsStructurePlacement rings) {
				for (ChunkPos chunk : state.getRingPositionsFor(rings)) {
					if (chunk.x() >= minCx && chunk.x() <= maxCx && chunk.z() >= minCz && chunk.z() <= maxCz) {
						result.add(new Candidate(chunk, set));
					}
				}
			}
		}
		return result;
	}

	private List<StructureMarker> findExact(MinecraftServer server, ServerLevel level, int x, int z, int radius) {
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		List<Candidate> candidates = candidates(state, state.possibleStructureSets(), level.getSeed(), x, z, radius);
		List<StructureMarker> found = new ArrayList<>();
		for (int start = 0; start < candidates.size(); start += SERVER_BATCH) {
			List<Candidate> batch = candidates.subList(start, Math.min(candidates.size(), start + SERVER_BATCH));
			// The structure check belongs to the server thread; small batches keep the server responsive.
			CompletableFuture<List<StructureMarker>> future = server.submit(() -> {
				List<StructureMarker> part = new ArrayList<>();
				for (Candidate candidate : batch) {
					for (StructureSet.StructureSelectionEntry entry : candidate.set().structures()) {
						String type = path(entry.structure());
						if (!StructureIcons.shown(type)) {
							continue;
						}
						StructureCheckResult result = level.structureManager().checkStructurePresence(candidate.chunk(),
								entry.structure().value(), candidate.set().placement(), false);
						if (result != StructureCheckResult.START_NOT_PRESENT) {
							BlockPos pos = candidate.set().placement().getLocatePos(candidate.chunk());
							part.add(new StructureMarker(type, pos.getX(), pos.getZ(),
									result == StructureCheckResult.START_PRESENT ? StructureMarker.Source.EXACT : StructureMarker.Source.SEED));
							break;
						}
					}
				}
				return part;
			});
			found.addAll(future.join());
		}
		return found;
	}

	private List<StructureMarker> findFromSeed(SeedGenerator generator, int x, int z, int radius) {
		HolderLookup<StructureSet> sets = SeedGenerator.lookup().lookupOrThrow(Registries.STRUCTURE_SET);
		ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(generator.randomState, generator.seed,
				new ChunkPos(0, 0), generator.biomeSource, sets);
		List<Candidate> candidates = candidates(state, sets.listElements().map(h -> (Holder<StructureSet>) h).toList(), generator.seed, x, z, radius);
		BiomeResolver resolver = generator.resolver();
		List<StructureMarker> found = new ArrayList<>();
		for (Candidate candidate : candidates) {
			BlockPos pos = candidate.set().placement().getLocatePos(candidate.chunk());
			int bx = candidate.chunk().getMiddleBlockX();
			int bz = candidate.chunk().getMiddleBlockZ();
			for (StructureSet.StructureSelectionEntry entry : candidate.set().structures()) {
				String type = path(entry.structure());
				if (!StructureIcons.shown(type)) {
					continue;
				}
				int y = switch (type) {
					case "ancient_city" -> ANCIENT_CITY_Y;
					case "trial_chambers" -> TRIAL_CHAMBERS_Y;
					default -> Math.max(generator.floorHeight(bx, bz), generator.seaLevel);
				};
				if (entry.structure().value().biomes().contains(generator.biome(resolver, bx, y, bz))) {
					found.add(new StructureMarker(type, pos.getX(), pos.getZ(), StructureMarker.Source.SEED));
					break;
				}
			}
		}
		return found;
	}

	private static String path(Holder<Structure> structure) {
		return structure.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
	}
}
