package dev.cobblecomfort.features.hanging;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.core.HiddenBlocks;
import dev.cobblecomfort.core.IrisCompat;
import dev.cobblecomfort.core.Movers;
import dev.isxander.yacl3.api.ConfigCategory;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hanging things swing: free vines, weeping vines, glow berries, hanging roots and moss, spore blossoms, and lanterns
 * on their chains. A hanging column is a rope of one-block links: each link hangs from the end of the one above, so a
 * long vine bends as it swings. Players, mobs, boats and projectiles push the links aside, and a light breeze keeps
 * the ones close to you moving. Like swaying plants, a rope is taken out of the chunk mesh while it swings (see
 * {@link HiddenBlocks}). Bells are drawn by their block entity, which is tilted instead (see {@code BellRendererMixin}).
 */
public final class HangingFeature extends Feature<HangingConfig> {
	public static final HangingFeature INSTANCE = new HangingFeature();

	private static final double SCAN_RANGE = 32;
	/** The breeze moves things this close to the camera; they go back to the mesh once a bit further away. */
	private static final int WIND_RANGE = 12;
	private static final double WIND_RELEASE = 16;
	private static final int WIND_SCAN_TICKS = 20;
	private static final int MAX_ROPES = 64;
	private static final int MAX_LINKS = 32;
	private static final int MAX_BELLS = 16;
	/** Ropes are taken out of the mesh a little before they are touched, so the rebuild is done when they move. */
	private static final double ACTIVATE_MARGIN = 0.6;
	private static final long SETTLE_NANOS = 600_000_000L;
	private static final long RETURN_NANOS = 150_000_000L;
	private static final float STEP = 1f / 240f;
	/** A bit stronger than real gravity: one-block links swing at a lively pace, not like a grandfather clock. */
	private static final float GRAVITY = 16f;
	private static final float DAMPING = 0.9f;
	/** Links keep this far from a body walking through them (blocks, added to its half width). */
	private static final float CLEARANCE = 0.2f;

	enum Part {
		PLANT, CHAIN, LANTERN
	}

	/** A hanging column: its blocks from the top down, and the end of each link, relative to where the top hangs from. */
	private static final class Rope {
		final BlockPos[] positions;
		final BlockState[] states;
		final double anchorX;
		final double anchorY;
		final double anchorZ;
		final float[] x;
		final float[] y;
		final float[] z;
		final float[] px;
		final float[] py;
		final float[] pz;
		final float phase;
		/** How much a push moves it: heavy lanterns less than vines. */
		final float give;
		boolean wind;
		long lastActive;
		long returned;

		Rope(List<BlockPos> positions, List<BlockState> states, long now) {
			int n = positions.size();
			this.positions = positions.toArray(new BlockPos[0]);
			this.states = states.toArray(new BlockState[0]);
			BlockPos top = this.positions[0];
			anchorX = top.getX() + 0.5;
			anchorY = top.getY() + 1.0;
			anchorZ = top.getZ() + 0.5;
			x = new float[n];
			y = new float[n];
			z = new float[n];
			for (int i = 0; i < n; i++) {
				y[i] = -(i + 1);
			}
			px = x.clone();
			py = y.clone();
			pz = z.clone();
			phase = (top.getX() * 0.37f + top.getZ() * 0.61f) % Mth.TWO_PI;
			give = this.states[n - 1].getBlock() instanceof LanternBlock ? 0.6f : 1f;
			lastActive = now;
		}

		void push(int i, float vx, float vz) {
			px[i] -= vx * STEP;
			pz[i] -= vz * STEP;
		}

		boolean settled() {
			for (int i = 0; i < x.length; i++) {
				if (Math.abs(x[i]) + Math.abs(z[i]) > 0.01f || Math.abs(x[i] - px[i]) + Math.abs(z[i] - pz[i]) > 0.0002f) {
					return false;
				}
			}
			return true;
		}
	}

	/** A bell: how far its bottom has swung out (as an angle per axis) and how fast. */
	private static final class Bell {
		final BlockPos pos;
		/** The axis it may swing along, or null if it hangs from the ceiling and swings any way. */
		final Direction.Axis along;
		final float phase;
		float x;
		float z;
		float vx;
		float vz;
		boolean wind;
		long lastActive;

		Bell(BlockPos pos, Direction.Axis along, long now) {
			this.pos = pos;
			this.along = along;
			this.phase = (pos.getX() * 0.53f + pos.getZ() * 0.29f) % Mth.TWO_PI;
			this.lastActive = now;
		}

