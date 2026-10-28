package dev.cobblecomfort.features.preview;

import dev.cobblecomfort.core.config.Opts;
import dev.cobblecomfort.features.shulker.PreviewKey;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class ItemPreviewConfigUi {
	private ItemPreviewConfigUi() {
	}

	public static ConfigCategory buildCategory(ItemPreviewFeature feature) {
		ItemPreviewConfig d = new ItemPreviewConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("preview.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.preview.size"))
						.option(Opts.bool("preview.compact_by_default", d.compactByDefault,
								() -> c(feature).compactByDefault, v -> c(feature).compactByDefault = v))
						.option(Opts.enumOpt("preview.large_key", PreviewKey.class, d.largeKey,
								() -> c(feature).largeKey, v -> c(feature).largeKey = v))
						.option(Opts.bool("preview.key_hint", d.keyHint, () -> c(feature).keyHint, v -> c(feature).keyHint = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.preview.maps"))
						.option(Opts.bool("preview.maps", d.maps, () -> c(feature).maps, v -> c(feature).maps = v))
						.option(Opts.bool("preview.map_markers", d.mapMarkers, () -> c(feature).mapMarkers, v -> c(feature).mapMarkers = v))
						.option(Opts.bool("preview.map_info", d.mapInfo, () -> c(feature).mapInfo, v -> c(feature).mapInfo = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.preview.books"))
						.option(Opts.bool("preview.written_books", d.writtenBooks,
								() -> c(feature).writtenBooks, v -> c(feature).writtenBooks = v))
						.option(Opts.bool("preview.writable_books", d.writableBooks,
								() -> c(feature).writableBooks, v -> c(feature).writableBooks = v))
						.option(Opts.bool("preview.scroll_pages", d.scrollPages,
								() -> c(feature).scrollPages, v -> c(feature).scrollPages = v))
						.option(Opts.bool("preview.enchanted_books", d.enchantedBooks,
								() -> c(feature).enchantedBooks, v -> c(feature).enchantedBooks = v))
						.build())
				.build();
	}

	private static ItemPreviewConfig c(ItemPreviewFeature feature) {
		return feature.config();
	}
}
