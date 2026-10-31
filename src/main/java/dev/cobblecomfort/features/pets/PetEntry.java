package dev.cobblecomfort.features.pets;

import net.minecraft.world.phys.Vec3;

/** A remembered pet or mount: what and who it is, where it was last seen and how it was doing. Saved as JSON. */
public final class PetEntry {
	/** Entity UUID. */
	public String uuid = "";
	/** Entity type id, e.g. "minecraft:wolf". */
	public String type = "";
	/** The name tag's name, or empty. */
	public String name = "";
	public String dimension = "";
	public double x, y, z;
	public float health;
	public float maxHealth;
	public boolean sitting;
	public boolean leashed;
	/** Wolves and cats: variant id (e.g. "minecraft:ashen") and collar colour id (-1 for none), for the backpack plush. */
	public String variant = "";
	public int collar = -1;
	/** Horse-like mounts: top speed (blocks/s) and jump height (blocks); -1 for others. */
	public double speed = -1;
	public double jump = -1;
	/** When it was last seen (ms since epoch). */
	public long seen;
	/** When it died (ms since epoch), or 0 while alive. */
	public long died;
	/** The death message, if the server sent one. */
	public String deathMessage = "";

	public boolean dead() {
		return died > 0;
	}

	public Vec3 position() {
		return new Vec3(x, y, z);
	}

	public double distanceTo(Vec3 point) {
		return position().distanceTo(point);
	}
}
