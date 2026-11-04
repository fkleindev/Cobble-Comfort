package dev.cobblecomfort.features.footprints;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class FootprintsConfigUi {
	private FootprintsConfigUi() {
	}

	public static ConfigCategory buildCategory(FootprintsFeature feature) {
		FootprintsConfig d = new FootprintsConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("footprints.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.footprints.ground"))
						.option(Opts.bool("footprints.snow", d.snow, () -> c(feature).snow, v -> c(feature).snow = v))
						.option(Opts.bool("footprints.sand", d.sand, () -> c(feature).sand, v -> c(feature).sand = v))
						.option(Opts.bool("footprints.mud", d.mud, () -> c(feature).mud, v -> c(feature).mud = v))
						.option(Opts.bool("footprints.wet", d.wet, () -> c(feature).wet, v -> c(feature).wet = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.footprints.who"))
						.option(Opts.bool("footprints.players", d.players, () -> c(feature).players, v -> c(feature).players = v))
						.option(Opts.bool("footprints.mobs", d.mobs, () -> c(feature).mobs, v -> c(feature).mobs = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.footprints.look"))
						.option(Opts.intSlider("footprints.duration", d.duration, 5, 120, 5,
								() -> c(feature).duration, v -> c(feature).duration = v))
						.option(Opts.percent("footprints.opacity", d.opacity, () -> c(feature).opacity, v -> c(feature).opacity = v))
						.option(Opts.bool("footprints.weather", d.weather, () -> c(feature).weather, v -> c(feature).weather = v))
						.build())
				.build();
	}

	private static FootprintsConfig c(FootprintsFeature feature) {
		return feature.config();
	}
}
