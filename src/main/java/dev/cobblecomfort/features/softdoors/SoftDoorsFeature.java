package dev.cobblecomfort.features.softdoors;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.core.HiddenBlocks;
import dev.cobblecomfort.core.IrisCompat;
import dev.isxander.yacl3.api.ConfigCategory;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Iterator;

/**
 * Doors, trapdoors and fence gates swing open and shut with a little overshoot instead of snapping. Only the look is
 * animated: the block itself (and its hitbox) changes at once as in vanilla. While it swings the block is left out of
 * its chunk mesh (see {@link HiddenBlocks}) and its new model is drawn turned back towards the old position, easing
 * into place on a spring. Gates swing as two halves, each around its post.
 */
public final class SoftDoorsFeature extends Feature<SoftDoorsConfig> {
	public static final SoftDoorsFeature INSTANCE = new SoftDoorsFeature();

	private static final double RANGE = 64;
	private static final int MAX_SWINGS = 64;
	/** After settling it is still drawn a moment longer, until the rebuilt mesh with it is on screen. */
	private static final long RETURN_NANOS = 150_000_000L;
	private static final float STEP = 1f / 240f;
	private static final float QUARTER = Mth.HALF_PI;

	enum Kind {
		DOOR, TRAPDOOR, GATE
	}

	/** One rotating piece: the whole door, or one half of a gate. */
	private static final class Segment {
		final Vector3f pivot;
		final Vector3f axis;
		/** +1 or -1: which way the piece turns from the old position to the new one. */
		final float sign;
		float angle;
		float velocity;

		Segment(Vector3f pivot, Vector3f axis, float sign) {
			this.pivot = pivot;
			this.axis = axis;
			this.sign = sign;
			this.angle = -sign * QUARTER;
		}

		Matrix4f transform() {
			return new Matrix4f().translation(pivot).rotate(angle, axis).translate(-pivot.x, -pivot.y, -pivot.z);
		}
	}

	private static final class Swing {
		final BlockPos pos;
		final Kind kind;
		BlockState state;
		Segment[] segments;
		/** For gates: along which axis the gate spans, so quads can be split into the two halves. */
		Direction.Axis span;
		/** When it was given back to the mesh, or 0 while it is still out. */
		long returned;

		Swing(BlockPos pos, Kind kind, BlockState state) {
			this.pos = pos;
			this.kind = kind;
			this.state = state;
		}
	}

	private final Long2ObjectLinkedOpenHashMap<Swing> swings = new Long2ObjectLinkedOpenHashMap<>();
	private long lastFrame;
	private float pending;

	private SoftDoorsFeature() {
		super("soft_doors", SoftDoorsConfig.class, SoftDoorsConfig::new);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			swings.clear();
			HiddenBlocks.clear();
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(this::render);
	}

	@Override
	public ConfigCategory buildCategory() {
		return SoftDoorsConfigUi.buildCategory(this);
	}

	private Kind kind(BlockState state) {
		if (state.getBlock() instanceof DoorBlock) {
			return config.doors ? Kind.DOOR : null;
		}
		if (state.getBlock() instanceof TrapDoorBlock) {
			return config.trapdoors ? Kind.TRAPDOOR : null;
		}
		if (state.getBlock() instanceof FenceGateBlock) {
			return config.gates ? Kind.GATE : null;
		}
		return null;
	}

	// ---------------------------------------------------------------- opening and closing

