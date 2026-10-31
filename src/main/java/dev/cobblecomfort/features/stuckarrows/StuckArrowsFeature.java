package dev.cobblecomfort.features.stuckarrows;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.core.IrisCompat;
import dev.isxander.yacl3.api.ConfigCategory;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Arrows and bee stingers stuck in a player wobble: they lag behind when the player starts, stops, turns or lands,
 * bob along with each step, and twang when the player is hit or another arrow sticks. Vanilla draws them in
 * {@code StuckInBodyLayer}; each one is turned a little around the point where it sticks (see
 * {@code StuckInBodyLayerMixin}).
 */
public final class StuckArrowsFeature extends Feature<StuckArrowsConfig> {
	public static final StuckArrowsFeature INSTANCE = new StuckArrowsFeature();

	private static final float STEP = 1f / 240f;
	private static final float OMEGA = Mth.TWO_PI * 3.2f;
	private static final float DAMPING_RATIO = 0.14f;
	private static final float MAX_ANGLE = 0.4f;
	private static final long FORGET_NANOS = 5_000_000_000L;

	/** One player's wobble: two angles on a spring, driven by how the body moves. */
	public static final class Wobble {
		double x;
		double y;
		double z;
		float forward;
		float side;
		float up;
		float pitch;
		float roll;
		float vPitch;
		float vRoll;
		float pending;
		long frame;
		int count;
		boolean hurt;
		boolean started;
		float strength;
	}

	private final Int2ObjectOpenHashMap<Wobble> wobbles = new Int2ObjectOpenHashMap<>();

	private StuckArrowsFeature() {
		super("stuck_arrows", StuckArrowsConfig.class, StuckArrowsConfig::new);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			long frame = mc.getFrameTimeNs();
			wobbles.values().removeIf(w -> frame - w.frame > FORGET_NANOS);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> wobbles.clear());
	}

	@Override
	public ConfigCategory buildCategory() {
		return StuckArrowsConfigUi.buildCategory(this);
	}

	/** The wobble for the arrows (or stingers) about to be drawn on this player, stepped once per frame; null if off. */
	public Wobble wobble(AvatarRenderState state, boolean stingers) {
		if (!isEnabled() || stingers && !config.stingers) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		boolean own = mc.player != null && mc.player.getId() == state.id;
		if (own ? !config.own : !config.others) {
			return null;
		}
		Wobble wobble = wobbles.computeIfAbsent(state.id, id -> new Wobble());
		wobble.strength = config.strength;
		long frame = mc.getFrameTimeNs();
		// Drawn again in the same frame (the inventory preview, the shadow map): already stepped.
		if (wobble.frame == frame || IrisCompat.inShadowPass()) {
			return wobble;
		}
		float dt = wobble.started ? Mth.clamp((frame - wobble.frame) / 1e9f, 0f, 0.1f) : 0f;
		wobble.frame = frame;
		step(wobble, state, dt);
		return wobble;
	}

	private static void step(Wobble wobble, AvatarRenderState state, float dt) {
		// How the body moves, along its own facing.
		float yaw = state.bodyRot * Mth.DEG_TO_RAD;
		float forward = 0f;
		float side = 0f;
		float up = 0f;
		if (wobble.started && dt > 0f) {
			double vx = (state.x - wobble.x) / dt;
			double vy = (state.y - wobble.y) / dt;
			double vz = (state.z - wobble.z) / dt;
			forward = (float) (-Mth.sin(yaw) * vx + Mth.cos(yaw) * vz);
			side = (float) (Mth.cos(yaw) * vx + Mth.sin(yaw) * vz);
			up = (float) vy;
		}
		int count = state.arrowCount + state.stingerCount;
		boolean hurt = state.hasRedOverlay;
		if (wobble.started && dt > 0f) {
			// Arrows lag behind a change in speed, and spring after a hit.
			float aForward = Mth.clamp((forward - wobble.forward) / dt, -60f, 60f);
			float aSide = Mth.clamp((side - wobble.side) / dt, -60f, 60f);
			float aUp = Mth.clamp((up - wobble.up) / dt, -60f, 60f);
			float kick = 0f;
			if (hurt && !wobble.hurt || count > wobble.count) {
				kick = 9f;
			}
			// Each step shakes them a little, twice per stride.
			float stride = state.walkAnimationSpeed * Mth.sin(state.walkAnimationPos * 0.6662f * 2f) * 1.6f;
			float drivePitch = -(aForward + aUp * 0.6f) * 0.05f + stride;
			float driveRoll = -aSide * 0.05f;
			wobble.vPitch += kick;
			wobble.vRoll += kick * 0.6f;
			wobble.pending = Math.min(wobble.pending + dt, 0.1f);
			float stiffness = OMEGA * OMEGA;
			float damping = 2f * DAMPING_RATIO * OMEGA;
			while (wobble.pending >= STEP) {
				wobble.pending -= STEP;
				wobble.vPitch += (-stiffness * wobble.pitch - damping * wobble.vPitch + drivePitch * stiffness * 0.05f) * STEP;
				wobble.vRoll += (-stiffness * wobble.roll - damping * wobble.vRoll + driveRoll * stiffness * 0.05f) * STEP;
				wobble.pitch = Mth.clamp(wobble.pitch + wobble.vPitch * STEP, -MAX_ANGLE, MAX_ANGLE);
				wobble.roll = Mth.clamp(wobble.roll + wobble.vRoll * STEP, -MAX_ANGLE, MAX_ANGLE);
			}
		}
		wobble.x = state.x;
		wobble.y = state.y;
		wobble.z = state.z;
		wobble.forward = forward;
		wobble.side = side;
		wobble.up = up;
		wobble.count = count;
		wobble.hurt = hurt;
		wobble.started = true;
	}

	/**
	 * Turns the {@code index}th stuck arrow by the wobble, around the point where it sticks (the pose's origin). Each
	 * arrow takes it a little differently, so they don't all swing as one.
	 */
	public static void tilt(PoseStack pose, Wobble wobble, int index) {
		int hash = index * 0x9E3779B9;
		float a = 0.6f + ((hash >>> 8) & 0xFF) / 255f * 0.7f;
		float b = 0.6f + ((hash >>> 16) & 0xFF) / 255f * 0.7f;
		boolean flip = (hash & 0x100) != 0;
		float pitch = wobble.pitch * a * wobble.strength;
		float roll = wobble.roll * b * wobble.strength;
		if (flip) {
			float swap = pitch;
			pitch = roll;
			roll = -swap;
		}
		pose.mulPose(new Matrix4f().rotationYXZ(roll, 0f, pitch));
	}
}
