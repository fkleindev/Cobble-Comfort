package dev.cobblecomfort.features.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.cobblecomfort.core.Voxels;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * By day, butterflies and dragonflies, the daytime counterpart to the fireflies. Butterflies flutter about wherever
 * flowers grow under the open sky (meadows, flower forests, gardens and greenhouses; big tropical ones in the jungle),
 * from flower to flower, settling on them with their wings slowly opening and closing. Dragonflies keep to water: they
 * hang in the air, dart off in a zigzag, stop dead, and rest on lily pads and reeds. Both get out of the way of players
 * and companions coming close, and they leave at dusk and when it rains. Drawn as voxel boxes, lit like any mob.
 */
final class Insects {
	private static final double MIN_RANGE = 4;
	private static final double RANGE = 18;
	/** Further than this from you, they fly off. */
	private static final double LEAVE_RANGE = 28;
	private static final double FLEE_RANGE = 2.2;
	private static final int TRIES_PER_TICK = 2;

	enum Kind {
		BUTTERFLY, DRAGONFLY
	}

	/**
	 * One kind: its wings (butterflies) or abdomen (dragonflies), the edges and tips of the wings (the thorax), the spots
	 * on them (the eyes), the body, and how big it is.
	 */
	record Species(Kind kind, int main, int edge, int spot, int body, float scale) {
	}

	private static final Species[] MEADOW = {
			new Species(Kind.BUTTERFLY, 0xFFF4F2EA, 0xFF3A3A40, 0xFF3A3A40, 0xFF2A2A2E, 1f),
			new Species(Kind.BUTTERFLY, 0xFFF2E25A, 0xFFE0C83A, 0xFFE88A2A, 0xFF4A4A30, 1f),
			new Species(Kind.BUTTERFLY, 0xFFB8402A, 0xFF3A2620, 0xFF4A7ACC, 0xFF2A1E1A, 1.1f),
			new Species(Kind.BUTTERFLY, 0xFF7AA8F0, 0xFFF4F4F4, 0xFF2A3A6A, 0xFF3A3A4A, 0.85f),
			new Species(Kind.BUTTERFLY, 0xFFE8862A, 0xFF1E1A18, 0xFFF4F0E0, 0xFF1E1A18, 1.2f)};
	private static final Species[] TROPICAL = {
			new Species(Kind.BUTTERFLY, 0xFF3A8AF0, 0xFF14181E, 0xFFF4F4F4, 0xFF14181E, 1.5f),
			new Species(Kind.BUTTERFLY, 0xFFF2DC4A, 0xFF1A1A1E, 0xFF3A6AE8, 0xFF1A1A1E, 1.4f),
			new Species(Kind.BUTTERFLY, 0xFF3ACC6A, 0xFF101410, 0xFFF0D040, 0xFF101410, 1.6f)};
	private static final Species[] DRAGONFLIES = {
			new Species(Kind.DRAGONFLY, 0xFF3A8AE8, 0xFF5AA84A, 0xFF3AC8E8, 0xFF1E2A3A, 1f),
			new Species(Kind.DRAGONFLY, 0xFFD8342A, 0xFF8A3A2A, 0xFFC8402A, 0xFF2A1A1A, 0.9f),
			new Species(Kind.DRAGONFLY, 0xFF6AC84A, 0xFF3A6A2A, 0xFF4AA83A, 0xFF1A2A1A, 1.05f),
			new Species(Kind.DRAGONFLY, 0xFFE8B83A, 0xFF8A6A2A, 0xFFA87A2A, 0xFF2A2214, 0.95f)};
	private static final int WING = 0xFFDCE8EE;

	enum State {
		FLY, SIT, HOVER, DART
	}

	static final class Insect {
		final Species species;
		final float seed;
		State state;
		double x, y, z, xo, yo, zo, vx, vy, vz;
		/** Where it heads (a flower to land on, a point over the water), and whether it lands there. */
		Vec3 target;
		boolean perch;
		/** Where it came out (a dragonfly's stretch of water). */
		Vec3 home;
		float yaw, yawO, size, sizeO;
		int age, timer;
		boolean leaving;

		Insect(Species species, double x, double y, double z, float seed) {
			this.species = species;
			this.x = xo = x;
			this.y = yo = y;
			this.z = zo = z;
			this.home = new Vec3(x, y, z);
			this.seed = seed;
			this.state = species.kind == Kind.BUTTERFLY ? State.FLY : State.HOVER;
		}

