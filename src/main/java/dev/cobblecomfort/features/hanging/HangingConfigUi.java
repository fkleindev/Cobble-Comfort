package dev.cobblecomfort.features.hanging;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class HangingConfigUi {
	private HangingConfigUi() {
	}

	public static ConfigCategory buildCategory(HangingFeature feature) {
		HangingConfig d = new HangingConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("hanging.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.hanging.what"))
						.option(Opts.bool("hanging.plants", d.plants, () -> c(feature).plants, v -> c(feature).plants = v))
						.option(Opts.bool("hanging.lanterns", d.lanterns, () -> c(feature).lanterns, v -> c(feature).lanterns = v))
						.option(Opts.bool("hanging.bells", d.bells, () -> c(feature).bells, v -> c(feature).bells = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.hanging.who"))
						.option(Opts.bool("hanging.players", d.players, () -> c(feature).players, v -> c(feature).players = v))
						.option(Opts.bool("hanging.mobs", d.mobs, () -> c(feature).mobs, v -> c(feature).mobs = v))
						.option(Opts.bool("hanging.other", d.other, () -> c(feature).other, v -> c(feature).other = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.hanging.feel"))
						.option(Opts.percent("hanging.strength", d.strength, () -> c(feature).strength, v -> c(feature).strength = v))
						.option(Opts.percent("hanging.wind", d.wind, () -> c(feature).wind, v -> c(feature).wind = v))
						.build())
				.build();
	}

	private static HangingConfig c(HangingFeature feature) {
		return feature.config();
	}
}
