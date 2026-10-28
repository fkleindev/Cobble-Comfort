package dev.cobblecomfort.features.map.structure;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Structures recognised while exploring, by blocks that only (or mostly) appear in them, saved per world.
 * Only the palette of each chunk section is checked first, so chunks without such blocks cost almost nothing.
 */
public final class DetectedStructures {
	private static final int MERGE_DISTANCE = 96;
	private static final Map<Block, String> SIGNATURES = Map.of(
			Blocks.BELL, "village",
			Blocks.END_PORTAL_FRAME, "stronghold",
			Blocks.NETHER_BRICKS, "fortress",
			Blocks.DARK_PRISMARINE, "monument",
			Blocks.PURPUR_PILLAR, "end_city",
			Blocks.REINFORCED_DEEPSLATE, "ancient_city",
			Blocks.TRIAL_SPAWNER, "trial_chambers",
			Blocks.GILDED_BLACKSTONE, "bastion_remnant",
			Blocks.SPAWNER, "dungeon");

	/** Saved form of one marker. */
	private static final class Entry {
		String type;
		int x, z;
		String dimension;
	}

	/** Saved form. */
	private static final class Data {
		List<Entry> entries = new ArrayList<>();
	}

	private Data data = new Data();
	private Path file;

	public void load(String worldKey) {
		file = ConfigManager.dir().resolve("map").resolve(worldKey).resolve("structures.json");
		data = new Data();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				Data loaded = ConfigManager.GSON.fromJson(reader, Data.class);
				if (loaded != null) {
					data = loaded;
				}
			} catch (Exception e) {
				CobbleComfortClient.LOGGER.warn("Could not read detected structures {}", file, e);
			}
		}
	}

	public void unload() {
		file = null;
		data = new Data();
	}

	public List<StructureMarker> markers(String dimension) {
		List<StructureMarker> list = new ArrayList<>();
		for (Entry entry : data.entries) {
			if (entry.dimension.equals(dimension)) {
				list.add(new StructureMarker(entry.type, entry.x, entry.z, StructureMarker.Source.DETECTED));
			}
		}
		return list;
	}

	/** Looks for signature blocks in a freshly scanned chunk and records what it finds. */
	public void scan(LevelChunk chunk, String dimension) {
		if (file == null) {
			return;
		}
		LevelChunkSection[] sections = chunk.getSections();
		for (int s = 0; s < sections.length; s++) {
			LevelChunkSection section = sections[s];
			if (section == null || section.hasOnlyAir() || !section.getStates().maybeHas(DetectedStructures::isSignature)) {
				continue;
			}
			for (int i = 0; i < 4096; i++) {
				int lx = i & 15;
				int lz = i >> 4 & 15;
				int ly = i >> 8;
				BlockState state = section.getBlockState(lx, ly, lz);
				String type = SIGNATURES.get(state.getBlock());
				if (type != null) {
					BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + lx, 0, chunk.getPos().getMinBlockZ() + lz);
					add(type, pos.getX(), pos.getZ(), dimension);
					break;
				}
			}
		}
	}

	private static boolean isSignature(BlockState state) {
		return SIGNATURES.containsKey(state.getBlock());
	}

	private void add(String type, int x, int z, String dimension) {
		for (Entry entry : data.entries) {
			if (entry.type.equals(type) && entry.dimension.equals(dimension)
					&& Math.abs(entry.x - x) < MERGE_DISTANCE && Math.abs(entry.z - z) < MERGE_DISTANCE) {
				return;
			}
		}
		Entry entry = new Entry();
		entry.type = type;
		entry.x = x;
		entry.z = z;
		entry.dimension = dimension;
		data.entries.add(entry);
		save();
	}

	private void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				ConfigManager.GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.warn("Could not write detected structures {}", file, e);
		}
	}
}
