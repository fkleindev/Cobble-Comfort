package dev.cobblecomfort.features.atmosphere;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * On clear nights, fireflies drift a little above the ground in swamps, meadows, flower forests and cherry groves,
 * blinking on and off, and dart away from players coming close. They fade out at dawn and when it starts raining.
 */
final class Fireflies {
	private static final List<ResourceKey<Biome>> BIOMES = List.of(Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.MEADOW,
			Biomes.FLOWER_FOREST, Biomes.CHERRY_GROVE, Biomes.SUNFLOWER_PLAINS);
	private static final double MIN_RANGE = 3;
	private static final double RANGE = 16;
	/** Fireflies further than this from you go out. */
	private static final double LEAVE_RANGE = 26;
	private static final int TRIES_PER_TICK = 3;
	private static final double MAX_SPEED = 0.045;
	private static final double FLEE_RANGE = 2.2;
	private static final int FADE = 40;
	private static final int COLOR = 0xFFD8FF5E;

	private final RandomSource random = RandomSource.create();
	private boolean night;

	void tick(ClientLevel level, Vec3 camera, Motes motes, AtmosphereConfig config) {
		night = level.isDarkOutside() && !level.isRaining();
		if (!night) {
			return;
		}
		int missing = config.fireflyCount - motes.count(Motes.Shape.GLOW);
		for (int i = 0; i < Math.min(missing, TRIES_PER_TICK); i++) {
			trySpawn(level, camera, motes);
		}
	}

	private void trySpawn(ClientLevel level, Vec3 camera, Motes motes) {
		double angle = random.nextDouble() * Math.PI * 2;
		double distance = MIN_RANGE + random.nextDouble() * (RANGE - MIN_RANGE);
		int x = Mth.floor(camera.x + Math.cos(angle) * distance);
		int z = Mth.floor(camera.z + Math.sin(angle) * distance);
		if (!level.hasChunkAt(x, z)) {
			return;
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos pos = new BlockPos(x, ground, z);
		if (Math.abs(ground - camera.y) > 14 || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				|| !inBiome(level.getBiome(pos))) {
			return;
		}
		double home = ground + 0.6 + random.nextDouble() * 1.8;
		Motes.Mote fly = motes.add(new Motes.Mote(Motes.Shape.GLOW, x + random.nextDouble(), home, z + random.nextDouble(),
				400 + random.nextInt(400)));
		fly.size0 = fly.size1 = 0.45f;
		fly.alpha = 0.95f;
		fly.color = COLOR;
		fly.drag = 0.9f;
		fly.fadeIn = FADE;
		fly.fadeOut = FADE;
		float phase = random.nextFloat() * Mth.TWO_PI;
		float blink = 0.06f + random.nextFloat() * 0.05f;
		fly.mover = m -> move(level, m, home, phase, blink);
	}

	private static boolean inBiome(Holder<Biome> biome) {
		for (ResourceKey<Biome> key : BIOMES) {
			if (biome.is(key)) {
				return true;
			}
		}
		return false;
	}

	private void move(ClientLevel level, Motes.Mote fly, double home, float phase, float blink) {
		// Wander, staying near the height it started at.
		fly.vx += (random.nextDouble() - 0.5) * 0.008;
		fly.vy += (random.nextDouble() - 0.5) * 0.005 + (home - fly.y) * 0.002;
		fly.vz += (random.nextDouble() - 0.5) * 0.008;
		for (Player player : level.players()) {
			double dx = fly.x - player.getX();
			double dy = fly.y - (player.getY() + 1);
			double dz = fly.z - player.getZ();
			double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (d < FLEE_RANGE && d > 1e-3) {
				double push = 0.012 * (1 - d / FLEE_RANGE);
				fly.vx += dx / d * push;
				fly.vy += Math.max(0, dy / d) * push;
				fly.vz += dz / d * push;
			}
		}
		double speed = Math.sqrt(fly.vx * fly.vx + fly.vy * fly.vy + fly.vz * fly.vz);
		if (speed > MAX_SPEED) {
			fly.vx *= MAX_SPEED / speed;
			fly.vy *= MAX_SPEED / speed;
			fly.vz *= MAX_SPEED / speed;
		}
		// Glows up and dims down again, never quite going out.
		float wave = Mth.sin(fly.age * blink + phase);
		fly.glow = 0.2f + 0.8f * (float) Math.pow(0.5f + 0.5f * wave, 3);
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		boolean far = Math.abs(fly.x - camera.x) > LEAVE_RANGE || Math.abs(fly.z - camera.z) > LEAVE_RANGE;
		if ((!night || far) && fly.life - fly.age > FADE) {
			fly.life = fly.age + FADE;
		}
	}
}
