package dev.cobblecomfort.features.tracker;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class TrackerConfigUi {
	private TrackerConfigUi() {
	}

	public static ConfigCategory buildCategory(TrackerFeature feature) {
		TrackerConfig d = new TrackerConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("tracker.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.option(ButtonOption.createBuilder()
						.name(Opts.name("tracker.open"))
						.description(Opts.desc("tracker.open"))
						.available(feature.hasWorld())
						.action(screen -> Minecraft.getInstance().gui.setScreen(new TrackerScreen(feature, Minecraft.getInstance().gui.screen())))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.tracker.counting"))
						.option(Opts.bool("tracker.count_shulkers", d.countShulkers,
								() -> c(feature).countShulkers, v -> c(feature).countShulkers = v))
						.option(Opts.bool("tracker.count_bundles", d.countBundles,
								() -> c(feature).countBundles, v -> c(feature).countBundles = v))
						.option(Opts.bool("tracker.nearby_chests", d.nearbyChests,
								() -> c(feature).nearbyChests, v -> c(feature).nearbyChests = v))
						.option(Opts.intSlider("tracker.nearby_radius", d.nearbyRadius, 4, 128, 4,
								() -> c(feature).nearbyRadius, v -> c(feature).nearbyRadius = v))
						.option(Opts.bool("tracker.nearby_counts_for_goal", d.nearbyCountsForGoal,
								() -> c(feature).nearbyCountsForGoal, v -> c(feature).nearbyCountsForGoal = v))
						.option(Opts.intSlider("tracker.default_quantity", d.defaultQuantity, 1, 1024, 1,
								() -> c(feature).defaultQuantity, v -> c(feature).defaultQuantity = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.tracker.hud"))
						.option(Opts.bool("tracker.hide_when_empty", d.hideWhenEmpty,
								() -> c(feature).hideWhenEmpty, v -> c(feature).hideWhenEmpty = v))
						.option(Opts.bool("tracker.show_title", d.showTitle, () -> c(feature).showTitle, v -> c(feature).showTitle = v))
						.option(Opts.bool("tracker.show_names", d.showNames, () -> c(feature).showNames, v -> c(feature).showNames = v))
						.option(Opts.bool("tracker.show_bar", d.showBar, () -> c(feature).showBar, v -> c(feature).showBar = v))
						.option(Opts.intSlider("tracker.max_rows", d.maxRows, 1, 20, 1, () -> c(feature).maxRows, v -> c(feature).maxRows = v))
						.option(Opts.color("tracker.text_color", d.textColor, false,
								() -> c(feature).textColor, v -> c(feature).textColor = v | 0xFF000000))
						.option(Opts.color("tracker.nearby_color", d.nearbyColor, false,
								() -> c(feature).nearbyColor, v -> c(feature).nearbyColor = v | 0xFF000000))
						.option(Opts.color("tracker.bar_color", d.barColor, false,
								() -> c(feature).barColor, v -> c(feature).barColor = v | 0xFF000000))
						.option(Opts.color("tracker.done_color", d.doneColor, false,
								() -> c(feature).doneColor, v -> c(feature).doneColor = v | 0xFF000000))
						.option(Opts.bool("tracker.background", d.background, () -> c(feature).background, v -> c(feature).background = v))
						.option(Opts.color("tracker.background_color", d.backgroundColor, true,
								() -> c(feature).backgroundColor, v -> c(feature).backgroundColor = v))
						.option(Opts.intSlider("tracker.corner_radius", d.cornerRadius, 0, 10, 1,
								() -> c(feature).cornerRadius, v -> c(feature).cornerRadius = v))
						.option(Opts.intSlider("tracker.padding", d.padding, 0, 12, 1, () -> c(feature).padding, v -> c(feature).padding = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.tracker.animation"))
						.option(Opts.bool("tracker.smooth_count", d.smoothCount, () -> c(feature).smoothCount, v -> c(feature).smoothCount = v))
						.option(Opts.bool("tracker.bump_on_change", d.bumpOnChange,
								() -> c(feature).bumpOnChange, v -> c(feature).bumpOnChange = v))
						.option(Opts.bool("tracker.slide_in", d.slideIn, () -> c(feature).slideIn, v -> c(feature).slideIn = v))
						.option(Opts.bool("tracker.completion_shine", d.completionShine,
								() -> c(feature).completionShine, v -> c(feature).completionShine = v))
						.option(Opts.bool("tracker.completion_confetti", d.completionConfetti,
								() -> c(feature).completionConfetti, v -> c(feature).completionConfetti = v))
						.option(Opts.bool("tracker.toast", d.toast, () -> c(feature).toast, v -> c(feature).toast = v))
						.build())
				.build();
	}

	private static TrackerConfig c(TrackerFeature feature) {
		return feature.config();
	}
}
