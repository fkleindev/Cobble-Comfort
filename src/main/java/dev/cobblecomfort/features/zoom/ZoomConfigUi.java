package dev.cobblecomfort.features.zoom;

import dev.cobblecomfort.core.config.Opts;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class ZoomConfigUi {
	private ZoomConfigUi() {
	}

	public static ConfigCategory buildCategory(ZoomFeature feature) {
		ZoomConfig d = new ZoomConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("zoom.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.zoom.behaviour"))
						.option(Opts.bool("zoom.toggle_mode", d.toggleMode, () -> c(feature).toggleMode, v -> c(feature).toggleMode = v))
						.option(Opts.slider("zoom.zoom_factor", d.zoomFactor, 1.5f, 20f, 0.5f,
								() -> c(feature).zoomFactor, v -> c(feature).zoomFactor = v))
						.option(Opts.bool("zoom.scroll_zoom", d.scrollZoom, () -> c(feature).scrollZoom, v -> c(feature).scrollZoom = v))
						.option(Opts.percent("zoom.scroll_step", d.scrollStep, () -> c(feature).scrollStep, v -> c(feature).scrollStep = v))
						.option(Opts.slider("zoom.min_zoom", d.minZoom, 1.1f, 10f, 0.1f, () -> c(feature).minZoom, v -> c(feature).minZoom = v))
						.option(Opts.slider("zoom.max_zoom", d.maxZoom, 2f, 100f, 1f, () -> c(feature).maxZoom, v -> c(feature).maxZoom = v))
						.option(Opts.bool("zoom.reset_on_release", d.resetOnRelease,
								() -> c(feature).resetOnRelease, v -> c(feature).resetOnRelease = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.zoom.transition"))
						.option(Opts.bool("zoom.smooth_transition", d.smoothTransition,
								() -> c(feature).smoothTransition, v -> c(feature).smoothTransition = v))
						.option(Opts.slider("zoom.transition_speed", d.transitionSpeed, 1f, 30f, 0.5f,
								() -> c(feature).transitionSpeed, v -> c(feature).transitionSpeed = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.zoom.style"))
						.option(Opts.enumOpt("zoom.style", ZoomStyle.class, d.style, () -> c(feature).style, v -> c(feature).style = v))
						.option(Opts.percent("zoom.bar_height", d.barHeight, () -> c(feature).barHeight, v -> c(feature).barHeight = v))
						.option(Opts.color("zoom.bar_color", d.barColor, true, () -> c(feature).barColor, v -> c(feature).barColor = v))
						.option(Opts.slider("zoom.bar_duration", d.barDuration, 0f, 2f, 0.05f,
								() -> c(feature).barDuration, v -> c(feature).barDuration = v))
						.option(Opts.enumOpt("zoom.bar_easing", Easing.class, d.barEasing,
								() -> c(feature).barEasing, v -> c(feature).barEasing = v))
						.option(Opts.bool("zoom.spyglass_overlay", d.spyglassOverlay,
								() -> c(feature).spyglassOverlay, v -> c(feature).spyglassOverlay = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.zoom.extras"))
						.option(Opts.bool("zoom.adjust_sensitivity", d.adjustSensitivity,
								() -> c(feature).adjustSensitivity, v -> c(feature).adjustSensitivity = v))
						.option(Opts.percent("zoom.sensitivity_strength", d.sensitivityStrength,
								() -> c(feature).sensitivityStrength, v -> c(feature).sensitivityStrength = v))
						.option(Opts.bool("zoom.smooth_camera", d.smoothCamera, () -> c(feature).smoothCamera, v -> c(feature).smoothCamera = v))
						.option(Opts.bool("zoom.hide_hand", d.hideHand, () -> c(feature).hideHand, v -> c(feature).hideHand = v))
						.option(Opts.bool("zoom.hide_hud", d.hideHud, () -> c(feature).hideHud, v -> c(feature).hideHud = v))
						.option(Opts.bool("zoom.show_zoom_level", d.showZoomLevel,
								() -> c(feature).showZoomLevel, v -> c(feature).showZoomLevel = v))
						.option(Opts.slider("zoom.zoom_level_duration", d.zoomLevelDuration, 0.5f, 5f, 0.25f,
								() -> c(feature).zoomLevelDuration, v -> c(feature).zoomLevelDuration = v))
						.build())
				.build();
	}

	private static ZoomConfig c(ZoomFeature feature) {
		return feature.config();
	}
}
