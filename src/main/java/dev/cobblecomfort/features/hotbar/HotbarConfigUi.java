package dev.cobblecomfort.features.hotbar;

import dev.cobblecomfort.core.config.Opts;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class HotbarConfigUi {
	private HotbarConfigUi() {
	}

	public static ConfigCategory buildCategory(HotbarFeature feature) {
		HotbarConfig d = new HotbarConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("hotbar.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.hotbar.selected"))
						.option(Opts.bool("hotbar.scale_selected", d.scaleSelected,
								() -> c(feature).scaleSelected, v -> c(feature).scaleSelected = v))
						.option(Opts.slider("hotbar.selected_scale", d.selectedScale, 1f, 1.6f, 0.05f,
								() -> c(feature).selectedScale, v -> c(feature).selectedScale = v))
						.option(Opts.slider("hotbar.grow_duration", d.growDuration, 0.05f, 1f, 0.05f,
								() -> c(feature).growDuration, v -> c(feature).growDuration = v))
						.option(Opts.percent("hotbar.bounce", d.bounce, () -> c(feature).bounce, v -> c(feature).bounce = v))
						.option(Opts.bool("hotbar.lift", d.lift, () -> c(feature).lift, v -> c(feature).lift = v))
						.option(Opts.intSlider("hotbar.lift_pixels", d.liftPixels, 1, 6, 1,
								() -> c(feature).liftPixels, v -> c(feature).liftPixels = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.hotbar.selection"))
						.option(Opts.bool("hotbar.smooth_selection", d.smoothSelection,
								() -> c(feature).smoothSelection, v -> c(feature).smoothSelection = v))
						.option(Opts.slider("hotbar.slide_duration", d.slideDuration, 0.05f, 0.6f, 0.05f,
								() -> c(feature).slideDuration, v -> c(feature).slideDuration = v))
						.option(Opts.enumOpt("hotbar.slide_easing", Easing.class, d.slideEasing,
								() -> c(feature).slideEasing, v -> c(feature).slideEasing = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.hotbar.extras"))
						.option(Opts.enumOpt("hotbar.slot_numbers", SlotNumberMode.class, d.slotNumbers,
								() -> c(feature).slotNumbers, v -> c(feature).slotNumbers = v))
						.option(Opts.slider("hotbar.number_hold", d.numberHoldSeconds, 0.25f, 5f, 0.25f,
								() -> c(feature).numberHoldSeconds, v -> c(feature).numberHoldSeconds = v))
						.option(Opts.slider("hotbar.number_scale", d.numberScale, 0.5f, 1f, 0.05f,
								() -> c(feature).numberScale, v -> c(feature).numberScale = v))
						.option(Opts.color("hotbar.number_color", d.numberColor, false,
								() -> c(feature).numberColor, v -> c(feature).numberColor = v | 0xFF000000))
						.option(Opts.bool("hotbar.dim_others", d.dimOthers, () -> c(feature).dimOthers, v -> c(feature).dimOthers = v))
						.option(Opts.percent("hotbar.dim_amount", d.dimAmount, () -> c(feature).dimAmount, v -> c(feature).dimAmount = v))
						.option(Opts.bool("hotbar.switch_particles", d.switchParticles,
								() -> c(feature).switchParticles, v -> c(feature).switchParticles = v))
						.option(Opts.intSlider("hotbar.particle_count", d.particleCount, 2, 16, 1,
								() -> c(feature).particleCount, v -> c(feature).particleCount = v))
						.build())
				.build();
	}

	private static HotbarConfig c(HotbarFeature feature) {
		return feature.config();
	}
}
