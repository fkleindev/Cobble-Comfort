package dev.cobblecomfort.features.furnace;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class FurnaceConfigUi {
	private FurnaceConfigUi() {
	}

	public static ConfigCategory buildCategory(FurnaceFeature feature) {
		FurnaceConfig d = new FurnaceConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("furnace.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.option(Opts.bool("furnace.brewing_stands", d.brewingStands,
						() -> c(feature).brewingStands, v -> c(feature).brewingStands = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.furnace.notify"))
						.option(Opts.bool("furnace.toast", d.toast, () -> c(feature).toast, v -> c(feature).toast = v))
						.option(Opts.bool("furnace.sound", d.sound, () -> c(feature).sound, v -> c(feature).sound = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.furnace.markers"))
						.option(Opts.bool("furnace.world_markers", d.worldMarkers,
								() -> c(feature).worldMarkers, v -> c(feature).worldMarkers = v))
						.option(Opts.intSlider("furnace.marker_distance", d.markerDistance, 8, 96, 4,
								() -> c(feature).markerDistance, v -> c(feature).markerDistance = v))
						.option(Opts.bool("furnace.map_markers", d.mapMarkers, () -> c(feature).mapMarkers, v -> c(feature).mapMarkers = v))
						.option(Opts.bool("furnace.minimap_markers", d.minimapMarkers,
								() -> c(feature).minimapMarkers, v -> c(feature).minimapMarkers = v))
						.build())
				.build();
	}

	private static FurnaceConfig c(FurnaceFeature feature) {
		return feature.config();
	}
}
