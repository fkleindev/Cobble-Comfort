package dev.cobblecomfort.features.atmosphere;

import dev.cobblecomfort.core.Movers;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Wading through shallow water: rings spread on the surface with every step and a few drops splash up; something
 * dropping into the water makes a bigger ring.
 */
final class Splashes {
	/** Water above this part of a walker's height is swimming, not wading. */
	private static final double WADE_DEPTH = 0.75;
	private static final double STRIDE = 0.6;
	private static final double MIN_SPEED = 0.03;
	private static final double DROP_SPEED = -0.25;
	private static final int RING_COLOR = 0xFFE8F4FF;

	/** How far each walker has waded since its last ring; walkers not in water have no entry. */
	private final Int2DoubleOpenHashMap waded = new Int2DoubleOpenHashMap();
	private final IntSet seen = new IntOpenHashSet();
	private final RandomSource random = RandomSource.create();

	void tick(ClientLevel level, List<Movers.Mover> movers, Motes motes) {
		seen.clear();
		for (Movers.Mover mover : movers) {
			Entity entity = mover.entity();
			double depth = entity.getFluidHeight(FluidTags.WATER);
			if (!entity.isInWater() || depth <= 0.02 || entity.isSwimming() || depth > entity.getBbHeight() * WADE_DEPTH) {
				continue;
			}
			int id = entity.getId();
			seen.add(id);
			Vec3 motion = mover.motion();
			float width = Mth.clamp(entity.getBbWidth() / 0.6f, 0.6f, 2f);
			double surface = entity.getY() + depth;
			if (!waded.containsKey(id)) {
				// Just got into the water: a dropping body makes a big ring and a splash.
				waded.put(id, 0);
				if (motion.y < DROP_SPEED) {
					ring(motes, entity.getX(), surface, entity.getZ(), width * 2.4f, 30);
					splash(level, entity.getX(), surface, entity.getZ(), width, 8);
				}
				continue;
			}
			double moved = motion.horizontalDistance();
			if (moved < MIN_SPEED) {
				continue;
			}
			double total = waded.get(id) + moved;
			if (total >= STRIDE * Math.max(0.8f, width)) {
				total = 0;
				ring(motes, entity.getX(), surface, entity.getZ(), width * 1.3f, 22);
				splash(level, entity.getX(), surface, entity.getZ(), width, 2 + random.nextInt(2));
			}
			waded.put(id, total);
		}
		waded.keySet().retainAll(seen);
	}

	private void ring(Motes motes, double x, double y, double z, float size, int life) {
		Motes.Mote ring = motes.add(new Motes.Mote(Motes.Shape.RING, x, y + 0.01, z, life));
		ring.size0 = size * 0.2f;
		ring.size1 = size;
		ring.alpha = 0.75f;
		ring.color = RING_COLOR;
	}

	private void splash(ClientLevel level, double x, double y, double z, float width, int count) {
		for (int i = 0; i < count; i++) {
			double dx = (random.nextDouble() - 0.5) * 0.4 * width;
			double dz = (random.nextDouble() - 0.5) * 0.4 * width;
			level.addParticle(ParticleTypes.SPLASH, x + dx, y, z + dz, dx * 0.4, 0.12, dz * 0.4);
		}
	}
}
