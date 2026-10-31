package dev.cobblecomfort.features.plush;

import dev.cobblecomfort.core.IrisCompat;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Soft-toy physics for one held plush: loose, under-damped springs for the whole body, the head and each limb. They
 * are pulled by how the holder moves (turning, speeding up, looking up and down, and the bounce of each step), so the
 * plush lags behind, overshoots and settles. Everything runs on interpolated positions, every frame, in small fixed
 * steps, so it stays smooth at any frame rate.
 */
public final class PlushJiggle {
	private static final float STEP = 1f / 240f;
	private static final float MAX_FRAME = 0.1f;
	/** Smoothing of the measured turn rate and velocity, in seconds. */
	private static final float RATE_SMOOTHING = 0.05f;
	private static final float ACCEL_SMOOTHING = 0.08f;
	private static final float WALK_FREQUENCY = 0.6662f;
	/** Overall strength of the jiggle; every push is scaled by it. */
	private static final float STRENGTH = 0.75f;

	/** An angle on a damped spring, in degrees. */
	private static final class Spring {
		private final float stiffness;
		private final float damping;
		private final float limit;
		float angle;
		float velocity;

		Spring(float frequencyHz, float dampingRatio, float limit) {
			float omega = Mth.TWO_PI * frequencyHz;
			this.stiffness = omega * omega;
			this.damping = 2 * dampingRatio * omega;
			this.limit = limit;
		}

		void step(float target, float dt) {
			velocity += (stiffness * (Mth.clamp(target, -limit, limit) - angle) - damping * velocity) * dt;
			angle += velocity * dt;
		}

		void reset() {
			angle = 0;
			velocity = 0;
		}
	}

	// The body sways slowly on its bottom, the head is looser and quicker, the limbs flop the most.
	private final Spring bodyRoll = new Spring(2.0f, 0.28f, 18);
	private final Spring bodyPitch = new Spring(2.0f, 0.28f, 16);
	private final Spring headRoll = new Spring(2.8f, 0.16f, 18);
	private final Spring headPitch = new Spring(2.8f, 0.16f, 15);
	// Arms lean together with the body and flap apart with each step.
	private final Spring armLean = new Spring(3.4f, 0.13f, 28);
	private final Spring armFlap = new Spring(3.4f, 0.13f, 20);
	private final Spring rightArmPitch = new Spring(3.4f, 0.13f, 30);
	private final Spring leftArmPitch = new Spring(3.4f, 0.13f, 30);
	private final Spring rightLegPitch = new Spring(3.8f, 0.2f, 25);
	private final Spring leftLegPitch = new Spring(3.8f, 0.2f, 25);
	// A tail (pet plushes) is the floppiest of all: it swings sideways and up and down.
	private final Spring tailSwing = new Spring(2.4f, 0.1f, 40);
	private final Spring tailLift = new Spring(2.4f, 0.1f, 35);

	private boolean primed;
	private float lastYaw;
	private float lastPitch;
	private Vec3 lastPos = Vec3.ZERO;
	private float turnRate;
	private float pitchRate;
	private float velForward;
	private float velSide;
	private float accForward;
	private float accSide;
	private float pending;

	public void reset() {
		primed = false;
		for (Spring spring : springs()) {
			spring.reset();
		}
	}