	/** Called for every block change the client sees, before the section is rebuilt with the new state. */
	public void changed(ClientLevel level, BlockPos pos, BlockState old, BlockState updated) {
		if (!isEnabled() || old == updated || old.getBlock() != updated.getBlock()) {
			return;
		}
		Kind kind = kind(updated);
		if (kind == null) {
			return;
		}
		Swing swing = swings.get(pos.asLong());
		if (old.getValue(BlockStateProperties.OPEN) == updated.getValue(BlockStateProperties.OPEN)) {
			if (swing != null) {
				swing.state = updated;
			}
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameRenderer.mainCamera().position().distanceToSqr(Vec3.atCenterOf(pos)) > RANGE * RANGE) {
			return;
		}
		Segment[] segments = segments(level, pos, kind, old, updated, swing);
		if (segments == null) {
			return;
		}
		if (swing == null) {
			if (swings.size() >= MAX_SWINGS) {
				return;
			}
			swing = new Swing(pos.immutable(), kind, updated);
			swings.put(pos.asLong(), swing);
			HiddenBlocks.hide(swing.pos);
		} else if (swing.returned != 0) {
			swing.returned = 0;
			HiddenBlocks.hide(swing.pos);
		} else if (swing.segments.length == segments.length) {
			// Turned around mid-swing: carry on from where it is drawn right now.
			for (int i = 0; i < segments.length; i++) {
				Segment before = swing.segments[i];
				segments[i].angle = before.angle + before.sign * QUARTER;
				segments[i].velocity = before.velocity;
			}
		}
		swing.state = updated;
		swing.segments = segments;
		swing.span = kind == Kind.GATE ? updated.getValue(FenceGateBlock.FACING).getClockWise().getAxis() : null;
	}

	private Segment[] segments(ClientLevel level, BlockPos pos, Kind kind, BlockState old, BlockState updated, Swing swing) {
		if (kind == Kind.GATE) {
			return gateSegments(level, pos, old, updated);
		}
		// A door or trapdoor turns a thin slab by a quarter around the line where its old and new shapes overlap.
		AABB from = old.getShape(level, pos).bounds();
		AABB to = updated.getShape(level, pos).bounds();
		AABB overlap = from.intersect(to);
		double[] size = {overlap.getXsize(), overlap.getYsize(), overlap.getZsize()};
		int longest = size[0] >= size[1] && size[0] >= size[2] ? 0 : size[1] >= size[2] ? 1 : 2;
		Vector3f axis = new Vector3f(longest == 0 ? 1 : 0, longest == 1 ? 1 : 0, longest == 2 ? 1 : 0);
		Vec3 center = overlap.getCenter();
		Vector3f pivot = new Vector3f((float) center.x, (float) center.y, (float) center.z);
		Vec3 a = from.getCenter();
		Vec3 b = to.getCenter();
		float sign = sign(pivot, axis, new Vector3f((float) a.x, (float) a.y, (float) a.z), new Vector3f((float) b.x, (float) b.y, (float) b.z));
		return new Segment[] {new Segment(pivot, axis, sign)};
	}

	/** The two halves of a gate turn around their posts; which way is read from where each half's quads lie. */
	private static Segment[] gateSegments(ClientLevel level, BlockPos pos, BlockState old, BlockState updated) {
		Direction.Axis span = updated.getValue(FenceGateBlock.FACING).getClockWise().getAxis();
		Vector3f[] from = halfCenters(level, pos, old, span);
		Vector3f[] to = halfCenters(level, pos, updated, span);
		if (from == null || to == null) {
			return null;
		}
		Vector3f axis = new Vector3f(0f, 1f, 0f);
		Segment[] segments = new Segment[2];
		for (int half = 0; half < 2; half++) {
			float along = half == 0 ? 1f / 16f : 15f / 16f;
			Vector3f pivot = span == Direction.Axis.X ? new Vector3f(along, 0f, 0.5f) : new Vector3f(0.5f, 0f, along);
			segments[half] = new Segment(pivot, axis, sign(pivot, axis, from[half], to[half]));
		}
		return segments;
	}

	/** Which quarter turn (+1 or -1) around the pivot carries the old center closest to the new one. */
	private static float sign(Vector3f pivot, Vector3f axis, Vector3f from, Vector3f to) {
		Vector3f offset = new Vector3f(from).sub(pivot);
		Vector3f plus = new Quaternionf().rotationAxis(QUARTER, axis).transform(new Vector3f(offset)).add(pivot);
		Vector3f minus = new Quaternionf().rotationAxis(-QUARTER, axis).transform(new Vector3f(offset)).add(pivot);
		return plus.distanceSquared(to) <= minus.distanceSquared(to) ? 1f : -1f;
	}

	/** The middle of each half of a gate model, split across the gate; null if a half has no quads. */
	private static Vector3f[] halfCenters(ClientLevel level, BlockPos pos, BlockState state, Direction.Axis span) {
		Vector3f[] sums = {new Vector3f(), new Vector3f()};
		int[] counts = new int[2];
		HiddenBlocks.tesselate(level, pos, state, (x, y, z, quad, instance) -> {
			Vector3f center = center(quad);
			int half = half(center, span);
			sums[half].add(center);
			counts[half]++;
		});
		if (counts[0] == 0 || counts[1] == 0) {
			return null;
		}
		return new Vector3f[] {sums[0].div(counts[0]), sums[1].div(counts[1])};
	}

