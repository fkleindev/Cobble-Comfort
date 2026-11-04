package dev.cobblecomfort.features.atmosphere;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * How much snow lies on each player: it settles while they stand in snowfall, melts away under a roof or out of the
 * snow, is gone at once in water or fire, and most of it falls off when they jump.
 */
final class SnowCover {
	private static final double RANGE = 64;
	/** A minute of snowfall covers a player completely; it melts in half that. */
	private static final float SETTLE = 1f / (20 * 60);
	private static final float MELT = 1f / (20 * 30);
	private static final float SHAKE_OFF = 0.6f;

	private final Int2FloatOpenHashMap amounts = new Int2FloatOpenHashMap();
	private final IntSet grounded = new IntOpenHashSet();
	private final IntSet seen = new IntOpenHashSet();
	private final RandomSource random = RandomSource.create();

	void tick(ClientLevel level, Vec3 camera) {
		seen.clear();
		for (AbstractClientPlayer player : level.players()) {
			if (player.isSpectator() || player.distanceToSqr(camera) > RANGE * RANGE) {
				continue;
			}
			int id = player.getId();
			seen.add(id);
			float amount = amounts.get(id);
			if (player.isInWater() || player.isInLava() || player.isOnFire()) {
				amount = 0f;
			} else if (snowingOn(level, player)) {
				amount = Math.min(1f, amount + SETTLE * level.getRainLevel(1f));
			} else {
				amount = Math.max(0f, amount - MELT);
			}
			boolean onGround = player.onGround();
			if (grounded.contains(id) && !onGround && player.getY() - player.yo > 0.1 && amount > 0.05f) {
				shake(level, player, amount * SHAKE_OFF);
				amount *= 1f - SHAKE_OFF;
			}
			if (onGround) {
				grounded.add(id);
			} else {
				grounded.remove(id);
			}
			if (amount > 0f) {
				amounts.put(id, amount);
			} else {
				amounts.remove(id);
			}
		}
		amounts.keySet().retainAll(seen);
		grounded.retainAll(seen);
	}

	private static boolean snowingOn(ClientLevel level, AbstractClientPlayer player) {
		if (!level.isRaining()) {
			return false;
		}
		BlockPos head = BlockPos.containing(player.getEyePosition());
		return level.canSeeSky(head) && level.getBiome(head).value().getPrecipitationAt(head, level.getSeaLevel()) == Biome.Precipitation.SNOW;
	}

	/** Snow crumbs falling off head and shoulders. */
	private void shake(ClientLevel level, AbstractClientPlayer player, float amount) {
		BlockParticleOption snow = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState());
		int count = 3 + Math.round(amount * 14);
		for (int i = 0; i < count; i++) {
			double x = player.getX() + (random.nextDouble() - 0.5) * 0.7;
			double y = player.getY() + player.getBbHeight() * (0.72 + random.nextDouble() * 0.28);
			double z = player.getZ() + (random.nextDouble() - 0.5) * 0.7;
			level.addParticle(snow, x, y, z, (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
		}
	}

	float amount(int id) {
		return amounts.get(id);
	}

	void clear() {
		amounts.clear();
		grounded.clear();
	}
}
