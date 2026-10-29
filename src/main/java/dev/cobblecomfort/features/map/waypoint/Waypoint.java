package dev.cobblecomfort.features.map.waypoint;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** A saved location. Colours use the 16 chat colours (0-15) like Xaero, so shared waypoints keep their colour. */
public final class Waypoint {
	public static final int DEFAULT_COLOR = 11;
	/** The 16 chat colours in their usual order (black, dark blue, ... white). */
	private static final int[] PALETTE = {0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00,
			0xFFAAAAAA, 0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF};

	public String name = "";
	public String initials = "";
	public int x;
	/** Null when the height is unknown (e.g. a shared waypoint with "~"). */
	public Integer y;
	public int z;
	public int color = DEFAULT_COLOR;
	public String set = WaypointStore.DEFAULT_SET;
	public String dimension = "minecraft:overworld";
	public boolean enabled = true;
	/** Death waypoints; the oldest are removed when there are too many. */
	public boolean death;
	/** The bed or respawn anchor the player respawns at; there is at most one. */
	public boolean respawn;
	/** Shown in the vanilla locator bar (above the hotbar). */
	public boolean locatorBar = true;
	/** An item id drawn in the marker instead of the initials, or null. */
	public String icon;
	public long created = System.currentTimeMillis();

	private transient ItemStack iconStack;
	/** When it was added in this session (0 if loaded), for the drop-in animation on the maps. */
	public transient long addedMs;

	public Waypoint() {
	}

	public Waypoint(String name, int x, Integer y, int z, String dimension) {
		this.name = name;
		this.initials = initialsOf(name);
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension;
	}

	public int rgb() {
		return PALETTE[Math.clamp(color, 0, 15)];
	}

	public double yOr(double fallback) {
		return y == null ? fallback : y;
	}

	/** The marker icon, or empty when the waypoint shows its initials. */
	public ItemStack iconStack() {
		if (icon == null || icon.isEmpty()) {
			return ItemStack.EMPTY;
		}
		if (iconStack == null) {
			Identifier id = Identifier.tryParse(icon);
			iconStack = id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
		}
		return iconStack;
	}

	public static String initialsOf(String name) {
		String trimmed = name.trim();
		if (trimmed.isEmpty()) {
			return "?";
		}
		String[] words = trimmed.split("\\s+");
		if (words.length >= 2) {
			return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(Locale.ROOT);
		}
		return trimmed.substring(0, 1).toUpperCase(Locale.ROOT);
	}
}
