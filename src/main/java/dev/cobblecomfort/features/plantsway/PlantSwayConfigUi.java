package dev.cobblecomfort.features.plantsway;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class PlantSwayConfigUi {
	private PlantSwayConfigUi() {
	}

	public static ConfigCategory buildCategory(PlantSwayFeature feature) {
		PlantSwayConfig d = new PlantSwayConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("plant_sway.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.plant_sway.plants"))
						.option(Opts.bool("plant_sway.grass", d.grass, () -> c(feature).grass, v -> c(feature).grass = v))
						.option(Opts.bool("plant_sway.flowers", d.flowers, () -> c(feature).flowers, v -> c(feature).flowers = v))
						.option(Opts.bool("plant_sway.crops", d.crops, () -> c(feature).crops, v -> c(feature).crops = v))
						.option(Opts.bool("plant_sway.pots", d.pots, () -> c(feature).pots, v -> c(feature).pots = v))
						.option(Opts.bool("plant_sway.lily_pads", d.lilyPads, () -> c(feature).lilyPads, v -> c(feature).lilyPads = v))
						.option(Opts.bool("plant_sway.leaves", d.leaves, () -> c(feature).leaves, v -> c(feature).leaves = v))
						.option(Opts.bool("plant_sway.falling_leaves", d.fallingLeaves, () -> c(feature).fallingLeaves, v -> c(feature).fallingLeaves = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.plant_sway.who"))
						.option(Opts.bool("plant_sway.players", d.players, () -> c(feature).players, v -> c(feature).players = v))
						.option(Opts.bool("plant_sway.mobs", d.mobs, () -> c(feature).mobs, v -> c(feature).mobs = v))
						.option(Opts.bool("plant_sway.other", d.other, () -> c(feature).other, v -> c(feature).other = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.plant_sway.feel"))
						.option(Opts.percent("plant_sway.strength", d.strength, () -> c(feature).strength, v -> c(feature).strength = v))
						.option(Opts.percent("plant_sway.swing", d.swing, () -> c(feature).swing, v -> c(feature).swing = v))
						.build())
				.build();
	}

	private static PlantSwayConfig c(PlantSwayFeature feature) {
		return feature.config();
	}
}
