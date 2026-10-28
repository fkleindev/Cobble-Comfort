package dev.cobblecomfort.features.enchanting;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class EnchantingConfigUi {
	private EnchantingConfigUi() {
	}

	public static ConfigCategory buildCategory(EnchantingFeature feature) {
		EnchantingConfig d = new EnchantingConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("enchanting.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.enchanting.shelves"))
						.option(Opts.bool("enchanting.shelf_counter", d.shelfCounter,
								() -> c(feature).shelfCounter, v -> c(feature).shelfCounter = v))
						.option(Opts.bool("enchanting.max_level_info", d.maxLevelInfo,
								() -> c(feature).maxLevelInfo, v -> c(feature).maxLevelInfo = v))
						.option(Opts.bool("enchanting.world_markers", d.worldMarkers,
								() -> c(feature).worldMarkers, v -> c(feature).worldMarkers = v))
						.option(Opts.bool("enchanting.mark_valid", d.markValid, () -> c(feature).markValid, v -> c(feature).markValid = v))
						.option(Opts.bool("enchanting.mark_blocked", d.markBlocked,
								() -> c(feature).markBlocked, v -> c(feature).markBlocked = v))
						.option(Opts.bool("enchanting.mark_empty", d.markEmpty, () -> c(feature).markEmpty, v -> c(feature).markEmpty = v))
						.option(Opts.color("enchanting.valid_color", d.validColor, false,
								() -> c(feature).validColor, v -> c(feature).validColor = v | 0xFF000000))
						.option(Opts.color("enchanting.blocked_color", d.blockedColor, false,
								() -> c(feature).blockedColor, v -> c(feature).blockedColor = v | 0xFF000000))
						.option(Opts.color("enchanting.empty_color", d.emptyColor, false,
								() -> c(feature).emptyColor, v -> c(feature).emptyColor = v | 0xFF000000))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.enchanting.odds"))
						.option(Opts.bool("enchanting.odds_panel", d.oddsPanel, () -> c(feature).oddsPanel, v -> c(feature).oddsPanel = v))
						.option(Opts.bool("enchanting.panel_open", d.panelOpen, () -> c(feature).panelOpen, v -> c(feature).panelOpen = v))
						.option(Opts.bool("enchanting.slot_odds", d.slotOdds, () -> c(feature).slotOdds, v -> c(feature).slotOdds = v))
						.option(Opts.intSlider("enchanting.max_extras", d.maxExtras, 0, 8, 1,
								() -> c(feature).maxExtras, v -> c(feature).maxExtras = v))
						.option(Opts.intSlider("enchanting.samples", d.samples, 500, 10000, 500,
								() -> c(feature).samples, v -> c(feature).samples = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.enchanting.descriptions"))
						.option(Opts.bool("enchanting.slot_descriptions", d.slotDescriptions,
								() -> c(feature).slotDescriptions, v -> c(feature).slotDescriptions = v))
						.option(Opts.bool("enchanting.panel_descriptions", d.panelDescriptions,
								() -> c(feature).panelDescriptions, v -> c(feature).panelDescriptions = v))
						.option(Opts.bool("enchanting.readable_runes", d.readableRunes,
								() -> c(feature).readableRunes, v -> c(feature).readableRunes = v))
						.build())
				.build();
	}

	private static EnchantingConfig c(EnchantingFeature feature) {
		return feature.config();
	}
}
