package dev.cobblecomfort.features.hud.render;

import dev.cobblecomfort.features.hud.Anchor;
import dev.cobblecomfort.features.hud.HudCustomizerFeature;
import dev.cobblecomfort.features.hud.HudElementConfig;
import dev.cobblecomfort.features.hud.HudElementDef;
import dev.cobblecomfort.features.hud.HudLayout;
import dev.cobblecomfort.features.hud.Rect;
import dev.cobblecomfort.features.hud.anim.ShowAnimation;
import dev.cobblecomfort.features.hud.editor.HudEditorScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/** Wraps a vanilla HUD layer and applies the element's position, scale, style and animation. */
public final class TransformingHudElement implements HudElement {
	private static final float EDITOR_HIDDEN_ALPHA = 0.35f;
	private static final float PULSE_AMOUNT = 0.08f;

	private final HudElementDef def;
	private final HudElement original;

	public TransformingHudElement(HudElementDef def, HudElement original) {
		this.def = def;
		this.original = original;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		HudCustomizerFeature feature = HudCustomizerFeature.INSTANCE;
		if (!feature.isEnabled() || HudRenderContext.active()) {
			original.extractRenderState(g, deltaTracker);
			return;
		}
		HudElementConfig cfg = feature.config().element(def);
		boolean editor = HudEditorScreen.isOpen();
		if (!cfg.visible && !editor) {
			return;
		}

		ElementState state = feature.state(def);
		boolean firstLayer = state.beginFrame(HudFrame.id());
		if (firstLayer) {
			state.updateVisibility(cfg, editor);
		}
		float vis = state.visibility();
		boolean hidden = vis <= 0.001f;

		float width = g.guiWidth();
		float height = g.guiHeight();
		HudLayout.Placement placement = HudLayout.place(def, cfg, width, height);

		ShowAnimation anim = cfg.animation;
		float alpha = cfg.opacity * (cfg.visible ? 1f : EDITOR_HIDDEN_ALPHA);
		float extraScale = 1f;
		float dx = 0f;
		float dy = 0f;
		if (anim.fade) {
			alpha *= Math.clamp(vis, 0f, 1f);
		}
		if (anim.scale) {
			extraScale *= Math.max(vis, 0f);
		}
		if (anim.slide) {
			Anchor anchor = HudLayout.effectiveAnchor(def, cfg);
			Rect bounds = state.lastBounds() != null ? state.lastBounds() : def.vanillaBounds().get(width, height);
			float hide = 1f - vis;
			if (anchor.fy != 0.5f || anchor.fx == 0.5f) {
				float dir = anchor.fy == 0f ? -1f : 1f;
				dy = dir * hide * (bounds.h() * cfg.scale + 8f);
			} else {
				float dir = anchor.fx == 0f ? -1f : 1f;
				dx = dir * hide * (bounds.w() * cfg.scale + 8f);
			}
		}
		if (cfg.pulseWhenLow && def.supportsPulse() && def.lowCondition().getAsBoolean()) {
			extraScale *= 1f + PULSE_AMOUNT * (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 120.0));
		}

		Matrix3x2fStack pose = g.pose();
		Matrix3x2f before = new Matrix3x2f(pose);
		pose.pushMatrix();
		pose.mul(placement.matrix(extraScale, dx, dy));
		try {
			if (firstLayer && !hidden && cfg.background && state.lastBounds() != null) {
				Rect b = state.lastBounds();
				int pad = cfg.backgroundPadding;
				RenderUtil.roundedFill(g, (int) Math.floor(b.x()) - pad, (int) Math.floor(b.y()) - pad,
						(int) Math.ceil(b.right()) + pad, (int) Math.ceil(b.bottom()) + pad,
						cfg.backgroundRadius, RenderUtil.withAlpha(cfg.backgroundColor, alpha));
			}
			HudRenderContext.begin(state, before, pose, hidden ? 0f : alpha, cfg.tint, cfg.textShadow,
					hidden || (anim.fade && vis < 0.5f));
			original.extractRenderState(g, deltaTracker);
		} finally {
			HudRenderContext.end();
			pose.popMatrix();
		}
	}
}
