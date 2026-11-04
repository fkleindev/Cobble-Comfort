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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * What falls from a hat: now and then a petal (or an autumn leaf) from a wreath and confetti from a party hat or a
 * New Year's top hat, more often while its wearer runs; notes rise from headphones while a jukebox plays nearby; a
 * bell on the neck or the back, an elf's hat or reindeer antlers rings softly as its wearer walks; snowflakes drift from
 * ice wings, and rockets on the back spray sparks while their wearer is in the air; a pipe puffs smoke.
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
	/** A pipe puffs about every this many ticks; its smoke is this big. */
	private static final float SMOKE_CHANCE = 1f / 50f;
	private static final float SMOKE_SCALE = 0.45f;
	/** How far the neck, which the head turns about, is below the eyes (blocks). */
	private static final double NECK_BELOW_EYES = 0.12;

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
			Hat hat = WardrobeFeature.INSTANCE.hatFor(player);
			if (bells && hat != null && (hat.style == Hat.Style.ELF || hat.style == Hat.Style.REINDEER)) {
				// The jingle bell on an elf's tip or a reindeer's band.
				ring(mc, player, random, 1.8f);
			}
			if (!particles) {
				continue;
			}
			// Your own would fall right past the camera.
			if (player == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
				continue;
			}
			smoke(mc, player, hat, random);
			BackPiece back = WardrobeFeature.INSTANCE.backFor(player);
			if (back != null && back.style == BackPiece.Style.ICE_WINGS) {
				fall(mc, player, random, ParticleTypes.SNOWFLAKE, 1f);
			} else if (back != null && back.style == BackPiece.Style.ROCKETS) {
				sparks(mc, player, random);
			}
			if (hat == null) {
				continue;
			}
			switch (hat.style) {
				case WREATH, LEAVES -> fall(mc, player, random, petal(hat, random), PETAL_SCALE);
				case PARTY, SPARKLE -> fall(mc, player, random,
						ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, CONFETTI[random.nextInt(CONFETTI.length)]), CONFETTI_SCALE);
				case HEADPHONES -> notes(mc, player, random);
				default -> {
				}
			}
		}
	}


	/**
	 * Now and then a puff of smoke from the bowl of a pipe, or bubbles under water. Not while a helmet or a pumpkin hides
	 * the face, nor lying down to swim or fly.
	 */
	private static void smoke(Minecraft mc, AbstractClientPlayer player, Hat hat, RandomSource random) {
		if (random.nextFloat() >= SMOKE_CHANCE || player.isVisuallySwimming() || player.isFallFlying()
				|| hat != null && hat.style == Hat.Style.PUMPKIN
				|| hat == null && !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
				|| WardrobeFeature.INSTANCE.facesFor(player).stream().noneMatch(face -> face.style == Face.Style.PIPE)) {
			return;
		}
		// The bowl, from the neck the head turns about: across to the left, up, out to the front.
		Vector3f bowl = FaceLayer.PIPE_BOWL;
		Vec3 forward = Vec3.directionFromRotation(player.getXRot(), player.getYHeadRot());
		Vec3 up = Vec3.directionFromRotation(player.getXRot() - 90f, player.getYHeadRot());
		double yaw = Math.toRadians(player.getYHeadRot());
		Vec3 left = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
		Vec3 at = new Vec3(player.getX(), player.getEyeY() - NECK_BELOW_EYES, player.getZ())
				.add(left.scale(bowl.x)).add(up.scale(-bowl.y)).add(forward.scale(-bowl.z));
		boolean water = player.isUnderWater();
		int puffs = 1 + random.nextInt(2);
		for (int i = 0; i < puffs; i++) {
			Particle particle = mc.particleEngine.createParticle(water ? ParticleTypes.BUBBLE : ParticleTypes.WHITE_SMOKE,
					at.x + (random.nextDouble() - 0.5) * 0.04, at.y + i * 0.04, at.z + (random.nextDouble() - 0.5) * 0.04,
					(random.nextDouble() - 0.5) * 0.01, 0.02 + random.nextDouble() * 0.01, (random.nextDouble() - 0.5) * 0.01);
			if (particle != null && !water) {
				particle.scale(SMOKE_SCALE);
			}
		}
	}

	/** Sparks from the bottoms of the rockets on the back while their wearer is in the air: jumping or falling. */
	private static void sparks(Minecraft mc, AbstractClientPlayer player, RandomSource random) {
		if (player.onGround() || player.isPassenger() || random.nextFloat() > 0.6f) {
			return;
		}
		double yaw = Math.toRadians(player.yBodyRot);
		// Behind the back, a rocket to each side.
		double side = random.nextBoolean() ? 0.2 : -0.2;
		double x = player.getX() + Math.sin(yaw) * 0.35 - Math.cos(yaw) * side;
		double z = player.getZ() - Math.cos(yaw) * 0.35 - Math.sin(yaw) * side;
		double y = player.getY() + (player.isCrouching() ? 0.55 : 0.75);
		mc.level.addParticle(ParticleTypes.FIREWORK, x, y, z, (random.nextDouble() - 0.5) * 0.04, -0.12, (random.nextDouble() - 0.5) * 0.04);
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
