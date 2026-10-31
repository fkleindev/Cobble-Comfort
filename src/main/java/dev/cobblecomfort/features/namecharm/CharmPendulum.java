package dev.cobblecomfort.features.namecharm;

import dev.cobblecomfort.core.IrisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;

/**
 * The charm as a weight on a stiff string, simulated in world space (Verlet with fixed sub-steps): when the item it
 * hangs from swings, turns or moves, the weight lags behind and swings back under gravity.
 */
public final class CharmPendulum {
	private static final double GRAVITY = 16.0;
	private static final double STEP = 1.0 / 240.0;
	/** After a pause this long (not drawn, game paused) the charm starts again hanging still. */
	private static final double RESET_SECONDS = 0.25;
	private static final int MAX_STEPS = 24;
	/** Parts drawn this many times bigger than a block are in a screen's entity preview. */
	private static final float GUI_SCALE = 4f;

	private final Vector3d weight = new Vector3d();
	private final Vector3d previous = new Vector3d();
	private final Vector3d anchor = new Vector3d();
	private final Vector3d direction = new Vector3d(0, -1, 0);
	private long lastNanos;
	private boolean started;
	public long lastSeen;

	/**
	 * Moves the weight towards this frame's anchor; {@code damping} is how much speed is lost per second.
	 * Returns the unit direction from the anchor to the weight.
	 */
	public Vector3dc update(Vector3dc newAnchor, double length, double damping, long now) {
		if (started && IrisCompat.inShadowPass()) {
			// Drawn again for the shadow map, from elsewhere: keep the swing of this frame.
			return direction;
		}
		lastSeen = now;
		double dt = (now - lastNanos) / 1e9;
		if (!started || dt > RESET_SECONDS || length <= 1e-6) {
			started = true;
			lastNanos = now;
			anchor.set(newAnchor);
			weight.set(newAnchor).add(0, -length, 0);
			previous.set(weight);
			direction.set(0, -1, 0);
			return direction;
		}
		if (dt < 1e-4) {
			// Drawn twice in the same frame: keep the result.
			return direction;
		}
		lastNanos = now;
		int steps = (int) Math.min(MAX_STEPS, Math.ceil(dt / STEP));
		double h = dt / steps;
		double keep = Math.exp(-damping * h);
		Vector3d from = new Vector3d(anchor);
		Vector3d at = new Vector3d();
		Vector3d velocity = new Vector3d();
		for (int i = 1; i <= steps; i++) {
			// The anchor moves evenly over the frame, so a fast swing still pulls smoothly.
			from.lerp(newAnchor, 1.0 / (steps - i + 1), at);
			from.set(at);
			weight.sub(previous, velocity).mul(keep);
			previous.set(weight);
			weight.add(velocity).add(0, -GRAVITY * h * h, 0);
			// The string doesn't stretch.
			weight.sub(at, direction);
			double d = direction.length();
			if (d < 1e-9) {
				direction.set(0, -1, 0);
			} else {
				direction.div(d);
			}
			weight.set(at).fma(length, direction);
		}
		anchor.set(newAnchor);
		return direction;
	}

	/**
	 * For something worn on an entity part: which way it hangs from {@code anchor} in the part's space ({@code part} is
	 * the pose there; entities are drawn relative to the camera, without its rotation). {@code length} is the distance
	 * to its weight in that space. The pendulum swings in the world. Null if the part is squashed flat.
	 */
	public Vector3f hang(Matrix4f part, Vector3f anchor, float length, double damping) {
		Matrix3f linear = part.get3x3(new Matrix3f());
		if (Math.abs(linear.determinant()) < 1e-12f) {
			return null;
		}
		float worldLength = linear.transform(new Vector3f(0f, length, 0f)).length();
		if (worldLength > length * GUI_SCALE) {
			// Drawn in a screen (inventory, wardrobe) in pixels, y down, not in the world: it just hangs. Simulating there
			// would also pull the swing of the same player in the world.
			Vector3f local = linear.invert().transform(new Vector3f(0f, 1f, 0f));
			return local.lengthSquared() < 1e-12f ? null : local.normalize();
		}
		Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		Vector3f world = part.transformPosition(new Vector3f(anchor));
		Vector3dc dir = update(new Vector3d(world).add(camera.x, camera.y, camera.z), worldLength, damping, System.nanoTime());
		Vector3f local = linear.invert().transform(new Vector3f((float) dir.x(), (float) dir.y(), (float) dir.z()));
		return local.lengthSquared() < 1e-12f ? null : local.normalize();
	}
}
