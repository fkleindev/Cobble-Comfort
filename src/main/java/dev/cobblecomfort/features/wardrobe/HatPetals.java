package dev.cobblecomfort.features.wardrobe;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Now and then a petal (or an autumn leaf) falls from a wreath, more often while its wearer runs. */
final class HatPetals {
	private static final double RANGE = 32.0;
	/** Chance per tick standing still, and added per block per second of speed. */
	private static final float STILL_CHANCE = 1f / 120f;
	private static final float SPEED_CHANCE = 1f / 60f;
	private static final float PETAL_SCALE = 0.45f;

	private HatPetals() {
	}

	static void tick(Minecraft mc) {
		RandomSource random = mc.level.getRandom();
		for (AbstractClientPlayer player : mc.level.players()) {
			if (player.isInvisible() || player.isSpectator() || player.distanceToSqr(mc.player) > RANGE * RANGE) {
				continue;
			}
			// Your own would fall right past the camera.
			if (player == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
				continue;
			}
			Hat hat = WardrobeFeature.INSTANCE.hatFor(player);
			if (hat == null || hat.style != Hat.Style.WREATH && hat.style != Hat.Style.LEAVES) {
				continue;
			}
			Vec3 motion = player.position().subtract(player.xo, player.yo, player.zo);
			float speed = (float) motion.horizontalDistance() * 20f;
			if (random.nextFloat() >= STILL_CHANCE + speed * SPEED_CHANCE) {
				continue;
			}
			double angle = random.nextDouble() * Math.PI * 2.0;
			double x = player.getX() + Math.cos(angle) * 0.3;
			double y = player.getEyeY() + 0.2;
			double z = player.getZ() + Math.sin(angle) * 0.3;
			Particle petal = mc.particleEngine.createParticle(petal(hat, random), x, y, z, 0.0, 0.0, 0.0);
			if (petal != null) {
				// Leaf particles are the size of a whole leaf; a petal is smaller.
				petal.scale(PETAL_SCALE);
			}
		}
	}

	private static ParticleOptions petal(Hat hat, RandomSource random) {
		if (hat == Hat.WREATH_CHERRY) {
			return ParticleTypes.CHERRY_LEAVES;
		}
		return ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, hat.main[random.nextInt(hat.main.length)]);
	}
}
