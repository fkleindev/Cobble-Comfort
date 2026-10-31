package dev.cobblecomfort.features.plantsway;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.core.HiddenBlocks;
import dev.cobblecomfort.core.IrisCompat;
import dev.cobblecomfort.core.Movers;
import dev.cobblecomfort.mixin.FallingLeavesInvoker;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingParticlesLeavesBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.List;

/**
 * Leaf blocks shake for a moment when something runs or jumps into them or a projectile flies through, and drop a few
 * of their falling leaves. A shaking block is drawn on its own, like a swaying plant.
 */
final class LeafRustle {
	private static final int MAX_LEAVES = 32;
	private static final long SHAKE_NANOS = 700_000_000L;
	private static final long RETURN_NANOS = 150_000_000L;
	/** A block shaken again this soon keeps shaking instead of starting over (and dropping more leaves). */
	private static final long COOLDOWN_NANOS = 450_000_000L;
	/** Slower than this (blocks per tick) only leans on leaves, it doesn't rustle them. */
	private static final double MIN_SPEED = 0.04;

	private static final class Leaf {
		final BlockPos pos;
		final BlockState state;
		long start;
		float strength;
		float dirX;
		float dirZ;
		long returned;

		Leaf(BlockPos pos, BlockState state) {
			this.pos = pos;
			this.state = state;
		}
	}

	private final Long2ObjectLinkedOpenHashMap<Leaf> leaves = new Long2ObjectLinkedOpenHashMap<>();
	private final RandomSource random = RandomSource.create();

	void tick(ClientLevel level, List<Movers.Mover> movers, PlantSwayConfig config, long now) {
		for (Movers.Mover mover : movers) {
			if (mover.kind() == Movers.Kind.PROJECTILE) {
				fly(level, mover, config, now);
			} else {
				bump(level, mover, config, now);
			}
		}
		Iterator<Leaf> it = leaves.values().iterator();
		while (it.hasNext()) {
			Leaf leaf = it.next();
			if (level.getBlockState(leaf.pos) != leaf.state) {
				if (leaf.returned == 0) {
					HiddenBlocks.show(leaf.pos);
				}
				it.remove();
			} else if (leaf.returned != 0) {
				if (now - leaf.returned > RETURN_NANOS) {
					it.remove();
				}
			} else if (now - leaf.start > SHAKE_NANOS) {
				leaf.returned = now;
				HiddenBlocks.show(leaf.pos);
			}
		}
	}

	/** Running, jumping or falling into leaves: the blocks it moves into shake. */
	private void bump(ClientLevel level, Movers.Mover mover, PlantSwayConfig config, long now) {
		Vec3 motion = mover.motion();
		double speed = motion.length();
		if (speed < MIN_SPEED) {
			return;
		}
		AABB box = mover.box().inflate(0.08);
		Vec3 center = mover.box().getCenter();
		float strength = (float) Mth.clamp(speed / 0.35, 0.3, 1.0) * config.strength;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
			for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
				for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (!(state.getBlock() instanceof LeavesBlock)) {
						continue;
					}
					Vec3 toward = Vec3.atCenterOf(pos).subtract(center);
					if (toward.dot(motion) <= 0) {
						continue;
					}
					rustle(level, pos.immutable(), state, motion, strength, config, now);
				}
			}
		}
	}

	private void fly(ClientLevel level, Movers.Mover mover, PlantSwayConfig config, long now) {
		Vec3 motion = mover.motion();
		double length = motion.length();
		if (length < 0.05) {
			return;
		}
		int steps = Math.min(64, (int) Math.ceil(length / 0.25));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int s = 0; s <= steps; s++) {
			Vec3 at = mover.from().add(motion.scale((double) s / steps));
			pos.set(at.x, at.y, at.z);
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof LeavesBlock) {
				rustle(level, pos.immutable(), state, motion, config.strength, config, now);
				return;
			}
		}
	}

	private void rustle(ClientLevel level, BlockPos pos, BlockState state, Vec3 motion, float strength, PlantSwayConfig config, long now) {
		Leaf leaf = leaves.get(pos.asLong());
		if (leaf != null && leaf.returned == 0 && now - leaf.start < COOLDOWN_NANOS) {
			return;
		}
		if (leaf == null) {
			if (leaves.size() >= MAX_LEAVES) {
				return;
			}
			leaf = new Leaf(pos, state);
			leaves.put(pos.asLong(), leaf);
			HiddenBlocks.hide(pos);
		} else if (leaf.returned != 0) {
			leaf.returned = 0;
			HiddenBlocks.hide(pos);
		}
		double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		if (horizontal < 1e-3) {
			// Straight up or down: shakes in some direction.
			float angle = random.nextFloat() * Mth.TWO_PI;
			leaf.dirX = Mth.cos(angle);
			leaf.dirZ = Mth.sin(angle);
		} else {
			leaf.dirX = (float) (motion.x / horizontal);
			leaf.dirZ = (float) (motion.z / horizontal);
		}
		leaf.start = now;
		leaf.strength = strength;
		if (config.fallingLeaves) {
			drop(level, pos, state, 1 + random.nextInt(1 + Math.round(strength * 2f)));
		}
	}

	/** A few falling leaves below the block, the kind the block drops on its own. */
	private void drop(ClientLevel level, BlockPos pos, BlockState state, int count) {
		BlockPos below = pos.below();
		if (Block.isFaceFull(level.getBlockState(below).getCollisionShape(level, below), Direction.UP)) {
			return;
		}
		for (int i = 0; i < count; i++) {
			if (state.getBlock() instanceof FallingParticlesLeavesBlock falling) {
				((FallingLeavesInvoker) falling).cobblecomfort$spawnFallingLeaf(level, pos, random);
			} else {
				ParticleUtils.spawnParticleBelow(level, pos, random,
						ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, level.getClientLeafTintColor(pos)));
			}
		}
	}

	void render(LevelRenderContext context, ClientLevel level, Vec3 camera, long now) {
		PoseStack pose = context.poseStack();
		for (Leaf leaf : leaves.values()) {
			float t = (now - leaf.start) / 1e9f;
			float angle = leaf.returned != 0 ? 0f : leaf.strength * 0.07f * (float) Math.exp(-6f * t) * Mth.sin(28f * t);
			BlockPos pos = leaf.pos;
			BlockState state = leaf.state;
			pose.pushPose();
			pose.translate(pos.getX() - camera.x + 0.5 + leaf.dirX * angle * 0.4f, pos.getY() - camera.y + 0.5,
					pos.getZ() - camera.z + 0.5 + leaf.dirZ * angle * 0.4f);
			if (angle != 0f) {
				// The top sways along the push, around the middle of the block.
				pose.mulPose(new Matrix4f().rotation(angle, new Vector3f(leaf.dirZ, 0f, -leaf.dirX)));
			}
			pose.translate(-0.5f, -0.5f, -0.5f);
			context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.cutoutMovingBlock(), (base, consumer) -> {
				boolean tagged = IrisCompat.beginBlock(consumer, state);
				HiddenBlocks.tesselate(level, pos, state, (x, y, z, quad, instance) -> consumer.putBakedQuad(base, quad, instance));
				if (tagged) {
					IrisCompat.endBlock(consumer);
				}
			});
			pose.popPose();
		}
	}

	boolean isEmpty() {
		return leaves.isEmpty();
	}

	void release() {
		for (Leaf leaf : leaves.values()) {
			if (leaf.returned == 0) {
				HiddenBlocks.show(leaf.pos);
			}
		}
		leaves.clear();
	}

	void clear() {
		leaves.clear();
	}
}
