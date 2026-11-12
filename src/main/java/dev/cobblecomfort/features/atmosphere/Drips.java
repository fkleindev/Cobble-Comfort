package dev.cobblecomfort.features.atmosphere;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Water dripping: off players who come out of the water or stood in the rain (less and less as they dry, faster in
 * the heat and steaming next to a fire, a shower of drops when they jump), from the undersides of leaves and the edges
 * of roofs for a while after the rain (roof edges in the rain too), with rings where the drops land in water.
 */
final class Drips {
	private static final double RANGE = 48;
	/** Half a minute to dry; soaked through after twenty seconds in the rain. */
	private static final float DRY = 1f / (20 * 30);
	private static final float SOAK = 1f / (20 * 20);
	private static final float SHAKE_OFF = 0.4f;
	/** How long leaves and roofs keep dripping after the rain (ticks). */
	private static final int AFTER_RAIN = 20 * 60;
	private static final int RING_COLOR = 0xFFE8F4FF;
	/** About how fast a falling drop falls (blocks per tick squared). */
	private static final double DROP_GRAVITY = 0.06;

	private final Int2FloatOpenHashMap wetness = new Int2FloatOpenHashMap();
	private final IntSet grounded = new IntOpenHashSet();
	private final IntSet seen = new IntOpenHashSet();
	private final RandomSource random = RandomSource.create();
	/** Rings still to come where drops will land in water: x, y, z, ticks left. */
	private final List<double[]> rings = new ArrayList<>();
	/** How wet the world still is from the rain, 0 to 1. */
	private float wetWorld;

	void clear() {
		wetness.clear();
		grounded.clear();
		rings.clear();
		wetWorld = 0;
	}

	/** How wet a player is, 0 to 1. */
	float wetness(int id) {
		return wetness.get(id);
	}

	void tick(ClientLevel level, Vec3 camera, Motes motes) {
		players(level, camera);
		world(level, camera);
		Iterator<double[]> it = rings.iterator();
		while (it.hasNext()) {
			double[] ring = it.next();
			if (--ring[3] <= 0) {
				Motes.Mote mote = motes.add(new Motes.Mote(Motes.Shape.RING, ring[0], ring[1] + 0.01, ring[2], 18));
				mote.size0 = 0.05f;
				mote.size1 = 0.45f;
				mote.alpha = 0.6f;
				mote.color = RING_COLOR;
				it.remove();
			}
		}
	}

	// ---------------------------------------------------------------- players

	private void players(ClientLevel level, Vec3 camera) {
		seen.clear();
		for (AbstractClientPlayer player : level.players()) {
			if (player.isSpectator() || player.distanceToSqr(camera) > RANGE * RANGE) {
				continue;
			}
			int id = player.getId();
			seen.add(id);
			float wet = wetness.get(id);
			boolean underwater = player.isUnderWater();
			if (player.isInWater()) {
				wet = 1f;
			} else if (rainingOn(level, BlockPos.containing(player.getEyePosition()))) {
				wet = Math.min(1f, wet + SOAK);
			} else if (wet > 0) {
				boolean hot = heat(level, player.blockPosition()) || player.isOnFire() || level.dimension() == Level.NETHER;
				float temperature = level.getBiome(player.blockPosition()).value().getBaseTemperature();
				float rate = DRY * (hot ? 6 : temperature > 1.0f ? 2 : temperature < 0.3f ? 0.5f : 1);
				if (hot && random.nextInt(3) == 0) {
					// Steaming dry.
					level.addParticle(ParticleTypes.WHITE_SMOKE, player.getX() + (random.nextDouble() - 0.5) * 0.6,
							player.getY() + random.nextDouble() * player.getBbHeight(), player.getZ() + (random.nextDouble() - 0.5) * 0.6,
							0, 0.04, 0);
				}
				wet = Math.max(0, wet - rate);
			}
			if (wet > 0 && !underwater && random.nextFloat() < wet * 0.35f) {
				level.addParticle(ParticleTypes.FALLING_WATER, player.getX() + (random.nextDouble() - 0.5) * 0.55,
						player.getY() + player.getBbHeight() * (0.15 + random.nextDouble() * 0.8),
						player.getZ() + (random.nextDouble() - 0.5) * 0.55, 0, 0, 0);
			}
			boolean onGround = player.onGround();
			if (grounded.contains(id) && !onGround && player.getY() - player.yo > 0.1 && wet > 0.1f && !player.isInWater()) {
				// A jump shakes a shower of drops off.
				int count = 4 + Math.round(wet * 12);
				for (int i = 0; i < count; i++) {
					level.addParticle(ParticleTypes.SPLASH, player.getX() + (random.nextDouble() - 0.5) * 0.7,
							player.getY() + player.getBbHeight() * (0.3 + random.nextDouble() * 0.6),
							player.getZ() + (random.nextDouble() - 0.5) * 0.7, (random.nextDouble() - 0.5) * 0.2, 0.1,
							(random.nextDouble() - 0.5) * 0.2);
				}
				wet *= 1f - SHAKE_OFF;
			}
			if (onGround) {
				grounded.add(id);
			} else {
				grounded.remove(id);
			}
			if (wet > 0) {
				wetness.put(id, wet);
			} else {
				wetness.remove(id);
			}
		}
		wetness.keySet().retainAll(seen);
		grounded.retainAll(seen);
	}

