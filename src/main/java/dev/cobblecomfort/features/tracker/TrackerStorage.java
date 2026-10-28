package dev.cobblecomfort.features.tracker;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** One JSON file per world (singleplayer) or server (multiplayer) in config/cobblecomfort/tracker/. */
public final class TrackerStorage {
	private TrackerStorage() {
	}

	/** A file-name-safe key for the world or server the client is currently in. */
	public static String currentWorldKey() {
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			Path folder = root.getFileName() != null && !root.getFileName().toString().equals(".")
					? root.getFileName() : root.getParent().getFileName();
			return "sp_" + sanitize(folder.toString());
		}
		ServerData data = mc.getCurrentServer();
		if (data != null) {
			return "mp_" + sanitize(data.ip);
		}
		return "unknown";
	}

	private static String sanitize(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
	}

	private static Path file(String key) {
		return ConfigManager.dir().resolve("tracker").resolve(key + ".json");
	}

	public static TrackerData load(String key) {
		Path file = file(key);
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				TrackerData data = ConfigManager.GSON.fromJson(reader, TrackerData.class);
				if (data != null) {
					return data;
				}
			} catch (Exception e) {
				CobbleComfortClient.LOGGER.error("Could not read tracker data {}", file, e);
			}
		}
		return new TrackerData();
	}

	public static void save(String key, TrackerData data) {
		Path file = file(key);
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				ConfigManager.GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.error("Could not write tracker data {}", file, e);
		}
	}
}