		void push(float vx, float vz) {
			this.vx += along == Direction.Axis.Z ? 0f : vx;
			this.vz += along == Direction.Axis.X ? 0f : vz;
		}
	}

	/** A body that links are pushed out of: a vertical cylinder. */
	private record Collider(double x, double z, double radius, double minY, double maxY) {
	}

	private final Long2ObjectLinkedOpenHashMap<Rope> ropes = new Long2ObjectLinkedOpenHashMap<>();
	private final Long2ObjectLinkedOpenHashMap<Bell> bells = new Long2ObjectLinkedOpenHashMap<>();
	private final List<Collider> colliders = new ArrayList<>();
	private int windScan;
	private long lastFrame;
	private float pending;
	private float time;

	private HangingFeature() {
		super("hanging", HangingConfig.class, HangingConfig::new);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			ropes.clear();
			bells.clear();
			colliders.clear();
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	@Override
	public ConfigCategory buildCategory() {
		return HangingConfigUi.buildCategory(this);
	}

	// ---------------------------------------------------------------- what hangs

	private Part part(ClientLevel level, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		if (block instanceof LanternBlock) {
			return config.lanterns && state.getValue(LanternBlock.HANGING) ? Part.LANTERN : null;
		}
		if (block instanceof ChainBlock) {
			return config.lanterns && state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y ? Part.CHAIN : null;
		}
		if (!config.plants || !state.getFluidState().isEmpty()) {
			return null;
		}
		if (block == Blocks.WEEPING_VINES || block == Blocks.WEEPING_VINES_PLANT || block == Blocks.CAVE_VINES
				|| block == Blocks.CAVE_VINES_PLANT || block == Blocks.HANGING_ROOTS || block == Blocks.PALE_HANGING_MOSS
				|| block == Blocks.SPORE_BLOSSOM) {
			return Part.PLANT;
		}
		if (block instanceof VineBlock && hangsFree(level, pos, state)) {
			return Part.PLANT;
		}
		return null;
	}

	/** A vine that clings to no wall: it hangs below leaves or another vine and may swing. */
	private static boolean hangsFree(ClientLevel level, BlockPos pos, BlockState state) {
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (state.getValue(VineBlock.getPropertyForFace(side)) && VineBlock.isAcceptableNeighbour(level, pos.relative(side), side)) {
				return false;
			}
		}
		return true;
	}

	/** The swinging rope through this block, taking it out of the mesh if it isn't swinging yet; null if it can't. */
	private Rope rope(ClientLevel level, BlockPos pos, long now) {
		BlockPos top = pos;
		for (int i = 0; i < 64; i++) {
			BlockPos above = top.above();
			if (part(level, above, level.getBlockState(above)) == null) {
				break;
			}
			top = above;
		}
		Rope rope = ropes.get(top.asLong());
		if (rope != null) {
			if (rope.returned != 0) {
				rope.returned = 0;
				hide(rope);
			}
			return rope;
		}
		if (ropes.size() >= MAX_ROPES) {
			return null;
		}
		List<BlockPos> positions = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		BlockPos at = top;
		while (true) {
			BlockState state = level.getBlockState(at);
			if (part(level, at, state) == null) {
				break;
			}
			if (positions.size() == MAX_LINKS) {
				// Too long to swing as a whole; it stays still.
				return null;
			}
			positions.add(at);
			states.add(state);
			at = at.below();
		}
		if (positions.isEmpty()) {
			return null;
		}
		rope = new Rope(positions, states, now);
		ropes.put(top.asLong(), rope);
		hide(rope);
		return rope;
	}

	private Bell bell(BlockPos pos, BlockState state, long now) {
		Bell bell = bells.get(pos.asLong());
		if (bell != null || bells.size() >= MAX_BELLS) {
			return bell;
		}
		Direction facing = state.getValue(BellBlock.FACING);
		Direction.Axis along = switch (state.getValue(BellBlock.ATTACHMENT)) {
			case CEILING -> null;
			case FLOOR -> facing.getAxis();
			// A bell on a wall arm can't swing into the wall, only sideways.
			default -> facing.getClockWise().getAxis();
		};
		bell = new Bell(pos.immutable(), along, now);
		bells.put(pos.asLong(), bell);
		return bell;
	}

	private static void hide(Rope rope) {
		for (BlockPos pos : rope.positions) {
			HiddenBlocks.hide(pos);
		}
	}

	private static void show(Rope rope) {
		for (BlockPos pos : rope.positions) {
			HiddenBlocks.show(pos);
		}
	}

	private void releaseAll() {
		for (Rope rope : ropes.values()) {
			if (rope.returned == 0) {
				show(rope);
			}
		}
		ropes.clear();
		bells.clear();
	}

