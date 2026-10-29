package dev.cobblecomfort.features.pets;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class PetConfigUi {
	private PetConfigUi() {
	}

	public static ConfigCategory buildCategory(PetFeature feature) {
		PetConfig d = new PetConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("pets.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.pets.remember"))
						.option(Opts.bool("pets.companions", d.companions, () -> c(feature).companions, v -> c(feature).companions = v))
						.option(Opts.bool("pets.mounts", d.mounts, () -> c(feature).mounts, v -> c(feature).mounts = v))
						.option(Opts.bool("pets.others", d.others, () -> c(feature).others, v -> c(feature).others = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.pets.display"))
						.option(Opts.bool("pets.death_toast", d.deathToast, () -> c(feature).deathToast, v -> c(feature).deathToast = v))
						.option(Opts.bool("pets.map_markers", d.mapMarkers, () -> c(feature).mapMarkers, v -> c(feature).mapMarkers = v))
						.build())
				.build();
	}

	private static PetConfig c(PetFeature feature) {
		return feature.config();
	}
}
