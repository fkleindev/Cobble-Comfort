package dev.cobblecomfort.features.belt;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class BeltConfigUi {
	private BeltConfigUi() {
	}

	public static ConfigCategory buildCategory(BeltFeature feature) {
		BeltConfig d = new BeltConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("belt.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.belt.who"))
						.option(Opts.bool("belt.show_own", d.showOwn, () -> c(feature).showOwn, v -> c(feature).showOwn = v))
						.option(Opts.bool("belt.show_others", d.showOthers, () -> c(feature).showOthers, v -> c(feature).showOthers = v))
						.option(Opts.bool("belt.share", d.share, () -> c(feature).share, v -> c(feature).share = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.belt.what"))
						.option(Opts.bool("belt.bundles", d.bundles, () -> c(feature).bundles, v -> c(feature).bundles = v))
						.option(Opts.bool("belt.melee", d.melee, () -> c(feature).melee, v -> c(feature).melee = v))
						.option(Opts.bool("belt.ranged", d.ranged, () -> c(feature).ranged, v -> c(feature).ranged = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.belt.look"))
						.option(Opts.intSlider("belt.count", d.count, 1, BeltBundle.MAX, 1, () -> c(feature).count, v -> c(feature).count = v))
						.option(Opts.percent("belt.swing", d.swing, () -> c(feature).swing, v -> c(feature).swing = v))
						.build())
				.build();
	}

	private static BeltConfig c(BeltFeature feature) {
		return feature.config();
	}
}
