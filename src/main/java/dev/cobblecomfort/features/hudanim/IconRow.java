package dev.cobblecomfort.features.hudanim;

import net.minecraft.resources.Identifier;

import java.util.Arrays;

/**
 * One row of HUD icons (hearts, shanks, armour, bubbles) as vanilla draws it: remembers how full each icon was in the
 * last frame, so an icon that fills can pop and one that empties can leave a ghost.
 */
final class IconRow {
	static final int EMPTY = 0;
	static final int HALF = 1;
	static final int FULL = 2;
	/** A row not drawn for this long starts over instead of animating everything that changed meanwhile. */
	private static final long STALE_MS = 500;

	private final boolean hiddenMeansEmpty;
	private int size;
	private int[] last = new int[0];
	private int[] current = new int[0];
	private Identifier[] lastSprite = new Identifier[0];
	private Identifier[] currentSprite = new Identifier[0];
	private int[] x = new int[0];
	private int[] y = new int[0];
	private long[] gained = new long[0];
	private long[] lost = new long[0];
	private Identifier[] ghost = new Identifier[0];
	private long lastFrame;
	private boolean valid;

	/** @param hiddenMeansEmpty whether a frame without any icon means all are empty (armour) or just hidden (air) */
	IconRow(boolean hiddenMeansEmpty) {
		this.hiddenMeansEmpty = hiddenMeansEmpty;
	}

	void begin(int icons) {
		if (icons > last.length) {
			int n = Math.max(icons, last.length * 2);
			last = grow(last, n, EMPTY);
			current = grow(current, n, -1);
			x = grow(x, n, 0);
			y = grow(y, n, 0);
			gained = Arrays.copyOf(gained, n);
			lost = Arrays.copyOf(lost, n);
			lastSprite = Arrays.copyOf(lastSprite, n);
			currentSprite = Arrays.copyOf(currentSprite, n);
			ghost = Arrays.copyOf(ghost, n);
		}
		size = icons;
		Arrays.fill(current, -1);
		Arrays.fill(currentSprite, null);
	}

	private static int[] grow(int[] array, int n, int fill) {
		int old = array.length;
		int[] grown = Arrays.copyOf(array, n);
		Arrays.fill(grown, old, n, fill);
		return grown;
	}

	boolean contains(int index) {
		return index >= 0 && index < size;
	}

	/** Vanilla drew {@code sprite} for icon {@code index}; the icon's level is the fullest sprite drawn for it. */
	void blit(int index, Identifier sprite, int level, int px, int py) {
		if (!contains(index)) {
			return;
		}
		if (current[index] < 0) {
			x[index] = px;
			y[index] = py;
		}
		if (level > current[index]) {
			current[index] = level;
			if (level > EMPTY) {
				currentSprite[index] = sprite;
			}
		}
	}

	/** Compares the frame with the last one and starts the pops and ghosts. */
	void end(long now) {
		boolean any = false;
		for (int i = 0; i < size; i++) {
			any |= current[i] >= 0;
		}
		if (!any && !hiddenMeansEmpty) {
			return;
		}
		boolean animate = valid && now - lastFrame < STALE_MS;
		for (int i = 0; i < size; i++) {
			int level = Math.max(0, current[i]);
			if (animate && level > last[i]) {
				gained[i] = now;
				lost[i] = 0;
			} else if (animate && level < last[i] && lastSprite[i] != null) {
				lost[i] = now;
				ghost[i] = lastSprite[i];
			}
			last[i] = level;
			lastSprite[i] = level > EMPTY ? currentSprite[i] : null;
		}
		for (int i = size; i < last.length; i++) {
			last[i] = EMPTY;
			lastSprite[i] = null;
		}
		lastFrame = now;
		valid = true;
	}

	void reset() {
		valid = false;
	}

	/** 0 to 1 through the pop of a filled icon, or -1 when it is not popping. */
	float gainProgress(int index, long now, float seconds) {
		return progress(contains(index) ? gained[index] : 0, now, seconds);
	}

	/** 0 to 1 through the ghost of an emptied icon, or -1. */
	float lossProgress(int index, long now, float seconds) {
		return progress(contains(index) ? lost[index] : 0, now, seconds);
	}

	private static float progress(long start, long now, float seconds) {
		if (start == 0) {
			return -1f;
		}
		float t = (now - start) / (seconds * 1000f);
		return t >= 1f ? -1f : Math.max(0f, t);
	}

	int size() {
		return size;
	}

	int x(int index) {
		return x[index];
	}

	int y(int index) {
		return y[index];
	}

	Identifier ghost(int index) {
		return ghost[index];
	}
}
