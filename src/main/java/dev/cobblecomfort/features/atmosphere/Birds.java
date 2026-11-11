package dev.cobblecomfort.features.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.cobblecomfort.core.Voxels;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Birds flying over now and then, the kind depending on the biome: gulls over the sea, geese in a V over rivers and
 * plains, ducks and herons over swamps, small songbirds hopping over the treetops, parrots over the jungle, ravens over
 * the taiga, eagles and vultures circling high over mountains and dry land; owls and bats at night. They come in from
 * beyond the fog, pass by or circle a while, and leave. Drawn as voxel boxes with an entity render type, so shader
 * packs light, shade and fog them like any mob. Only on this client.
 */
final class Birds {
	/** Where flocks come in from and leave to, from the camera. */
	private static final double ENTER = 96;
	private static final double LEAVE = 140;
	private static final double DRAW = 192;
	/** Mean ticks between flocks at 100 % by day and by night. */
	private static final int DAY_INTERVAL = 280;
	private static final int NIGHT_INTERVAL = 600;
	private static final int MAX_AGE = 20 * 180;

	private final RandomSource random = RandomSource.create();
	private final List<Flock> flocks = new ArrayList<>();
	private int cooldown = 60;

	void clear() {
		flocks.clear();
		cooldown = 60;
	}

	int count() {
		return flocks.size();
	}

	void tick(ClientLevel level, Vec3 camera, AtmosphereConfig config) {
		Iterator<Flock> it = flocks.iterator();
		while (it.hasNext()) {
			Flock flock = it.next();
			flock.tick(level, camera);
			if (flock.gone(camera)) {
				it.remove();
			}
		}
		if (config.birdSounds) {
			for (Flock flock : flocks) {
				flock.call(camera);
			}
		}
		if (level.dimension() != Level.OVERWORLD || level.isThundering() || --cooldown > 0) {
			return;
		}
		boolean night = level.isDarkOutside();
		int interval = night ? NIGHT_INTERVAL : DAY_INTERVAL;
		if (level.isRaining()) {
			interval *= 3;
		}
		float amount = Math.max(0.1f, config.birdAmount / 100f);
		cooldown = (int) (interval * (0.5f + random.nextFloat()) / amount);
		int max = 2 + Math.round(2 * amount);
		if (flocks.size() >= max || night && !config.birdsAtNight || !nearSurface(level, camera)) {
			return;
		}
		trySpawn(level, camera, night);
	}

	/** Underground nobody would see them; there they stay away. */
	private static boolean nearSurface(ClientLevel level, Vec3 camera) {
		int x = Mth.floor(camera.x), z = Mth.floor(camera.z);
		return camera.y > level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 12;
	}

