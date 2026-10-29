package dev.cobblecomfort.features.map.waypoint;

import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.config.ConfigManager;
import dev.cobblecomfort.features.tracker.TrackerStorage;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Waypoints and their sets for the current world or server, saved as JSON. */
public final class WaypointStore {
	public static final String DEFAULT_SET = "default";
	public static final String DEATH_SET = "deaths";
	public static final String RESPAWN_SET = "respawn";

	/** Saved form. */
	private static final class Data {
		List<Waypoint> waypoints = new ArrayList<>();
		/** Set name -> visible. */
		Map<String, Boolean> sets = new LinkedHashMap<>();
		boolean xaeroImported;
	}

	private Data data = new Data();
	private String key;

	public void load() {
		key = TrackerStorage.currentWorldKey();
		data = new Data();
		Path file = file();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				Data loaded = ConfigManager.GSON.fromJson(reader, Data.class);
				if (loaded != null) {
					data = loaded;
				}
			} catch (Exception e) {
				CobbleComfortClient.LOGGER.error("Could not read waypoints {}", file, e);
			}
		}
		data.sets.putIfAbsent(DEFAULT_SET, true);
		data.sets.putIfAbsent(DEATH_SET, true);
		data.sets.putIfAbsent(RESPAWN_SET, true);
	}

	public void unload() {
		key = null;
		data = new Data();
	}

	public boolean loaded() {
		return key != null;
	}

	public List<Waypoint> all() {
		return data.waypoints;
	}

	/** Enabled waypoints of visible sets in a dimension. */
	public List<Waypoint> visible(String dimension) {
		List<Waypoint> list = new ArrayList<>();
		for (Waypoint waypoint : data.waypoints) {
			if (waypoint.enabled && waypoint.dimension.equals(dimension) && data.sets.getOrDefault(waypoint.set, true)) {
				list.add(waypoint);
			}
		}
		return list;
	}

	public Map<String, Boolean> sets() {
		return data.sets;
	}

	/** How many waypoints a set holds. */
	public int count(String set) {
		int count = 0;
		for (Waypoint waypoint : data.waypoints) {
			if (waypoint.set.equals(set)) {
				count++;
			}
		}
		return count;
	}

	/** Sets that hold waypoints, in their saved order. */
	public List<String> usedSets() {
		List<String> list = new ArrayList<>();
		for (String set : data.sets.keySet()) {
			if (count(set) > 0) {
				list.add(set);
			}
		}
		return list;
	}

	/** Sets a waypoint can be put into by hand: the default one and all other used ones except deaths and respawn. */
	public List<String> choosableSets() {
		List<String> list = new ArrayList<>();
		list.add(DEFAULT_SET);
		for (String set : usedSets()) {
			if (!set.equals(DEFAULT_SET) && !set.equals(DEATH_SET) && !set.equals(RESPAWN_SET)) {
				list.add(set);
			}
		}
		return list;
	}

	/** The name shown for a set; the built-in ones are translated. */
	public static String displayName(String set) {
		return switch (set) {
			case DEFAULT_SET -> Component.translatable("cobblecomfort.waypoint.set_default").getString();
			case DEATH_SET -> Component.translatable("cobblecomfort.waypoint.set_deaths").getString();
			case RESPAWN_SET -> Component.translatable("cobblecomfort.waypoint.set_respawn").getString();
			default -> set;
		};
	}

	/**
	 * The set for a typed name: an existing set whose name matches regardless of case (so "basis" lands in "Basis"
	 * instead of starting a new set), otherwise the trimmed text as a new set.
	 */
	public String setFor(String typed) {
		String trimmed = typed.trim();
		if (trimmed.isEmpty()) {
			return DEFAULT_SET;
		}
		for (String set : data.sets.keySet()) {
			if (set.equalsIgnoreCase(trimmed) || displayName(set).equalsIgnoreCase(trimmed)) {
				return set;
			}
		}
		return trimmed;
	}

	/** The shown name of the first choosable set that starts with the typed text (ignoring case), or null. */
	public String completion(String typed) {
		if (typed.isEmpty()) {
			return null;
		}
		String lower = typed.toLowerCase(Locale.ROOT);
		for (String set : choosableSets()) {
			String name = displayName(set);
			if (name.length() > typed.length() && name.toLowerCase(Locale.ROOT).startsWith(lower)) {
				return name;
			}
		}
		return null;
	}

	public void add(Waypoint waypoint) {
		waypoint.addedMs = System.currentTimeMillis();
		data.sets.putIfAbsent(waypoint.set, true);
		data.waypoints.add(waypoint);
		save();
	}

	public void remove(Waypoint waypoint) {
		data.waypoints.remove(waypoint);
		save();
	}

	/** Adds a death waypoint and removes the oldest ones beyond {@code keep}. */
	public void addDeath(Waypoint waypoint, int keep) {
		waypoint.addedMs = System.currentTimeMillis();
		waypoint.death = true;
		waypoint.set = DEATH_SET;
		data.waypoints.add(waypoint);
		List<Waypoint> deaths = new ArrayList<>(data.waypoints.stream().filter(w -> w.death).toList());
		deaths.sort(Comparator.comparingLong(w -> w.created));
		while (deaths.size() > Math.max(1, keep)) {
			data.waypoints.remove(deaths.removeFirst());
		}
		save();
	}

	/** The waypoint at the bed or respawn anchor, or null. */
	public Waypoint respawn() {
		for (Waypoint waypoint : data.waypoints) {
			if (waypoint.respawn) {
				return waypoint;
			}
		}
		return null;
	}

	/** Replaces the respawn waypoint. */
	public void setRespawn(Waypoint waypoint) {
		data.waypoints.removeIf(w -> w.respawn);
		waypoint.respawn = true;
		waypoint.set = RESPAWN_SET;
		data.sets.putIfAbsent(RESPAWN_SET, true);
		data.waypoints.add(waypoint);
		save();
	}

	public void removeRespawn() {
		if (data.waypoints.removeIf(w -> w.respawn)) {
			save();
		}
	}

	public boolean xaeroImported() {
		return data.xaeroImported;
	}

	public void markXaeroImported() {
		data.xaeroImported = true;
		save();
	}

	private Path file() {
		return ConfigManager.dir().resolve("waypoints").resolve((key == null ? "unknown" : key) + ".json");
	}

	public void save() {
		if (key == null) {
			return;
		}
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file)) {
				ConfigManager.GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			CobbleComfortClient.LOGGER.error("Could not write waypoints {}", file, e);
		}
	}
}
