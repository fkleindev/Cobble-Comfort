package dev.cobblecomfort.features.trade;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class TradeConfigUi {
	private TradeConfigUi() {
	}

	public static ConfigCategory buildCategory(TradeFeature feature) {
		TradeConfig d = new TradeConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("trade.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.trade.remember"))
						.option(Opts.bool("trade.wandering_traders", d.wanderingTraders,
								() -> c(feature).wanderingTraders, v -> c(feature).wanderingTraders = v))
						.option(Opts.bool("trade.forget_gone", d.forgetGone, () -> c(feature).forgetGone, v -> c(feature).forgetGone = v))
						.option(Opts.bool("trade.naming", d.naming, () -> c(feature).naming, v -> c(feature).naming = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.trade.hints"))
						.option(Opts.bool("trade.tooltip_hint", d.tooltipHint, () -> c(feature).tooltipHint, v -> c(feature).tooltipHint = v))
						.option(Opts.intSlider("trade.tooltip_lines", d.tooltipLines, 1, 8, 1,
								() -> c(feature).tooltipLines, v -> c(feature).tooltipLines = v))
						.option(Opts.bool("trade.map_markers", d.mapMarkers, () -> c(feature).mapMarkers, v -> c(feature).mapMarkers = v))
						.build())
				.build();
	}

	private static TradeConfig c(TradeFeature feature) {
		return feature.config();
	}
}
