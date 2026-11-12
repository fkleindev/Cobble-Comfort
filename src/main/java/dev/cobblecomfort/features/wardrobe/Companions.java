package dev.cobblecomfort.features.wardrobe;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.core.Voxels;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
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
	/** Resting, it gets up to follow once its player is further away than this (blocks). */
	private static final double LEASH = 4.8;
	/** Following, how far behind its player it keeps (blocks). */
	private static final double FOLLOW = 3.2;

	private final Map<UUID, State> states = new HashMap<>();
	private final RandomSource random = RandomSource.create();

	void clear() {
		states.clear();
		meetings.clear();
	}

	int count() {
		return states.size();
	}

	/** Where the companions are that can be seen. */
	List<Vec3> positions() {
		List<Vec3> out = new ArrayList<>();
		for (State s : states.values()) {
			if (!s.hidden) {
				out.add(new Vec3(s.x, s.y + 0.3, s.z));
			}
		}
		return out;
	}

	/** The nearest butterfly within {@code range} of a companion, or null. */
	private static Vec3 butterfly(State s, double range) {
		Vec3 best = null;
		double bestDistance = range * range;
		for (Vec3 at : dev.cobblecomfort.features.atmosphere.AtmosphereFeature.INSTANCE.butterflies()) {
			double d = s.distanceSq(at);
			if (d < bestDistance) {
				bestDistance = d;
				best = at;
			}
		}
		return best;
	}

	/** The companion following this player now, for tests; null for none. */
	State state(UUID player) {
		return states.get(player);
	}

	/** Where the settings are (your mech's weathering is kept there). */
	private WardrobeFeature wardrobe;

	void tick(Minecraft mc, ClientLevel level, WardrobeFeature wardrobe) {
		this.wardrobe = wardrobe;
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
		catsWatch(level);
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
		// Those with wings keep up with a player on elytra; the rest wait out of sight.
		boolean chasing = style.movement == Companion.Movement.FLY && owner.isFallFlying() && !owner.isSpectator()
				&& !owner.isInvisible();
		boolean away = !chasing && (owner.isSpectator() || owner.isInvisible() || owner.isFallFlying()
				|| !riding && ownerSpeed > TOO_FAST);
		if (chasing && s.hidden) {
			s.hidden = false;
			Vec3 spot = chaseSpot(s, owner);
			s.x = spot.x;
			s.y = spot.y;
			s.z = spot.z;
			s.save();
			puff(level, s.x, s.y + 0.3, s.z);
		}
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

		// Following or resting: once its player stands still it settles down where it is (then sits, then falls asleep),
		// and it only gets up to follow again once they have gone a few steps away (or up or down), not at every little
		// move. A perched owl takes off as soon as they move.
		boolean still = ownerSpeed < 0.02 && Math.abs(owner.getY() - owner.yOld) < 0.02;
		s.ownerStill = still ? s.ownerStill + 1 : 0;
		if (s.following) {
			if (s.ownerStill > 10 && !riding) {
				s.following = false;
			}
		} else if (riding || Math.hypot(owner.getX() - s.x, owner.getZ() - s.z) > LEASH || Math.abs(owner.getY() - s.y) > 2.2
				|| s.mode == Mode.PERCHED && !still) {
			s.following = true;
			s.rest = null;
		}
		s.idle = s.following ? 0 : s.idle + 1;
		notice(level, s, owner, riding);
		if (s.beg) {
			s.idle = 0;
		}
		// By a campfire it dozes off sooner.
		boolean sleeping = owner.isSleeping() || !s.beg && s.idle > (s.fire != null ? 500 : 900);
		s.happy = Math.max(0, s.happy - 0.02f);
		float dark = 1 - level.getMaxLocalRawBrightness(BlockPos.containing(s.x, s.y + 0.5, s.z)) / 15f;
		s.shade = approach(s.shade, dark, 0.01f);
		if (style == Companion.Style.MECH || s.look.companion() == Companion.LANTERN_COPPER) {
			weather(level, s, owner == mc.player);
		}
		if (style == Companion.Style.LANTERN) {
			lanternMood(level, s, owner);
		} else if (style == Companion.Style.WISP) {
			// In the Nether it flickers.
			s.fear = approach(s.fear, level.dimension() == Level.NETHER ? 1 : 0, 0.05f);
		}
		s.meetCooldown--;

		boolean wasOnGround = s.onGround;
		s.swimming = false;
		s.galloping = false;
		double fallSpeed = s.vy;
		if (chasing) {
			chase(level, s, owner);
		} else if (riding) {
			ride(s, owner);
		} else if (style == Companion.Style.LANTERN && !sleeping && s.meeting == null && !s.beg && s.curious == null && s.fire == null
				&& (s.cave > 0.5f || s.close)) {
			// Lighting the way round its player in a cave; close in when they are badly hurt or dread is near.
			circle(level, s, owner, ownerSpeed);
		} else if (style == Companion.Style.WISP && s.meeting == null) {
			ghost(level, s, owner, ownerSpeed);
		} else if (style == Companion.Style.OWL && s.idle > 60 && !owner.isInWater() && s.meeting == null) {
			perch(s, owner);
		} else if (style.movement == Companion.Movement.FLY && (s.idle < 100 || owner.isInWater()) && s.meeting == null && !s.beg) {
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
		float sitTarget = s.mode == Mode.RIDING || s.mode == Mode.PERCHED || s.idle > 40 && s.speed < 0.02 && s.meeting == null
				|| s.beg && s.speed < 0.02 ? 1 : 0;
		s.sit = approach(s.sit, sleeping ? 0 : sitTarget, 0.1f);
		s.sleep = approach(s.sleep, sleeping && (s.mode != Mode.FLYING || style == Companion.Style.WISP) ? 1 : 0, sleeping ? 0.03f : 0.2f);
		// Wings out when flying; for those that walk, how much they swim.
		s.fly = approach(s.fly, s.mode == Mode.FLYING || s.swimming || s.rolling ? 1 : 0, s.rolling ? 0.2f : 0.12f);
		s.walking = approach(s.walking, Mth.clamp((float) s.speed * 8f, 0, 1), 0.25f);
		s.walk += (float) Math.min(s.speed, 0.4) * (style == Companion.Style.GOLEM || style == Companion.Style.MECH ? 3.2f : style == Companion.Style.SALAMANDER ? 5.5f : 4.5f)
				* (s.galloping ? 1.3f : 1f);
		// A whirlwind runs wild after a sprinting player.
		s.dash = approach(s.dash, chasing || style == Companion.Style.WIND && owner.isSprinting() && s.following ? 1 : 0, 0.08f);
		if (!chasing) {
			s.pitch = approach(s.pitch, 0, 0.1f);
		}
		s.flap += style == Companion.Style.WIND ? (0.5f + s.dash * 0.9f) * (1 - 0.7f * s.sleep) : 0.5f + s.fly * 0.3f + s.dash * 0.7f;

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
		drip(level, s);
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
		s.thread.clear();
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

	/**
	 * A place to rest near {@code wanted}: on the ground, with room to stand, at the player's height (give or take a
	 * step). If there is none there, another spot round the player; if none at all, close beside them.
	 */
	private Vec3 restingPlace(ClientLevel level, Player owner, Vec3 wanted) {
		for (int i = 0; i < 6; i++) {
			Vec3 at = wanted;
			if (i > 0) {
				float angle = random.nextFloat() * Mth.TWO_PI;
				double r = 2.2 + random.nextDouble() * 1.4;
				at = owner.position().add(Mth.cos(angle) * r, 0, Mth.sin(angle) * r);
			}
			Double ground = ground(level, at.x, owner.getY() + 1.2, at.z, 2.5);
			if (ground != null && free(level, at.x, ground + 0.2, at.z) && free(level, at.x, ground + 0.7, at.z)) {
				return new Vec3(at.x, ground, at.z);
			}
		}
		return spot(owner, 0.9, 0.25);
	}

	/** Whether stepping to ({@code x}, {@code z}) would go down more than a step from {@code floor}. */
	private static boolean drop(ClientLevel level, double x, double z, double y, double floor) {
		Double ground = ground(level, x, y + 0.5, z, 30);
		return ground == null || ground < floor - 1.1;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
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

	/** Flies off to the player's side, behind and below head height, bobbing; out of the way of their view. */
	private void fly(ClientLevel level, State s, Player owner, double ownerSpeed) {
		s.mode = Mode.FLYING;
		Vec3 spot = spot(owner, 2.2, -1.9);
		double ty = owner.getY() + 1.2 + Mth.sin(s.age * 0.08f) * 0.15;
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

	/** Where it races along beside a player on elytra: off to the side, now falling behind, now pulling up level. */
	private static Vec3 chaseSpot(State s, Player owner) {
		Vec3 dir = new Vec3(owner.getX() - owner.xOld, owner.getY() - owner.yOld, owner.getZ() - owner.zOld);
		dir = dir.lengthSqr() > 1.0E-4 ? dir.normalize() : owner.getLookAngle();
		Vec3 right = new Vec3(-dir.z, 0, dir.x);
		right = right.lengthSqr() > 1.0E-4 ? right.normalize().scale(s.side) : new Vec3(s.side, 0, 0);
		double back = 1.6 + Mth.sin(s.age * 0.045f) * 1.2;
		double up = 0.4 + Mth.sin(s.age * 0.07f) * 0.35;
		return owner.position().add(right.scale(1.9)).subtract(dir.scale(back)).add(0, up, 0);
	}

	/** Races after its player on elytra: carried along at their speed, closing in on its place beside them, tipped into the dive. */
	private void chase(ClientLevel level, State s, Player owner) {
		s.mode = Mode.FLYING;
		Vec3 spot = chaseSpot(s, owner);
		double ox = owner.getX() - owner.xOld, oy = owner.getY() - owner.yOld, oz = owner.getZ() - owner.zOld;
		double vx = ox + (spot.x - s.x) * 0.22, vy = oy + (spot.y - s.y) * 0.22, vz = oz + (spot.z - s.z) * 0.22;
		s.vx = Mth.lerp(0.6, s.vx, vx);
		s.vy = Mth.lerp(0.6, s.vy, vy);
		s.vz = Mth.lerp(0.6, s.vz, vz);
		s.x += s.vx;
		s.y += s.vy;
		s.z += s.vz;
		s.speed = Math.hypot(s.vx, s.vz);
		s.stuck = 0;
		s.onGround = false;
		if (s.speed > 0.05) {
			s.yaw = turnTo(s.yaw, (float) Math.atan2(s.vx, s.vz), 0.35f);
		}
		// Nose down into a dive, up when climbing.
		float tip = (float) Mth.clamp(Math.atan2(-s.vy, Math.max(s.speed, 0.05)), -0.9, 0.9);
		s.pitch = s.pitch + (tip - s.pitch) * 0.25f;
		// Air streaming off its wing tips.
		if (s.dash > 0.5f && s.age % 2 == 0) {
			double cos = Mth.cos(s.yaw), sin = Mth.sin(s.yaw);
			double span = s.look.companion().style == Companion.Style.DRAGON ? 0.45 : 0.4;
			for (int side = -1; side <= 1; side += 2) {
				level.addParticle(ParticleTypes.CLOUD, s.x + cos * span * side, s.y + 0.35, s.z - sin * span * side, 0, 0, 0);
			}
		}
	}

	/**
	 * A lantern spirit lighting the way: circling its player at shoulder height, closer in where the circle would go into
	 * the rock (behind them in a narrow tunnel), facing them as it goes round; close beside them when they are badly hurt
	 * or dread is near.
	 */
	private void circle(ClientLevel level, State s, Player owner, double ownerSpeed) {
		s.mode = Mode.FLYING;
		s.orbit += (s.close ? 0.05 : 0.03) * s.side;
		double r = s.close ? 0.95 : 1.7;
		double ty = owner.getY() + (s.close ? 1.1 : 1.45) + Mth.sin(s.age * 0.09f) * 0.12;
		double tx = Double.NaN, tz = 0;
		for (double rr = r; rr >= 0.6; rr -= 0.35) {
			double x = owner.getX() + Math.cos(s.orbit) * rr, z = owner.getZ() + Math.sin(s.orbit) * rr;
			if (free(level, x, ty, z) && free(level, x, ty + 0.4, z)) {
				tx = x;
				tz = z;
				break;
			}
		}
		if (Double.isNaN(tx)) {
			Vec3 behind = spot(owner, 0, -1.0);
			tx = behind.x;
			tz = behind.z;
			ty = owner.getY() + 1.2;
		}
		double dx = tx - s.x, dy = ty - s.y, dz = tz - s.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		double max = Math.max(ownerSpeed * 1.4, 0.1) + (dist > 3 ? 0.3 : 0);
		double speed = Math.min(dist * 0.3, max);
		s.vx = Mth.lerp(0.35, s.vx, dist > 0.01 ? dx / dist * speed : 0);
		s.vz = Mth.lerp(0.35, s.vz, dist > 0.01 ? dz / dist * speed : 0);
		s.vy = Mth.lerp(0.25, s.vy, Mth.clamp(dy * 0.25, -0.3, 0.3));
		double x = s.x + s.vx, y = s.y + s.vy, z = s.z + s.vz;
		if (!free(level, x, y + 0.1, z) || !free(level, x, y + 0.5, z)) {
			// Not through the rock: up or down only, else it waits (and pops over once left too far behind).
			x = s.x;
			z = s.z;
			if (!free(level, x, y + 0.1, z) || !free(level, x, y + 0.5, z)) {
				y = s.y;
			}
		}
		s.x = x;
		s.y = y;
		s.z = z;
		s.speed = Math.hypot(s.vx, s.vz);
		s.onGround = false;
		s.yaw = ownerSpeed > 0.08 && s.speed > 0.05 ? turnTo(s.yaw, (float) Math.atan2(s.vx, s.vz), 0.25f)
				: turnTo(s.yaw, (float) Math.atan2(owner.getX() - s.x, owner.getZ() - s.z), 0.2f);
	}

	/**
	 * A wisp glides after its player through the air and through walls alike: off to their side, a little behind, at
	 * waist height. Resting, it drifts about its place (by the fire, at what caught its eye, in front of them while they
	 * eat), and now and then slips into a wall nearby, only its face peeking out, before it comes back.
	 */
	private void ghost(ClientLevel level, State s, Player owner, double ownerSpeed) {
		s.mode = Mode.FLYING;
		if (!s.following && s.rest == null) {
			// Settles where it is, unless that is right next to its player.
			Vec3 here = new Vec3(s.x, owner.getY(), s.z);
			s.rest = horizontal(here, owner.position()) >= 1.8 ? here : spot(owner, 2.4 * s.side, 0.3);
			s.restAge = 0;
		}
		if (!s.following && s.haunt == null && !roomy(level, s.rest.x, s.rest.y + 0.75, s.rest.z)) {
			// Not to rest in or against a wall (drifting about, it would be half in it): out to a free place near its player.
			s.rest = restingPlace(level, owner, spot(owner, 2.2 * s.side, 0.3));
		}
		if (s.following || s.beg) {
			s.haunt = null;
		} else if (s.haunt == null && s.fire == null && s.curious == null && s.idle > 120 && --s.hauntCooldown <= 0) {
			s.haunt = wall(level, s.rest.add(0, 0.75, 0));
			s.hauntAge = 0;
			s.hauntCooldown = 400 + random.nextInt(600);
		}
		if (s.haunt != null && ++s.hauntAge > 110) {
			s.haunt = null;
		}
		// Another player coming close: it ducks away, slips over to them and pops up in front of them: boo.
		if (s.booVictim != null && (s.following || s.beg || s.action != CompanionModel.HIDE || !s.booVictim.isAlive())) {
			s.booVictim = null;
		}
		if (s.booVictim == null && !s.following && !s.beg && s.haunt == null && s.action == CompanionModel.NONE && s.sleep < 0.05f
				&& --s.booCooldown <= 0) {
			s.booCooldown = 10;
			for (Player other : level.players()) {
				if (other != owner && !other.isSpectator() && !other.isInvisible() && other.distanceToSqr(s.x, s.y, s.z) < 3.5 * 3.5) {
					s.booVictim = other;
					start(s, CompanionModel.HIDE);
					s.booCooldown = 600 + random.nextInt(600);
					break;
				}
			}
		}
		double tx, ty, tz;
		if (s.beg) {
			Vec3 spot = spot(owner, 0.25 * s.side, 1.2);
			tx = spot.x;
			ty = owner.getY() + 0.6;
			tz = spot.z;
		} else if (s.following) {
			Vec3 spot = spot(owner, 1.7 * s.side, -1.7);
			tx = spot.x;
			ty = owner.getY() + 0.75 + Mth.sin(s.age * 0.06f) * 0.1;
			tz = spot.z;
		} else if (s.booVictim != null) {
			// Right in front of their face.
			Vec3 look = s.booVictim.getLookAngle().multiply(1, 0, 1);
			look = look.lengthSqr() > 1.0E-4 ? look.normalize() : new Vec3(0, 0, 1);
			tx = s.booVictim.getX() + look.x * 0.9;
			ty = s.booVictim.getY() + 0.9;
			tz = s.booVictim.getZ() + look.z * 0.9;
		} else if (s.haunt != null) {
			tx = s.haunt.x;
			ty = s.haunt.y;
			tz = s.haunt.z;
		} else {
			// Drifting about its place.
			tx = s.rest.x + Mth.sin(s.age * 0.021f) * 0.3;
			ty = s.rest.y + 0.75 + Mth.sin(s.age * 0.05f) * 0.1;
			tz = s.rest.z + Mth.cos(s.age * 0.017f) * 0.3;
		}
		double dx = tx - s.x, dy = ty - s.y, dz = tz - s.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		double max = Math.max(ownerSpeed * 1.25, s.booVictim != null ? 0.14 : 0.06) + (dist > 3 ? 0.3 : 0);
		double speed = Math.min(dist * 0.2, max);
		s.vx = Mth.lerp(0.25, s.vx, dist > 0.01 ? dx / dist * speed : 0);
		s.vz = Mth.lerp(0.25, s.vz, dist > 0.01 ? dz / dist * speed : 0);
		s.vy = Mth.lerp(0.2, s.vy, Mth.clamp(dy * 0.15, -0.25, 0.25));
		s.x += s.vx;
		s.y += s.vy;
		s.z += s.vz;
		s.speed = Math.hypot(s.vx, s.vz);
		s.onGround = false;
		s.stuck = 0;
		// Facing where it glides; resting, its player (peeking out of a wall too) or what it warms itself at or sniffs.
		Vec3 toward = s.booVictim != null ? s.booVictim.position() : s.curious != null ? s.curious : s.fire;
		float rest = toward != null ? (float) Math.atan2(toward.x - s.x, toward.z - s.z)
				: (float) Math.atan2(owner.getX() - s.x, owner.getZ() - s.z);
		s.yaw = s.speed > 0.03 && s.haunt == null && s.booVictim == null ? turnTo(s.yaw, (float) Math.atan2(s.vx, s.vz), 0.2f) : turnTo(s.yaw, rest, 0.12f);
	}

	/** Whether there is room round this point to drift about: nothing solid there or half a block to each side. */
	private static boolean roomy(ClientLevel level, double x, double y, double z) {
		return free(level, x, y, z) && free(level, x + 0.5, y, z) && free(level, x - 0.5, y, z) && free(level, x, y, z + 0.5)
				&& free(level, x, y, z - 0.5);
	}

	/**
	 * A spot just inside a wall next to {@code from} (a little way into the first solid block in some direction), for a
	 * wisp to peek out of; null if there is no wall close by.
	 */
	private Vec3 wall(ClientLevel level, Vec3 from) {
		float start = random.nextFloat() * Mth.TWO_PI;
		for (int i = 0; i < 8; i++) {
			float angle = start + i * Mth.TWO_PI / 8;
			double ux = Mth.cos(angle), uz = Mth.sin(angle);
			for (double d = 0.6; d <= 2.6; d += 0.2) {
				if (!free(level, from.x + ux * d, from.y, from.z + uz * d)) {
					return new Vec3(from.x + ux * (d + 0.3), from.y - 0.2, from.z + uz * (d + 0.3));
				}
			}
		}
		return null;
	}

	/** Walks (or hops, or floats) after the player on the ground; steps up, falls down, swims, stops at walls. */
	private void walk(ClientLevel level, State s, Player owner, double ownerSpeed, Companion.Movement movement) {
		boolean landing = s.mode == Mode.FLYING || s.mode == Mode.PERCHED;
		s.mode = Mode.WALKING;
		// Behind you while you walk; when you stand still, at a resting place a little away from you, fixed in the world
		// (looking around does not move it), now and then strolling over to another one.
		Vec3 spot;
		boolean strolling = false;
		boolean settled = s.fire != null || s.curious != null;
		if (s.target != null) {
			spot = s.target;
		} else if (s.beg) {
			// Begging: right in front of its player, looking up at the food.
			spot = spot(owner, 0.25 * s.side, 1.25);
		} else if (!s.following) {
			Vec3 feet = owner.position();
			if (s.rest == null || horizontal(s.rest, feet) > LEASH + 0.5 || !settled && horizontal(s.rest, feet) < 1.8) {
				// Where it is now, if that is a good distance away; else beside its player.
				double away = Math.hypot(s.x - feet.x, s.z - feet.z);
				Vec3 here = new Vec3(s.x, s.y, s.z);
				s.rest = s.rest == null && away > 2.2 && away < LEASH && s.onGround ? here
						: restingPlace(level, owner, spot(owner, 2.6 * s.side, 0.4));
				s.restAge = 0;
			} else if (++s.restAge > 200 && !settled && s.action == CompanionModel.NONE && s.idle < 750 && s.sleep < 0.05f
					&& random.nextInt(260) == 0) {
				// Round the player from where it is now, a little to one side or the other (never across past them).
				float angle = (float) Math.atan2(s.z - feet.z, s.x - feet.x) + (random.nextBoolean() ? 1 : -1) * (0.4f + random.nextFloat() * 0.9f);
				double r = 2.3 + random.nextDouble() * 1.6;
				s.rest = restingPlace(level, owner, feet.add(Mth.cos(angle) * r, 0, Mth.sin(angle) * r));
				s.restAge = 0;
			}
			spot = s.rest;
			strolling = true;
		} else {
			s.rest = null;
			// Trailing a few blocks after its player like a dog on a long lead: towards them only while further away
			// than that, never crowding them.
			double ax = s.x - owner.getX(), az = s.z - owner.getZ();
			double apart = Math.hypot(ax, az);
			spot = apart > FOLLOW ? new Vec3(owner.getX() + ax / apart * FOLLOW, owner.getY(), owner.getZ() + az / apart * FOLLOW)
					: new Vec3(s.x, s.y, s.z);
		}
		double dx = spot.x - s.x, dz = spot.z - s.z;
		double dist = Math.sqrt(dx * dx + dz * dz);
		double max = Math.max(ownerSpeed * 1.15, s.target != null ? 0.15 : strolling && dist < 4 ? 0.07 : 0.13) + (dist > 4 ? 0.25 : 0);
		// A hedgehog far behind curls up and rolls after you; a penguin slides on its belly over snow and ice.
		Companion.Style kind = s.look.companion().style;
		boolean canRoll = s.target == null && !strolling && s.onGround && !s.swimming
				&& (kind == Companion.Style.HEDGEHOG || kind == Companion.Style.PENGUIN && slippery(level, s));
		s.rolling = canRoll && (s.rolling ? dist > 0.9 : dist > 2.6);
		if (s.rolling) {
			max *= 1.35;
		}
		double speed = dist > 0.35 ? Math.min(dist * 0.4, max) : 0;
		// Its player sprinting: it gallops after them in long bounds (hoppers leap higher; the mech skates on its
		// rocket boots instead).
		s.galloping = owner.isSprinting() && !strolling && s.target == null && !s.beg && !s.rolling && speed > 0.12
				&& movement != Companion.Movement.HOVER && !s.swimming;
		if (s.galloping && s.onGround && movement == Companion.Movement.WALK && kind != Companion.Style.MECH && kind != Companion.Style.WIND) {
			s.vy = 0.24;
			s.onGround = false;
			BlockState under = level.getBlockState(BlockPos.containing(s.x, s.y - 0.1, s.z));
			if (!under.isAir()) {
				for (int i = 0; i < 2; i++) {
					level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, under), s.x + (random.nextDouble() - 0.5) * 0.4, s.y + 0.05,
							s.z + (random.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
				}
			}
		}
		if (s.galloping && kind == Companion.Style.MECH && s.age % 2 == 0) {
			float back = s.yaw + Mth.PI;
			level.addParticle(ParticleTypes.SMALL_FLAME, s.x + Mth.sin(back) * 0.25, s.y + 0.05, s.z + Mth.cos(back) * 0.25, 0, 0.01, 0);
		}
		// Hoppers only move while in the air.
		if (movement == Companion.Movement.HOP && s.onGround && speed > 0.02) {
			s.vy = s.galloping ? 0.38 : 0.3;
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
			// Not over an edge higher than a step, unless its player went down there too: along the edge where it can,
			// else it waits (and pops over to them once left too far behind).
			if (!landing && (x != s.x || z != s.z) && owner.getY() > s.y - 1.0) {
				Double here = ground(level, s.x, s.y + 0.5, s.z, 30);
				double floor = here != null ? here : s.y;
				if (drop(level, x, z, s.y, floor)) {
					if (!drop(level, x, s.z, s.y, floor)) {
						z = s.z;
					} else if (!drop(level, s.x, z, s.y, floor)) {
						x = s.x;
					} else {
						x = s.x;
						z = s.z;
						s.vx = s.vz = 0;
						if (dist > 2) {
							s.stuck++;
						}
					}
				}
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
		// Facing its player while resting (turning round with their view would look restless), else where they head.
		// What has caught its eye, the fire it warms itself at, or the food it begs for comes first.
		Vec3 toward = s.curious != null ? s.curious : s.fire != null ? s.fire : null;
		float rest = s.face != null ? (float) Math.atan2(s.face.x - s.x, s.face.z - s.z)
				: toward != null ? (float) Math.atan2(toward.x - s.x, toward.z - s.z)
				: s.rest != null || s.beg ? (float) Math.atan2(owner.getX() - s.x, owner.getZ() - s.z) : heading(owner);
		boolean eager = s.face != null || s.beg || s.curious != null;
		s.yaw = moving ? turnTo(s.yaw, (float) Math.atan2(s.vx, s.vz), 0.3f) : turnTo(s.yaw, rest, eager ? 0.25f : 0.04f);
	}

	/**
	 * Things a red panda or a turtle does by itself while you stand still: the panda rears up, nibbles bamboo or washes its
	 * face; the turtle hides in its shell, munches a berry or yawns; the kitten ducks into its box, hops out to stretch,
	 * licks a paw or yawns. Moving or falling asleep stops it.
	 */
	/**
	 * What it notices round it: its player eating (it comes begging, and sometimes gets a crumb), a lit campfire nearby
	 * while resting (it settles down by it to warm itself, happy, dozing off sooner), and, now and then, an item on the
	 * ground or a flower, which it trots over to and sniffs (a flower may make it sneeze).
	 */
	private void notice(ClientLevel level, State s, Player owner, boolean riding) {
		Companion.Style kind = s.look.companion().style;
		boolean eating = !riding && owner.isUsingItem() && owner.getUseItem().has(DataComponents.FOOD) && s.mode != Mode.PERCHED
				&& s.meeting == null && s.distanceSq(owner.position()) < 100;
		if (eating) {
			if (!s.beg) {
				s.beg = true;
				s.begAge = 0;
				s.action = CompanionModel.NONE;
			}
			s.begAge++;
			s.begFood = owner.getUseItem().getItem();
			s.curious = null;
			// Up on its hind legs every so often (a little hop for those without).
			if (s.onGround && s.speed < 0.02 && s.begAge % 28 == 14) {
				if (kind == Companion.Style.RED_PANDA || kind == Companion.Style.PENGUIN) {
					start(s, CompanionModel.STAND);
				} else {
					s.vy = 0.22;
					s.onGround = false;
				}
			}
		} else if (s.beg) {
			s.beg = false;
			s.action = CompanionModel.NONE;
			if (s.begAge >= 28 && s.begFood != null && random.nextInt(3) != 0 && s.distanceSq(owner.position()) < 9) {
				// A crumb for it: munched with bits flying, and a happy hop.
				for (int i = 0; i < 6; i++) {
					level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, s.begFood), s.x, s.y + 0.4, s.z,
							(random.nextDouble() - 0.5) * 0.1, 0.08 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.1);
				}
				level.addParticle(ParticleTypes.HEART, s.x, s.y + 0.8, s.z, 0, 0.02, 0);
				s.happy = 1;
				if (s.onGround) {
					s.vy = 0.3;
					s.onGround = false;
				}
			}
		}

		if (s.following || s.beg) {
			s.fire = null;
			s.curious = null;
			s.curiousItem = null;
			return;
		}
		// A campfire to sit by (looked for every two seconds).
		if (s.age % 40 == 0) {
			Vec3 fire = campfire(level, owner);
			if (fire != null && s.fire == null) {
				// Beside it, on the far side from its player.
				float angle = (float) Math.atan2(fire.z - owner.getZ(), fire.x - owner.getX()) + (float) s.side * 0.9f;
				s.rest = restingPlace(level, owner, fire.add(Mth.cos(angle) * 1.3, -0.5, Mth.sin(angle) * 1.3));
				s.restAge = 0;
				s.curious = null;
				s.curiousItem = null;
				// Wide awake again: it sits and warms itself first, then dozes off.
				s.idle = 41;
			}
			s.fire = fire;
		}
		if (s.fire != null) {
			if (s.distanceSq(s.fire) < 5 && s.speed < 0.02) {
				// Warm and content; a snowman sweats a little.
				s.happy = Math.max(s.happy, 0.5f);
				if (kind == Companion.Style.SNOWMAN && random.nextInt(12) == 0) {
					level.addParticle(ParticleTypes.DRIPPING_WATER, s.x + (random.nextDouble() - 0.5) * 0.4, s.y + 0.5 + random.nextDouble() * 0.4,
							s.z + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
				}
			}
			return;
		}

		if (s.curious != null) {
			s.curiousAge++;
			boolean gone = s.curiousItem != null && (s.curiousItem.isRemoved() || s.curiousItem.distanceToSqr(s.curious) > 1);
			boolean there = s.distanceSq(s.curious) < 1.2 && s.speed < 0.02;
			if (there) {
				s.sniff++;
				if (s.sniff % 10 == 0 && s.sniff < 50) {
					s.squash = 0.15f;
				}
			}
			if (s.sniff == 30 && s.curiousItem == null && kind == Companion.Style.PANDA
					&& level.getBlockState(BlockPos.containing(s.curious)).is(net.minecraft.world.level.block.Blocks.BAMBOO)) {
				// Bamboo! It sits down and munches some.
				s.curious = null;
				s.curiosityCooldown = 400 + random.nextInt(600);
				start(s, CompanionModel.SNACK);
				return;
			}
			if (s.sniff == 55) {
				if (s.curiousItem == null && random.nextInt(2) == 0) {
					// Atchoo: the pollen got it.
					level.addParticle(ParticleTypes.SNEEZE, s.x + Mth.sin(s.yaw) * 0.3, s.y + 0.35, s.z + Mth.cos(s.yaw) * 0.3,
							Mth.sin(s.yaw) * 0.05, 0.02, Mth.cos(s.yaw) * 0.05);
					s.squash = 0.8f;
				}
				if (s.onGround) {
					s.vy = 0.24;
					s.onGround = false;
				}
			}
			if (gone || s.sniff > 75 || s.curiousAge > 200) {
				s.curious = null;
				s.curiousItem = null;
				s.rest = null;
				s.curiosityCooldown = 300 + random.nextInt(500);
			}
			return;
		}
		if (s.idle > 80 && s.action == CompanionModel.NONE && s.sleep < 0.05f && s.mode == Mode.WALKING && --s.curiosityCooldown <= 0) {
			s.curiosityCooldown = 120 + random.nextInt(200);
			ItemEntity item = null;
			for (ItemEntity candidate : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(
					s.x - 5, s.y - 1.5, s.z - 5, s.x + 5, s.y + 1.5, s.z + 5))) {
				if (horizontal(candidate.position(), owner.position()) < LEASH - 0.4 && candidate.onGround()) {
					item = candidate;
					break;
				}
			}
			// A kitten goes for a ball of yarn first (and bats at it); any of them, now and then, after a butterfly.
			Vec3 toy = kind == Companion.Style.KITTEN ? yarnNear(s, 5) : null;
			if (toy == null && random.nextInt(3) == 0) {
				// Or after a butterfly, to pounce where it was (it flutters off in time).
				toy = butterfly(s, 5);
				if (toy != null) {
					toy = new Vec3(toy.x, s.y, toy.z);
				}
			}
			if (toy != null) {
				item = null;
			}
			Vec3 thing = toy != null ? toy : item != null ? item.position() : flower(level, s, owner);
			if (thing != null) {
				double dx = s.x - thing.x, dz = s.z - thing.z, d = Math.max(0.01, Math.hypot(dx, dz));
				s.curious = thing;
				s.curiousItem = item;
				s.curiousAge = 0;
				s.sniff = 0;
				s.rest = new Vec3(thing.x + dx / d * 0.55, thing.y, thing.z + dz / d * 0.55);
				s.restAge = 0;
			}
		}
	}

	/** The middle of a lit campfire within a few blocks of the player, or null. */
	private static Vec3 campfire(ClientLevel level, Player owner) {
		BlockPos feet = owner.blockPosition();
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-4, -2, -4), feet.offset(4, 1, 4))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.CAMPFIRES) && CampfireBlock.isLitCampfire(state)) {
				double d = pos.distSqr(feet);
				if (d < bestDistance) {
					bestDistance = d;
					best = Vec3.atBottomCenterOf(pos).add(0, 0.5, 0);
				}
			}
		}
		return best;
	}

	/** The nearest bamboo to it within a few blocks (and not too far from its player), at about their height, or null. */
	private static Vec3 bamboo(ClientLevel level, State s, Player owner) {
		BlockPos at = BlockPos.containing(s.x, s.y, s.z);
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-5, -1, -5), at.offset(5, 1, 5))) {
			if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BAMBOO)) {
				Vec3 spot = Vec3.atBottomCenterOf(pos);
				double d = s.distanceSq(spot);
				if (d < bestDistance && horizontal(spot, owner.position()) < LEASH - 0.4 && horizontal(spot, owner.position()) > 1.2) {
					bestDistance = d;
					best = spot;
				}
			}
		}
		return best;
	}

	/** A flower near it (and its player) at about their height, picked at random, or null. */
	private Vec3 flower(ClientLevel level, State s, Player owner) {
		if (s.look.companion().style == Companion.Style.PANDA) {
			// A baby panda goes for bamboo first.
			Vec3 bamboo = bamboo(level, s, owner);
			if (bamboo != null) {
				return bamboo;
			}
		}
		for (int i = 0; i < 16; i++) {
			BlockPos pos = BlockPos.containing(s.x + random.nextInt(9) - 4, s.y + random.nextInt(3) - 1, s.z + random.nextInt(9) - 4);
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.SMALL_FLOWERS) && horizontal(Vec3.atBottomCenterOf(pos), owner.position()) < LEASH - 0.4
					&& horizontal(Vec3.atBottomCenterOf(pos), owner.position()) > 1.2) {
				return Vec3.atBottomCenterOf(pos);
			}
		}
		return null;
	}

	private void act(ClientLevel level, State s, Player owner) {
		Companion.Style style = s.look.companion().style;
		if (style != Companion.Style.RED_PANDA && style != Companion.Style.TURTLE && style != Companion.Style.KITTEN
				&& style != Companion.Style.SHADOW_WOLF && style != Companion.Style.HEDGEHOG && style != Companion.Style.PENGUIN
				&& style != Companion.Style.MECH && style != Companion.Style.SALAMANDER && style != Companion.Style.LANTERN && style != Companion.Style.WISP
				&& style != Companion.Style.WIND && style != Companion.Style.YARN && style != Companion.Style.PANDA) {
			s.action = CompanionModel.NONE;
			return;
		}
		// A wisp never stands on the ground: calm is drifting at its place.
		boolean calm = s.sleep < 0.05f && s.meeting == null && s.curious == null && (style == Companion.Style.WISP
				? s.booVictim != null || !s.following && s.haunt == null && s.speed < 0.03
				: s.mode == Mode.WALKING && s.speed < 0.02 && s.onGround);
		if (!calm) {
			s.action = CompanionModel.NONE;
			s.actionAge = 0;
			return;
		}
		if (s.action == CompanionModel.NONE) {
			if (s.idle > 50 && !s.beg && --s.actionCooldown <= 0) {
				int[] choices = style == Companion.Style.MECH
						? new int[]{CompanionModel.SCAN, CompanionModel.ROCKET, CompanionModel.SHAKE}
						: style == Companion.Style.LANTERN
						? new int[]{CompanionModel.STAND, CompanionModel.YAWN, CompanionModel.SHAKE}
						: style == Companion.Style.WISP
						? new int[]{CompanionModel.SHAKE, CompanionModel.YAWN, CompanionModel.HIDE}
						: style == Companion.Style.WIND
						? new int[]{CompanionModel.STAND, CompanionModel.YAWN}
						: style == Companion.Style.YARN
						? new int[]{CompanionModel.STAND, CompanionModel.SHAKE}
						: style == Companion.Style.PANDA
						? new int[]{CompanionModel.SNACK, CompanionModel.SHAKE, CompanionModel.BASK, CompanionModel.GROOM, CompanionModel.YAWN}
						: style == Companion.Style.SALAMANDER
						? s.fire != null ? new int[]{CompanionModel.BASK, CompanionModel.BASK, CompanionModel.BASK, CompanionModel.YAWN}
						: new int[]{CompanionModel.BASK, CompanionModel.YAWN, CompanionModel.STAND}
						: style == Companion.Style.HEDGEHOG
						? new int[]{CompanionModel.SNIFF, CompanionModel.SNACK, CompanionModel.HIDE, CompanionModel.YAWN}
						: style == Companion.Style.PENGUIN
						? new int[]{CompanionModel.STAND, CompanionModel.SHAKE, CompanionModel.YAWN}
						: style == Companion.Style.SHADOW_WOLF
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
			case CompanionModel.SNIFF -> 70;
			case CompanionModel.SHAKE -> 45;
			case CompanionModel.ROCKET -> 80;
			case CompanionModel.SCAN -> 90;
			case CompanionModel.BASK -> 160;
			default -> 75;
		};
	}

	/** Where its head turns: to its player, or to you when you look at it from close by. */
	private void look(Minecraft mc, State s, Player owner) {
		Vec3 at = s.face != null ? new Vec3(s.face.x, s.face.y + 0.35, s.face.z) : owner.getEyePosition();
		if (s.face == null && s.curious != null && s.distanceSq(s.curious) < 4) {
			// Nose down to what it found.
			at = s.curious;
		} else if (s.face == null && s.look.companion().style == Companion.Style.KITTEN && yarnNear(s, 6) != null) {
			// A kitten can't take its eyes off a ball of yarn.
			at = yarnNear(s, 6);
		} else if (s.face == null && !s.following && s.mode != Mode.PERCHED && (s.butterfly = butterfly(s, 4)) != null) {
			// Watching a butterfly flutter by.
			at = s.butterfly;
		} else if (s.face == null && s.fire != null && s.idle % 240 < 170) {
			// Gazing into the flames, now and then back at its player.
			at = s.fire.add(0, 0.3, 0);
		}
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
		int shy = kind == Companion.Style.SHADOW_WOLF ? CompanionModel.PLAYBOW : kind == Companion.Style.HEDGEHOG ? CompanionModel.HIDE
				: kind == Companion.Style.PENGUIN ? CompanionModel.STAND : CompanionModel.NONE;
		if (s.happy < 0.3f && shy != CompanionModel.NONE && s.action == CompanionModel.NONE && s.onGround
				&& s.mode == Mode.WALKING && random.nextInt(2) == 0) {
			// Instead of hopping: the shadow wolf bows down to play, the hedgehog curls up shyly, the penguin flaps.
			start(s, shy);
		} else if (s.happy < 0.3f && s.onGround && s.mode == Mode.WALKING && s.action == CompanionModel.NONE) {
			s.vy = 0.3;
			s.onGround = false;
		}
		if (s.happy < 0.3f && (kind == Companion.Style.RED_PANDA || kind == Companion.Style.TURTLE) && s.action == CompanionModel.NONE
				&& random.nextInt(3) == 0) {
			// The red panda rears up, the turtle hides shyly.
			start(s, kind == Companion.Style.TURTLE ? CompanionModel.HIDE : CompanionModel.STAND);
		}
		if ((kind == Companion.Style.MECH || s.look.companion() == Companion.LANTERN_COPPER) && s.wear > 0) {
			// Petting a mech (or a copper lantern) polishes it: the green comes off again.
			s.wear = Math.max(0, s.wear - 0.004f);
			if (s.age % 4 == 0) {
				level.addParticle(ParticleTypes.SCRAPE, s.x + (random.nextDouble() - 0.5) * 0.5, s.y + 0.2 + random.nextDouble() * 0.5,
						s.z + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
			}
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

	/** Hours of wearing until a mech's copper is all green; in the rain or in water it goes eight times as fast. */
	private static final float WEAR_HOURS = 3;

	/**
	 * A mech's copper weathering: slowly as it comes along, faster when wet. Yours is kept in the settings (read once,
	 * written back now and then); others' start new in each session.
	 */
	private void weather(ClientLevel level, State s, boolean yours) {
		if (yours && !s.wearLoaded && wardrobe != null) {
			s.wear = Mth.clamp(wardrobe.config().companionWear, 0, 1);
			s.wearLoaded = true;
		}
		boolean wet = s.swimming || level.isRainingAt(BlockPos.containing(s.x, s.y + 1, s.z));
		s.wear = Math.min(1, s.wear + (wet ? 8 : 1) / (WEAR_HOURS * 72000));
		if (yours && wardrobe != null && s.age % 1200 == 0 && Math.abs(wardrobe.config().companionWear - s.wear) > 0.001f) {
			wardrobe.config().companionWear = s.wear;
			wardrobe.save();
		}
	}

	/**
	 * A mech's steam: a puff at each stomp, now and then from its exhaust, a burst as it vents; flames and smoke under its
	 * boots as it hovers.
	 */
	private void mechPuffs(ClientLevel level, State s) {
		float step = Mth.sin(s.walk);
		if (s.walking > 0.3f && Math.signum(step) != Math.signum(s.lastStep)) {
			double side = step > 0 ? -0.09 : 0.09;
			double rx = Mth.cos(s.yaw) * side, rz = -Mth.sin(s.yaw) * side;
			level.addParticle(ParticleTypes.WHITE_SMOKE, s.x + rx, s.y + 0.05, s.z + rz, (random.nextDouble() - 0.5) * 0.03, 0.01,
					(random.nextDouble() - 0.5) * 0.03);
		}
		s.lastStep = step;
		double bx = s.x - Mth.sin(s.yaw) * 0.17, bz = s.z - Mth.cos(s.yaw) * 0.17;
		if (s.action == CompanionModel.SHAKE ? s.age % 3 == 0 : random.nextInt(90) == 0) {
			level.addParticle(ParticleTypes.WHITE_SMOKE, bx, s.y + 0.68, bz, (random.nextDouble() - 0.5) * 0.02, 0.05, (random.nextDouble() - 0.5) * 0.02);
		}
		if (s.action == CompanionModel.ROCKET) {
			float a = (float) s.actionAge / s.actionLength;
			if (a > 0.15f && a < 0.9f) {
				level.addParticle(random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMOKE, s.x + (random.nextDouble() - 0.5) * 0.2,
						s.y + 0.3, s.z + (random.nextDouble() - 0.5) * 0.2, 0, -0.05, 0);
			}
		}
	}

	/**
	 * A lava salamander's sparks as it runs, the embers it yawns out, and steam hissing off it in the rain or in water.
	 */
	private void salamanderSparks(ClientLevel level, State s) {
		ParticleOptions spark = switch (s.look.companion()) {
			case SALAMANDER_SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
			case SALAMANDER_GILDED -> ParticleTypes.WAX_ON;
			default -> ParticleTypes.SMALL_FLAME;
		};
		if (s.speed > 0.04 && random.nextInt(4) == 0) {
			level.addParticle(spark, s.x + (random.nextDouble() - 0.5) * 0.4, s.y + 0.05, s.z + (random.nextDouble() - 0.5) * 0.4,
					0, 0.01, 0);
		}
		if (s.speed > 0.04 && random.nextInt(30) == 0 && s.look.companion() != Companion.SALAMANDER_SOUL) {
			level.addParticle(ParticleTypes.LAVA, s.x, s.y + 0.1, s.z, 0, 0, 0);
		}
		if (s.action == CompanionModel.YAWN && s.actionAge > 12 && s.actionAge < 38 && s.age % 2 == 0) {
			double mx = s.x + Mth.sin(s.yaw) * 0.55, mz = s.z + Mth.cos(s.yaw) * 0.55;
			level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : spark, mx, s.y + 0.2, mz,
					Mth.sin(s.yaw) * 0.03 + (random.nextDouble() - 0.5) * 0.02, 0.04, Mth.cos(s.yaw) * 0.03 + (random.nextDouble() - 0.5) * 0.02);
		}
		boolean wet = s.swimming || level.isRainingAt(BlockPos.containing(s.x, s.y + 1, s.z));
		if (wet && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.CLOUD, s.x + (random.nextDouble() - 0.5) * 0.5, s.y + 0.3, s.z + (random.nextDouble() - 0.5) * 0.5,
					0, 0.05, 0);
			if (random.nextInt(25) == 0) {
				level.playLocalSound(s.x, s.y, s.z, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
						net.minecraft.sounds.SoundSource.NEUTRAL, 0.06f, 1.7f + random.nextFloat() * 0.3f, false);
			}
		}
	}

	/**
	 * What a lantern spirit feels round its player: monsters close by make its flame gutter, a hissing creeper makes it
	 * flicker wildly and flinch, its player badly hurt dims it and keeps it close, a warden near (or the darkness it
	 * brings) all but puts it out and makes it shiver. And whether they are down in a cave: no sky and dark round it.
	 */
	private void lanternMood(ClientLevel level, State s, Player owner) {
		if (s.age % 4 == 0) {
			float threat = 0;
			boolean hiss = false, dread = owner.hasEffect(MobEffects.DARKNESS);
			for (Monster monster : level.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(24), Entity::isAlive)) {
				double d = monster.distanceTo(owner);
				if (monster instanceof Warden) {
					dread = true;
				} else if (d < 10) {
					threat = Math.max(threat, (float) Mth.clamp((10 - d) / 8, 0, 1) * 0.6f);
					if (monster instanceof Creeper creeper && (creeper.getSwelling(1f) > 0.01f || creeper.isIgnited())) {
						hiss = true;
					}
				}
			}
			if (hiss && !s.hiss) {
				// Flinching.
				s.squash = 0.8f;
			}
			s.hiss = hiss;
			s.threat = hiss ? 1 : threat;
			s.dread = dread;
		}
		boolean hurt = owner.getHealth() <= 6 && !owner.isCreative() && !owner.isSpectator();
		s.close = hurt || s.dread;
		float fear = Math.max(s.threat, s.dread ? 0.45f : 0);
		s.fear = approach(s.fear, fear, fear > s.fear ? 0.15f : 0.02f);
		s.dim = approach(s.dim, s.dread ? 0.85f : hurt ? 0.5f : 0, 0.03f);
		boolean cave = s.shade > 0.5f && !level.canSeeSky(BlockPos.containing(owner.getX(), owner.getEyeY(), owner.getZ()));
		s.cave = approach(s.cave, cave ? 1 : 0, 0.02f);
		Double floor = ground(level, s.x, s.y + 0.3, s.z, 3);
		s.floor = floor != null ? floor : Double.NaN;
	}

	/**
	 * A lantern spirit's sparks: now and then one rising from its cap, a burst as it flares up, spat out when it is
	 * frightened; souls drifting off it when dread is near.
	 */
	private void lanternSparks(ClientLevel level, State s) {
		ParticleOptions spark = switch (s.look.companion()) {
			case LANTERN_SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
			case LANTERN_COPPER -> ParticleTypes.COPPER_FIRE_FLAME;
			case LANTERN_AMETHYST -> ParticleTypes.WITCH;
			default -> ParticleTypes.SMALL_FLAME;
		};
		double top = s.y + 0.62;
		boolean flaring = s.action == CompanionModel.STAND && s.actionAge > 15 && s.actionAge < 40;
		if (flaring ? s.age % 2 == 0 : random.nextInt(s.fear > 0.8f ? 3 : 45) == 0) {
			double spread = s.fear > 0.8f ? 0.08 : 0.02;
			level.addParticle(spark, s.x + (random.nextDouble() - 0.5) * 0.15, top, s.z + (random.nextDouble() - 0.5) * 0.15,
					(random.nextDouble() - 0.5) * spread, 0.03, (random.nextDouble() - 0.5) * spread);
		}
		if (s.dread && random.nextInt(10) == 0) {
			level.addParticle(ParticleTypes.SOUL, s.x + (random.nextDouble() - 0.5) * 0.3, s.y + 0.3, s.z + (random.nextDouble() - 0.5) * 0.3,
					0, 0.02, 0);
		}
	}

	/**
	 * A wisp's trail: wisps of smoke (souls, ash, end sparks) off its tail as it glides, a puff as it pops out for its boo,
	 * and flickers of soul fire in the Nether.
	 */
	private void wispSmoke(ClientLevel level, State s) {
		ParticleOptions trail = switch (s.look.companion()) {
			case WISP_SOUL -> ParticleTypes.SOUL;
			case WISP_PALE -> ParticleTypes.WHITE_ASH;
			case WISP_VOID -> ParticleTypes.REVERSE_PORTAL;
			default -> ParticleTypes.SMOKE;
		};
		double bx = s.x - Mth.sin(s.yaw) * 0.25, bz = s.z - Mth.cos(s.yaw) * 0.25;
		if (s.speed > 0.04 ? random.nextInt(s.look.companion() == Companion.WISP_SOUL ? 10 : 4) == 0 : random.nextInt(60) == 0) {
			level.addParticle(trail, bx + (random.nextDouble() - 0.5) * 0.2, s.y + 0.15, bz + (random.nextDouble() - 0.5) * 0.2, 0, 0.01, 0);
		}
		if (s.action == CompanionModel.HIDE && s.actionAge == s.actionLength * 6 / 10) {
			poof(level, s.x, s.y + 0.2, s.z, ParticleTypes.SMOKE, 6);
		}
		if (s.fear > 0.5f && random.nextInt(25) == 0) {
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, s.x + (random.nextDouble() - 0.5) * 0.3, s.y + 0.4,
					s.z + (random.nextDouble() - 0.5) * 0.3, 0, 0.02, 0);
		}
	}

	/** A baby panda's sneeze (a puff, and it bounces back a little) and the bits of bamboo it drops as it munches. */
	private void pandaBits(ClientLevel level, State s) {
		double fx = Mth.sin(s.yaw), fz = Mth.cos(s.yaw);
		if (s.action == CompanionModel.GROOM && s.actionAge == s.actionLength * 62 / 100) {
			level.addParticle(ParticleTypes.SNEEZE, s.x + fx * 0.4, s.y + 0.35, s.z + fz * 0.4, fx * 0.06, 0.02, fz * 0.06);
			s.squash = 0.7f;
			if (s.onGround) {
				s.vy = 0.18;
				s.onGround = false;
			}
		}
		if (s.action == CompanionModel.SNACK && s.actionAge > 10 && s.actionAge % 14 == 0) {
			level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, net.minecraft.world.item.Items.BAMBOO),
					s.x + fx * 0.3, s.y + 0.4, s.z + fz * 0.3, (random.nextDouble() - 0.5) * 0.05, 0.05, (random.nextDouble() - 0.5) * 0.05);
		}
	}

	/**
	 * A companion getting wet (swimming, out in the rain) drips as it dries, faster and steaming by a fire, and shakes
	 * itself off with a shower of drops once it is out of the water and on its feet. Not those made of fire, air or
	 * smoke, nor the lantern.
	 */
	private void drip(ClientLevel level, State s) {
		Companion.Style kind = s.look.companion().style;
		if (!dev.cobblecomfort.features.atmosphere.AtmosphereFeature.INSTANCE.drips() || kind == Companion.Style.SALAMANDER
				|| kind == Companion.Style.WISP || kind == Companion.Style.WIND || kind == Companion.Style.LANTERN) {
			s.wet = 0;
			return;
		}
		BlockPos top = BlockPos.containing(s.x, s.y + 0.6, s.z);
		boolean rain = level.isRaining() && level.canSeeSky(top)
				&& level.getBiome(top).value().getPrecipitationAt(top, level.getSeaLevel()) == net.minecraft.world.level.biome.Biome.Precipitation.RAIN;
		if (s.swimming) {
			s.wet = 1;
			s.soaked = true;
		} else if (rain) {
			s.wet = Math.min(1, s.wet + 1 / 400f);
		} else if (s.wet > 0) {
			boolean hot = s.fire != null || level.dimension() == Level.NETHER;
			s.wet = Math.max(0, s.wet - (hot ? 1 / 100f : 1 / 600f));
			if (hot && random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.WHITE_SMOKE, s.x + (random.nextDouble() - 0.5) * 0.4, s.y + 0.3, s.z + (random.nextDouble() - 0.5) * 0.4,
						0, 0.03, 0);
			}
		}
		if (s.soaked && !s.swimming && s.onGround && s.speed < 0.05) {
			// Out of the water: a good shake.
			s.soaked = false;
			double h = CompanionModel.height(kind) * 0.5;
			for (int i = 0; i < 14; i++) {
				double angle = random.nextDouble() * Mth.TWO_PI;
				level.addParticle(ParticleTypes.SPLASH, s.x + Math.cos(angle) * 0.25, s.y + h, s.z + Math.sin(angle) * 0.25,
						Math.cos(angle) * 0.15, 0.08, Math.sin(angle) * 0.15);
			}
			s.squash = 0.6f;
			s.wet *= 0.3f;
			if (kind == Companion.Style.PENGUIN && s.action == CompanionModel.NONE) {
				start(s, CompanionModel.SHAKE);
			}
		}
		if (s.wet > 0 && !s.swimming && random.nextFloat() < s.wet * 0.2f) {
			level.addParticle(ParticleTypes.FALLING_WATER, s.x + (random.nextDouble() - 0.5) * 0.35,
					s.y + CompanionModel.height(kind) * random.nextDouble() * 0.8, s.z + (random.nextDouble() - 0.5) * 0.35, 0, 0, 0);
		}
	}

	/** Small touches: a rain cloud drips in the rain, a snowman leaves a few flakes, a golem's steps thud. */
	private void ambient(ClientLevel level, State s, Player owner) {
		Companion.Style style = s.look.companion().style;
		if (style == Companion.Style.CLOUD && level.isRainingAt(BlockPos.containing(s.x, s.y + 1, s.z)) && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.FALLING_WATER, s.x + (random.nextDouble() - 0.5) * 0.4, s.y + 0.15,
					s.z + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
		}
		if (style == Companion.Style.MECH) {
			mechPuffs(level, s);
		} else if (style == Companion.Style.SALAMANDER) {
			salamanderSparks(level, s);
		} else if (style == Companion.Style.LANTERN) {
			lanternSparks(level, s);
		} else if (style == Companion.Style.WISP) {
			wispSmoke(level, s);
		} else if (style == Companion.Style.WIND) {
			windDust(level, s);
		} else if (style == Companion.Style.YARN) {
			thread(level, s);
		} else if (style == Companion.Style.PANDA) {
			pandaBits(level, s);
		}
		boolean sliding = style == Companion.Style.PENGUIN && s.rolling && random.nextInt(2) == 0;
		if ((style == Companion.Style.SNOWMAN && s.speed > 0.05 && random.nextInt(4) == 0) || sliding) {
			level.addParticle(ParticleTypes.SNOWFLAKE, s.x, s.y + 0.1, s.z, (random.nextDouble() - 0.5) * 0.02, 0.02,
					(random.nextDouble() - 0.5) * 0.02);
		}
		if (style == Companion.Style.PENGUIN && s.action == CompanionModel.SHAKE && (s.actionAge == 12 || s.actionAge == 26)) {
			// Shaking itself fluffy: flakes flying off.
			for (int i = 0; i < 6; i++) {
				level.addParticle(ParticleTypes.SNOWFLAKE, s.x + (random.nextDouble() - 0.5) * 0.5, s.y + 0.35,
						s.z + (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.12, 0.05,
						(random.nextDouble() - 0.5) * 0.12);
			}
		}
		if (style == Companion.Style.HEDGEHOG && s.action == CompanionModel.SNIFF && s.actionAge % 14 == 7) {
			// Snuffling at the ground.
			level.addParticle(ParticleTypes.WHITE_ASH, s.x + Mth.sin(s.yaw) * 0.3, s.y + 0.08, s.z + Mth.cos(s.yaw) * 0.3,
					0, 0.01, 0);
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

	/** Whether it stands on snow or ice (a penguin slides there). */
	private static boolean slippery(ClientLevel level, State s) {
		BlockState at = level.getBlockState(BlockPos.containing(s.x, s.y + 0.05, s.z));
		BlockState below = level.getBlockState(BlockPos.containing(s.x, s.y - 0.1, s.z));
		return at.is(net.minecraft.world.level.block.Blocks.SNOW) || below.is(net.minecraft.tags.BlockTags.ICE)
				|| below.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK) || below.is(net.minecraft.world.level.block.Blocks.SNOW)
				|| below.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
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
		// Across from where it stands, on the other side of its player.
		Vec3 spot = new Vec3(2 * owner.getX() - s.x, owner.getY(), 2 * owner.getZ() - s.z);
		Double ground = ground(level, spot.x, owner.getY() + 1.5, spot.z, 4);
		if (ground == null || !free(level, spot.x, ground + 0.2, spot.z) || !free(level, spot.x, ground + 0.6, spot.z)) {
			return;
		}
		s.side = -s.side;
		s.rest = new Vec3(spot.x, ground, spot.z);
		s.restAge = 0;
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


	/** How many points (a little under a third of a block apart) a ball of yarn's thread has when it is all unwound. */
	private static final int THREAD = 48;

	/**
	 * A ball of yarn's thread: laid down on the ground behind it as it rolls along (the far end let go of once it is all
	 * unwound), wound up again from the far end while it rests, the ball turning as it reels it in.
	 */
	private void thread(ClientLevel level, State s) {
		if (s.speed > 0.01 && !s.swimming) {
			Double ground = ground(level, s.x, s.y + 0.3, s.z, 2);
			Vec3 here = new Vec3(s.x, (ground != null ? ground : s.y) + 0.02, s.z);
			Vec3 last = s.thread.peekLast();
			if (last == null || last.distanceToSqr(here) > 0.3 * 0.3) {
				s.thread.addLast(here);
				if (s.thread.size() > THREAD) {
					s.thread.removeFirst();
				}
			}
		} else if (!s.thread.isEmpty() && s.age % 2 == 0) {
			s.thread.removeFirst();
			s.walk += 0.35f;
		}
		s.wear = s.thread.size() / (float) THREAD;
	}

	/**
	 * A whirlwind's swirl: whatever it passes over (the ground, grass, flowers, snow) whirled up and spun round it, its own
	 * leaves (sand, flakes) flying off, and gusts sweeping off it when it races after a sprinting player.
	 */
	private void windDust(ClientLevel level, State s) {
		BlockState under = level.getBlockState(BlockPos.containing(s.x, s.y - 0.1, s.z));
		BlockState at = level.getBlockState(BlockPos.containing(s.x, s.y + 0.1, s.z));
		float gust = s.dash;
		if (random.nextInt(s.speed > 0.04 || gust > 0.3 ? 2 : 10) == 0) {
			BlockState what = !at.isAir() && at.getFluidState().isEmpty() ? at : under;
			if (!what.isAir()) {
				double a = random.nextDouble() * Mth.TWO_PI, r = 0.25 + 0.15 * gust;
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, what), s.x + Math.cos(a) * r, s.y + 0.05 + random.nextDouble() * 0.3,
						s.z + Math.sin(a) * r, -Math.sin(a) * 0.15, 0.08 + 0.1 * gust, Math.cos(a) * 0.15);
			}
		}
		if (random.nextInt(gust > 0.5f ? 3 : 25) == 0) {
			ParticleOptions own = switch (s.look.companion()) {
				case WIND_AUTUMN -> random.nextBoolean() ? ParticleTypes.ORANGE_POPLAR_LEAVES : ParticleTypes.RED_POPLAR_LEAVES;
				case WIND_SNOW -> ParticleTypes.SNOWFLAKE;
				case WIND_DUST -> ParticleTypes.DUST_PLUME;
				default -> ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, s.look.companion().third);
			};
			double a = random.nextDouble() * Mth.TWO_PI;
			level.addParticle(own, s.x + Math.cos(a) * 0.35, s.y + 0.2 + random.nextDouble() * 0.5, s.z + Math.sin(a) * 0.35,
					-Math.sin(a) * 0.1, 0.03, Math.cos(a) * 0.1);
		}
		if (gust > 0.5f && random.nextInt(5) == 0) {
			level.addParticle(ParticleTypes.SMALL_GUST, s.x + (random.nextDouble() - 0.5) * 0.8, s.y + 0.2 + random.nextDouble() * 0.6,
					s.z + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
		}
	}

	/** Where the nearest ball of yarn (another player's companion) is, within {@code range} of this one; null if none. */
	private Vec3 yarnNear(State s, double range) {
		Vec3 best = null;
		double bestDistance = range * range;
		for (State other : states.values()) {
			if (other != s && !other.hidden && other.look.companion().style == Companion.Style.YARN) {
				double d = s.distanceSq(new Vec3(other.x, other.y, other.z));
				if (d < bestDistance) {
					bestDistance = d;
					best = new Vec3(other.x, other.y + 0.15, other.z);
				}
			}
		}
		return best;
	}

	/**
	 * Cats and ocelots near a ball of yarn can't take their eyes off it: they turn their heads after it (only as drawn
	 * here; the game's own looking about takes over again when it rolls away).
	 */
	private void catsWatch(ClientLevel level) {
		for (State s : states.values()) {
			if (s.hidden || s.look.companion().style != Companion.Style.YARN) {
				continue;
			}
			Vec3 ball = new Vec3(s.x, s.y + 0.15, s.z);
			for (LivingEntity cat : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(ball, ball).inflate(6, 2, 6),
					e -> e instanceof Cat || e instanceof Ocelot)) {
				if (cat instanceof Cat sleeping && sleeping.isLying()) {
					continue;
				}
				double dx = ball.x - cat.getX(), dz = ball.z - cat.getZ(), dy = ball.y - cat.getEyeY();
				float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
				float pitch = (float) -(Mth.atan2(dy, Math.hypot(dx, dz)) * Mth.RAD_TO_DEG);
				cat.setYHeadRot(Mth.approachDegrees(cat.getYHeadRot(), yaw, 20));
				cat.setXRot(Mth.approachDegrees(cat.getXRot(), Mth.clamp(pitch, -40, 40), 10));
			}
		}
	}

	/** A ball of yarn's thread lying on the ground behind it, from its far end up to the ball. */
	private static void drawThread(LevelRenderContext context, ClientLevel level, State s, double x, double y, double z, Vec3 eye) {
		List<Vec3> points = new ArrayList<>(s.thread);
		points.add(new Vec3(x, y + 0.03, z));
		int n = points.size() - 1;
		float[] at = new float[n * 3];
		float[] turn = new float[n * 2];
		float[] length = new float[n];
		int[] lights = new int[n];
		for (int i = 0; i < n; i++) {
			Vec3 a = points.get(i), b = points.get(i + 1);
			double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			at[i * 3] = (float) (a.x - eye.x);
			at[i * 3 + 1] = (float) (a.y - eye.y);
			at[i * 3 + 2] = (float) (a.z - eye.z);
			turn[i * 2] = (float) Math.atan2(dx, dz);
			turn[i * 2 + 1] = len > 1.0E-4 ? (float) -Math.asin(Mth.clamp(dy / len, -1, 1)) : 0;
			length[i] = (float) len;
			lights[i] = LightCoordsUtil.getLightCoords(level, BlockPos.containing(a.x, a.y + 0.1, a.z));
		}
		int color = s.look.companion().main;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), CompanionTexture.type(), (p, vc) -> {
			for (int i = 0; i < n; i++) {
				if (length[i] < 1.0E-3f) {
					continue;
				}
				PoseStack.Pose q = p.copy();
				q.translate(at[i * 3], at[i * 3 + 1], at[i * 3 + 2]);
				q.rotate(com.mojang.math.Axis.YP, turn[i * 2]);
				q.rotate(com.mojang.math.Axis.XP, turn[i * 2 + 1]);
				CompanionTexture.box(vc, q, -0.025f, 0, -0.01f, 0.025f, 0.045f, length[i] + 0.01f, color, lights[i],
						CompanionTexture.Material.PLAIN, 0);
			}
		});
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
			// Inside a wall (a wisp gliding through) the light of the blocks round it.
			BlockPos at = BlockPos.containing(x, y + 0.3, z);
			int around = LightCoordsUtil.getLightCoords(mc.level, at);
			if (!free(mc.level, x, y + 0.3, z)) {
				for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
					around = LightCoordsUtil.max(around, LightCoordsUtil.getLightCoords(mc.level, at.relative(direction)));
				}
			}
			Companion.Look look = s.look;
			// A lantern spirit is lit by its own flame, and lights the ground under it.
			boolean lantern = look.companion().style == Companion.Style.LANTERN;
			int light = lantern ? LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(around),
					Math.round(6 + 9 * Math.min(1, CompanionModel.lanternBright(pose)))), LightCoordsUtil.sky(around)) : around;
			PoseStack poseStack = context.poseStack();
			if (lantern) {
				lightPool(context, s, pose, x - eye.x, y, z - eye.z, eye.y);
			}
			if (look.companion().style == Companion.Style.YARN && !s.thread.isEmpty()) {
				drawThread(context, mc.level, s, x, y, z, eye);
			}
			poseStack.pushPose();
			poseStack.translate(dx, dy, dz);
			poseStack.mulPose(new org.joml.Matrix4f().rotationY(yaw));
			float pitch = Mth.lerp(partial, s.pitchO, s.pitch);
			if (Math.abs(pitch) > 0.001f) {
				// Tipped round its middle.
				float middle = CompanionModel.height(look.companion().style) * 0.45f;
				poseStack.translate(0, middle, 0);
				poseStack.mulPose(new org.joml.Matrix4f().rotationX(pitch));
				poseStack.translate(0, -middle, 0);
			}
			context.submitNodeCollector().submitCustomGeometry(poseStack,
					CompanionModel.seeThrough(look.companion().style) ? CompanionTexture.ghostType() : CompanionTexture.type(),
					(p, vc) -> CompanionModel.draw(vc, p, look, pose, light));
			if (CompanionModel.glows(look.companion().style)) {
				context.submitNodeCollector().submitCustomGeometry(poseStack, CompanionTexture.glowType(),
						(p, vc) -> CompanionModel.draw(vc, p, look, pose, light, true));
			}
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

	/**
	 * The soft light a lantern spirit casts on the ground under it: a few see-through layers, turned against each other,
	 * brightest in the middle; stronger in the dark, fading the higher it floats.
	 */
	private static void lightPool(LevelRenderContext context, State s, CompanionModel.Pose pose, double dx, double y, double dz, double eyeY) {
		if (Double.isNaN(s.floor)) {
			return;
		}
		double above = y - s.floor;
		float strength = (float) ((0.05 + 0.3 * pose.shade()) * Math.min(1, CompanionModel.lanternBright(pose)) * Mth.clamp(1 - above / 3, 0, 1));
		if (strength < 0.02f) {
			return;
		}
		int accent = s.look.companion().accent;
		int color = ARGB.color(Math.round(strength * 255 * 0.2f), ARGB.red(accent), ARGB.green(accent), ARGB.blue(accent));
		PoseStack poseStack = context.poseStack();
		float[] radii = {1.45f, 1.25f, 1.05f, 0.85f, 0.65f, 0.45f};
		for (int i = 0; i < radii.length; i++) {
			float r = radii[i], lift = 0.015f + i * 0.003f;
			poseStack.pushPose();
			poseStack.translate(dx, s.floor - eyeY, dz);
			poseStack.mulPose(new org.joml.Matrix4f().rotationY(i * 0.52f + 0.26f));
			context.submitNodeCollector().submitCustomGeometry(poseStack, CompanionTexture.glowType(),
					(p, vc) -> CompanionTexture.box(vc, p, -r, lift, -r, r, lift + 0.002f, r, color, Voxels.FULL_BRIGHT,
							CompanionTexture.Material.PLAIN, 0));
			poseStack.popPose();
		}
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
		/** Chasing its player through the air on their elytra (0 to 1), and how far its body tips nose down (radians). */
		float dash, dashO, pitch, pitchO;
		boolean onGround = true, hidden, swimming;
		/** Rolling along as a ball (hedgehog) or sliding on its belly (penguin). */
		boolean rolling;
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
		/** How weathered it is (0 to 1: a mech's copper going green), whether that was read from your settings yet, and the step it was last on. */
		float wear;
		boolean wearLoaded;
		float lastStep;
		/** Where it rests while its player stands still (fixed in the world), and for how long it has been there. */
		Vec3 rest;
		int restAge;
		/** Following its player (else resting near them), and for how long they have stood still. */
		boolean following = true;
		/** Begging while its player eats, for how long, and what they eat. */
		boolean beg;
		int begAge;
		Item begFood;
		/** The lit campfire it warms itself at while resting (its middle), or null. */
		Vec3 fire;
		/** What it went over to sniff while resting (an item on the ground, else a flower), for how long, how long it sniffed, and until it looks again. */
		Vec3 curious;
		ItemEntity curiousItem;
		int curiousAge, sniff, curiosityCooldown = 200;
		/** A wisp slipping into a wall nearby to peek out of it: where, for how long so far, and until the next time. */
		Vec3 haunt;
		int hauntAge, hauntCooldown = 300;
		/** Another player it is about to give a fright (boo), and until it may do so again. */
		Player booVictim;
		int booCooldown = 200;
		/** The butterfly it watches now, or null. */
		Vec3 butterfly;
		/** How wet it is (0 to 1), and whether it has been swimming and is still to shake itself off. */
		float wet;
		boolean soaked;
		/** A ball of yarn's thread on the ground behind it, the far end first. */
		final java.util.ArrayDeque<Vec3> thread = new java.util.ArrayDeque<>();
		/** Bounding after its sprinting player. */
		boolean galloping;
		/**
		 * A lantern spirit: how frightened (0 to 1) and how dimmed it is, how close the nearest monster is (1: a creeper
		 * hissing), whether a creeper hisses or a warden is near, whether it keeps close to its player, how far down in a
		 * cave (0 to 1), where it is on its circle round its player (radians), and the ground under it (NaN none).
		 */
		float fear, dim, threat;
		boolean hiss, dread, close;
		float cave;
		double orbit, floor = Double.NaN;
		int ownerStill;

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
			dashO = dash;
			pitchO = pitch;
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
					action == CompanionModel.NONE ? 0 : Math.min(1, (actionAge + partial) / actionLength), actionBlend(partial), shade, wear, Mth.lerp(partial, dashO, dash), fear, dim);
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
