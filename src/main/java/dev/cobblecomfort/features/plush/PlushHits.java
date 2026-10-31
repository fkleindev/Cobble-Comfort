package dev.cobblecomfort.features.plush;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Plushes are squeezed when whoever carries them is hit, and pop back. Hits are seen on the client for every entity
 * (the hurt animation restarts, or an armour stand wobbles); how hard is read from the health (and absorption) lost
 * around that moment, which arrives a little before or after it.
 */
public final class PlushHits {
	/** Health lost this close to the hit counts towards it. */
	private static final long WINDOW_NANOS = 300_000_000L;
	private static final float SECONDS = 0.9f;
	/** How far the strongest hit squeezes the plush (a fraction of its height). */
	private static final float MAX_SQUEEZE = 0.36f;
	/** Health lost for the strongest squeeze; smaller hits squeeze less, but always a little. */
	private static final float FULL_DAMAGE = 10f;
	private static final float MIN_STRENGTH = 0.3f;
	/** Strength when no health was lost (armour stands, a hit taken only by armour). */
	private static final float UNKNOWN_STRENGTH = 0.5f;
	private static final long FORGET_NANOS = 5_000_000_000L;

	private static final class State {
		int hurtTime;
		long lastHit;
		float total;
		float drop;
		long dropAt;
		long hitAt;
		float strength;
		long lastSeen;
	}

	private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();

	private PlushHits() {
	}

	/** How squashed (1 = not at all) a plush carried by this entity is right now. */
	public static float squish(LivingEntity holder, long now) {
		State s = STATES.get(holder.getId());
		float total = holder.getHealth() + holder.getAbsorptionAmount();
		long lastHit = holder instanceof ArmorStand stand ? stand.lastHit : 0L;
		if (s == null) {
			if (STATES.size() > 128) {
				STATES.int2ObjectEntrySet().removeIf(e -> now - e.getValue().lastSeen > FORGET_NANOS);
			}
			s = new State();
			s.hurtTime = holder.hurtTime;
			s.lastHit = lastHit;
			s.total = total;
			STATES.put(holder.getId(), s);
		}
		s.lastSeen = now;

		if (total < s.total - 0.01f) {
			if (now - s.dropAt > WINDOW_NANOS) {
				s.drop = 0f;
			}
			s.drop += s.total - total;
			s.dropAt = now;
			// The health arrived after the hit: now it is known how hard it was.
			if (s.hitAt != 0 && now - s.hitAt < WINDOW_NANOS) {
				s.strength = strength(s.drop);
			}
		}
		s.total = total;

		boolean hit = holder.hurtTime > s.hurtTime || lastHit != s.lastHit;
		s.hurtTime = holder.hurtTime;
		s.lastHit = lastHit;
		if (hit) {
			s.hitAt = now;
			s.strength = now - s.dropAt < WINDOW_NANOS ? strength(s.drop) : UNKNOWN_STRENGTH;
		}

		if (s.hitAt == 0) {
			return 1f;
		}
		float t = (now - s.hitAt) / 1e9f;
		if (t >= SECONDS) {
			return 1f;
		}
		// Pressed flat at once, then popping back with a little overshoot.
		return 1f - MAX_SQUEEZE * s.strength * (float) Math.exp(-5f * t) * Mth.cos(t * 15f);
	}

	private static float strength(float drop) {
		return Mth.clamp(drop / FULL_DAMAGE, MIN_STRENGTH, 1f);
	}

	public static void clear() {
		STATES.clear();
	}
}
