package dev.cobblecomfort.features.zoom;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cobblecomfort.CobbleComfortClient;
import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.features.hud.render.RenderUtil;
import dev.cobblecomfort.mixin.HudInvoker;
import dev.isxander.yacl3.api.ConfigCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Zoom key with scroll-to-zoom, smooth transitions and optional cinematic bars. */
public final class ZoomFeature extends Feature<ZoomConfig> {
	public static final ZoomFeature INSTANCE = new ZoomFeature();
	private static final float ZOOM_LEVEL_FADE = 0.3f;

	private KeyMapping key;
	private boolean toggled;
	private boolean active;
	private float targetZoom = 1f;
	private float currentZoom = 1f;
	private float barProgress;
	private long lastFrameNanos;
	private long zoomLevelShownMs;
	private Boolean savedSmoothCamera;

	private ZoomFeature() {
		super("zoom", ZoomConfig.class, ZoomConfig::new);
	}

	@Override
	public void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.cobblecomfort.zoom", InputConstants.KEY_C, CobbleComfortClient.keyCategory()));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (key.consumeClick()) {
				toggled = !toggled;
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, CobbleComfortClient.id("zoom_overlay"),
				(g, delta) -> drawOverlay(g));
	}

	@Override
	public ConfigCategory buildCategory() {
		return ZoomConfigUi.buildCategory(this);
	}

	public KeyMapping key() {
		return key;
	}

	public boolean isZooming() {
		return active;
	}

	/** True while the zoom transition is still visible, i.e. FOV or bars are not back to normal. */
	private boolean isAnimating() {
		return currentZoom > 1.001f || barProgress > 0f;
	}

	// ---------------------------------------------------------------- per-frame update

	/** Called once per frame from the FOV calculation. Returns the FOV divisor. */
	public float update() {
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min((now - lastFrameNanos) / 1e9f, 0.1f);
		lastFrameNanos = now;

		Minecraft mc = Minecraft.getInstance();
		boolean wanted = isEnabled() && mc.gui.screen() == null && mc.player != null
				&& (config.toggleMode ? toggled : key.isDown());
		if (!config.toggleMode || !isEnabled()) {
			toggled = false;
		}
		if (wanted != active) {
			setActive(wanted);
		}

		float target = active ? targetZoom : 1f;
		if (config.smoothTransition) {
			// Interpolate in log space so zooming in and out feels equally fast at any level.
			float k = 1f - (float) Math.exp(-config.transitionSpeed * dt);
			currentZoom = (float) Math.exp(Math.log(currentZoom) + (Math.log(target) - Math.log(currentZoom)) * k);
			if (Math.abs(currentZoom - target) < 0.001f) {
				currentZoom = target;
			}
		} else {
			currentZoom = target;
		}

		boolean bars = active && config.style == ZoomStyle.CINEMATIC;
		if (config.barDuration <= 0f) {
			barProgress = bars ? 1f : 0f;
		} else {
			float step = dt / config.barDuration;
			barProgress = Math.clamp(barProgress + (bars ? step : -step), 0f, 1f);
		}
		return isEnabled() || isAnimating() ? currentZoom : 1f;
	}

	private void setActive(boolean value) {
		active = value;
		Minecraft mc = Minecraft.getInstance();
		if (value) {
			if (config.resetOnRelease || targetZoom <= 1f) {
				targetZoom = clampZoom(config.zoomFactor);
			}
			if (config.smoothCamera) {
				savedSmoothCamera = mc.options.smoothCamera;
				mc.options.smoothCamera = true;
			}
		} else if (savedSmoothCamera != null) {
			mc.options.smoothCamera = savedSmoothCamera;
			savedSmoothCamera = null;
		}
	}

	private float clampZoom(float zoom) {
		float min = Math.max(1.01f, Math.min(config.minZoom, config.maxZoom));
		return Math.clamp(zoom, min, Math.max(min, config.maxZoom));
	}

	// ---------------------------------------------------------------- input hooks

	/** Mouse scroll while zooming changes the zoom level. Returns true if the scroll was used. */
	public boolean onScroll(double amount) {
		if (!active || !config.scrollZoom || amount == 0) {
			return false;
		}
		// One step per wheel notch; fractional amounts (touchpads) scale smoothly.
		targetZoom = clampZoom(targetZoom * (float) Math.pow(1f + config.scrollStep, amount));
		zoomLevelShownMs = System.currentTimeMillis();
		return true;
	}

	/** Multiplier for mouse sensitivity, so aiming stays precise at high zoom. */
	public double sensitivityMultiplier() {
		if (!isEnabled() || !config.adjustSensitivity || currentZoom <= 1f) {
			return 1.0;
		}
		return Math.pow(1.0 / currentZoom, config.sensitivityStrength);
	}

	public boolean hideHand() {
		return isEnabled() && config.hideHand && active;
	}

	public boolean hideHud() {
		return isEnabled() && config.hideHud && active;
	}

	// ---------------------------------------------------------------- overlay

	/** Bars, spyglass mask and zoom level. Also called directly when the HUD is hidden while zooming. */
	public void drawOverlay(GuiGraphicsExtractor g) {
		if (!isEnabled() && !isAnimating()) {
			return;
		}
		int width = g.guiWidth();
		int height = g.guiHeight();

		if (config.spyglassOverlay && active) {
			((HudInvoker) Minecraft.getInstance().gui.hud).cobblecomfort$extractSpyglassOverlay(g, 1f);
		}

		if (barProgress > 0f) {
			int bar = Math.round(height * config.barHeight * config.barEasing.apply(barProgress));
			g.fill(0, 0, width, bar, config.barColor);
			g.fill(0, height - bar, width, height, config.barColor);
		}

		if (config.showZoomLevel && active && zoomLevelShownMs > 0) {
			float age = (System.currentTimeMillis() - zoomLevelShownMs) / 1000f;
			float alpha = Math.clamp((config.zoomLevelDuration - age) / ZOOM_LEVEL_FADE, 0f, 1f);
			if (alpha > 0.02f) {
				Component text = Component.literal(String.format(Locale.ROOT, "%.1f×", targetZoom));
				g.centeredText(Minecraft.getInstance().font, text, width / 2, height / 2 + 12,
						RenderUtil.withAlpha(0xFFFFFFFF, alpha));
			}
		}
	}
}