	private void trySpawn(ClientLevel level, Vec3 camera, boolean night) {
		// The biome of a spot somewhere around: near a coast the gulls show up too.
		double angle = random.nextDouble() * Mth.TWO_PI;
		double distance = random.nextDouble() * 48;
		int x = Mth.floor(camera.x + Math.cos(angle) * distance);
		int z = Mth.floor(camera.z + Math.sin(angle) * distance);
		if (!level.hasChunkAt(x, z)) {
			return;
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		BirdSpecies species = pick(level.getBiome(new BlockPos(x, ground, z)), night);
		if (species != null) {
			spawn(level, camera, species, false);
		}
	}

	/** One kind of bird that suits the biome (and time of day), weighted; null for none. */
	private BirdSpecies pick(Holder<Biome> biome, boolean night) {
		List<BirdSpecies> pool = new ArrayList<>();
		if (night) {
			if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(Biomes.DARK_FOREST)
					|| biome.is(Biomes.PALE_GARDEN) || biome.is(Biomes.PLAINS) || biome.is(Biomes.MEADOW)) {
				add(pool, BirdSpecies.OWL, 2);
			}
			add(pool, BirdSpecies.BAT, 2);
		} else if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH) || biome.is(Biomes.MUSHROOM_FIELDS)) {
			add(pool, BirdSpecies.SEAGULL, 5);
		} else if (biome.is(BiomeTags.IS_RIVER)) {
			add(pool, BirdSpecies.GOOSE, 3);
			add(pool, BirdSpecies.DUCK, 2);
			add(pool, BirdSpecies.HERON, 2);
			add(pool, BirdSpecies.SEAGULL, 1);
		} else if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
			add(pool, BirdSpecies.HERON, 3);
			add(pool, BirdSpecies.DUCK, 3);
			add(pool, BirdSpecies.CROW, 1);
		} else if (biome.is(BiomeTags.IS_JUNGLE)) {
			add(pool, BirdSpecies.PARROT, 5);
			add(pool, BirdSpecies.SONGBIRD, 1);
		} else if (biome.is(BiomeTags.IS_BADLANDS) || biome.is(Biomes.DESERT)) {
			add(pool, BirdSpecies.VULTURE, 4);
			add(pool, BirdSpecies.CROW, 1);
		} else if (biome.is(BiomeTags.IS_SAVANNA)) {
			add(pool, BirdSpecies.VULTURE, 3);
			add(pool, BirdSpecies.EAGLE, 1);
			add(pool, BirdSpecies.SONGBIRD, 2);
		} else if (biome.is(Biomes.FROZEN_PEAKS) || biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.STONY_PEAKS)
				|| biome.is(Biomes.SNOWY_SLOPES) || biome.is(Biomes.WINDSWEPT_HILLS) || biome.is(Biomes.WINDSWEPT_GRAVELLY_HILLS)
				|| biome.is(Biomes.GROVE)) {
			add(pool, BirdSpecies.EAGLE, 4);
			add(pool, BirdSpecies.CROW, 2);
		} else if (biome.is(BiomeTags.IS_TAIGA) || biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PALE_GARDEN)
				|| biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.ICE_SPIKES)) {
			add(pool, BirdSpecies.CROW, 4);
			add(pool, BirdSpecies.SONGBIRD, 1);
			add(pool, BirdSpecies.EAGLE, 1);
		} else if (biome.is(BiomeTags.IS_FOREST) || biome.is(Biomes.MEADOW) || biome.is(Biomes.CHERRY_GROVE)) {
			add(pool, BirdSpecies.SONGBIRD, 5);
			add(pool, BirdSpecies.CROW, 1);
			add(pool, BirdSpecies.GOOSE, 1);
		} else if (biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS)) {
			add(pool, BirdSpecies.SONGBIRD, 4);
			add(pool, BirdSpecies.GOOSE, 2);
			add(pool, BirdSpecies.CROW, 2);
			add(pool, BirdSpecies.EAGLE, 1);
		} else if (biome.is(BiomeTags.IS_OVERWORLD)) {
			// Modded or other biomes: a common mix.
			add(pool, BirdSpecies.SONGBIRD, 2);
			add(pool, BirdSpecies.CROW, 1);
		}
		return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
	}

	private static void add(List<BirdSpecies> pool, BirdSpecies species, int weight) {
		for (int i = 0; i < weight; i++) {
			pool.add(species);
		}
	}

	/** A flock coming in from beyond the fog; {@code near} puts it right in front instead (for screenshots). */
	Flock spawn(ClientLevel level, Vec3 camera, BirdSpecies species, boolean near) {
		Flock flock = new Flock(species, random);
		double heading = random.nextDouble() * Mth.TWO_PI;
		double sx = Math.sin(heading), sz = Math.cos(heading);
		if (species.circles && random.nextFloat() < 0.65f) {
			// Circle over a spot not far away for a while.
			flock.centerX = camera.x + (random.nextDouble() - 0.5) * 50;
			flock.centerZ = camera.z + (random.nextDouble() - 0.5) * 50;
			flock.radius = 9 + random.nextDouble() * 16;
			flock.circleTicks = 400 + random.nextInt(600);
			flock.turnSign = random.nextBoolean() ? 1 : -1;
			flock.mode = Mode.APPROACH;
			flock.x = flock.centerX - sx * ENTER;
			flock.z = flock.centerZ - sz * ENTER;
		} else {
			// Pass by, closest somewhere between right overhead and far off to the side.
			double side = (random.nextDouble() * 2 - 1) * 40;
			flock.x = camera.x - sx * ENTER + sz * side;
			flock.z = camera.z - sz * ENTER - sx * side;
			flock.mode = Mode.PASS;
		}
		if (near) {
			flock.x = camera.x;
			flock.z = camera.z;
			flock.mode = Mode.PASS;
		}
		flock.heading = (float) Math.atan2(sx, sz);
		if (flock.mode == Mode.APPROACH) {
			flock.heading = (float) Math.atan2(flock.centerX - flock.x, flock.centerZ - flock.z);
		}
		flock.y = flock.targetY(level);
		flock.form(random);
		flocks.add(flock);
		return flock;
	}

	void render(LevelRenderContext context, ClientLevel level, Vec3 eye, float partial) {
		if (flocks.isEmpty()) {
			return;
		}
		List<Drawn> drawn = new ArrayList<>();
		for (Flock flock : flocks) {
			for (Bird bird : flock.birds) {
				double x = Mth.lerp(partial, bird.xo, bird.x);
				double y = Mth.lerp(partial, bird.yo, bird.y);
				double z = Mth.lerp(partial, bird.zo, bird.z);
				double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
				if (dx * dx + dy * dy + dz * dz > DRAW * DRAW) {
					continue;
				}
				int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(x, y, z));
				float yaw = Mth.rotLerp(partial, bird.yawO * Mth.RAD_TO_DEG, bird.yaw * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
				float pitch = Mth.lerp(partial, bird.pitchO, bird.pitch);
				float roll = Mth.lerp(partial, bird.rollO, bird.roll);
				float time = bird.age + partial + bird.seed;
				drawn.add(new Drawn(flock.species, bird.look, (float) dx, (float) dy, (float) dz, yaw, pitch, roll,
						BirdModel.wings(flock.species.flight, time, bird.climb), light));
			}
		}
		if (drawn.isEmpty()) {
			return;
		}
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), Voxels.TYPE, (base, consumer) -> {
			for (Drawn d : drawn) {
				PoseStack.Pose pose = base.copy();
				pose.translate(d.dx, d.dy, d.dz);
				pose.rotate(Axis.YP, d.yaw);
				pose.rotate(Axis.XP, d.pitch);
				pose.rotate(Axis.ZP, d.roll);
				BirdModel.draw(consumer, pose, d.species, d.look, d.wings, d.light);
			}
		});
	}

	private record Drawn(BirdSpecies species, BirdSpecies.Look look, float dx, float dy, float dz, float yaw, float pitch,
			float roll, BirdModel.Wings wings, int light) {
	}

	enum Mode {
		PASS, APPROACH, CIRCLE, LEAVE
	}

	/** The way a flock flies, led by an invisible point the birds keep their places around. */
	static final class Flock {
		final BirdSpecies species;
		final List<Bird> birds = new ArrayList<>();
		final RandomSource random;
		Mode mode;
		double x, y, z;
		float heading, turn;
		double centerX, centerZ, radius;
		int circleTicks, turnSign = 1;
		float height;
		int age;
		int callIn;
		/** Frozen in place (screenshots). */
		boolean still;

		Flock(BirdSpecies species, RandomSource random) {
			this.species = species;
			this.random = random;
			this.height = species.minHeight + random.nextFloat() * (species.maxHeight - species.minHeight);
			this.callIn = 40 + random.nextInt(200);
		}

		/** Puts the birds in their places: a V, a loose bunch, or one alone. */
		void form(RandomSource random) {
			int count = species.minCount + random.nextInt(species.maxCount - species.minCount + 1);
			float span = (species.shape.wingIn + species.shape.wingOut) * species.shape.scale * 2;
			for (int i = 0; i < count; i++) {
				Bird bird = new Bird(species.looks[random.nextInt(species.looks.length)], random.nextFloat() * 100f);
				if (species.group == BirdSpecies.Group.V && i > 0) {
					int rank = (i + 1) / 2;
					int side = i % 2 == 0 ? 1 : -1;
					bird.ox = side * rank * span * 0.9f;
					bird.oz = -rank * span * 0.75f;
					bird.oy = rank * 0.05f;
				} else if (i > 0) {
					float spread = Math.max(1.2f, span * 2.2f);
					bird.ox = (random.nextFloat() - 0.5f) * 2 * spread;
					bird.oz = (random.nextFloat() - 0.5f) * 2 * spread;
					bird.oy = (random.nextFloat() - 0.5f) * spread * 0.6f;
				}
				birds.add(bird);
			}
			for (Bird bird : birds) {
				place(bird, 0f);
				bird.xo = bird.x;
				bird.yo = bird.y;
				bird.zo = bird.z;
				bird.yaw = bird.yawO = heading;
			}
		}

		/** Height to fly at here: above the ground, trees or water ahead, with a slow rise and fall. */
		double targetY(ClientLevel level) {
			double ax = x + Math.sin(heading) * 10, az = z + Math.cos(heading) * 10;
			int here = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
			int ahead = level.hasChunkAt(Mth.floor(ax), Mth.floor(az))
					? level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(ax), Mth.floor(az)) : here;
			int ground = Math.max(Math.max(here, ahead), level.getSeaLevel());
			return ground + height + Mth.sin(age * 0.02f) * 1.5f;
		}

		void tick(ClientLevel level, Vec3 camera) {
			age++;
			if (still) {
				for (Bird bird : birds) {
					bird.age++;
					bird.save();
				}
				return;
			}
			float wanted = heading;
			switch (mode) {
				case PASS, LEAVE -> {
					turn += (random.nextFloat() - 0.5f) * 0.004f;
					turn *= 0.97f;
					wanted = heading + turn * 20;
				}
				case APPROACH -> {
					wanted = (float) Math.atan2(centerX - x, centerZ - z);
					if (Math.hypot(centerX - x, centerZ - z) < radius) {
						mode = Mode.CIRCLE;
					}
				}
				case CIRCLE -> {
					double ox = x - centerX, oz = z - centerZ;
					double dist = Math.max(0.1, Math.hypot(ox, oz));
					// Tangent, pulled back onto the circle.
					double tx = -oz / dist * turnSign, tz = ox / dist * turnSign;
					double pull = Mth.clamp((dist - radius) / radius, -0.6, 0.6);
					wanted = (float) Math.atan2(tx - ox / dist * pull, tz - oz / dist * pull);
					centerX += Math.sin(age * 0.003) * 0.02;
					if (--circleTicks <= 0) {
						mode = Mode.LEAVE;
					}
				}
			}
			float maxTurn = species.speed / Math.max(6f, (float) radius) * 1.3f + 0.01f;
			float delta = Mth.clamp(Mth.wrapDegrees((wanted - heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD, -maxTurn, maxTurn);
			heading += delta;
			double speed = species.speed;
			double vx = Math.sin(heading) * speed, vz = Math.cos(heading) * speed;
			x += vx;
			z += vz;
			double ty = level.hasChunkAt(Mth.floor(x), Mth.floor(z)) ? targetY(level) : y;
			double vy = Mth.clamp((ty - y) * 0.04, -speed * 0.5, speed * 0.6);
			y += vy;
			if (mode == Mode.PASS && Math.hypot(x - camera.x, z - camera.z) > ENTER * 0.5 && age > 200) {
				mode = Mode.LEAVE;
			}
			for (Bird bird : birds) {
				bird.save();
				bird.age++;
				place(bird, delta);
			}
		}

		/** Moves a bird towards its place in the flock, with its own small wander, and turns it its way. */
		private void place(Bird bird, float delta) {
			float wobble = species == BirdSpecies.BAT ? 1.2f : species.group == BirdSpecies.Group.V ? 0.15f : 0.5f;
			float t = bird.age + bird.seed;
			double cos = Math.cos(heading), sin = Math.sin(heading);
			double tx = x + bird.ox * cos + bird.oz * sin + Mth.sin(t * 0.05f) * wobble;
			double ty = y + bird.oy + Mth.sin(t * 0.07f + 1f) * wobble * 0.5f;
			double tz = z - bird.ox * sin + bird.oz * cos + Mth.cos(t * 0.045f) * wobble;
			if (species == BirdSpecies.BAT) {
				ty += Mth.sin(t * 0.4f) * 0.3f;
			}
			double nx = bird.x + (tx - bird.x) * 0.25, ny = bird.y + (ty - bird.y) * 0.25, nz = bird.z + (tz - bird.z) * 0.25;
			double mx = nx - bird.x, my = ny - bird.y, mz = nz - bird.z;
			bird.x = nx;
			bird.y = ny;
			bird.z = nz;
			double horizontal = Math.hypot(mx, mz);
			if (horizontal > 0.02) {
				float yaw = (float) Math.atan2(mx, mz);
				bird.yaw = bird.yaw + Mth.wrapDegrees((yaw - bird.yaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * 0.3f;
			}
			bird.climb = (float) my;
			bird.pitch = Mth.lerp(0.2f, bird.pitch, (float) -Math.atan2(my, Math.max(horizontal, 0.05)) * 0.8f);
			bird.roll = Mth.lerp(0.15f, bird.roll, Mth.clamp(-delta * 18f, -0.7f, 0.7f));
		}

		boolean gone(Vec3 camera) {
			if (still) {
				return false;
			}
			double dist = Math.hypot(x - camera.x, z - camera.z);
			return age > MAX_AGE || mode == Mode.LEAVE && dist > LEAVE;
		}

		/** Now and then one of them calls, quietly, from its direction (placed nearer so it is heard at all). */
		void call(Vec3 camera) {
			if (species.callVolume <= 0 || --callIn > 0 || birds.isEmpty()) {
				return;
			}
			callIn = 60 + random.nextInt(240);
			Bird bird = birds.get(random.nextInt(birds.size()));
			double dx = bird.x - camera.x, dy = bird.y - camera.y, dz = bird.z - camera.z;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist > 64 || dist < 0.01) {
				return;
			}
			double near = Math.min(dist, 6) / dist;
			float volume = species.callVolume * (float) (1 - dist / 72);
			float pitch = species.callPitch * (0.9f + random.nextFloat() * 0.2f);
			Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(species.call(), SoundSource.AMBIENT,
					volume, pitch, random, camera.x + dx * near, camera.y + dy * near, camera.z + dz * near));
		}
	}

	static final class Bird {
		final BirdSpecies.Look look;
		final float seed;
		/** Place in the flock: to the side, up, forward (blocks). */
		float ox, oy, oz;
		double x, y, z, xo, yo, zo;
		float yaw, yawO, pitch, pitchO, roll, rollO, climb;
		int age;

		Bird(BirdSpecies.Look look, float seed) {
			this.look = look;
			this.seed = seed;
		}

		void save() {
			xo = x;
			yo = y;
			zo = z;
			yawO = yaw;
			pitchO = pitch;
			rollO = roll;
		}
	}
}
