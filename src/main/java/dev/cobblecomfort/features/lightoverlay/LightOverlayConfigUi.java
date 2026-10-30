package dev.cobblecomfort.features.lightoverlay;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class LightOverlayConfigUi {
	private LightOverlayConfigUi() {
	}

	public static ConfigCategory buildCategory(LightOverlayFeature feature) {
		LightOverlayConfig d = new LightOverlayConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("light_overlay.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.option(Opts.bool("light_overlay.active", d.active, () -> c(feature).active, v -> c(feature).active = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.light_overlay.display"))
						.option(Opts.intSlider("light_overlay.range", d.range, 8, 32, 1, () -> c(feature).range, v -> c(feature).range = v))
						.option(Opts.bool("light_overlay.show_night", d.showNight, () -> c(feature).showNight, v -> c(feature).showNight = v))
						.option(Opts.bool("light_overlay.numbers", d.numbers, () -> c(feature).numbers, v -> c(feature).numbers = v))
						.option(Opts.intSlider("light_overlay.number_range", d.numberRange, 2, 16, 1,
								() -> c(feature).numberRange, v -> c(feature).numberRange = v))
						.option(Opts.bool("light_overlay.fade", d.fade, () -> c(feature).fade, v -> c(feature).fade = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.light_overlay.extras"))
						.option(Opts.bool("light_overlay.toggle_message", d.toggleMessage,
								() -> c(feature).toggleMessage, v -> c(feature).toggleMessage = v))
						.option(Opts.bool("light_overlay.remember_state", d.rememberState,
								() -> c(feature).rememberState, v -> c(feature).rememberState = v))
						.build())
				.build();
	}

	private static LightOverlayConfig c(LightOverlayFeature feature) {
		return feature.config();
	}
}
