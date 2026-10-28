package dev.cobblecomfort.features.map.data;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the player has walked recently, per dimension, for the trail on the map and minimap. Kept in memory only;
 * a jump (teleport, respawn) starts a new piece of the trail instead of drawing a line across the map.
 */
public final class MovementTrail {
	private static final int MAX_POINTS = 3000;
	/** Blocks the player has to move before the next point is recorded. */
	private static final double MIN_STEP = 1.5;
	/** A move this far in one tick is a teleport. */
	private static final double JUMP = 32;

	/** A recorded position; {@code start} marks the first point of a new piece. */
	public record Point(double x, double z, boolean start) {
	}

	private final Map<String, ArrayDeque<Point>> trails = new HashMap<>();
	private String lastDim;
	private double lastX = Double.NaN, lastZ;

	public void tick(String dimension, double x, double z) {
		ArrayDeque<Point> trail = trails.computeIfAbsent(dimension, d -> new ArrayDeque<>());
		boolean jumped = !dimension.equals(lastDim) || Double.isNaN(lastX) || distanceSqr(lastX, lastZ, x, z) > JUMP * JUMP;
		lastDim = dimension;
		lastX = x;
		lastZ = z;
		Point last = trail.peekLast();
		if (!jumped && last != null && distanceSqr(last.x, last.z, x, z) < MIN_STEP * MIN_STEP) {
			return;
		}
		trail.addLast(new Point(x, z, jumped || last == null));
		while (trail.size() > MAX_POINTS) {
			trail.removeFirst();
		}
	}

	/** The trail in a dimension, oldest point first. */
	public Collection<Point> points(String dimension) {
		ArrayDeque<Point> trail = trails.get(dimension);
		return trail == null ? List.of() : trail;
	}

	public void clear() {
		trails.clear();
		lastDim = null;
		lastX = Double.NaN;
	}

	private static double distanceSqr(double x1, double z1, double x2, double z2) {
		double dx = x2 - x1;
		double dz = z2 - z1;
		return dx * dx + dz * dz;
	}
}