	/** Measures how the holder moved since the last frame and advances the springs. */
	public void update(LivingEntity holder, float partialTick, float dt) {
		if (IrisCompat.inShadowPass()) {
			// Drawn again for the shadow map in the same frame: the plush already moved.
			return;
		}
		float yaw = holder.getViewYRot(partialTick);
		float pitch = holder.getViewXRot(partialTick);
		Vec3 pos = holder.getPosition(partialTick);
		if (!primed || dt <= 0f || dt > MAX_FRAME) {
			primed = true;
			lastYaw = yaw;
			lastPitch = pitch;
			lastPos = pos;
			return;
		}

		// Turn and look rates in degrees per second, velocity in blocks per second in the holder's own frame.
		float rate = smoothing(dt, RATE_SMOOTHING);
		turnRate += (Mth.wrapDegrees(yaw - lastYaw) / dt - turnRate) * rate;
		pitchRate += ((pitch - lastPitch) / dt - pitchRate) * rate;
		float yawRad = yaw * Mth.DEG_TO_RAD;
		double vx = (pos.x - lastPos.x) / dt;
		double vz = (pos.z - lastPos.z) / dt;
		float forward = (float) (-vx * Mth.sin(yawRad) + vz * Mth.cos(yawRad));
		float side = (float) (-vx * Mth.cos(yawRad) - vz * Mth.sin(yawRad));
		float oldForward = velForward;
		float oldSide = velSide;
		velForward += (forward - velForward) * rate;
		velSide += (side - velSide) * rate;
		float accRate = smoothing(dt, ACCEL_SMOOTHING);
		accForward += ((velForward - oldForward) / dt - accForward) * accRate;
		accSide += ((velSide - oldSide) / dt - accSide) * accRate;
		lastYaw = yaw;
		lastPitch = pitch;
		lastPos = pos;

		float speed = Math.min(1f, holder.walkAnimation.speed(partialTick));
		float phase = holder.walkAnimation.position(partialTick) * WALK_FREQUENCY;
		// Each step lifts the plush a little: a bounce at twice the stride frequency.
		float bounce = Mth.cos(phase * 2f) * speed;
		float stride = Mth.sin(phase) * speed;
		// Sideways push: speeding up to the right or turning right leaves the plush leaning left (positive here).
		float lateral = accSide * 1.2f + turnRate * 0.05f;
		// Forward push: speeding up leaves it leaning back; looking down tips it forward.
		float longitudinal = accForward * 1.1f - pitchRate * 0.025f;
		bounce *= STRENGTH;
		stride *= STRENGTH;
		lateral *= STRENGTH;
		longitudinal *= STRENGTH;

		pending += dt;
		while (pending >= STEP) {
			pending -= STEP;
			bodyRoll.step(lateral, STEP);
			bodyPitch.step(longitudinal + bounce * 2f, STEP);
			headRoll.step(lateral * 1.6f, STEP);
			headPitch.step(longitudinal * 1.2f + bounce * 5f, STEP);
			armLean.step(lateral * 1.4f, STEP);
			armFlap.step(bounce * 10f, STEP);
			rightArmPitch.step(longitudinal * 2f + stride * 16f, STEP);
			leftArmPitch.step(longitudinal * 2f - stride * 16f, STEP);
			rightLegPitch.step(longitudinal - stride * 15f, STEP);
			leftLegPitch.step(longitudinal + stride * 15f, STEP);
			tailSwing.step(-lateral * 2.2f + stride * 12f, STEP);
			tailLift.step(-longitudinal * 1.8f + bounce * 9f, STEP);
		}
	}

	/**
	 * The current sway. {@code mirrored} is for a plush that faces the holder (seen in first person), where the
	 * holder's left is the plush's right and leaning back means leaning towards it.
	 */
	float bodyRoll(boolean mirrored) {
		return sign(mirrored) * bodyRoll.angle;
	}

	float bodyPitch(boolean mirrored) {
		return sign(mirrored) * bodyPitch.angle;
	}

	public PlushPose pose(boolean mirrored) {
		float s = sign(mirrored) * Mth.DEG_TO_RAD;
		// The flap looks the same from both sides; only the lean flips.
		float flap = armFlap.angle * Mth.DEG_TO_RAD;
		return new PlushPose(
				s * headPitch.angle, s * headRoll.angle,
				s * rightArmPitch.angle, s * armLean.angle + flap,
				s * leftArmPitch.angle, s * armLean.angle - flap,
				s * rightLegPitch.angle, s * leftLegPitch.angle);
	}

	/** The tail's sideways swing and lift right now, in radians (sideways mirrored like the rest). */
	public float tailSwing(boolean mirrored) {
		return sign(mirrored) * tailSwing.angle * Mth.DEG_TO_RAD;
	}

	public float tailLift() {
		return tailLift.angle * Mth.DEG_TO_RAD;
	}

	private static float sign(boolean mirrored) {
		return mirrored ? -1f : 1f;
	}

	private static float smoothing(float dt, float seconds) {
		return 1f - (float) Math.exp(-dt / seconds);
	}

	private Spring[] springs() {
		return new Spring[]{bodyRoll, bodyPitch, headRoll, headPitch, armLean, armFlap, rightArmPitch,
				leftArmPitch, rightLegPitch, leftLegPitch, tailSwing, tailLift};
	}
}
