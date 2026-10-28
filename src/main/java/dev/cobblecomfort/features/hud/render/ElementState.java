package dev.cobblecomfort.features.hud.render;

import dev.cobblecomfort.features.hud.HudElementConfig;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.anim.ShowAnimation;

/**
 * Runtime state of one element: what it drew last frame (bounds and a content hash for change
 * detection) and the progress of its show/hide animation.
 */
public final class ElementState {
	private int frame = -1;

	private float minX, minY, maxX, maxY;
	private boolean drewAnything;
	private long hash;

	private Rect lastBounds;
	private long lastHash;
	private long lastChangeMs;

	private float showProgress = 1f;
	private long lastAnimMs;
	private float eased = 1f;

	/** Rolls over to a new frame if needed. Returns true for the first layer of the element in this frame. */
	public boolean beginFrame(int frameId) {
		if (frame == frameId) {
			return false;
		}
		if (frame != -1) {
			lastBounds = drewAnything ? new Rect(minX, minY, maxX - minX, maxY - minY) : null;
			if (hash != lastHash) {
				lastChangeMs = System.currentTimeMillis();
				lastHash = hash;
			}
		}
		frame = frameId;
		drewAnything = false;
		hash = 17;
		minX = minY = Float.MAX_VALUE;
		maxX = maxY = -Float.MAX_VALUE;
		return true;
	}

	public void extend(float x0, float y0, float x1, float y1) {
		minX = Math.min(minX, Math.min(x0, x1));
		minY = Math.min(minY, Math.min(y0, y1));
		maxX = Math.max(maxX, Math.max(x0, x1));
		maxY = Math.max(maxY, Math.max(y0, y1));
		drewAnything = true;
	}

	public void mix(long value) {
		hash = hash * 31 + value;
	}

	/** Bounds drawn last frame in pre-transform space, or null if the element drew nothing. */
	public Rect lastBounds() {
		return lastBounds;
	}

	/** Advances the show/hide animation once per frame and returns its eased progress (0 hidden, 1 shown). */
	public float updateVisibility(HudElementConfig cfg, boolean forceShow) {
		long now = System.currentTimeMillis();
		if (lastChangeMs == 0) {
			lastChangeMs = now;
		}
		boolean target = forceShow || !cfg.autoHide || now - lastChangeMs < (long) (cfg.autoHideDelay * 1000);
		if (cfg.animation == ShowAnimation.NONE || cfg.animationDuration <= 0 || lastAnimMs == 0) {
			showProgress = target ? 1f : 0f;
		} else {
			float step = (now - lastAnimMs) / (cfg.animationDuration * 1000f);
			showProgress = Math.clamp(showProgress + (target ? step : -step), 0f, 1f);
		}
		lastAnimMs = now;
		eased = showProgress <= 0f ? 0f : showProgress >= 1f ? 1f : cfg.easing.apply(showProgress);
		return eased;
	}

	public float visibility() {
		return eased;
	}

	public void resetAnimation() {
		lastChangeMs = System.currentTimeMillis();
		showProgress = 1f;
		eased = 1f;
	}
}
