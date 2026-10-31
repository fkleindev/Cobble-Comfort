package dev.cobblecomfort.features.plantsway;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.core.HiddenBlocks;
import dev.cobblecomfort.core.IrisCompat;
import dev.cobblecomfort.core.Movers;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.List;

/**
 * Lily pads bob: they dip and tip under whoever stands on them, rock in the wake of boats and swimmers, and jump when a
 * projectile lands next to them. A bobbing pad is drawn on its own, like a swaying plant.
 */
final class LilyPads {
	private static final int MAX_PADS = 48;
	/** How far around a boat or swimmer pads feel its wake (blocks beyond its edge). */
	private static final double WAKE_RANGE = 1.5;
	private static final long SETTLE_NANOS = 400_000_000L;
	private static final long RETURN_NANOS = 150_000_000L;
	private static final float STEP = 1f / 240f;

	private static final class Pad {
		final BlockPos pos;
		final BlockState state;
		/** How far it is pushed under (blocks), and its tip (the edge's rise per block, along x and z) and turn. */
		float depth;
		float tipX;
		float tipZ;
		float turn;
		float vDepth;
		float vTipX;
		float vTipZ;
		float vTurn;
		float targetDepth;
		float targetTipX;
		float targetTipZ;
		long lastActive;
		long returned;

		Pad(BlockPos pos, BlockState state, long now) {
			this.pos = pos;
			this.state = state;
			this.lastActive = now;
		}

		boolean settled() {
			return Math.abs(depth) + Math.abs(tipX) + Math.abs(tipZ) + Math.abs(turn) < 0.003f
					&& Math.abs(vDepth) + Math.abs(vTipX) + Math.abs(vTipZ) + Math.abs(vTurn) < 0.02f;
		}
	}

	private final Long2ObjectLinkedOpenHashMap<Pad> pads = new Long2ObjectLinkedOpenHashMap<>();
	private float pending;

	void tick(ClientLevel level, List<Movers.Mover> movers, PlantSwayConfig config, long now) {
		for (Pad pad : pads.values()) {
			pad.targetDepth = 0f;
			pad.targetTipX = 0f;
			pad.targetTipZ = 0f;
		}
		for (Movers.Mover mover : movers) {
			if (mover.kind() == Movers.Kind.PROJECTILE) {
				splash(level, mover, config, now);
			} else {
				touch(level, mover, config, now);
			}
		}
		Iterator<Pad> it = pads.values().iterator();
		while (it.hasNext()) {
			Pad pad = it.next();
			if (level.getBlockState(pad.pos) != pad.state) {
				if (pad.returned == 0) {
					HiddenBlocks.show(pad.pos);
				}
				it.remove();
			} else if (pad.returned != 0) {
				if (now - pad.returned > RETURN_NANOS) {
					it.remove();
				}
			} else if (now - pad.lastActive > SETTLE_NANOS && pad.settled()) {
				pad.depth = pad.tipX = pad.tipZ = pad.turn = 0f;
				pad.vDepth = pad.vTipX = pad.vTipZ = pad.vTurn = 0f;
				pad.returned = now;
				HiddenBlocks.show(pad.pos);
			}
		}
	}

