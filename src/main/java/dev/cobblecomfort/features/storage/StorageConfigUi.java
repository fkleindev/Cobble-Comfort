package dev.cobblecomfort.features.storage;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class StorageConfigUi {
	private StorageConfigUi() {
	}

	public static ConfigCategory buildCategory(StorageFeature feature) {
		StorageConfig d = new StorageConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("storage.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.storage.remember"))
						.option(Opts.bool("storage.chests", d.chests, () -> c(feature).chests, v -> c(feature).chests = v))
						.option(Opts.bool("storage.ender_chest", d.enderChest, () -> c(feature).enderChest, v -> c(feature).enderChest = v))
						.option(Opts.bool("storage.utility_blocks", d.utilityBlocks,
								() -> c(feature).utilityBlocks, v -> c(feature).utilityBlocks = v))
						.option(Opts.bool("storage.entities", d.entities, () -> c(feature).entities, v -> c(feature).entities = v))
						.option(Opts.bool("storage.forget_broken", d.forgetBroken,
								() -> c(feature).forgetBroken, v -> c(feature).forgetBroken = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.storage.search"))
						.option(Opts.bool("storage.inventory_hint", d.inventoryHint,
								() -> c(feature).inventoryHint, v -> c(feature).inventoryHint = v))
						.option(Opts.bool("storage.count_shulker_contents", d.countShulkerContents,
								() -> c(feature).countShulkerContents, v -> c(feature).countShulkerContents = v))
						.option(Opts.bool("storage.ender_tooltip", d.enderTooltip,
								() -> c(feature).enderTooltip, v -> c(feature).enderTooltip = v))
						.option(Opts.bool("storage.show_age", d.showAge, () -> c(feature).showAge, v -> c(feature).showAge = v))
						.option(Opts.intSlider("storage.stale_days", d.staleDays, 1, 60, 1,
								() -> c(feature).staleDays, v -> c(feature).staleDays = v))
						.option(Opts.bool("storage.naming", d.naming, () -> c(feature).naming, v -> c(feature).naming = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.storage.find"))
						.option(Opts.intSlider("storage.highlight_seconds", d.highlightSeconds, 5, 300, 5,
								() -> c(feature).highlightSeconds, v -> c(feature).highlightSeconds = v))
						.option(Opts.bool("storage.outline", d.outline, () -> c(feature).outline, v -> c(feature).outline = v))
						.option(Opts.color("storage.outline_color", d.outlineColor, false,
								() -> c(feature).outlineColor, v -> c(feature).outlineColor = v | 0xFF000000))
						.option(Opts.bool("storage.compass_arrow", d.compassArrow,
								() -> c(feature).compassArrow, v -> c(feature).compassArrow = v))
						.option(Opts.bool("storage.map_markers", d.mapMarkers, () -> c(feature).mapMarkers, v -> c(feature).mapMarkers = v))
						.option(Opts.bool("storage.map_all", d.mapAllContainers,
								() -> c(feature).mapAllContainers, v -> c(feature).mapAllContainers = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.storage.look"))
						.option(Opts.bool("storage.look_info", d.lookInfo, () -> c(feature).lookInfo, v -> c(feature).lookInfo = v))
						.option(Opts.intSlider("storage.look_info_rows", d.lookInfoRows, 1, 6, 1,
								() -> c(feature).lookInfoRows, v -> c(feature).lookInfoRows = v))
						.build())
				.build();
	}

	private static StorageConfig c(StorageFeature feature) {
		return feature.config();
	}
}
