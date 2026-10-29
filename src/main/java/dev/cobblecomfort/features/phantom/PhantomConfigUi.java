package dev.cobblecomfort.features.phantom;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;

public final class PhantomConfigUi {
	private PhantomConfigUi() {
	}

	public static ConfigCategory buildCategory(PhantomFeature feature) {
		PhantomConfig d = new PhantomConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("phantom.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.option(Opts.bool("phantom.hud", d.hudElement, () -> c(feature).hudElement, v -> c(feature).hudElement = v))
				.option(Opts.bool("phantom.warning", d.warning, () -> c(feature).warning, v -> c(feature).warning = v))
				.build();
	}

	private static PhantomConfig c(PhantomFeature feature) {
		return feature.config();
	}
}