	private void touch(ClientLevel level, Movers.Mover mover, PlantSwayConfig config, long now) {
		AABB box = mover.box();
		Vec3 motion = mover.motion();
		double speed = motion.horizontalDistance();
		// Boats and swimmers make waves; anyone else only weighs down the pad they stand on.
		boolean wake = (mover.kind() == Movers.Kind.BOAT || mover.entity().isInWater()) && speed > 0.02;
		AABB near = box.inflate(wake ? WAKE_RANGE : 0.1, 0.6, wake ? WAKE_RANGE : 0.1);
		Vec3 center = box.getCenter();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = Mth.floor(near.minY); y <= Mth.floor(near.maxY); y++) {
			for (int x = Mth.floor(near.minX); x <= Mth.floor(near.maxX); x++) {
				for (int z = Mth.floor(near.minZ); z <= Mth.floor(near.maxZ); z++) {
					pos.set(x, y, z);
					if (!level.getBlockState(pos).is(Blocks.LILY_PAD)) {
						continue;
					}
					Pad pad = pad(level, pos, now);
					if (pad == null) {
						continue;
					}
					pad.lastActive = now;
					double dx = pos.getX() + 0.5 - center.x;
					double dz = pos.getZ() + 0.5 - center.z;
					boolean onTop = box.minY >= pos.getY() - 0.05 && box.minY <= pos.getY() + 0.3
							&& box.intersects(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 0.3, pos.getZ() + 1));
					if (onTop) {
						// Pressed down, more on the side it's stood on; a landing sends it deeper for a moment.
						pad.targetDepth = Math.max(pad.targetDepth, 0.05f * config.strength);
						pad.targetTipX = (float) Mth.clamp(-dx * 0.25, -0.12, 0.12) * config.strength;
						pad.targetTipZ = (float) Mth.clamp(-dz * 0.25, -0.12, 0.12) * config.strength;
						if (motion.y < -0.1) {
							pad.vDepth += (float) (-motion.y * 1.2) * config.strength;
						}
					} else if (wake) {
						double distance = Math.max(0.5, Math.sqrt(dx * dx + dz * dz) - mover.radius());
						float push = (float) (Math.min(speed, 0.6) / distance) * config.strength;
						// Lifted and tipped away by the wave, and turned a little by the water flowing past.
						pad.vDepth -= push * 1.2f;
						pad.vTipX += (float) (dx / (distance + 0.5)) * push * 3f;
						pad.vTipZ += (float) (dz / (distance + 0.5)) * push * 3f;
						pad.vTurn += (float) (motion.x * dz - motion.z * dx > 0 ? push : -push) * 2.5f;
					}
				}
			}
		}
	}

	/** A projectile hitting the water or a pad: pads close by jump and rock. */
	private void splash(ClientLevel level, Movers.Mover mover, PlantSwayConfig config, long now) {
		Vec3 motion = mover.motion();
		if (motion.lengthSqr() < 0.0025 || !mover.entity().isInWater() && motion.y > -0.05) {
			return;
		}
		Vec3 at = mover.to();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = Mth.floor(at.y) - 1; y <= Mth.floor(at.y) + 1; y++) {
			for (int x = Mth.floor(at.x) - 1; x <= Mth.floor(at.x) + 1; x++) {
				for (int z = Mth.floor(at.z) - 1; z <= Mth.floor(at.z) + 1; z++) {
					pos.set(x, y, z);
					if (!level.getBlockState(pos).is(Blocks.LILY_PAD) || at.distanceToSqr(Vec3.atCenterOf(pos)) > 2.5) {
						continue;
					}
					Pad pad = pad(level, pos, now);
					if (pad == null) {
						continue;
					}
					pad.lastActive = now;
					double dx = pos.getX() + 0.5 - at.x;
					double dz = pos.getZ() + 0.5 - at.z;
					pad.vDepth += 0.5f * config.strength;
					pad.vTipX += (float) (dx * 1.5 + motion.x) * config.strength;
					pad.vTipZ += (float) (dz * 1.5 + motion.z) * config.strength;
				}
			}
		}
	}

	private Pad pad(ClientLevel level, BlockPos pos, long now) {
		Pad pad = pads.get(pos.asLong());
		if (pad != null) {
			if (pad.returned != 0) {
				pad.returned = 0;
				HiddenBlocks.hide(pad.pos);
			}
			return pad;
		}
		if (pads.size() >= MAX_PADS) {
			return null;
		}
		pad = new Pad(pos.immutable(), level.getBlockState(pos), now);
		pads.put(pos.asLong(), pad);
		HiddenBlocks.hide(pad.pos);
		return pad;
	}

	private void step(float dt, float swing) {
		float bob = Mth.TWO_PI * 1.3f;
		float tip = Mth.TWO_PI * 1.7f;
		float turn = Mth.TWO_PI * 0.6f;
		float damping = Mth.lerp(Mth.clamp(swing, 0f, 1f), 0.6f, 0.15f);
		pending = Math.min(pending + dt, 0.1f);
		while (pending >= STEP) {
			pending -= STEP;
			for (Pad pad : pads.values()) {
				if (pad.returned != 0) {
					continue;
				}
				pad.vDepth += (bob * bob * (pad.targetDepth - pad.depth) - 2f * damping * bob * pad.vDepth) * STEP;
				pad.vTipX += (tip * tip * (pad.targetTipX - pad.tipX) - 2f * damping * tip * pad.vTipX) * STEP;
				pad.vTipZ += (tip * tip * (pad.targetTipZ - pad.tipZ) - 2f * damping * tip * pad.vTipZ) * STEP;
				pad.vTurn += (-turn * turn * pad.turn - 2f * 0.5f * turn * pad.vTurn) * STEP;
				pad.depth = Mth.clamp(pad.depth + pad.vDepth * STEP, -0.04f, 0.1f);
				pad.tipX = Mth.clamp(pad.tipX + pad.vTipX * STEP, -0.25f, 0.25f);
				pad.tipZ = Mth.clamp(pad.tipZ + pad.vTipZ * STEP, -0.25f, 0.25f);
				pad.turn = Mth.clamp(pad.turn + pad.vTurn * STEP, -0.5f, 0.5f);
			}
		}
	}

	void render(LevelRenderContext context, ClientLevel level, Vec3 camera, float dt, float swing) {
		step(dt, swing);
		PoseStack pose = context.poseStack();
		for (Pad pad : pads.values()) {
			BlockPos pos = pad.pos;
			BlockState state = pad.state;
			pose.pushPose();
			pose.translate(pos.getX() - camera.x + 0.5, pos.getY() - camera.y - pad.depth, pos.getZ() - camera.z + 0.5);
			pose.mulPose(new Matrix4f().rotationY(pad.turn));
			float angle = (float) Math.sqrt(pad.tipX * pad.tipX + pad.tipZ * pad.tipZ);
			if (angle > 1e-5f) {
				// The edge towards (tipX, tipZ) dips.
				pose.mulPose(new Matrix4f().rotation(angle, new Vector3f(pad.tipZ, 0f, -pad.tipX).normalize()));
			}
			pose.translate(-0.5f, 0f, -0.5f);
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
		return pads.isEmpty();
	}

	void release() {
		for (Pad pad : pads.values()) {
			if (pad.returned == 0) {
				HiddenBlocks.show(pad.pos);
			}
		}
		pads.clear();
	}

	void clear() {
		pads.clear();
	}
}
