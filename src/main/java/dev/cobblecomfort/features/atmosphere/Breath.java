package dev.cobblecomfort.features.atmosphere;

import dev.cobblecomfort.core.Movers;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** In the cold, players and mobs breathe out small clouds; more often while running. The undead don't breathe. */
final class Breath {
	private static final int CALM = 55;
	private static final int RUNNING = 22;
	private static final int PUFFS = 3;

	/** When each breather breathes out next, in client ticks. */
	private final Int2IntOpenHashMap next = new Int2IntOpenHashMap();
	private final IntSet seen = new IntOpenHashSet();
	private final RandomSource random = RandomSource.create();

	void tick(Minecraft mc, ClientLevel level, List<Movers.Mover> movers, Motes motes, AtmosphereConfig config, int now) {
		seen.clear();
		for (Movers.Mover mover : movers) {
			Entity entity = mover.entity();
			boolean own = entity == mc.player;
			if (!breathes(entity) || (mover.kind() == Movers.Kind.MOB && !config.breathMobs) || (own && !config.ownBreath)) {
				continue;
			}
			int id = entity.getId();
			seen.add(id);
			if (!next.containsKey(id)) {
				// Not all in step.
				next.put(id, now + random.nextInt(CALM));
				continue;
			}
			if (now < next.get(id)) {
				continue;
			}
			int calm = entity.isSprinting() ? RUNNING : CALM;
			next.put(id, now + calm + random.nextInt(calm / 3 + 1) - calm / 6);
			if (!entity.isUnderWater() && !entity.isInvisible() && cold(level, BlockPos.containing(entity.getEyePosition()))) {
				breatheOut(mc, entity, own, motes);
			}
		}
		next.keySet().retainAll(seen);
	}

	private static boolean breathes(Entity entity) {
		return (entity instanceof Player || entity instanceof Animal || entity instanceof AbstractVillager
				|| entity instanceof AbstractIllager || entity instanceof Witch) && !entity.typeHolder().is(EntityTypeTags.UNDEAD);
	}

	/** Cold enough to snow there: snowy biomes, and high up in any other. */
	static boolean cold(ClientLevel level, BlockPos pos) {
		Biome biome = level.getBiome(pos).value();
		return biome.coldEnoughToSnow(pos, level.getSeaLevel());
	}

	private void breatheOut(Minecraft mc, Entity entity, boolean own, Motes motes) {
		Vec3 look = Vec3.directionFromRotation(entity.getXRot(), entity.getYHeadRot());
		float scale = Mth.clamp(entity.getBbHeight() / 1.8f, 0.5f, 1.4f);
		Vec3 mouth;
		if (own && !mc.gameRenderer.mainCamera().isDetached()) {
			// Ahead and below the eyes, where your own breath can be seen without hiding anything.
			mouth = entity.getEyePosition().add(look.scale(0.45)).add(0, -0.3, 0);
		} else {
			mouth = entity.getEyePosition().add(look.scale(entity.getBbWidth() * 0.5 + 0.08)).add(0, -0.12 * scale, 0);
		}
		Vec3 motion = entity.getDeltaMovement();
		for (int i = 0; i < PUFFS; i++) {
			Motes.Mote puff = motes.add(new Motes.Mote(Motes.Shape.PUFF, mouth.x, mouth.y, mouth.z, 30 + random.nextInt(15)));
			puff.vx = look.x * 0.05 + motion.x * 0.5 + (random.nextFloat() - 0.5f) * 0.008;
			puff.vy = look.y * 0.05 + 0.004 + (random.nextFloat() - 0.5f) * 0.006;
			puff.vz = look.z * 0.05 + motion.z * 0.5 + (random.nextFloat() - 0.5f) * 0.008;
			puff.drag = 0.92f;
			puff.size0 = 0.1f * scale;
			puff.size1 = (0.5f + random.nextFloat() * 0.12f) * scale;
			puff.alpha = 0.5f;
			puff.color = 0xFFFFFFFF;
			puff.fadeIn = 2 + i;
			puff.mover = m -> m.vy += 0.0008;
		}
	}
}
