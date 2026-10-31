package dev.cobblecomfort.features.wardrobe;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What falls from a hat: now and then a petal (or an autumn leaf) from a wreath and confetti from a party hat, more
 * often while its wearer runs; notes rise from headphones while a jukebox plays nearby; and a bell on the neck or
 * the back rings softly as its wearer walks.
 */
final class HatPetals {
	private static final double RANGE = 32.0;
	/** Chance per tick standing still, and added per block per second of speed. */
	private static final float STILL_CHANCE = 1f / 120f;
	private static final float SPEED_CHANCE = 1f / 60f;
	private static final float PETAL_SCALE = 0.45f;
	private static final float CONFETTI_SCALE = 0.3f;
	private static final int[] CONFETTI = {0xFFF4466A, 0xFFF2D23A, 0xFF4AA8E8, 0xFF5AD25A, 0xFFB86AF2, 0xFFF29A3A};
	/** How far from headphones a jukebox is heard (blocks), and how often notes rise (ticks). */
	private static final int JUKEBOX_RANGE = 6;
	private static final int NOTE_TICKS = 15;
	/** A neck bell: rings every this many ticks while its wearer moves at least this fast (blocks a second), this loud. */
	private static final int BELL_TICKS = 7;
	private static final float BELL_SPEED = 1.5f;
	private static final float BELL_VOLUME = 0.15f;

	private HatPetals() {
	}

	/** Once a tick: {@code particles} for the hats' petals, confetti and notes, {@code bells} for neck bells ringing. */
	static void tick(Minecraft mc, boolean particles, boolean bells) {
		RandomSource random = mc.level.getRandom();
		for (AbstractClientPlayer player : mc.level.players()) {
			if (player.isInvisible() || player.isSpectator() || player.distanceToSqr(mc.player) > RANGE * RANGE) {
				continue;
			}
			Neck neck = WardrobeFeature.INSTANCE.neckFor(player);
			if (bells && neck != null && neck.style == Neck.Style.BELL) {
				ring(mc, player, random, 1.5f);
			}
			Trinket trinket = WardrobeFeature.INSTANCE.trinketFor(player);
			if (bells && trinket != null && trinket.style == Trinket.Style.BELL) {
				// The little bell on the back rings higher.
				ring(mc, player, random, 2f);
			}
			if (!particles) {
				continue;
			}
			// Your own would fall right past the camera.
			if (player == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
				continue;
			}
			Hat hat = WardrobeFeature.INSTANCE.hatFor(player);
			if (hat == null) {
				continue;
			}
			switch (hat.style) {
				case WREATH, LEAVES -> fall(mc, player, random, petal(hat, random), PETAL_SCALE);
				case PARTY -> fall(mc, player, random,
						ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, CONFETTI[random.nextInt(CONFETTI.length)]), CONFETTI_SCALE);
				case HEADPHONES -> notes(mc, player, random);
				default -> {
				}
			}
		}
	}

	private static void fall(Minecraft mc, AbstractClientPlayer player, RandomSource random, ParticleOptions what, float scale) {
		Vec3 motion = player.position().subtract(player.xo, player.yo, player.zo);
		float speed = (float) motion.horizontalDistance() * 20f;
		if (random.nextFloat() >= STILL_CHANCE + speed * SPEED_CHANCE) {
			return;
		}
		double angle = random.nextDouble() * Math.PI * 2.0;
		double x = player.getX() + Math.cos(angle) * 0.3;
		double y = player.getEyeY() + 0.2;
		double z = player.getZ() + Math.sin(angle) * 0.3;
		Particle particle = mc.particleEngine.createParticle(what, x, y, z, 0.0, 0.0, 0.0);
		if (particle != null) {
			// Leaf particles are the size of a whole leaf; a petal or a scrap of confetti is smaller.
			particle.scale(scale);
		}
	}

	/** A soft ring of the bell every few steps while walking. */
	private static void ring(Minecraft mc, AbstractClientPlayer player, RandomSource random, float pitch) {
		float speed = (float) player.position().subtract(player.xo, player.yo, player.zo).horizontalDistance() * 20f;
		if (speed < BELL_SPEED || (player.tickCount + player.getId()) % BELL_TICKS != 0) {
			return;
		}
		mc.level.playLocalSound(player.getX(), player.getEyeY() - 0.5, player.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(),
				SoundSource.PLAYERS, BELL_VOLUME, pitch + random.nextFloat() * 0.3f, false);
	}

	private static ParticleOptions petal(Hat hat, RandomSource random) {
		if (hat == Hat.WREATH_CHERRY) {
			return ParticleTypes.CHERRY_LEAVES;
		}
		return ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, hat.main[random.nextInt(hat.main.length)]);
	}

	/** A note from one of the ear cups now and then, while a jukebox near the wearer plays. */
	private static void notes(Minecraft mc, AbstractClientPlayer player, RandomSource random) {
		if ((player.tickCount + player.getId()) % NOTE_TICKS != 0 || !jukeboxPlaying(mc, player.blockPosition())) {
			return;
		}
		double side = random.nextBoolean() ? 0.3 : -0.3;
		double yaw = Math.toRadians(player.yHeadRot);
		double x = player.getX() + Math.cos(yaw) * side;
		double z = player.getZ() + Math.sin(yaw) * side;
		// A note particle takes its colour from the x speed.
		mc.level.addParticle(ParticleTypes.NOTE, x, player.getEyeY(), z, random.nextInt(25) / 24.0, 0.0, 0.0);
	}

	private static boolean jukeboxPlaying(Minecraft mc, BlockPos at) {
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-JUKEBOX_RANGE, -3, -JUKEBOX_RANGE), at.offset(JUKEBOX_RANGE, 3, JUKEBOX_RANGE))) {
			BlockState state = mc.level.getBlockState(pos);
			if (state.hasProperty(JukeboxBlock.HAS_RECORD) && state.getValue(JukeboxBlock.HAS_RECORD)) {
				return true;
			}
		}
		return false;
	}
}