	private static Vector3f center(BakedQuad quad) {
		Vector3f center = new Vector3f();
		for (int i = 0; i < 4; i++) {
			center.add(quad.position(i));
		}
		return center.div(4f);
	}

	private static int half(Vector3f center, Direction.Axis span) {
		return (span == Direction.Axis.X ? center.x : center.z) < 0.5f ? 0 : 1;
	}

	private void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || swings.isEmpty()) {
			return;
		}
		boolean enabled = isEnabled();
		long now = System.nanoTime();
		Iterator<Swing> it = swings.values().iterator();
		while (it.hasNext()) {
			Swing swing = it.next();
			BlockState state = level.getBlockState(swing.pos);
			if (!enabled || state.getBlock() != swing.state.getBlock()) {
				// Broken or switched off: back into the mesh at once.
				if (swing.returned == 0) {
					HiddenBlocks.show(swing.pos);
				}
				it.remove();
				continue;
			}
			swing.state = state;
			if (swing.returned != 0) {
				if (now - swing.returned > RETURN_NANOS) {
					it.remove();
				}
				continue;
			}
			boolean settled = true;
			for (Segment segment : swing.segments) {
				settled &= Math.abs(segment.angle) < 0.003f && Math.abs(segment.velocity) < 0.03f;
			}
			if (settled) {
				for (Segment segment : swing.segments) {
					segment.angle = segment.velocity = 0f;
				}
				swing.returned = now;
				HiddenBlocks.show(swing.pos);
			}
		}
	}

	// ---------------------------------------------------------------- swinging and drawing

	private void step(float dt) {
		float omega = Mth.lerp(Mth.clamp(config.speed, 0f, 1f), 7f, 24f);
		float stiffness = omega * omega;
		float damping = 2f * Mth.lerp(Mth.clamp(config.bounce, 0f, 1f), 1f, 0.25f) * omega;
		pending = Math.min(pending + dt, 0.1f);
		while (pending >= STEP) {
			pending -= STEP;
			for (Swing swing : swings.values()) {
				if (swing.returned != 0) {
					continue;
				}
				for (Segment segment : swing.segments) {
					segment.velocity += (-stiffness * segment.angle - damping * segment.velocity) * STEP;
					segment.angle += segment.velocity * STEP;
				}
			}
		}
	}

	private void render(LevelRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : (now - lastFrame) / 1e9f;
		lastFrame = now;
		if (level == null || swings.isEmpty()) {
			return;
		}
		step(dt);
		Vec3 camera = mc.gameRenderer.mainCamera().position();
		PoseStack pose = context.poseStack();
		for (Swing swing : swings.values()) {
			// The submit is drawn later; take what it needs now.
			BlockPos pos = swing.pos;
			BlockState state = swing.state;
			Direction.Axis span = swing.span;
			// Lit evenly by its own block: baked per-face light would turn the underside of a trapdoor black once it swings up.
			int light = LightCoordsUtil.getLightCoords(level, pos);
			Matrix4f[] transforms = new Matrix4f[swing.segments.length];
			for (int i = 0; i < transforms.length; i++) {
				transforms[i] = swing.segments[i].transform();
			}
			pose.pushPose();
			pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
			context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.cutoutMovingBlock(), (base, consumer) -> {
				PoseStack.Pose[] poses = new PoseStack.Pose[transforms.length];
				for (int i = 0; i < poses.length; i++) {
					poses[i] = base.copy();
					poses[i].mulPose(transforms[i]);
				}
				boolean tagged = IrisCompat.beginBlock(consumer, state);
				HiddenBlocks.tesselate(level, pos, state, (x, y, z, quad, instance) -> {
					instance.setLightCoords(light);
					int segment = span == null ? 0 : half(center(quad), span);
					consumer.putBakedQuad(poses[segment], quad, instance);
				});
				if (tagged) {
					IrisCompat.endBlock(consumer);
				}
			});
			pose.popPose();
		}
	}
}