	/** Whether rain (not snow) falls here. */
	static boolean rainingOn(ClientLevel level, BlockPos pos) {
		return level.isRaining() && level.canSeeSky(pos)
				&& level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.RAIN;
	}

	/** A lit campfire, fire, lava or a burning furnace within a couple of blocks. */
	static boolean heat(ClientLevel level, BlockPos at) {
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 2, 2))) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA) || CampfireBlock.isLitCampfire(state)
					|| state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- leaves and roofs

	private void world(ClientLevel level, Vec3 camera) {
		if (level.dimension() != Level.OVERWORLD) {
			wetWorld = 0;
			return;
		}
		boolean raining = level.isRaining() && level.getRainLevel(1f) > 0.2f;
		wetWorld = raining ? 1f : Math.max(0, wetWorld - 1f / AFTER_RAIN);
		if (wetWorld <= 0) {
			return;
		}
		int tries = Math.round(10 * wetWorld);
		for (int i = 0; i < tries; i++) {
			// A spot somewhere around, and in it from the top the first underside with air below it (a roof, a canopy).
			int x = Mth.floor(camera.x) + random.nextInt(25) - 12;
			int z = Mth.floor(camera.z) + random.nextInt(25) - 12;
			BlockPos pos = null;
			for (int y = Mth.floor(camera.y) + 10; y > Mth.floor(camera.y) - 4; y--) {
				BlockPos at = new BlockPos(x, y, z);
				if (!level.getBlockState(at).isAir() && level.getBlockState(at.below()).isAir()) {
					pos = at;
					break;
				}
			}
			if (pos == null) {
				continue;
			}
			int y = pos.getY();
			BlockPos below = pos.below();
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.LEAVES)) {
				// Leaves: after the rain only (in it, the rain falls through them anyway), where it rains at all.
				if (!raining && random.nextFloat() < wetWorld && rainsHere(level, pos)) {
					drop(level, x + 0.15 + random.nextDouble() * 0.7, y - 0.02, z + 0.15 + random.nextDouble() * 0.7);
				}
			} else if (state.isFaceSturdy(level, pos, Direction.DOWN) && level.canSeeSky(pos.above()) && rainsHere(level, pos)) {
				// A roof edge: rained on from above, open below and to a side. The drops run off that side.
				for (Direction side : Direction.Plane.HORIZONTAL) {
					if (level.getBlockState(pos.relative(side)).isAir() && level.getBlockState(below.relative(side)).isAir()) {
						double along = 0.1 + random.nextDouble() * 0.8;
						double dx = side.getStepX() != 0 ? (side.getStepX() > 0 ? 0.92 : 0.08) : along;
						double dz = side.getStepZ() != 0 ? (side.getStepZ() > 0 ? 0.92 : 0.08) : along;
						drop(level, x + dx, y - 0.02, z + dz);
						break;
					}
				}
			}
		}
	}

	private static boolean rainsHere(ClientLevel level, BlockPos pos) {
		return level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.RAIN;
	}

	/** A drop falling from here, and a ring where it lands if that is water. */
	private void drop(ClientLevel level, double x, double y, double z) {
		level.addParticle(ParticleTypes.FALLING_WATER, x, y, z, 0, 0, 0);
		BlockPos pos = BlockPos.containing(x, y - 1, z);
		for (int i = 0; i < 24; i++, pos = pos.below()) {
			if (level.getFluidState(pos).is(FluidTags.WATER)) {
				double surface = pos.getY() + level.getFluidState(pos).getHeight(level, pos);
				int ticks = (int) Math.ceil(Math.sqrt(2 * Math.max(0, y - surface) / DROP_GRAVITY));
				rings.add(new double[]{x, surface, z, ticks});
				return;
			}
			if (!level.getBlockState(pos).isAir()) {
				return;
			}
		}
	}
}
