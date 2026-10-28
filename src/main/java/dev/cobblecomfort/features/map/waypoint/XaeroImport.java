package dev.cobblecomfort.features.map.waypoint;

import dev.cobblecomfort.CobbleComfortClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * One-time import of Xaero's Minimap waypoints for the current world/server. Xaero keeps them as text files under
 * xaero/minimap/&lt;world or Multiplayer_ip&gt;/dim%&lt;id&gt;/*.txt with lines "waypoint:name:initials:x:y:z:color:disabled:...:set:...".
 */
public final class XaeroImport {
	private static final int MIN_PARTS = 8;

	private XaeroImport() {
	}

	public static void importInto(WaypointStore store, String worldKey) {
		Path root = FabricLoader.getInstance().getGameDir().resolve("xaero").resolve("minimap");
		if (!Files.isDirectory(root) || worldKey == null) {
			store.markXaeroImported();
			return;
		}
		// Our keys are "sp_<folder>" / "mp_<ip>"; Xaero uses "<folder>" / "Multiplayer_<ip>" with "_" written as "%us%".
		int imported = 0;
		try (Stream<Path> worlds = Files.list(root)) {
			for (Path world : worlds.toList()) {
				if (!Files.isDirectory(world) || !worldKey.equals(keyOf(world.getFileName().toString()))) {
					continue;
				}
				imported += importWorld(store, world);
			}
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.warn("Could not read Xaero waypoints", e);
		}
		if (imported > 0) {
			CobbleComfortClient.LOGGER.info("Imported {} Xaero waypoints for {}", imported, worldKey);
		}
		store.markXaeroImported();
	}

	/** Our world key for a Xaero world folder name. */
	private static String keyOf(String folder) {
		String name = folder.replace("%us%", "_");
		boolean multiplayer = name.startsWith("Multiplayer_");
		if (multiplayer) {
			name = name.substring("Multiplayer_".length());
		}
		return (multiplayer ? "mp_" : "sp_") + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	private static int importWorld(WaypointStore store, Path world) throws IOException {
		int count = 0;
		try (Stream<Path> files = Files.walk(world, 3)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".txt")).toList()) {
				String dimension = dimensionOf(file);
				List<String> lines = Files.readAllLines(file);
				for (String line : lines) {
					Waypoint waypoint = parseLine(line, dimension);
					if (waypoint != null) {
						store.all().add(waypoint);
						store.sets().putIfAbsent(waypoint.set, true);
						count++;
					}
				}
			}
		}
		store.save();
		return count;
	}

	private static String dimensionOf(Path file) {
		for (Path part : file) {
			String name = part.toString().toLowerCase(Locale.ROOT);
			if (name.equals("dim%-1") || name.contains("nether")) {
				return "minecraft:the_nether";
			}
			if (name.equals("dim%1") || name.contains("the_end")) {
				return "minecraft:the_end";
			}
		}
		return "minecraft:overworld";
	}

	private static Waypoint parseLine(String line, String dimension) {
		if (!line.startsWith("waypoint:")) {
			return null;
		}
		String[] parts = line.substring("waypoint:".length()).split(":");
		if (parts.length < MIN_PARTS) {
			return null;
		}
		try {
			Waypoint waypoint = new Waypoint();
			waypoint.name = parts[0].replace("§§", ":").replace("gui.xaero_deathpoint_old", "Death (old)").replace("gui.xaero_deathpoint", "Death");
			waypoint.initials = parts[1];
			waypoint.x = Integer.parseInt(parts[2]);
			waypoint.y = parts[3].equals("~") ? null : Integer.parseInt(parts[3]);
			waypoint.z = Integer.parseInt(parts[4]);
			waypoint.color = Math.clamp(Integer.parseInt(parts[5]), 0, 15);
			waypoint.enabled = !Boolean.parseBoolean(parts[6]);
			waypoint.set = parts.length > 8 && !parts[8].isBlank() ? parts[8].replace("gui.xaero_default", WaypointStore.DEFAULT_SET) : WaypointStore.DEFAULT_SET;
			waypoint.dimension = dimension;
			return waypoint;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
