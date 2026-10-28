package dev.cobblecomfort.features.toasts;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class ToastsConfigUi {
	private ToastsConfigUi() {
	}

	public static ConfigCategory buildCategory(ToastsFeature feature) {
		ToastsConfig d = new ToastsConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("toasts.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.toasts.toasts"))
						.option(Opts.bool("toasts.smooth", d.smoothToasts, () -> c(feature).smoothToasts, v -> c(feature).smoothToasts = v))
						.option(Opts.bool("toasts.stack", d.stackToasts, () -> c(feature).stackToasts, v -> c(feature).stackToasts = v))
						.option(Opts.slider("toasts.time", d.toastTime, 0.2f, 1.5f, 0.05f, () -> c(feature).toastTime, v -> c(feature).toastTime = v))
						.option(Opts.bool("toasts.hide_recipes", d.hideRecipeToasts,
								() -> c(feature).hideRecipeToasts, v -> c(feature).hideRecipeToasts = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.toasts.tab"))
						.option(Opts.bool("toasts.tab_fade", d.tabFade, () -> c(feature).tabFade, v -> c(feature).tabFade = v))
						.option(Opts.bool("toasts.ping_numbers", d.pingNumbers, () -> c(feature).pingNumbers, v -> c(feature).pingNumbers = v))
						.option(Opts.bool("toasts.tab_sort", d.tabSort, () -> c(feature).tabSort, v -> c(feature).tabSort = v))
						.option(Opts.bool("toasts.head_frames", d.headFrames, () -> c(feature).headFrames, v -> c(feature).headFrames = v))
						.build())
				.build();
	}

	private static ToastsConfig c(ToastsFeature feature) {
		return feature.config();
	}
}