	// ---------------------------------------------------------------- pushing

	private void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			return;
		}
		if (!isEnabled()) {
			if (!ropes.isEmpty() || !bells.isEmpty()) {
				releaseAll();
			}
			return;
		}
		long now = System.nanoTime();
		Vec3 camera = mc.gameRenderer.mainCamera().position();
		colliders.clear();
		for (Movers.Mover mover : Movers.around(level, camera, SCAN_RANGE, config.players, config.mobs, config.other)) {
			if (mover.kind() == Movers.Kind.PROJECTILE) {
				fly(level, mover, now);
			} else {
				touch(level, mover, now);
			}
		}
		if (config.wind > 0f && ++windScan >= WIND_SCAN_TICKS) {
			windScan = 0;
			breeze(level, BlockPos.containing(camera), now);
		}
		Iterator<Rope> it = ropes.values().iterator();
		while (it.hasNext()) {
			Rope rope = it.next();
			if (changed(level, rope)) {
				if (rope.returned == 0) {
					show(rope);
				}
				it.remove();
				continue;
			}
			if (rope.returned != 0) {
				if (now - rope.returned > RETURN_NANOS) {
					it.remove();
				}
				continue;
			}
			rope.wind = config.wind > 0f && camera.distanceToSqr(rope.anchorX, rope.anchorY, rope.anchorZ) < WIND_RELEASE * WIND_RELEASE;
			if (rope.wind) {
				rope.lastActive = now;
			} else if (now - rope.lastActive > SETTLE_NANOS && rope.settled()) {
				rope.returned = now;
				show(rope);
			}
		}
		Iterator<Bell> bellIt = bells.values().iterator();
		while (bellIt.hasNext()) {
			Bell bell = bellIt.next();
			if (!config.bells || !(level.getBlockState(bell.pos).getBlock() instanceof BellBlock)) {
				bellIt.remove();
				continue;
			}
			bell.wind = config.wind > 0f && camera.distanceToSqr(Vec3.atCenterOf(bell.pos)) < WIND_RELEASE * WIND_RELEASE;
			boolean still = Math.abs(bell.x) + Math.abs(bell.z) < 0.002f && Math.abs(bell.vx) + Math.abs(bell.vz) < 0.01f;
			if (!bell.wind && now - bell.lastActive > SETTLE_NANOS && still) {
				bellIt.remove();
			}
		}
	}

	/** Whether a rope's blocks changed (broken, grown, a lantern hung below) so it must be looked at afresh. */
	private boolean changed(ClientLevel level, Rope rope) {
		for (int i = 0; i < rope.positions.length; i++) {
			if (level.getBlockState(rope.positions[i]) != rope.states[i]) {
				return true;
			}
		}
		BlockPos below = rope.positions[rope.positions.length - 1].below();
		return part(level, below, level.getBlockState(below)) != null;
	}

	/** A body walking, swimming or flying through hanging things: they are pushed aside while it is in them. */
	private void touch(ClientLevel level, Movers.Mover mover, long now) {
		AABB box = mover.box();
		AABB near = box.inflate(ACTIVATE_MARGIN);
		Vec3 motion = mover.motion();
		boolean any = false;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = Mth.floor(near.minY); y <= Mth.floor(near.maxY); y++) {
			for (int x = Mth.floor(near.minX); x <= Mth.floor(near.maxX); x++) {
				for (int z = Mth.floor(near.minZ); z <= Mth.floor(near.maxZ); z++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					if (config.bells && state.getBlock() instanceof BellBlock) {
						// Bells are solid: they swing when bumped into, in the direction of the bump.
						Vec3 toward = Vec3.atCenterOf(pos).subtract(box.getCenter());
						if (box.inflate(0.15).intersects(new AABB(pos)) && motion.horizontalDistanceSqr() > 1e-4
								&& motion.x * toward.x + motion.z * toward.z > 0) {
							Bell bell = bell(pos, state, now);
							if (bell != null) {
								bell.lastActive = now;
								float scale = 0.9f * config.strength;
								bell.push((float) motion.x * scale * 20f * 0.25f, (float) motion.z * scale * 20f * 0.25f);
							}
						}
						continue;
					}
					if (part(level, pos, state) == null) {
						continue;
					}
					Rope rope = rope(level, pos, now);
					if (rope != null) {
						rope.lastActive = now;
						any = true;
					}
				}
			}
		}
		if (any) {
			colliders.add(new Collider(box.getCenter().x, box.getCenter().z, mover.radius() + CLEARANCE * config.strength, box.minY, box.maxY));
		}
	}

	/** A projectile flying past knocks what it passes close to along its path. */
	private void fly(ClientLevel level, Movers.Mover mover, long now) {
		Vec3 from = mover.from();
		Vec3 motion = mover.motion();
		double length = motion.length();
		if (length < 0.05) {
			return;
		}
		int steps = Math.min(64, (int) Math.ceil(length / 0.25));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		long hitKey = Long.MIN_VALUE;
		for (int s = 0; s <= steps; s++) {
			Vec3 at = from.add(motion.scale((double) s / steps));
			pos.set(at.x, at.y, at.z);
			if (pos.asLong() == hitKey) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			float speed = (float) Math.min(length, 3.0) * 20f * config.strength;
			if (config.bells && state.getBlock() instanceof BellBlock) {
				Bell bell = bell(pos, state, now);
				if (bell != null) {
					bell.lastActive = now;
					bell.push((float) (motion.x / length) * speed * 0.08f, (float) (motion.z / length) * speed * 0.08f);
				}
				hitKey = pos.asLong();
				continue;
			}
			if (part(level, pos, state) == null) {
				continue;
			}
			Rope rope = rope(level, pos, now);
			if (rope == null) {
				continue;
			}
			hitKey = pos.asLong();
			rope.lastActive = now;
			// Knocks the link it went through and, a little, the ones next to it.
			int link = rope.positions[0].getY() - pos.getY();
			float kick = speed * 0.06f * rope.give;
			for (int i = Math.max(0, link - 1); i <= Math.min(rope.x.length - 1, link + 1); i++) {
				float share = i == link ? 1f : 0.4f;
				rope.push(i, (float) (motion.x / length) * kick * share, (float) (motion.z / length) * kick * share);
			}
		}
	}

	/** Brings the hanging things close to the camera into the breeze. */
	private void breeze(ClientLevel level, BlockPos center, long now) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = -WIND_RANGE; y <= WIND_RANGE; y++) {
			for (int x = -WIND_RANGE; x <= WIND_RANGE; x++) {
				for (int z = -WIND_RANGE; z <= WIND_RANGE; z++) {
					pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					if (config.bells && state.getBlock() instanceof BellBlock) {
						bell(pos, state, now);
					} else if (part(level, pos, state) != null && !ropes.containsKey(pos.asLong())) {
						rope(level, pos, now);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- swinging

	private void step(float dt) {
		pending = Math.min(pending + dt, 0.1f);
		float keep = 1f - DAMPING * STEP;
		float breeze = config.wind * 2.2f;
		while (pending >= STEP) {
			pending -= STEP;
			time += STEP;
			for (Rope rope : ropes.values()) {
				if (rope.returned != 0) {
					continue;
				}
				float windX = 0f;
				float windZ = 0f;
				if (rope.wind) {
					windX = breeze * (Mth.sin(time * 0.8f + rope.phase) + 0.5f * Mth.sin(time * 2.1f + rope.phase * 1.7f));
					windZ = breeze * (Mth.cos(time * 0.6f + rope.phase * 1.3f) + 0.5f * Mth.sin(time * 1.7f + rope.phase));
				}
				int n = rope.x.length;
				for (int i = 0; i < n; i++) {
					float vx = (rope.x[i] - rope.px[i]) * keep;
					float vy = (rope.y[i] - rope.py[i]) * keep;
					float vz = (rope.z[i] - rope.pz[i]) * keep;
					rope.px[i] = rope.x[i];
					rope.py[i] = rope.y[i];
					rope.pz[i] = rope.z[i];
					rope.x[i] += vx + windX * rope.give * STEP * STEP;
					rope.y[i] += vy - GRAVITY * STEP * STEP;
					rope.z[i] += vz + windZ * rope.give * STEP * STEP;
				}
				collide(rope);
				constrain(rope);
			}
			for (Bell bell : bells.values()) {
				swing(bell, breeze);
			}
		}
	}

	/** Pushes the ends of the links out of the bodies standing in them. */
	private void collide(Rope rope) {
		for (Collider collider : colliders) {
			for (int i = 0; i < rope.x.length; i++) {
				double wy = rope.anchorY + rope.y[i] + 0.5;
				if (wy < collider.minY() || wy > collider.maxY() + 0.5) {
					continue;
				}
				double dx = rope.anchorX + rope.x[i] - collider.x();
				double dz = rope.anchorZ + rope.z[i] - collider.z();
				double distance = Math.sqrt(dx * dx + dz * dz);
				if (distance >= collider.radius()) {
					continue;
				}
				if (distance < 1e-4) {
					dx = 1;
					dz = 0;
					distance = 1;
				}
				// Moved part of the way each step, so links slide off softly rather than jump.
				double out = (collider.radius() - distance) * 0.2 * rope.give;
				rope.x[i] += (float) (dx / distance * out);
				rope.z[i] += (float) (dz / distance * out);
			}
		}
	}

	/** Keeps every link one block long, hanging from the end of the one above (the first from its anchor). */
	private static void constrain(Rope rope) {
		float ax = 0f;
		float ay = 0f;
		float az = 0f;
		for (int i = 0; i < rope.x.length; i++) {
			float dx = rope.x[i] - ax;
			float dy = Math.min(rope.y[i] - ay, -0.05f);
			float dz = rope.z[i] - az;
			float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			rope.x[i] = ax + dx / length;
			rope.y[i] = ay + dy / length;
			rope.z[i] = az + dz / length;
			ax = rope.x[i];
			ay = rope.y[i];
			az = rope.z[i];
		}
	}

	private void swing(Bell bell, float breeze) {
		float omega = Mth.TWO_PI * 0.9f;
		float stiffness = omega * omega;
		float damping = 2f * 0.12f * omega;
		float windX = 0f;
		float windZ = 0f;
		if (bell.wind) {
			windX = breeze * 0.25f * Mth.sin(time * 0.7f + bell.phase);
			windZ = breeze * 0.25f * Mth.cos(time * 0.5f + bell.phase * 1.3f);
		}
		if (bell.along == Direction.Axis.Z) {
			windX = 0f;
		} else if (bell.along == Direction.Axis.X) {
			windZ = 0f;
		}
		bell.vx += (-stiffness * bell.x - damping * bell.vx + windX) * STEP;
		bell.vz += (-stiffness * bell.z - damping * bell.vz + windZ) * STEP;
		bell.x = Mth.clamp(bell.x + bell.vx * STEP, -0.6f, 0.6f);
		bell.z = Mth.clamp(bell.z + bell.vz * STEP, -0.6f, 0.6f);
	}

	// ---------------------------------------------------------------- drawing

	private void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : (now - lastFrame) / 1e9f;
		lastFrame = now;
		if (level == null || ropes.isEmpty() && bells.isEmpty()) {
			return;
		}
		step(dt);
		Vec3 camera = mc.gameRenderer.mainCamera().position();
		PoseStack pose = context.poseStack();
		for (Rope rope : ropes.values()) {
			float ax = 0f;
			float ay = 0f;
			float az = 0f;
			for (int i = 0; i < rope.positions.length; i++) {
				BlockPos pos = rope.positions[i];
				BlockState state = rope.states[i];
				int light = LightCoordsUtil.getLightCoords(level, pos);
				Vector3f down = new Vector3f(rope.x[i] - ax, rope.y[i] - ay, rope.z[i] - az).normalize();
				pose.pushPose();
				pose.translate(rope.anchorX + ax - camera.x, rope.anchorY + ay - camera.y, rope.anchorZ + az - camera.z);
				pose.mulPose(new Matrix4f().rotation(new Quaternionf().rotationTo(0f, -1f, 0f, down.x, down.y, down.z)));
				pose.translate(-0.5f, -1f, -0.5f);
				context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.cutoutMovingBlock(), (base, consumer) -> {
					boolean tagged = IrisCompat.beginBlock(consumer, state);
					HiddenBlocks.tesselate(level, pos, state, (x, y, z, quad, instance) -> {
						// Lit evenly by its own block: once a link turns, the light of its neighbours no longer fits its faces.
						instance.setLightCoords(light);
						consumer.putBakedQuad(base, quad, instance);
					});
					if (tagged) {
						IrisCompat.endBlock(consumer);
					}
				});
				pose.popPose();
				ax = rope.x[i];
				ay = rope.y[i];
				az = rope.z[i];
			}
		}
	}

	/** Tilts a bell's body (drawn by its block entity) by its swing; called with the pose at the block's corner. */
	public void tiltBell(BlockPos pos, PoseStack pose) {
		Bell bell = pos == null ? null : bells.get(pos.asLong());
		if (bell == null) {
			return;
		}
		float angle = (float) Math.sqrt(bell.x * bell.x + bell.z * bell.z);
		if (angle < 1e-5f) {
			return;
		}
		// The bottom swings out along (x, z), around the top of the body.
		Vector3f axis = new Vector3f(-bell.z, 0f, bell.x).normalize();
		pose.translate(0.5f, 0.75f, 0.5f);
		pose.mulPose(new Matrix4f().rotation(angle, axis));
		pose.translate(-0.5f, -0.75f, -0.5f);
	}
}