		void save() {
			xo = x;
			yo = y;
			zo = z;
			yawO = yaw;
			sizeO = size;
		}

		Vec3 position() {
			return new Vec3(x, y, z);
		}
	}

	private final RandomSource random = RandomSource.create();
	private final List<Insect> insects = new ArrayList<>();
	private boolean day;

	void clear() {
		insects.clear();
	}

	int count(Kind kind) {
		int n = 0;
		for (Insect insect : insects) {
			if (insect.species.kind == kind && !insect.leaving) {
				n++;
			}
		}
		return n;
	}

	/** Where the butterflies are (for companions to watch and chase). */
	List<Vec3> butterflies() {
		List<Vec3> out = new ArrayList<>();
		for (Insect insect : insects) {
			if (insect.species.kind == Kind.BUTTERFLY && !insect.leaving) {
				out.add(insect.position());
			}
		}
		return out;
	}

	void tick(ClientLevel level, Vec3 camera, AtmosphereConfig config, List<Vec3> companions) {
		day = level.dimension() == Level.OVERWORLD && !level.isDarkOutside() && !level.isRaining();
		Iterator<Insect> it = insects.iterator();
		while (it.hasNext()) {
			Insect insect = it.next();
			insect.save();
			insect.age++;
			boolean wanted = insect.species.kind == Kind.BUTTERFLY ? config.butterflies : config.dragonflies;
			if (!day || !wanted || Math.abs(insect.x - camera.x) > LEAVE_RANGE || Math.abs(insect.z - camera.z) > LEAVE_RANGE) {
				insect.leaving = true;
			}
			if (insect.species.kind == Kind.BUTTERFLY) {
				butterfly(level, insect, companions);
			} else {
				dragonfly(level, insect, companions);
			}
			insect.size = insect.leaving ? insect.size - 0.04f : Math.min(1, insect.size + 0.05f);
			if (insect.leaving && insect.size <= 0) {
				it.remove();
			}
		}
		if (!day) {
			return;
		}
		for (int i = 0; i < TRIES_PER_TICK; i++) {
			if (config.butterflies && count(Kind.BUTTERFLY) < config.butterflyCount) {
				spawnButterfly(level, camera);
			}
			if (config.dragonflies && count(Kind.DRAGONFLY) < config.dragonflyCount) {
				spawnDragonfly(level, camera);
			}
		}
	}

	// ---------------------------------------------------------------- butterflies

	private void spawnButterfly(ClientLevel level, Vec3 camera) {
		double angle = random.nextDouble() * Mth.TWO_PI;
		double distance = MIN_RANGE + random.nextDouble() * (RANGE - MIN_RANGE);
		int x = Mth.floor(camera.x + Math.cos(angle) * distance);
		int z = Mth.floor(camera.z + Math.sin(angle) * distance);
		if (!level.hasChunkAt(x, z)) {
			return;
		}
		// The first flower from a little above you down to a little below, with the sky over it (a greenhouse too).
		for (int y = Mth.floor(camera.y) + 8; y >= Mth.floor(camera.y) - 8; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			if (flower(state) && level.getBrightness(LightLayer.SKY, pos) >= 10) {
				boolean jungle = level.getBiome(pos).is(BiomeTags.IS_JUNGLE);
				Species[] pool = jungle && random.nextFloat() < 0.75f ? TROPICAL : MEADOW;
				Insect fly = new Insect(pool[random.nextInt(pool.length)], x + 0.5, y + 1.2 + random.nextDouble(), z + 0.5,
						random.nextFloat() * 100);
				fly.timer = 20 + random.nextInt(60);
				insects.add(fly);
			}
			return;
		}
	}

	private static boolean flower(BlockState state) {
		return state.is(BlockTags.FLOWERS) && !state.is(BlockTags.LEAVES);
	}

	/** A flower to land on near {@code from}: a spot just above its top, or null. */
	private Vec3 findFlower(ClientLevel level, Vec3 from, int radius) {
		for (int i = 0; i < 24; i++) {
			BlockPos pos = BlockPos.containing(from.x + random.nextInt(radius * 2 + 1) - radius, from.y + random.nextInt(6) - 4,
					from.z + random.nextInt(radius * 2 + 1) - radius);
			if (flower(level.getBlockState(pos))) {
				while (flower(level.getBlockState(pos.above()))) {
					pos = pos.above();
				}
				if (level.getBlockState(pos.above()).isAir()) {
					return new Vec3(pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5);
				}
			}
		}
		return null;
	}

