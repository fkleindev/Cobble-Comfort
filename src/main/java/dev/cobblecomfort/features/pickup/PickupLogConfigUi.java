package dev.cobblecomfort.features.pickup;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class PickupLogConfigUi {
	private PickupLogConfigUi() {
	}

	public static ConfigCategory buildCategory(PickupLogFeature feature) {
		PickupLogConfig d = new PickupLogConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("pickup_log.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.pickup_log.list"))
						.option(Opts.intSlider("pickup_log.max_entries", d.maxEntries, 1, 12, 1,
								() -> c(feature).maxEntries, v -> c(feature).maxEntries = v))
						.option(Opts.slider("pickup_log.display_seconds", d.displaySeconds, 1f, 15f, 0.5f,
								() -> c(feature).displaySeconds, v -> c(feature).displaySeconds = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.pickup_log.style"))
						.option(Opts.bool("pickup_log.show_icon", d.showIcon, () -> c(feature).showIcon, v -> c(feature).showIcon = v))
						.option(Opts.bool("pickup_log.show_total", d.showTotal, () -> c(feature).showTotal, v -> c(feature).showTotal = v))
						.option(Opts.bool("pickup_log.rarity_colors", d.rarityColors,
								() -> c(feature).rarityColors, v -> c(feature).rarityColors = v))
						.option(Opts.bool("pickup_log.animations", d.animations,
								() -> c(feature).animations, v -> c(feature).animations = v))
						.option(Opts.bool("pickup_log.background", d.background,
								() -> c(feature).background, v -> c(feature).background = v))
						.option(Opts.percent("pickup_log.background_opacity", d.backgroundOpacity,
								() -> c(feature).backgroundOpacity, v -> c(feature).backgroundOpacity = v))
						.build())
				.build();
	}

	private static PickupLogConfig c(PickupLogFeature feature) {
		return feature.config();
	}
}
