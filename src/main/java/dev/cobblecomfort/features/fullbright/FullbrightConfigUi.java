package dev.cobblecomfort.features.fullbright;

import dev.cobblecomfort.core.config.Opts;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class FullbrightConfigUi {
	private FullbrightConfigUi() {
	}

	public static ConfigCategory buildCategory(FullbrightFeature feature) {
		FullbrightConfig d = new FullbrightConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("fullbright.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.option(Opts.bool("fullbright.active", d.active, () -> c(feature).active, v -> c(feature).active = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.fullbright.brightness"))
						.option(Opts.enumOpt("fullbright.method", FullbrightMethod.class, d.method,
								() -> c(feature).method, v -> c(feature).method = v))
						.option(Opts.percent("fullbright.strength", d.strength, () -> c(feature).strength, v -> c(feature).strength = v))
						.option(Opts.bool("fullbright.smooth_fade", d.smoothFade, () -> c(feature).smoothFade, v -> c(feature).smoothFade = v))
						.option(Opts.slider("fullbright.fade_duration", d.fadeDuration, 0.05f, 3f, 0.05f,
								() -> c(feature).fadeDuration, v -> c(feature).fadeDuration = v))
						.option(Opts.enumOpt("fullbright.easing", Easing.class, d.easing, () -> c(feature).easing, v -> c(feature).easing = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.fullbright.extras"))
						.option(Opts.bool("fullbright.show_hud", d.showHud, () -> c(feature).showHud, v -> c(feature).showHud = v))
						.option(Opts.bool("fullbright.toggle_message", d.toggleMessage,
								() -> c(feature).toggleMessage, v -> c(feature).toggleMessage = v))
						.option(Opts.bool("fullbright.remember_state", d.rememberState,
								() -> c(feature).rememberState, v -> c(feature).rememberState = v))
						.build())
				.build();
	}

	private static FullbrightConfig c(FullbrightFeature feature) {
		return feature.config();
	}
}