	private void butterfly(ClientLevel level, Insect b, List<Vec3> companions) {
		if (b.leaving) {
			// Off and away, upwards.
			b.state = State.FLY;
			b.vy += 0.006;
			move(b, 0.09);
			return;
		}
		Vec3 threat = threat(level, b, companions);
		if (threat != null) {
			// Away from whoever comes close, and up.
			double dx = b.x - threat.x, dz = b.z - threat.z, d = Math.max(0.01, Math.hypot(dx, dz));
			b.state = State.FLY;
			b.vx += dx / d * 0.03;
			b.vz += dz / d * 0.03;
			b.vy += 0.03;
			b.target = new Vec3(b.x + dx / d * 4, b.y + 1.5, b.z + dz / d * 4);
			b.perch = false;
			b.timer = 60;
		}
		if (b.state == State.SIT) {
			if (--b.timer <= 0) {
				b.state = State.FLY;
				b.target = null;
				b.vy = 0.04;
			}
			return;
		}
		if (b.target == null || --b.timer <= 0) {
			Vec3 flower = random.nextFloat() < 0.6f ? findFlower(level, b.position(), 6) : null;
			if (flower != null) {
				b.target = flower;
				b.perch = true;
			} else {
				b.target = new Vec3(b.x + (random.nextDouble() - 0.5) * 6, groundBelow(level, b) + 1 + random.nextDouble() * 1.8,
						b.z + (random.nextDouble() - 0.5) * 6);
				b.perch = false;
			}
			b.timer = 80 + random.nextInt(160);
		}
		double dx = b.target.x - b.x, dy = b.target.y - b.y, dz = b.target.z - b.z;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (b.perch && dist < 0.15) {
			b.state = State.SIT;
			b.x = b.target.x;
			b.y = b.target.y;
			b.z = b.target.z;
			b.vx = b.vy = b.vz = 0;
			b.timer = 60 + random.nextInt(200);
			return;
		}
		// Fluttering: towards where it heads, jittering this way and that, bobbing with its wingbeats.
		double pull = b.perch && dist < 1.2 ? 0.02 : 0.008;
		b.vx += dx / Math.max(dist, 0.01) * pull + (random.nextDouble() - 0.5) * 0.014;
		b.vy += dy / Math.max(dist, 0.01) * pull + Mth.sin(b.age * 0.45f + b.seed) * 0.008;
		b.vz += dz / Math.max(dist, 0.01) * pull + (random.nextDouble() - 0.5) * 0.014;
		move(b, b.perch && dist < 1.2 ? 0.05 : 0.08);
		if (!free(level, b.x, b.y, b.z)) {
			// Bumped into something: back out and up.
			b.x = b.xo;
			b.z = b.zo;
			b.y = b.yo + 0.05;
			b.vx = -b.vx * 0.5;
			b.vz = -b.vz * 0.5;
			b.target = null;
		}
	}

	private static double groundBelow(ClientLevel level, Insect b) {
		BlockPos pos = BlockPos.containing(b.x, b.y, b.z);
		for (int i = 0; i < 8 && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				&& level.getFluidState(pos).isEmpty(); i++) {
			pos = pos.below();
		}
		return pos.getY() + 1;
	}

	// ---------------------------------------------------------------- dragonflies

	private void spawnDragonfly(ClientLevel level, Vec3 camera) {
		double angle = random.nextDouble() * Mth.TWO_PI;
		double distance = MIN_RANGE + random.nextDouble() * (RANGE - MIN_RANGE);
		int x = Mth.floor(camera.x + Math.cos(angle) * distance);
		int z = Mth.floor(camera.z + Math.sin(angle) * distance);
		if (!level.hasChunkAt(x, z)) {
			return;
		}
		Double surface = water(level, x + 0.5, camera.y + 8, z + 0.5, 16);
		if (surface == null || Math.abs(surface - camera.y) > 12) {
			return;
		}
		BlockPos pos = BlockPos.containing(x, surface, z);
		if (level.getBrightness(LightLayer.SKY, pos) < 10 || level.getBiome(pos).value().getBaseTemperature() < 0.3f) {
			return;
		}
		Insect fly = new Insect(DRAGONFLIES[random.nextInt(DRAGONFLIES.length)], x + 0.5, surface + 0.5 + random.nextDouble(),
				z + 0.5, random.nextFloat() * 100);
		fly.timer = 20 + random.nextInt(40);
		fly.yaw = random.nextFloat() * Mth.TWO_PI;
		insects.add(fly);
	}

