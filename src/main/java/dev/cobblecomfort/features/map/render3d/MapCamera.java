package dev.cobblecomfort.features.map.render3d;

import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The camera of the 3D map: it circles a point on the ground at some distance, turned by yaw (0 = north up) and tilted
 * by pitch (0 = looking straight ahead, π/2 = straight down). Positions handed to the GPU are relative to the target,
 * so floats stay precise far from the world's origin.
 */
public final class MapCamera {
	public static final float FOV_DEGREES = 50f;
	private static final float MIN_PITCH = 0.3f;
	private static final float MAX_PITCH = 1.5f;
	public static final float MIN_DISTANCE = 12f;
	public static final float MAX_DISTANCE = 6000f;

	public double targetX;
	public double targetY;
	public double targetZ;
	public float yaw;
	public float pitch = 0.95f;
	public float distance = 160f;

	public MapCamera copy() {
		MapCamera copy = new MapCamera();
		copy.targetX = targetX;
		copy.targetY = targetY;
		copy.targetZ = targetZ;
		copy.yaw = yaw;
		copy.pitch = pitch;
		copy.distance = distance;
		return copy;
	}

	public void turn(float dYaw, float dPitch) {
		yaw = (float) Math.IEEEremainder(yaw + dYaw, Math.PI * 2);
		pitch = Mth.clamp(pitch + dPitch, MIN_PITCH, MAX_PITCH);
	}

	public void zoom(float factor) {
		distance = Mth.clamp(distance * factor, MIN_DISTANCE, MAX_DISTANCE);
	}

	/** Moves the target by screen pixels of a map area {@code height} pixels high, so the ground follows the mouse. */
	public void pan(double dx, double dy, int height) {
		double perPixel = 2 * distance * Math.tan(Math.toRadians(FOV_DEGREES / 2)) / Math.max(1, height);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		// Seen at a slant, a pixel up the screen covers more ground than one across it.
		double forward = dy * perPixel / Math.max(0.35, Math.sin(pitch));
		double right = dx * perPixel;
		targetX += -cos * right + sin * forward;
		targetZ += -sin * right - cos * forward;
	}

	/** Moves the target along the ground: {@code forward} up the screen, {@code right} across it. */
	public void move(double forward, double right) {
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		targetX += cos * right + sin * forward;
		targetZ += sin * right - cos * forward;
	}

	/**
	 * Tilts the camera up just enough to keep it above the ground: tilted low, it would otherwise dip into hills and
	 * show them from inside.
	 */
	public void keepAbove(Ground ground, float clearance) {
		for (int i = 0; i < 4; i++) {
			double[] eye = eye();
			int highest = Integer.MIN_VALUE;
			// Around the eye too, so the near plane doesn't cut into a slope right in front.
			for (int dz = -2; dz <= 2; dz += 2) {
				for (int dx = -2; dx <= 2; dx += 2) {
					highest = Math.max(highest, ground.height(Mth.floor(targetX + eye[0]) + dx, Mth.floor(targetZ + eye[2]) + dz));
				}
			}
			if (highest == Integer.MIN_VALUE || targetY + eye[1] >= highest + 1 + clearance) {
				return;
			}
			double sin = (highest + 1 + clearance - targetY) / distance;
			float needed = sin >= 1 ? MAX_PITCH : (float) Math.asin(Math.max(sin, Math.sin(MIN_PITCH)));
			if (needed <= pitch + 1e-3f) {
				needed = pitch + 0.02f;
			}
			pitch = Mth.clamp(needed, MIN_PITCH, MAX_PITCH);
		}
	}

	public float near() {
		return Math.max(0.5f, distance * 0.02f);
	}

	public float far() {
		return distance * 4f + 3000f;
	}

	/** World (relative to the target) to view space. */
	public Matrix4f view() {
		return new Matrix4f().translate(0f, 0f, -distance).rotateX(pitch).rotateY(yaw);
	}

	/** Where the camera is, relative to the target. */
	public double[] eye() {
		Matrix4f inverse = view().invert();
		Vector4f eye = inverse.transform(new Vector4f(0f, 0f, 0f, 1f));
		return new double[]{eye.x, eye.y, eye.z};
	}

	/** Projection for a {@code width} x {@code height} view; z is reversed (near 1, far 0) as Minecraft draws. */
	public Matrix4f projection(float width, float height, boolean zeroToOne) {
		return new Matrix4f().setPerspective((float) Math.toRadians(FOV_DEGREES), width / Math.max(1f, height), far(), near(), zeroToOne);
	}

	/**
	 * Where a world point lands in a map area at (x, y) of size w x h, or null behind the camera. The result is
	 * {screen x, screen y, distance from the camera}.
	 */
	public float[] project(double wx, double wy, double wz, int x, int y, int w, int h) {
		Matrix4f matrix = projection(w, h, true).mul(view());
		Vector4f clip = matrix.transform(new Vector4f((float) (wx - targetX), (float) (wy - targetY), (float) (wz - targetZ), 1f));
		if (clip.w <= 0.01f) {
			return null;
		}
		float sx = x + (clip.x / clip.w + 1f) * 0.5f * w;
		float sy = y + (1f - clip.y / clip.w) * 0.5f * h;
		return new float[]{sx, sy, clip.w};
	}

	/**
	 * The block column under a screen point: a ray from the camera walked until it is below the ground, whose height
	 * {@code ground} gives (Integer.MIN_VALUE where nothing is known). Null if it hits nothing known.
	 */
	public double[] pick(double sx, double sy, int x, int y, int w, int h, Ground ground) {
		Matrix4f inverse = projection(w, h, true).mul(view()).invert();
		float nx = (float) ((sx - x) / w * 2 - 1);
		float ny = (float) (1 - (sy - y) / h * 2);
		Vector4f near = inverse.transform(new Vector4f(nx, ny, 1f, 1f));
		Vector4f far = inverse.transform(new Vector4f(nx, ny, 0f, 1f));
		near.div(near.w);
		far.div(far.w);
		double dx = far.x - near.x;
		double dy = far.y - near.y;
		double dz = far.z - near.z;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1e-6) {
			return null;
		}
		dx /= length;
		dy /= length;
		dz /= length;
		double t = 0;
		for (int i = 0; i < 6000 && t < length; i++) {
			double px = targetX + near.x + dx * t;
			double py = targetY + near.y + dy * t;
			double pz = targetZ + near.z + dz * t;
			int height = ground.height(Mth.floor(px), Mth.floor(pz));
			if (height != Integer.MIN_VALUE && py <= height + 1) {
				return new double[]{px, height, pz};
			}
			// Finer steps close by, coarser far away.
			t += Math.max(0.25, t * 0.004);
		}
		return null;
	}

	@FunctionalInterface
	public interface Ground {
		int height(int bx, int bz);
	}
}
