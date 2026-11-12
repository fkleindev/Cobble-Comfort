package dev.cobblecomfort.features.atmosphere;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class AtmosphereConfigUi {
	private AtmosphereConfigUi() {
	}

	public static ConfigCategory buildCategory(AtmosphereFeature feature) {
		AtmosphereConfig d = new AtmosphereConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("atmosphere.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.atmosphere.breath"))
						.option(Opts.bool("atmosphere.breath", d.breath, () -> c(feature).breath, v -> c(feature).breath = v))
						.option(Opts.bool("atmosphere.breath_mobs", d.breathMobs, () -> c(feature).breathMobs, v -> c(feature).breathMobs = v))
						.option(Opts.bool("atmosphere.own_breath", d.ownBreath, () -> c(feature).ownBreath, v -> c(feature).ownBreath = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.atmosphere.nature"))
						.option(Opts.bool("atmosphere.splashes", d.splashes, () -> c(feature).splashes, v -> c(feature).splashes = v))
						.option(Opts.bool("atmosphere.drips", d.drips, () -> c(feature).drips, v -> c(feature).drips = v))
						.option(Opts.bool("atmosphere.landing_dust", d.landingDust, () -> c(feature).landingDust, v -> c(feature).landingDust = v))
						.option(Opts.bool("atmosphere.fireflies", d.fireflies, () -> c(feature).fireflies, v -> c(feature).fireflies = v))
						.option(Opts.intSlider("atmosphere.firefly_count", d.fireflyCount, 4, 64, 4,
								() -> c(feature).fireflyCount, v -> c(feature).fireflyCount = v))
						.option(Opts.bool("atmosphere.butterflies", d.butterflies, () -> c(feature).butterflies, v -> c(feature).butterflies = v))
						.option(Opts.intSlider("atmosphere.butterfly_count", d.butterflyCount, 2, 32, 2,
								() -> c(feature).butterflyCount, v -> c(feature).butterflyCount = v))
						.option(Opts.bool("atmosphere.dragonflies", d.dragonflies, () -> c(feature).dragonflies, v -> c(feature).dragonflies = v))
						.option(Opts.intSlider("atmosphere.dragonfly_count", d.dragonflyCount, 1, 16, 1,
								() -> c(feature).dragonflyCount, v -> c(feature).dragonflyCount = v))
						.option(Opts.bool("atmosphere.snow_cover", d.snowCover, () -> c(feature).snowCover, v -> c(feature).snowCover = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.atmosphere.birds"))
						.option(Opts.bool("atmosphere.birds", d.birds, () -> c(feature).birds, v -> c(feature).birds = v))
						.option(Opts.intSlider("atmosphere.bird_amount", d.birdAmount, 25, 300, 25,
								() -> c(feature).birdAmount, v -> c(feature).birdAmount = v))
						.option(Opts.bool("atmosphere.bird_sounds", d.birdSounds, () -> c(feature).birdSounds, v -> c(feature).birdSounds = v))
						.option(Opts.bool("atmosphere.birds_at_night", d.birdsAtNight, () -> c(feature).birdsAtNight, v -> c(feature).birdsAtNight = v))
						.build())
				.build();
	}

	private static AtmosphereConfig c(AtmosphereFeature feature) {
		return feature.config();
	}
}
