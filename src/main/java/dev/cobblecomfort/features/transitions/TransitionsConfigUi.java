package dev.cobblecomfort.features.transitions;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;

public final class TransitionsConfigUi {
	private TransitionsConfigUi() {
	}

	public static ConfigCategory buildCategory(TransitionsFeature feature) {
		TransitionsConfig d = new TransitionsConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("transitions.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.option(Opts.bool("transitions.world_fade", d.worldFade, () -> c(feature).worldFade, v -> c(feature).worldFade = v))
				.option(Opts.slider("transitions.world_fade_seconds", d.worldFadeSeconds, 0.2f, 2f, 0.1f,
						() -> c(feature).worldFadeSeconds, v -> c(feature).worldFadeSeconds = v))
				.option(Opts.bool("transitions.death_fade", d.deathFade, () -> c(feature).deathFade, v -> c(feature).deathFade = v))
				.option(Opts.bool("transitions.sleep_fade", d.sleepFade, () -> c(feature).sleepFade, v -> c(feature).sleepFade = v))
				.option(Opts.bool("transitions.title_slide", d.titleSlide, () -> c(feature).titleSlide, v -> c(feature).titleSlide = v))
				.build();
	}

	private static TransitionsConfig c(TransitionsFeature feature) {
		return feature.config();
	}
}
