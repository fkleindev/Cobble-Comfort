package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.core.Voxels;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The companions of everyone around: each client works out on its own where they walk, from where their player is,
 * so nothing but what they look like goes over the network. They are only drawn: no entity, nothing collides with them
 * or can hit them, blocks can be placed where they stand.
 */
final class Companions {
	/** Further than this from their player, they come over at once (a puff of smoke there and here). */
	private static final double TELEPORT = 18;
	/** Owner faster than this (blocks per tick, not riding): flying, a trident, an ender pearl; the companion waits out of sight. */
	private static final double TOO_FAST = 0.9;
	private static final double NAME_RANGE = 12;
	private static final float GRAVITY = 0.08f;

	private final Map<UUID, State> states = new HashMap<>();
	private final RandomSource random = RandomSource.create();

	void clear() {
		states.clear();
		meetings.clear();
	}

	int count() {
		return states.size();
	}

	/** The companion following this player now, for tests; null for none. */
	State state(UUID player) {
		return states.get(player);
	}

	void tick(Minecraft mc, ClientLevel level, WardrobeFeature wardrobe) {
		Map<UUID, Player> present = new HashMap<>();
		for (Player player : level.players()) {
			present.put(player.getUUID(), player);
		}
		Iterator<Map.Entry<UUID, State>> it = states.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, State> entry = it.next();
			Player owner = present.get(entry.getKey());
			if (owner == null || !(owner instanceof AbstractClientPlayer client) || wardrobe.companionFor(client) == null) {
				it.remove();
			}
		}
		meet(level, present);
		for (Player player : present.values()) {
			if (!(player instanceof AbstractClientPlayer client)) {
				continue;
			}
			Companion.Look look = wardrobe.companionFor(client);
			if (look == null) {
				continue;
			}
			State state = states.computeIfAbsent(player.getUUID(), id -> new State(look, player));
			if (state.look.companion().style != look.companion().style) {
				// Another kind: it comes anew, with a puff.
				puff(level, state.x, state.y, state.z);
				state = new State(look, player);
				states.put(player.getUUID(), state);
				state.hidden = true;
			}
			state.look = look;
			tick(mc, level, state, player);
		}
	}

	// ---------------------------------------------------------------- meeting each other

	/** Two companions greeting each other (and maybe playing tag), while their players stand together. */
	static final class Meeting {
		static final int APPROACH = 0, SNIFF = 1, PLAY = 2, BYE = 3;
		final UUID a, b;
		final boolean play;
		/** Where they meet, and the line between them (unit, from a to b). */
		final double cx, cz, dx, dz;
		int phase = APPROACH, age, phaseAge;
		float angle;

		Meeting(UUID a, UUID b, State sa, State sb, boolean play) {
			this.a = a;
			this.b = b;
			this.play = play;
			this.cx = (sa.x + sb.x) / 2;
			this.cz = (sa.z + sb.z) / 2;
			double d = Math.max(0.01, Math.hypot(sb.x - sa.x, sb.z - sa.z));
			this.dx = (sb.x - sa.x) / d;
			this.dz = (sb.z - sa.z) / d;
			this.angle = (float) Math.atan2(sa.z - cz, sa.x - cx);
		}

		void next(int phase) {
			this.phase = phase;
			this.phaseAge = 0;
		}
	}

	private final List<Meeting> meetings = new ArrayList<>();

	int meetingCount() {
		return meetings.size();
	}

	/**
	 * Companions of players standing together walk up to each other, sniff noses, hop for joy with hearts, sometimes
	 * chase each other round in a circle, say goodbye with a hop and go back. Any of the players moving ends it.
	 */
	private void meet(ClientLevel level, Map<UUID, Player> present) {
		Iterator<Meeting> it = meetings.iterator();
		while (it.hasNext()) {
			Meeting m = it.next();
			State a = states.get(m.a), b = states.get(m.b);
			if (a == null || b == null || !stillMeeting(a) || !stillMeeting(b) || a.meeting != m || b.meeting != m) {
				part(a, m);
				part(b, m);
				it.remove();
				continue;
			}
			m.age++;
			m.phaseAge++;
			a.face = b;
			b.face = a;
			switch (m.phase) {
				case Meeting.APPROACH -> {
					// Nose to nose at the middle.
					a.target = new Vec3(m.cx - m.dx * 0.5, 0, m.cz - m.dz * 0.5);
					b.target = new Vec3(m.cx + m.dx * 0.5, 0, m.cz + m.dz * 0.5);
					boolean there = Math.hypot(a.x - a.target.x, a.z - a.target.z) < 0.45 && Math.hypot(b.x - b.target.x, b.z - b.target.z) < 0.45;
					if (there || m.phaseAge > 80) {
						m.next(Meeting.SNIFF);
					}
				}
				case Meeting.SNIFF -> {
					// Sniffing, then a hop for joy with hearts.
					if (m.phaseAge == 26) {
						hop(a, 0.32);
						hop(b, 0.32);
						a.happy = b.happy = 1;
						for (int i = 0; i < 3; i++) {
							level.addParticle(ParticleTypes.HEART, m.cx + (random.nextDouble() - 0.5) * 0.6, (a.y + b.y) / 2 + 0.7,
									m.cz + (random.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
						}
					}
					if (m.phaseAge > 50) {
						m.next(m.play ? Meeting.PLAY : Meeting.BYE);
					}
				}
				case Meeting.PLAY -> {
					// Tag: running round the middle, b just behind a.
					m.angle += 0.11f;
					double r = 1.15;
					a.target = new Vec3(m.cx + Math.cos(m.angle) * r, 0, m.cz + Math.sin(m.angle) * r);
					b.target = new Vec3(m.cx + Math.cos(m.angle - 1.3) * r, 0, m.cz + Math.sin(m.angle - 1.3) * r);
					a.face = null;
					b.face = null;
					a.happy = Math.max(a.happy, 0.6f);
					b.happy = Math.max(b.happy, 0.6f);
					if (m.phaseAge % 30 == 15) {
						hop(m.phaseAge % 60 == 15 ? a : b, 0.3);
					}
					if (m.phaseAge > 120) {
						// Back face to face for goodbye.
						a.target = new Vec3(m.cx - m.dx * 0.55, 0, m.cz - m.dz * 0.55);
						b.target = new Vec3(m.cx + m.dx * 0.55, 0, m.cz + m.dz * 0.55);
						m.next(Meeting.BYE);
					}
				}
				case Meeting.BYE -> {
					if (m.phaseAge == 12) {
						hop(a, 0.26);
						hop(b, 0.26);
						a.happy = b.happy = 1;
					}
					if (m.phaseAge > 30) {
						part(a, m);
						part(b, m);
						it.remove();
					}
				}
				default -> {
				}
			}
		}
		// New meetings: two companions of players standing close together, both at ease.
		List<Map.Entry<UUID, State>> all = new ArrayList<>(states.entrySet());
		for (int i = 0; i < all.size(); i++) {
			for (int j = i + 1; j < all.size(); j++) {
				State a = all.get(i).getValue(), b = all.get(j).getValue();
				Player pa = present.get(all.get(i).getKey()), pb = present.get(all.get(j).getKey());
				if (pa == null || pb == null || !ready(a) || !ready(b) || pa.distanceToSqr(pb) > 36
						|| Math.hypot(a.x - b.x, a.z - b.z) > 7 || Math.abs(a.y - b.y) > 1.5 || random.nextInt(30) != 0) {
					continue;
				}
				Meeting m = new Meeting(all.get(i).getKey(), all.get(j).getKey(), a, b, random.nextBoolean());
				a.meeting = m;
				b.meeting = m;
				meetings.add(m);
			}
		}
	}

	/** Free to meet: out, at ease (its player still for a moment), awake, not busy, not met anyone for a while. */
	private static boolean ready(State s) {
		return !s.hidden && s.meeting == null && s.meetCooldown <= 0 && s.idle >= 40 && s.sleep < 0.1f && s.mode != Mode.RIDING
				&& s.action == CompanionModel.NONE;
	}

	private static boolean stillMeeting(State s) {
		return !s.hidden && s.idle > 0 && s.sleep < 0.3f && s.mode != Mode.RIDING;
	}

	private void part(State s, Meeting m) {
		if (s != null && s.meeting == m) {
			s.meeting = null;
			s.target = null;
			s.face = null;
			s.meetCooldown = 1200 + random.nextInt(1200);
		}
	}

	private static void hop(State s, double speed) {
		if (s.onGround) {
			s.vy = speed;
			s.onGround = false;
		}
	}

	// ---------------------------------------------------------------- behaviour

	private void tick(Minecraft mc, ClientLevel level, State s, Player owner) {
		s.save();
		s.age++;
		Companion.Style style = s.look.companion().style;
		double ownerSpeed = Math.hypot(owner.getX() - owner.xOld, owner.getZ() - owner.zOld);
		boolean riding = owner.isPassenger();
		boolean away = owner.isSpectator() || owner.isInvisible() || owner.isFallFlying()
				|| !riding && ownerSpeed > TOO_FAST;
		if (away) {
			if (!s.hidden) {
				if (!owner.isSpectator()) {
					puff(level, s.x, s.y + 0.3, s.z);
				}
				s.hidden = true;
			}
			return;
		}
		if (s.hidden) {
			// Back out next to the player once they are walking (or riding) again.
			if (!riding && !owner.onGround() && !owner.isInWater()) {
				return;
			}
			s.hidden = false;
			appear(level, s, owner);
			puff(level, s.x, s.y + 0.3, s.z);
		}

		// Standing still: sit down after a while, then fall asleep; any move wakes it.
		boolean still = ownerSpeed < 0.02 && Math.abs(owner.getY() - owner.yOld) < 0.02;
		s.idle = still ? s.idle + 1 : 0;
		boolean sleeping = owner.isSleeping() || s.idle > 900;
		s.happy = Math.max(0, s.happy - 0.02f);
		float dark = 1 - level.getMaxLocalRawBrightness(BlockPos.containing(s.x, s.y + 0.5, s.z)) / 15f;
		s.shade = approach(s.shade, dark, 0.01f);
		s.meetCooldown--;

		boolean wasOnGround = s.onGround;
		s.swimming = false;
		double fallSpeed = s.vy;
		if (riding) {
			ride(s, owner);
		} else if (style == Companion.Style.OWL && s.idle > 60 && !owner.isInWater() && s.meeting == null) {
			perch(s, owner);
		} else if (style.movement == Companion.Movement.FLY && (s.idle < 100 || owner.isInWater()) && s.meeting == null) {
			fly(level, s, owner, ownerSpeed);
		} else {
			walk(level, s, owner, ownerSpeed, style.movement);
		}

		double dist = Math.sqrt(s.distanceSq(owner.position()));
		if (!riding && s.mode != Mode.PERCHED && (dist > TELEPORT || s.stuck > 60)) {
			puff(level, s.x, s.y + 0.3, s.z);
			appear(level, s, owner);
			puff(level, s.x, s.y + 0.3, s.z);
		}

		// Smooth poses.
		float sitTarget = s.mode == Mode.RIDING || s.mode == Mode.PERCHED || s.idle > 40 && s.speed < 0.02 && s.meeting == null ? 1 : 0;
		s.sit = approach(s.sit, sleeping ? 0 : sitTarget, 0.1f);
		s.sleep = approach(s.sleep, sleeping && s.mode != Mode.FLYING ? 1 : 0, sleeping ? 0.03f : 0.2f);
		// Wings out when flying; for those that walk, how much they swim.
		s.fly = approach(s.fly, s.mode == Mode.FLYING || s.swimming ? 1 : 0, 0.12f);
		s.walking = approach(s.walking, Mth.clamp((float) s.speed * 8f, 0, 1), 0.25f);
		s.walk += (float) Math.min(s.speed, 0.4) * (style == Companion.Style.GOLEM ? 3.2f : 4.5f);
		s.flap += 0.5f + s.fly * 0.3f;

		// Squash on landing, stretch while rising.
		if (!wasOnGround && s.onGround) {
			s.squash = (float) Math.min(1, Math.abs(fallSpeed) * 2.5 + 0.25);
		} else if (!s.onGround && s.vy > 0.05) {
			s.squash = approach(s.squash, -0.5f, 0.2f);
		} else {
			s.squash *= 0.72f;
		}
		act(level, s, owner);
		look(mc, s, owner);
		pet(mc, level, s);
		ambient(level, s, owner);
	}

	/** Comes out next to the player, on the ground. */
	private void appear(ClientLevel level, State s, Player owner) {
		Vec3 spot = spot(owner, s.look.companion().style.movement == Companion.Movement.FLY ? 1.0 : 0.8, -0.8);
		s.x = spot.x;
		s.z = spot.z;
		Double ground = ground(level, s.x, owner.getY() + 1.5, s.z, 4);
		s.y = ground != null ? ground : owner.getY();
		if (!free(level, s.x, s.y + 0.2, s.z)) {
			s.x = owner.getX();
			s.z = owner.getZ();
			s.y = owner.getY();
		}
		s.vx = s.vy = s.vz = 0;
		s.stuck = 0;
		s.mode = Mode.WALKING;
		s.yaw = heading(owner);
		s.save();
	}

	/** Its place next to the player: {@code side} blocks to the right, {@code back} forward (negative: behind). */
	private static Vec3 spot(Player owner, double side, double back) {
		float yaw = owner.yBodyRot * Mth.DEG_TO_RAD;
		// The player faces (-sin yaw, cos yaw); right of that is (-cos yaw, -sin yaw).
		double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
		double rx = -fz, rz = fx;
		return new Vec3(owner.getX() + rx * side + fx * back, owner.getY(), owner.getZ() + rz * side + fz * back);
	}

	/** Sits behind its player on whatever they ride. */
	private void ride(State s, Player owner) {
		s.mode = Mode.RIDING;
		Vec3 spot = spot(owner, 0, -0.42);
		s.x = spot.x;
		s.y = owner.getY() + (owner.getVehicle() != null && owner.getVehicle().getBbHeight() > 1.2f ? 0.05 : 0.0);
		s.z = spot.z;
		s.vx = s.vy = s.vz = 0;
		s.speed = 0;
		s.yaw = turnTo(s.yaw, heading(owner), 0.5f);
	}

	/** An owl on the player's left shoulder. */
	private void perch(State s, Player owner) {
		s.mode = Mode.PERCHED;
		Vec3 spot = spot(owner, -0.42, 0);
		double y = owner.getY() + (owner.isCrouching() ? 1.15 : 1.4);
		s.x = Mth.lerp(0.35, s.x, spot.x);
		s.y = Mth.lerp(0.35, s.y, y);
		s.z = Mth.lerp(0.35, s.z, spot.z);
		s.speed = 0;
		s.yaw = turnTo(s.yaw, heading(owner), 0.4f);
	}

	/** Flies beside the player's shoulder, a little behind, bobbing. */
	private void fly(ClientLevel level, State s, Player owner, double ownerSpeed) {
		s.mode = Mode.FLYING;
		Vec3 spot = spot(owner, 1.0, -0.6);
		double ty = owner.getY() + 1.5 + Mth.sin(s.age * 0.08f) * 0.15;
		double dx = spot.x - s.x, dy = ty - s.y, dz = spot.z - s.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		double max = Math.max(ownerSpeed * 1.2, 0.08) + (dist > 3 ? 0.3 : 0);
		double speed = Math.min(dist * 0.25, max);
		double nx = dist > 0.01 ? dx / dist * speed : 0, nz = dist > 0.01 ? dz / dist * speed : 0;
		s.vx = Mth.lerp(0.3, s.vx, nx);
		s.vz = Mth.lerp(0.3, s.vz, nz);
		s.vy = Mth.lerp(0.2, s.vy, Mth.clamp(dy * 0.2, -0.3, 0.3));
		double x = s.x + s.vx, y = s.y + s.vy, z = s.z + s.vz;
		// Up and over what is in the way instead of through it.
		if (!free(level, x, y + 0.2, z)) {
			y = s.y + 0.15;
			if (!free(level, x, y + 0.2, z)) {
				x = s.x;
				z = s.z;
			}
		}
		s.x = x;
		s.y = y;
		s.z = z;
		s.speed = Math.hypot(s.vx, s.vz);
		s.yaw = s.speed > 0.03 ? turnTo(s.yaw, (float) Math.atan2(s.vx, s.vz), 0.25f) : turnTo(s.yaw, heading(owner), 0.1f);
		s.onGround = false;
	}

	/** Walks (or hops, or floats) after the player on the ground; steps up, falls down, swims, stops at walls. */
	private void walk(ClientLevel level, State s, Player owner, double ownerSpeed, Companion.Movement movement) {
		boolean landing = s.mode == Mode.FLYING || s.mode == Mode.PERCHED;
		s.mode = Mode.WALKING;
		// Behind you while you walk; next to you when you stand still.
		Vec3 spot = s.target != null ? s.target : s.idle > 30 ? spot(owner, 0.9 * s.side, 0.25) : spot(owner, 0.8, -0.8);
		double dx = spot.x - s.x, dz = spot.z - s.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		double max = Math.max(ownerSpeed * 1.15, s.target != null ? 0.15 : 0.13) + (dist > 4 ? 0.25 : 0);
		double speed = dist > 0.35 ? Math.min(dist * 0.25, max) : 0;
		// Hoppers only move while in the air.
		if (movement == Companion.Movement.HOP && s.onGround && speed > 0.02) {
			s.vy = 0.3;
			s.onGround = false;
		}
		if (s.look.companion().style == Companion.Style.RED_PANDA && s.onGround && dist > 2.5 && random.nextInt(10) == 0) {
			// A quick bound to catch up.
			s.vy = 0.26;
			s.onGround = false;
		}
		if (movement == Companion.Movement.HOP && s.onGround) {
			speed = 0;
		}
		double nx = dist > 0.01 ? dx / dist * speed : 0, nz = dist > 0.01 ? dz / dist * speed : 0;
		s.vx = Mth.lerp(0.35, s.vx, nx);
		s.vz = Mth.lerp(0.35, s.vz, nz);
		double x = s.x + s.vx, z = s.z + s.vz;
		// What is ahead: a wall (two blocks), a step (one), or open ground.
		boolean low = !free(level, x, s.y + 0.3, z);
		boolean high = !free(level, x, s.y + 1.3, z);
		boolean moving = Math.hypot(s.vx, s.vz) > 0.02;
		if (low && high) {
			x = s.x;
			z = s.z;
			s.stuck = moving ? s.stuck + 1 : s.stuck;
		} else {
			s.stuck = Math.max(0, s.stuck - 1);
		}
		if (movement == Companion.Movement.HOVER) {
			if (low && !high) {
				// Floats up the step before going on.
				s.y += 0.2;
				if (!free(level, x, s.y + 0.3, z)) {
					x = s.x;
					z = s.z;
				}
			}
			Double ground = ground(level, x, s.y + 0.9, z, 4);
			double base = ground != null ? ground : s.y - 0.3;
			double water = surface(level, x, base + 0.5, z);
			s.y = Mth.lerp(0.2, s.y, Math.max(base, water) + 0.3);
			s.onGround = true;
		} else if (level.getFluidState(BlockPos.containing(s.x, s.y + 0.25, s.z)).is(FluidTags.WATER)) {
			// Paddle at the surface; climb out where the bank is low.
			s.swimming = true;
			double water = surface(level, s.x, s.y + 0.25, s.z);
			s.vy = 0;
			s.y = Mth.lerp(0.3, s.y, (Double.isInfinite(water) ? s.y + 0.25 : water) - 0.25);
			s.onGround = false;
			if (low && !high) {
				s.y += 0.5;
			}
		} else {
			if (low && !high) {
				// A step: hop, and wait until high enough.
				if (s.onGround) {
					s.vy = 0.42;
					s.onGround = false;
				}
				x = s.x;
				z = s.z;
			}
			Double below = ground(level, x, s.y + 0.5, z, 30);
			if (s.onGround && (below == null || below < s.y - 0.55)) {
				s.onGround = false;
			}
			if (!s.onGround || landing) {
				s.vy = (s.vy - GRAVITY) * 0.98;
				s.y += s.vy;
				if (below != null && s.y <= below) {
					s.y = below;
					s.vy = 0;
					s.onGround = true;
				}
			} else if (below != null) {
				s.y = below;
			}
			// Jump when the player jumps.
			if (s.onGround && owner.getY() - owner.yOld > 0.25 && s.idle == 0) {
				s.vy = 0.38;
				s.onGround = false;
			}
		}
		s.x = x;
		s.z = z;
		s.speed = Math.hypot(x - s.xo, z - s.zo);
		float rest = s.face != null ? (float) Math.atan2(s.face.x - s.x, s.face.z - s.z) : heading(owner);
		s.yaw = moving ? turnTo(s.yaw, (float) Math.atan2(s.vx, s.vz), 0.3f) : turnTo(s.yaw, rest, s.face != null ? 0.25f : 0.04f);
	}

	/**
	 * Things a red panda or a turtle does by itself while you stand still: the panda rears up, nibbles bamboo or washes its
	 * face; the turtle hides in its shell, munches a berry or yawns; the kitten ducks into its box, hops out to stretch,
	 * licks a paw or yawns. Moving or falling asleep stops it.
	 */
	private void act(ClientLevel level, State s, Player owner) {
		Companion.Style style = s.look.companion().style;
		if (style != Companion.Style.RED_PANDA && style != Companion.Style.TURTLE && style != Companion.Style.KITTEN
				&& style != Companion.Style.SHADOW_WOLF) {
			s.action = CompanionModel.NONE;
			return;
		}
		boolean calm = s.sleep < 0.05f && s.mode == Mode.WALKING && s.speed < 0.02 && s.onGround && s.meeting == null;
		if (!calm) {
			s.action = CompanionModel.NONE;
			s.actionAge = 0;
			return;
		}
		if (s.action == CompanionModel.NONE) {
			if (s.idle > 50 && --s.actionCooldown <= 0) {
				int[] choices = style == Companion.Style.SHADOW_WOLF
						? s.shade > 0.5f
						? new int[]{CompanionModel.HOWL, CompanionModel.HOWL, CompanionModel.PLAYBOW, CompanionModel.VANISH, CompanionModel.YAWN}
						: new int[]{CompanionModel.HOWL, CompanionModel.PLAYBOW, CompanionModel.VANISH, CompanionModel.YAWN}
						: style == Companion.Style.KITTEN
						? new int[]{CompanionModel.HIDE, CompanionModel.OUT, CompanionModel.GROOM, CompanionModel.YAWN}
						: style == Companion.Style.TURTLE
						? new int[]{CompanionModel.HIDE, CompanionModel.SNACK, CompanionModel.YAWN}
						: new int[]{CompanionModel.STAND, CompanionModel.SNACK, CompanionModel.GROOM};
				start(s, choices[random.nextInt(choices.length)]);
			}
		} else if (s.action == CompanionModel.VANISH && s.actionAge == s.actionLength / 2) {
			s.actionAge++;
			shadowStep(level, s, owner);
		} else if (++s.actionAge >= s.actionLength) {
			s.action = CompanionModel.NONE;
			s.actionCooldown = 80 + random.nextInt(220);
		}
	}

	private void start(State s, int action) {
		s.action = action;
		s.actionAge = 0;
		s.actionLength = switch (action) {
			case CompanionModel.STAND -> 55;
			case CompanionModel.SNACK -> 130;
			case CompanionModel.HIDE -> 100;
			case CompanionModel.OUT -> 200;
			case CompanionModel.YAWN -> 50;
			case CompanionModel.HOWL -> 90;
			case CompanionModel.PLAYBOW -> 60;
			case CompanionModel.VANISH -> 56;
			default -> 75;
		};
	}

	/** Where its head turns: to its player, or to you when you look at it from close by. */
	private void look(Minecraft mc, State s, Player owner) {
		Vec3 at = s.face != null ? new Vec3(s.face.x, s.face.y + 0.35, s.face.z) : owner.getEyePosition();
		Player viewer = mc.player;
		if (viewer != null && viewer != owner && s.distanceSq(viewer.position()) < 36 && looksAt(viewer, s)) {
			at = viewer.getEyePosition();
		}
		if (s.mode == Mode.PERCHED) {
			// Looks where its player looks, now and then somewhere else.
			at = owner.getEyePosition().add(owner.getLookAngle().scale(4)).add(Mth.sin(s.age * 0.02f) * 2, 0, 0);
		}
		double dx = at.x - s.x, dy = at.y - (s.y + 0.4), dz = at.z - s.z;
		float yaw = Mth.wrapDegrees(((float) Math.atan2(dx, dz) - s.yaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
		float pitch = (float) -Math.atan2(dy, Math.hypot(dx, dz));
		s.headYaw = Mth.lerp(0.2f, s.headYaw, Mth.clamp(yaw, -1.1f, 1.1f));
		s.headPitch = Mth.lerp(0.2f, s.headPitch, Mth.clamp(pitch, -0.6f, 0.5f));
	}

	/** Sneaking next to one and looking at it pets it: hearts (spores for the mushroom), a happy hop. */
	private void pet(Minecraft mc, ClientLevel level, State s) {
		Player you = mc.player;
		if (you == null || !you.isCrouching() || s.distanceSq(you.position()) > 6.25 || !looksAt(you, s)) {
			return;
		}
		Companion.Style kind = s.look.companion().style;
		if (s.happy < 0.3f && kind == Companion.Style.SHADOW_WOLF && s.action == CompanionModel.NONE && s.onGround
				&& s.mode == Mode.WALKING && random.nextInt(2) == 0) {
			// The shadow wolf bows down to play instead of hopping.
			start(s, CompanionModel.PLAYBOW);
		} else if (s.happy < 0.3f && s.onGround && s.mode == Mode.WALKING && s.action != CompanionModel.PLAYBOW) {
			s.vy = 0.3;
			s.onGround = false;
		}
		if (s.happy < 0.3f && (kind == Companion.Style.RED_PANDA || kind == Companion.Style.TURTLE) && s.action == CompanionModel.NONE
				&& random.nextInt(3) == 0) {
			// The red panda rears up, the turtle hides shyly.
			start(s, kind == Companion.Style.TURTLE ? CompanionModel.HIDE : CompanionModel.STAND);
		}
		s.happy = 1;
		s.idle = Math.min(s.idle, 40);
		if (s.age % 8 == 0) {
			double h = CompanionModel.height(s.look.companion().style);
			level.addParticle(ParticleTypes.HEART, s.x + (random.nextDouble() - 0.5) * 0.4, s.y + h + 0.1,
					s.z + (random.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
			if (s.look.companion().style == Companion.Style.MUSHROOM) {
				for (int i = 0; i < 4; i++) {
					level.addParticle(ParticleTypes.WHITE_ASH, s.x + (random.nextDouble() - 0.5) * 0.6, s.y + h,
							s.z + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
				}
			}
		}
	}

	/** Small touches: a rain cloud drips in the rain, a snowman leaves a few flakes, a golem's steps thud. */
	private void ambient(ClientLevel level, State s, Player owner) {
		Companion.Style style = s.look.companion().style;
		if (style == Companion.Style.CLOUD && level.isRainingAt(BlockPos.containing(s.x, s.y + 1, s.z)) && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.FALLING_WATER, s.x + (random.nextDouble() - 0.5) * 0.4, s.y + 0.15,
					s.z + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
		}
		if (style == Companion.Style.SNOWMAN && s.speed > 0.05 && random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.SNOWFLAKE, s.x, s.y + 0.1, s.z, (random.nextDouble() - 0.5) * 0.02, 0.02,
					(random.nextDouble() - 0.5) * 0.02);
		}
		if (style == Companion.Style.SHADOW_WOLF) {
			// Smoke (or embers, soul flames, end sparks) off its paws, more in the dark; rising from its muzzle as it howls.
			if (s.speed > 0.05 ? random.nextInt(6) == 0 : random.nextInt(Math.round(40 - s.shade * 25)) == 0) {
				level.addParticle(wolfParticle(s.look.companion()), s.x + (random.nextDouble() - 0.5) * 0.4, s.y + 0.05,
						s.z + (random.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
			}
			if (s.action == CompanionModel.HOWL && s.actionBlend(0) > 0.6f && s.age % 3 == 0) {
				ParticleOptions howl = s.look.companion() == Companion.SHADOW_WOLF_NIGHT ? ParticleTypes.END_ROD : wolfParticle(s.look.companion());
				level.addParticle(howl, s.x + Mth.sin(s.yaw) * 0.35 + (random.nextDouble() - 0.5) * 0.1, s.y + 0.85,
						s.z + Mth.cos(s.yaw) * 0.35 + (random.nextDouble() - 0.5) * 0.1, (random.nextDouble() - 0.5) * 0.02, 0.06,
						(random.nextDouble() - 0.5) * 0.02);
			}
		}
	}

	private static ParticleOptions wolfParticle(Companion companion) {
		return switch (companion) {
			case SHADOW_WOLF_VOID -> ParticleTypes.REVERSE_PORTAL;
			case SHADOW_WOLF_EMBER -> ParticleTypes.SMALL_FLAME;
			case SHADOW_WOLF_SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
			default -> ParticleTypes.SMOKE;
		};
	}

	/** The shadow wolf melts into the shadows and steps out on its player's other side. */
	private void shadowStep(ClientLevel level, State s, Player owner) {
		Vec3 spot = spot(owner, -0.9 * s.side, 0.25);
		Double ground = ground(level, spot.x, owner.getY() + 1.5, spot.z, 4);
		if (ground == null || !free(level, spot.x, ground + 0.2, spot.z) || !free(level, spot.x, ground + 0.6, spot.z)) {
			return;
		}
		s.side = -s.side;
		ParticleOptions smoke = wolfParticle(s.look.companion());
		poof(level, s.x, s.y + 0.2, s.z, ParticleTypes.LARGE_SMOKE, 5);
		poof(level, s.x, s.y + 0.2, s.z, smoke, 6);
		s.x = s.xo = spot.x;
		s.y = s.yo = ground;
		s.z = s.zo = spot.z;
		s.vx = s.vy = s.vz = 0;
		s.yaw = s.yawO = (float) Math.atan2(owner.getX() - s.x, owner.getZ() - s.z);
		poof(level, s.x, s.y + 0.2, s.z, ParticleTypes.LARGE_SMOKE, 5);
		poof(level, s.x, s.y + 0.2, s.z, smoke, 6);
	}

	/** Whether this player is looking at the companion (its middle within a small cone). */
	private static boolean looksAt(Player player, State s) {
		Vec3 eye = player.getEyePosition();
		Vec3 to = new Vec3(s.x - eye.x, s.y + 0.3 - eye.y, s.z - eye.z);
		double dist = to.length();
		return dist > 0.01 && player.getLookAngle().dot(to.scale(1 / dist)) > 1 - 0.18 / Math.max(1, dist);
	}

	private static float heading(Player owner) {
		float yaw = owner.yBodyRot * Mth.DEG_TO_RAD;
		return (float) Math.atan2(-Mth.sin(yaw), Mth.cos(yaw));
	}

	private static float turnTo(float from, float to, float rate) {
		return from + Mth.wrapDegrees((to - from) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * rate;
	}

	private static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	/** The top of what can be stood on below {@code fromY} (within {@code depth}), or null. */
	private static Double ground(ClientLevel level, double x, double fromY, double z, double depth) {
		int bx = Mth.floor(x), bz = Mth.floor(z);
		int top = Mth.floor(fromY);
		int bottom = Mth.floor(fromY - depth);
		for (int by = top; by >= bottom; by--) {
			BlockPos pos = new BlockPos(bx, by, bz);
			BlockState state = level.getBlockState(pos);
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				double surface = by + shape.max(net.minecraft.core.Direction.Axis.Y);
				if (surface <= fromY + 0.01) {
					return surface;
				}
			}
		}
		return null;
	}

	/** The top of the water at this spot, or negative infinity if there is none. */
	private static double surface(ClientLevel level, double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		if (!level.getFluidState(pos).is(FluidTags.WATER)) {
			pos = pos.below();
			if (!level.getFluidState(pos).is(FluidTags.WATER)) {
				return Double.NEGATIVE_INFINITY;
			}
		}
		while (level.getFluidState(pos.above()).is(FluidTags.WATER) && pos.getY() < y + 4) {
			pos = pos.above();
		}
		return pos.getY() + level.getFluidState(pos).getHeight(level, pos);
	}

	/** Whether nothing solid is at this point. */
	private static boolean free(ClientLevel level, double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
		return shape.isEmpty() || !shape.bounds().move(pos).contains(x, y, z);
	}

	private void puff(ClientLevel level, double x, double y, double z) {
		poof(level, x, y, z, ParticleTypes.POOF, 6);
	}

	private void poof(ClientLevel level, double x, double y, double z, ParticleOptions type, int count) {
		for (int i = 0; i < count; i++) {
			level.addParticle(type, x + (random.nextDouble() - 0.5) * 0.5, y + random.nextDouble() * 0.4,
					z + (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.04, 0.02,
					(random.nextDouble() - 0.5) * 0.04);
		}
	}

	// ---------------------------------------------------------------- drawing

	void render(LevelRenderContext context, Minecraft mc, float partial) {
		if (states.isEmpty() || mc.level == null) {
			return;
		}
		Vec3 eye = mc.gameRenderer.mainCamera().position();
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		List<Runnable> names = new ArrayList<>();
		for (Map.Entry<UUID, State> entry : states.entrySet()) {
			State s = entry.getValue();
			if (s.hidden) {
				continue;
			}
			boolean own = mc.player != null && entry.getKey().equals(mc.player.getUUID());
			if (own && firstPerson && s.mode == Mode.PERCHED) {
				// Right next to your eyes: it would hide half the view.
				continue;
			}
			double x = Mth.lerp(partial, s.xo, s.x);
			double y = Mth.lerp(partial, s.yo, s.y);
			double z = Mth.lerp(partial, s.zo, s.z);
			double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
			if (dx * dx + dy * dy + dz * dz > 96 * 96) {
				continue;
			}
			float yaw = Mth.rotLerp(partial, s.yawO * Mth.RAD_TO_DEG, s.yaw * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
			CompanionModel.Pose pose = s.pose(partial);
			int light = LightCoordsUtil.getLightCoords(mc.level, BlockPos.containing(x, y + 0.3, z));
			Companion.Look look = s.look;
			PoseStack poseStack = context.poseStack();
			poseStack.pushPose();
			poseStack.translate(dx, dy, dz);
			poseStack.mulPose(new org.joml.Matrix4f().rotationY(yaw));
			context.submitNodeCollector().submitCustomGeometry(poseStack, Voxels.TYPE,
					(p, vc) -> CompanionModel.draw(vc, p, look, pose, light));
			poseStack.popPose();
			if (!look.name().isBlank() && dx * dx + dy * dy + dz * dz < NAME_RANGE * NAME_RANGE) {
				float height = CompanionModel.height(look.companion().style) * (1 - pose.sleep() * 0.2f) + 0.05f;
				names.add(() -> {
					poseStack.pushPose();
					poseStack.translate(dx, dy, dz);
					context.submitNodeCollector().submitNameTag(poseStack, new Vec3(0, height, 0), 0,
							Component.literal(look.name()), true, light, context.levelState().cameraRenderState);
					poseStack.popPose();
				});
			}
		}
		names.forEach(Runnable::run);
	}

	// ---------------------------------------------------------------- state

	enum Mode {
		WALKING, FLYING, RIDING, PERCHED
	}

	/** One companion: where it is and how it moves. */
	static final class State {
		Companion.Look look;
		Mode mode = Mode.WALKING;
		double x, y, z, xo, yo, zo, vx, vy, vz, speed;
		float yaw, yawO, headYaw, headYawO, headPitch, headPitchO;
		float walk, walkO, walking, walkingO, sit, sitO, sleep, sleepO, fly, flyO, flap, flapO, happy, squash, squashO;
		boolean onGround = true, hidden, swimming;
		int age, idle, stuck;
		/** What it does by itself ({@link CompanionModel#STAND} and so on), for how long so far and in all, and until the next. */
		int action, actionAge, actionLength, actionCooldown = 100;
		/** Meeting another companion: the meeting, where to walk, whom to face; and how long until the next. */
		Meeting meeting;
		Vec3 target;
		State face;
		int meetCooldown = 100;
		/** Which side of its player it sits on (1 right, -1 left; the shadow wolf swaps by stepping through the shadows); how dark it is around it (0 to 1). */
		double side = 1;
		float shade;

		State(Companion.Look look, Player owner) {
			this.look = look;
			this.x = owner.getX();
			this.y = owner.getY();
			this.z = owner.getZ();
			save();
		}

		void save() {
			xo = x;
			yo = y;
			zo = z;
			yawO = yaw;
			headYawO = headYaw;
			headPitchO = headPitch;
			walkO = walk;
			walkingO = walking;
			sitO = sit;
			sleepO = sleep;
			flyO = fly;
			flapO = flap;
			squashO = squash;
		}

		double distanceSq(Vec3 to) {
			double dx = to.x - x, dy = to.y - y, dz = to.z - z;
			return dx * dx + dy * dy + dz * dz;
		}

		CompanionModel.Pose pose(float partial) {
			return new CompanionModel.Pose(Mth.lerp(partial, walkO, walk), Mth.lerp(partial, walkingO, walking),
					Mth.lerp(partial, sitO, sit), Mth.lerp(partial, sleepO, sleep), Mth.lerp(partial, flyO, fly),
					Mth.lerp(partial, flapO, flap), Mth.lerp(partial, headYawO, headYaw),
					Mth.lerp(partial, headPitchO, headPitch), happy, Mth.lerp(partial, squashO, squash), age + partial, action,
					action == CompanionModel.NONE ? 0 : Math.min(1, (actionAge + partial) / actionLength), actionBlend(partial), shade);
		}

		/** How much of the action shows: easing in over its first half second and out over its last. */
		float actionBlend(float partial) {
			if (action == CompanionModel.NONE) {
				return 0;
			}
			float t = actionAge + partial;
			return Mth.clamp(Math.min(t, actionLength - t) / 10f, 0, 1);
		}
	}
}
