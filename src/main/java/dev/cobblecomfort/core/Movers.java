package dev.cobblecomfort.core;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Things moving through the world that can set blocks swinging (plants, lanterns, lily pads): players, mobs, boats
 * and flying projectiles, with where they were last tick and where they are now.
 */
public final class Movers {
	public enum Kind {
		PLAYER, MOB, BOAT, PROJECTILE
	}

	/** One mover: its box now, and its motion over the last tick (blocks per tick). */
	public record Mover(Entity entity, Kind kind, AABB box, Vec3 from, Vec3 to) {
		public Vec3 motion() {
			return to.subtract(from);
		}

		public double radius() {
			return entity.getBbWidth() / 2.0;
		}
	}

	private Movers() {
	}

	/** The movers around a point: players, mobs, and (with {@code other}) boats and projectiles. */
	public static List<Mover> around(ClientLevel level, Vec3 center, double range, boolean players, boolean mobs, boolean other) {
		List<Mover> movers = new ArrayList<>();
		AABB area = new AABB(center, center).inflate(range);
		for (Entity entity : level.getEntities((Entity) null, area, e -> !e.isSpectator() && e.isAlive())) {
			Kind kind = kind(entity);
			if (kind == null
					|| kind == Kind.PLAYER && !players
					|| kind == Kind.MOB && !mobs
					|| (kind == Kind.BOAT || kind == Kind.PROJECTILE) && !other) {
				continue;
			}
			// The position before the last tick; remote entities don't report a velocity.
			Vec3 from = new Vec3(entity.xo, entity.yo, entity.zo);
			movers.add(new Mover(entity, kind, entity.getBoundingBox(), from, entity.position()));
		}
		return movers;
	}

	private static Kind kind(Entity entity) {
		if (entity instanceof Player) {
			return Kind.PLAYER;
		}
		if (entity instanceof LivingEntity) {
			return Kind.MOB;
		}
		if (entity instanceof AbstractBoat) {
			return Kind.BOAT;
		}
		if (entity instanceof Projectile) {
			return Kind.PROJECTILE;
		}
		return null;
	}
}
