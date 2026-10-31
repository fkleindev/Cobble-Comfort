package dev.cobblecomfort.features.stuckarrows;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class StuckArrowsConfigUi {
	private StuckArrowsConfigUi() {
	}

	public static ConfigCategory buildCategory(StuckArrowsFeature feature) {
		StuckArrowsConfig d = new StuckArrowsConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("stuck_arrows.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.stuck_arrows.who"))
						.option(Opts.bool("stuck_arrows.own", d.own, () -> c(feature).own, v -> c(feature).own = v))
						.option(Opts.bool("stuck_arrows.others", d.others, () -> c(feature).others, v -> c(feature).others = v))
						.option(Opts.bool("stuck_arrows.stingers", d.stingers, () -> c(feature).stingers, v -> c(feature).stingers = v))
						.build())
				.option(Opts.percent("stuck_arrows.strength", d.strength, () -> c(feature).strength, v -> c(feature).strength = v))
				.build();
	}

	private static StuckArrowsConfig c(StuckArrowsFeature feature) {
		return feature.config();
	}
}
