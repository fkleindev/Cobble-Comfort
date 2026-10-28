package dev.cobblecomfort.features.food;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class FoodHudConfigUi {
	private FoodHudConfigUi() {
	}

	public static ConfigCategory buildCategory(FoodHudFeature feature) {
		FoodHudConfig d = new FoodHudConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("food_hud.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.food_hud.bar"))
						.option(Opts.bool("food_hud.saturation_overlay", d.saturationOverlay,
								() -> c(feature).saturationOverlay, v -> c(feature).saturationOverlay = v))
						.option(Opts.bool("food_hud.eat_preview", d.eatPreview, () -> c(feature).eatPreview, v -> c(feature).eatPreview = v))
						.option(Opts.bool("food_hud.exhaustion", d.exhaustion, () -> c(feature).exhaustion, v -> c(feature).exhaustion = v))
						.option(Opts.bool("food_hud.estimate_exhaustion", d.estimateExhaustion,
								() -> c(feature).estimateExhaustion, v -> c(feature).estimateExhaustion = v))
						.option(Opts.bool("food_hud.numbers", d.numbers, () -> c(feature).numbers, v -> c(feature).numbers = v))
						.option(Opts.bool("food_hud.hunger_warning", d.hungerWarning,
								() -> c(feature).hungerWarning, v -> c(feature).hungerWarning = v))
						.option(Opts.intSlider("food_hud.warn_below", d.warnBelow, 1, 19, 1, () -> c(feature).warnBelow, v -> c(feature).warnBelow = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.food_hud.extras"))
						.option(Opts.bool("food_hud.heal_preview", d.healPreview, () -> c(feature).healPreview, v -> c(feature).healPreview = v))
						.option(Opts.bool("food_hud.tooltip_values", d.tooltipValues,
								() -> c(feature).tooltipValues, v -> c(feature).tooltipValues = v))
						.option(Opts.bool("food_hud.best_food", d.bestFood, () -> c(feature).bestFood, v -> c(feature).bestFood = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.food_hud.colors"))
						.option(Opts.color("food_hud.exhaustion_color", d.exhaustionColor, true,
								() -> c(feature).exhaustionColor, v -> c(feature).exhaustionColor = v))
						.option(Opts.color("food_hud.warning_color", d.warningColor, false,
								() -> c(feature).warningColor, v -> c(feature).warningColor = v | 0xFF000000))
						.option(Opts.color("food_hud.best_food_color", d.bestFoodColor, false,
								() -> c(feature).bestFoodColor, v -> c(feature).bestFoodColor = v | 0xFF000000))
						.build())
				.build();
	}

	private static FoodHudConfig c(FoodHudFeature feature) {
		return feature.config();
	}
}
