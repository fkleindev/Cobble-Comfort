package dev.cobblecomfort.features.hud;

import org.joml.Matrix3x2f;

/**
 * Position math. An element is drawn by vanilla in "local" space (its vanilla location). We map it to
 * the screen with: screen = T + scale * (local - P), where P is the pivot (the anchor point of the
 * element's vanilla bounds) and T is the target position of that pivot.
 */
public final class HudLayout {
	private HudLayout() {
	}

	public record Placement(float tx, float ty, float scale, float px, float py) {
		public float screenX(float localX) {
			return tx + scale * (localX - px);
		}

		public float screenY(float localY) {
			return ty + scale * (localY - py);
		}

		public Rect toScreen(Rect local) {
			return new Rect(screenX(local.x()), screenY(local.y()), local.w() * scale, local.h() * scale);
		}

		public Matrix3x2f matrix(float extraScale, float dx, float dy) {
			float s = scale * extraScale;
			return new Matrix3x2f().translate(tx + dx, ty + dy).scale(s).translate(-px, -py);
		}
	}

	public static Anchor effectiveAnchor(HudElementDef def, HudElementConfig cfg) {
		return cfg.customPosition ? cfg.anchor : def.naturalAnchor();
	}

	public static Placement place(HudElementDef def, HudElementConfig cfg, float width, float height) {
		Rect vanilla = def.vanillaBounds().get(width, height);
		Anchor anchor = effectiveAnchor(def, cfg);
		float px = vanilla.pointX(anchor);
		float py = vanilla.pointY(anchor);
		if (!cfg.customPosition) {
			return new Placement(px, py, cfg.scale, px, py);
		}
		return new Placement(width * anchor.fx + cfg.offsetX, height * anchor.fy + cfg.offsetY, cfg.scale, px, py);
	}

	/** Switches the element to a custom position without moving it. */
	public static void makeCustom(HudElementDef def, HudElementConfig cfg, float width, float height) {
		if (cfg.customPosition) {
			return;
		}
		Placement p = place(def, cfg, width, height);
		cfg.customPosition = true;
		cfg.anchor = def.naturalAnchor();
		cfg.offsetX = p.tx() - width * cfg.anchor.fx;
		cfg.offsetY = p.ty() - height * cfg.anchor.fy;
	}

	/** Places the pivot at the given screen position (relative to the current anchor). */
	public static void moveTo(HudElementConfig cfg, float tx, float ty, float width, float height) {
		cfg.offsetX = tx - width * cfg.anchor.fx;
		cfg.offsetY = ty - height * cfg.anchor.fy;
	}

	/** Changes the anchor while keeping the element exactly where it is on screen. */
	public static void reanchor(HudElementDef def, HudElementConfig cfg, Anchor anchor, float width, float height) {
		makeCustom(def, cfg, width, height);
		Placement p = place(def, cfg, width, height);
		Rect vanilla = def.vanillaBounds().get(width, height);
		float tx = p.tx() + p.scale() * (vanilla.pointX(anchor) - p.px());
		float ty = p.ty() + p.scale() * (vanilla.pointY(anchor) - p.py());
		cfg.anchor = anchor;
		moveTo(cfg, tx, ty, width, height);
	}
}