	/** The top of open water (air above) somewhere below {@code fromY} within {@code depth}, or null. */
	private static Double water(ClientLevel level, double x, double fromY, double z, int depth) {
		BlockPos pos = BlockPos.containing(x, fromY, z);
		for (int i = 0; i < depth; i++, pos = pos.below()) {
			if (level.getFluidState(pos).is(FluidTags.WATER)) {
				return level.getBlockState(pos.above()).isAir() ? (double) pos.getY() + 0.9 : null;
			}
			if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).is(Blocks.LILY_PAD)) {
				return null;
			}
		}
		return null;
	}

	/** A lily pad or the top of a reed (sugar cane) near {@code from} to rest on: a spot on it, or null. */
	private Vec3 findPerch(ClientLevel level, Vec3 from) {
		for (int i = 0; i < 16; i++) {
			BlockPos pos = BlockPos.containing(from.x + random.nextInt(9) - 4, from.y + random.nextInt(5) - 3, from.z + random.nextInt(9) - 4);
			BlockState state = level.getBlockState(pos);
			if (state.is(Blocks.LILY_PAD)) {
				return new Vec3(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
			}
			if (state.is(Blocks.SUGAR_CANE) && level.getBlockState(pos.above()).isAir()) {
				return new Vec3(pos.getX() + 0.5, pos.getY() + 1.02, pos.getZ() + 0.5);
			}
		}
		return null;
	}

	private void dragonfly(ClientLevel level, Insect d, List<Vec3> companions) {
		if (d.leaving) {
			d.state = State.DART;
			d.vy = 0.05;
			d.vx = Math.sin(d.yaw) * 0.2;
			d.vz = Math.cos(d.yaw) * 0.2;
			d.x += d.vx;
			d.y += d.vy;
			d.z += d.vz;
			return;
		}
		Vec3 threat = threat(level, d, companions);
		if (threat != null && d.state != State.DART) {
			double dx = d.x - threat.x, dz = d.z - threat.z, len = Math.max(0.01, Math.hypot(dx, dz));
			d.target = new Vec3(d.x + dx / len * 3.5, d.y + 0.6, d.z + dz / len * 3.5);
			d.perch = false;
			d.state = State.DART;
			d.timer = 30;
		}
		switch (d.state) {
			case HOVER, SIT -> {
				if (d.state == State.HOVER) {
					// Hanging in the air, trembling a little.
					d.x += Mth.sin(d.age * 0.9f + d.seed) * 0.004;
					d.y += Mth.sin(d.age * 0.7f + d.seed * 2) * 0.004;
					d.yaw += (random.nextFloat() - 0.5f) * 0.05f;
				}
				if (--d.timer <= 0) {
					Vec3 perch = random.nextFloat() < 0.25f ? findPerch(level, d.position()) : null;
					if (perch != null) {
						d.target = perch;
						d.perch = true;
					} else {
						d.target = overWater(level, d);
						d.perch = false;
					}
					d.state = State.DART;
					d.timer = 40;
				}
			}
			case DART -> {
				double dx = d.target.x - d.x, dy = d.target.y - d.y, dz = d.target.z - d.z;
				double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (dist < 0.06 || --d.timer <= 0) {
					// Stopping dead.
					d.state = d.perch ? State.SIT : State.HOVER;
					d.timer = d.perch ? 60 + random.nextInt(160) : 12 + random.nextInt(45);
					d.vx = d.vy = d.vz = 0;
				} else {
					double speed = Math.min(0.3, dist * 0.35 + 0.03);
					d.vx = dx / dist * speed;
					d.vy = dy / dist * speed;
					d.vz = dz / dist * speed;
					d.x += d.vx;
					d.y += d.vy;
					d.z += d.vz;
					d.yaw = turn(d.yaw, (float) Math.atan2(dx, dz), 0.6f);
				}
			}
			default -> {
			}
		}
	}

	/** Somewhere to dart to: a few blocks off over the water near its home, a little above it. */
	private Vec3 overWater(ClientLevel level, Insect d) {
		for (int i = 0; i < 6; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI, r = 1 + random.nextDouble() * 2.5;
			double x = d.x + Math.cos(angle) * r, z = d.z + Math.sin(angle) * r;
			if (Math.hypot(x - d.home.x, z - d.home.z) > 7) {
				continue;
			}
			Double surface = water(level, x, d.y + 3, z, 8);
			if (surface != null) {
				return new Vec3(x, surface + 0.4 + random.nextDouble() * 1.2, z);
			}
		}
		// No water that way: back towards home.
		return new Vec3(d.home.x + (random.nextDouble() - 0.5) * 2, d.home.y, d.home.z + (random.nextDouble() - 0.5) * 2);
	}

	// ---------------------------------------------------------------- shared

	/** The nearest player or companion too close (closer still for one standing still), or null. */
	private static Vec3 threat(ClientLevel level, Insect insect, List<Vec3> companions) {
		Vec3 at = insect.position();
		for (Player player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			double speed = Math.hypot(player.getX() - player.xOld, player.getZ() - player.zOld);
			double range = speed > 0.15 ? FLEE_RANGE * 1.8 : FLEE_RANGE;
			Vec3 body = player.position().add(0, 1, 0);
			if (body.distanceToSqr(at) < range * range) {
				return body;
			}
		}
		for (Vec3 companion : companions) {
			if (companion.distanceToSqr(at) < 1.4 * 1.4) {
				return companion;
			}
		}
		return null;
	}

	private static void move(Insect insect, double max) {
		insect.vx *= 0.9;
		insect.vy *= 0.9;
		insect.vz *= 0.9;
		double speed = Math.sqrt(insect.vx * insect.vx + insect.vy * insect.vy + insect.vz * insect.vz);
		if (speed > max) {
			insect.vx *= max / speed;
			insect.vy *= max / speed;
			insect.vz *= max / speed;
		}
		insect.x += insect.vx;
		insect.y += insect.vy;
		insect.z += insect.vz;
		if (Math.hypot(insect.vx, insect.vz) > 0.006) {
			insect.yaw = turn(insect.yaw, (float) Math.atan2(insect.vx, insect.vz), 0.25f);
		}
	}

	private static float turn(float from, float to, float rate) {
		return from + Mth.wrapDegrees((to - from) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * rate;
	}

	private static boolean free(ClientLevel level, double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y, z);
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	// ---------------------------------------------------------------- drawing

	void render(LevelRenderContext context, ClientLevel level, Vec3 eye, float partial) {
		if (insects.isEmpty()) {
			return;
		}
		List<Drawn> drawn = new ArrayList<>();
		for (Insect insect : insects) {
			double x = Mth.lerp(partial, insect.xo, insect.x);
			double y = Mth.lerp(partial, insect.yo, insect.y);
			double z = Mth.lerp(partial, insect.zo, insect.z);
			double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
			float size = Mth.lerp(partial, insect.sizeO, insect.size);
			if (dx * dx + dy * dy + dz * dz > 48 * 48 || size <= 0) {
				continue;
			}
			float yaw = Mth.rotLerp(partial, insect.yawO * Mth.RAD_TO_DEG, insect.yaw * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
			float time = insect.age + partial + insect.seed;
			float wings = wings(insect, time);
			drawn.add(new Drawn(insect.species, (float) dx, (float) dy, (float) dz, yaw, wings, size,
					LightCoordsUtil.getLightCoords(level, BlockPos.containing(x, y, z))));
		}
		if (drawn.isEmpty()) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), Voxels.TYPE, (base, consumer) -> {
			for (Drawn d : drawn) {
				PoseStack.Pose pose = base.copy();
				pose.translate(d.dx, d.dy, d.dz);
				pose.rotate(Axis.YP, d.yaw);
				float s = d.species.scale / 32f * d.size;
				pose.scale(s, s, s);
				if (d.species.kind == Kind.BUTTERFLY) {
					butterflyModel(consumer, pose, d.species, d.wings, d.light);
				} else {
					dragonflyModel(consumer, pose, d.species, d.wings, d.light);
				}
			}
		});
	}

	/**
	 * How far the wings are up (radians): a butterfly flutters in flight and sits with them closed over its back,
	 * opening them now and then to bask; a dragonfly's buzz in a blur, held flat when it rests.
	 */
	private static float wings(Insect insect, float time) {
		if (insect.species.kind == Kind.BUTTERFLY) {
			if (insect.state == State.SIT) {
				float bask = Mth.clamp((Mth.sin(time * 0.012f) - 0.5f) * 4, 0, 1);
				return Mth.lerp(bask, 1.3f + Mth.sin(time * 0.06f) * 0.2f, 0.1f);
			}
			return 0.2f + Mth.sin(time * 1.4f) * 1.0f;
		}
		return insect.state == State.SIT ? -0.08f : Mth.sin(time * 3.1f) * 0.3f;
	}

	private record Drawn(Species species, float dx, float dy, float dz, float yaw, float wings, float size, int light) {
	}

	/** A butterfly, in 1/32 block (times its size), nose along +z: a slim body, antennae, four patterned wings. */
	private static void butterflyModel(VertexConsumer vc, PoseStack.Pose p, Species s, float wings, int light) {
		Voxels.box(vc, p, -0.4f, -0.4f, -3f, 0.4f, 0.4f, 3f, s.body, light);
		Voxels.box(vc, p, -0.6f, -0.5f, 3f, 0.6f, 0.6f, 4.2f, s.body, light);
		for (int side = -1; side <= 1; side += 2) {
			PoseStack.Pose a = p.copy();
			a.translate(side * 0.3f, 0.4f, 4.0f);
			a.rotate(Axis.YP, side * 0.35f);
			a.rotate(Axis.XP, -0.6f);
			Voxels.box(vc, a, -0.1f, -0.1f, 0, 0.1f, 0.1f, 3.2f, s.body, light);
			Voxels.box(vc, a, -0.25f, -0.25f, 3.0f, 0.25f, 0.25f, 3.5f, s.body, light);
		}
		for (int side = -1; side <= 1; side += 2) {
			PoseStack.Pose w = p.copy();
			w.rotate(Axis.ZP, side * wings);
			// Forewing, its tip, a spot.
			box(vc, w, side, 0.4f, 6.5f, -0.1f, 0.1f, 0f, 5.2f, s.main, light);
			box(vc, w, side, 4.6f, 6.7f, -0.13f, 0.13f, 2.4f, 5.4f, s.edge, light);
			box(vc, w, side, 2.9f, 4.3f, -0.16f, 0.16f, 1.9f, 3.3f, s.spot, light);
			// Hindwing and its edge.
			box(vc, w, side, 0.4f, 4.6f, -0.1f, 0.1f, -4.2f, 0.2f, s.main, light);
			box(vc, w, side, 3.6f, 4.8f, -0.13f, 0.13f, -4.4f, -1.4f, s.edge, light);
		}
	}

	/** A dragonfly, in 1/32 block (times its size): a long ringed abdomen, a thorax, big eyes, four clear wings. */
	private static void dragonflyModel(VertexConsumer vc, PoseStack.Pose p, Species s, float wings, int light) {
		Voxels.box(vc, p, -0.35f, -0.35f, -10f, 0.35f, 0.35f, 0f, s.main, light);
		for (float z = -9f; z < -0.5f; z += 2f) {
			Voxels.box(vc, p, -0.4f, -0.4f, z, 0.4f, 0.4f, z + 0.35f, s.body, light);
		}
		Voxels.box(vc, p, -0.75f, -0.65f, 0f, 0.75f, 0.75f, 3f, s.edge, light);
		for (int side = -1; side <= 1; side += 2) {
			box(vc, p, side, 0.1f, 1.1f, -0.45f, 0.85f, 3f, 4.4f, s.spot, light);
		}
		for (int side = -1; side <= 1; side += 2) {
			for (int pair = 0; pair < 2; pair++) {
				PoseStack.Pose w = p.copy();
				w.translate(0, 0.7f, pair == 0 ? 2.2f : 0.9f);
				w.rotate(Axis.ZP, side * (wings + (pair == 0 ? 0.05f : -0.05f)));
				w.rotate(Axis.YP, side * (pair == 0 ? 0.08f : -0.12f));
				box(vc, w, side, 0.6f, 8f, -0.05f, 0.05f, -0.6f, 0.6f, WING, light);
				box(vc, w, side, 6.6f, 7.4f, -0.08f, 0.08f, 0.1f, 0.6f, 0xFF4A4A4A, light);
			}
		}
	}

	/** A box from {@code a} to {@code b} out to one side ({@code side} -1 left, 1 right). */
	private static void box(VertexConsumer vc, PoseStack.Pose p, int side, float a, float b, float y0, float y1, float z0, float z1,
			int color, int light) {
		float x0 = side * a, x1 = side * b;
		Voxels.box(vc, p, Math.min(x0, x1), y0, z0, Math.max(x0, x1), y1, z1, color, light);
	}
}
