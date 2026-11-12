package dev.cobblecomfort.features.atmosphere;

import dev.cobblecomfort.core.Movers;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A cloud of dust where players and mobs land after a fall, from whatever they land on (sand, earth, snow, gravel...):
 * a small puff from a couple of blocks up, a wide ring of dust and crumbs from a long fall.
 */
final class LandingDust {
	/** Falls shorter than this (blocks) raise no dust. */
	private static final double MIN_FALL = 2;

	/** The highest point of each mover in the air since it last stood; movers on the ground have none. */
	private final Int2DoubleOpenHashMap peak = new Int2DoubleOpenHashMap();
	private final IntSet seen = new IntOpenHashSet();
	private final RandomSource random = RandomSource.create();

	void clear() {
		peak.clear();
	}

	void tick(ClientLevel level, List<Movers.Mover> movers, Motes motes) {
		seen.clear();
		for (Movers.Mover mover : movers) {
			Entity entity = mover.entity();
			int id = entity.getId();
			seen.add(id);
			if (entity.isInWater() || entity.isPassenger() || entity.isNoGravity()) {
				peak.remove(id);
				continue;
			}
			if (!entity.onGround()) {
				peak.put(id, Math.max(peak.getOrDefault(id, entity.getY()), entity.getY()));
				continue;
			}
			if (peak.containsKey(id)) {
				double fall = peak.remove(id) - entity.getY();
				if (fall >= MIN_FALL) {
					dust(level, motes, entity, fall);
				}
			}
		}
		peak.keySet().retainAll(seen);
	}

	private void dust(ClientLevel level, Motes motes, Entity entity, double fall) {
		BlockPos pos = BlockPos.containing(entity.getX(), entity.getY() - 0.2, entity.getZ());
		BlockState ground = level.getBlockState(pos);
		if (ground.isAir()) {
			return;
		}
		float strength = (float) Mth.clamp((fall - MIN_FALL) / 10, 0, 1);
		float width = Mth.clamp(entity.getBbWidth() / 0.6f, 0.6f, 2.5f);
		int color = 0xFF000000 | ground.getMapColor(level, pos).col;
		// Dust in the colour of the ground, a little greyer and darker so it shows against it, spreading out low.
		color = ARGB.scaleRGB(ARGB.srgbLerp(0.2f, color, 0xFFB8AE9E), 0.9f);
		int puffs = 5 + Math.round(strength * 12);
		for (int i = 0; i < puffs; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double speed = (0.03 + random.nextDouble() * 0.04) * (1 + strength * 2) * width;
			Motes.Mote puff = motes.add(new Motes.Mote(Motes.Shape.PUFF, entity.getX() + Math.cos(angle) * 0.2 * width, entity.getY() + 0.08,
					entity.getZ() + Math.sin(angle) * 0.2 * width, 22 + random.nextInt(14) + Math.round(strength * 16)));
			puff.vx = Math.cos(angle) * speed;
			puff.vy = 0.008 + random.nextDouble() * 0.01;
			puff.vz = Math.sin(angle) * speed;
			puff.drag = 0.86f;
			puff.size0 = 0.15f * width;
			puff.size1 = (0.45f + strength * 0.5f + random.nextFloat() * 0.15f) * width;
			puff.alpha = 0.6f;
			puff.color = color;
			puff.fadeIn = 2;
		}
		// Crumbs of the ground thrown up.
		BlockParticleOption crumbs = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		int count = Math.round(2 + strength * 14);
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double speed = 0.06 + random.nextDouble() * 0.1 * (1 + strength);
			level.addParticle(crumbs, entity.getX() + Math.cos(angle) * 0.3 * width, entity.getY() + 0.05,
					entity.getZ() + Math.sin(angle) * 0.3 * width, Math.cos(angle) * speed, 0.1 + strength * 0.1, Math.sin(angle) * speed);
		}
	}
}
