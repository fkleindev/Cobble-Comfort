package dev.cobblecomfort.features.shulker;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class ShulkerPreviewConfigUi {
	private ShulkerPreviewConfigUi() {
	}

	public static ConfigCategory buildCategory(ShulkerPreviewFeature feature) {
		ShulkerPreviewConfig d = new ShulkerPreviewConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("shulker.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.shulker.tooltip"))
						.option(Opts.bool("shulker.tooltip", d.tooltip, () -> c(feature).tooltip, v -> c(feature).tooltip = v))
						.option(Opts.bool("shulker.compact_by_default", d.compactByDefault,
								() -> c(feature).compactByDefault, v -> c(feature).compactByDefault = v))
						.option(Opts.enumOpt("shulker.full_key", PreviewKey.class, d.fullKey,
								() -> c(feature).fullKey, v -> c(feature).fullKey = v))
						.option(Opts.bool("shulker.key_hint", d.keyHint, () -> c(feature).keyHint, v -> c(feature).keyHint = v))
						.option(Opts.bool("shulker.sort_by_count", d.sortByCount,
								() -> c(feature).sortByCount, v -> c(feature).sortByCount = v))
						.option(Opts.intSlider("shulker.compact_columns", d.compactColumns, 3, 9, 1,
								() -> c(feature).compactColumns, v -> c(feature).compactColumns = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.shulker.look"))
						.option(Opts.bool("shulker.tint", d.tint, () -> c(feature).tint, v -> c(feature).tint = v))
						.option(Opts.percent("shulker.tint_strength", d.tintStrength,
								() -> c(feature).tintStrength, v -> c(feature).tintStrength = v))
						.option(Opts.bool("shulker.rarity_glow", d.rarityGlow, () -> c(feature).rarityGlow, v -> c(feature).rarityGlow = v))
						.option(Opts.bool("shulker.pop_in", d.popIn, () -> c(feature).popIn, v -> c(feature).popIn = v))
						.option(Opts.slider("shulker.pop_duration", d.popDuration, 0.05f, 0.6f, 0.01f,
								() -> c(feature).popDuration, v -> c(feature).popDuration = v))
						.option(Opts.intSlider("shulker.stagger", d.staggerMs, 0, 40, 2, () -> c(feature).staggerMs, v -> c(feature).staggerMs = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.shulker.hud"))
						.option(Opts.bool("shulker.hud", d.hud, () -> c(feature).hud, v -> c(feature).hud = v))
						.option(Opts.bool("shulker.hud_offhand", d.hudOffhand, () -> c(feature).hudOffhand, v -> c(feature).hudOffhand = v))
						.option(Opts.intSlider("shulker.hud_columns", d.hudColumns, 3, 9, 1,
								() -> c(feature).hudColumns, v -> c(feature).hudColumns = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.shulker.fill"))
						.option(Opts.bool("shulker.fill_bar", d.fillBar, () -> c(feature).fillBar, v -> c(feature).fillBar = v))
						.option(Opts.bool("shulker.fill_bar_hotbar", d.fillBarInHotbar,
								() -> c(feature).fillBarInHotbar, v -> c(feature).fillBarInHotbar = v))
						.option(Opts.enumOpt("shulker.fill_mode", FillMode.class, d.fillMode,
								() -> c(feature).fillMode, v -> c(feature).fillMode = v))
						.option(Opts.color("shulker.fill_color", d.fillColor, false,
								() -> c(feature).fillColor, v -> c(feature).fillColor = v | 0xFF000000))
						.option(Opts.color("shulker.full_color", d.fullColor, false,
								() -> c(feature).fullColor, v -> c(feature).fullColor = v | 0xFF000000))
						.build())
				.build();
	}

	private static ShulkerPreviewConfig c(ShulkerPreviewFeature feature) {
		return feature.config();
	}
}
