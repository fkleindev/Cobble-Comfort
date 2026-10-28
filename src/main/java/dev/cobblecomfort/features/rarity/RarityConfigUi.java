package dev.cobblecomfort.features.rarity;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class RarityConfigUi {
	private RarityConfigUi() {
	}

	public static ConfigCategory buildCategory(RarityFeature feature) {
		RarityConfig d = new RarityConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("rarity.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.rarity.places"))
						.option(Opts.bool("rarity.in_inventories", d.inInventories,
								() -> c(feature).inInventories, v -> c(feature).inInventories = v))
						.option(Opts.bool("rarity.in_hotbar", d.inHotbar, () -> c(feature).inHotbar, v -> c(feature).inHotbar = v))
						.option(Opts.bool("rarity.in_creative", d.inCreative, () -> c(feature).inCreative, v -> c(feature).inCreative = v))
						.option(Opts.bool("rarity.tooltip_frame", d.tooltipFrame,
								() -> c(feature).tooltipFrame, v -> c(feature).tooltipFrame = v))
						.option(Opts.enumOpt("rarity.min_rarity", RarityLevel.class, d.minRarity,
								() -> c(feature).minRarity, v -> c(feature).minRarity = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.rarity.style"))
						.option(Opts.enumOpt("rarity.style", IndicatorStyle.class, d.style, () -> c(feature).style, v -> c(feature).style = v))
						.option(Opts.percent("rarity.intensity", d.intensity, () -> c(feature).intensity, v -> c(feature).intensity = v))
						.option(Opts.intSlider("rarity.thickness", d.thickness, 1, 4, 1, () -> c(feature).thickness, v -> c(feature).thickness = v))
						.option(Opts.intSlider("rarity.corner_size", d.cornerSize, 2, 12, 1,
								() -> c(feature).cornerSize, v -> c(feature).cornerSize = v))
						.option(Opts.color("rarity.common_color", d.commonColor, false,
								() -> c(feature).commonColor, v -> c(feature).commonColor = v | 0xFF000000))
						.option(Opts.color("rarity.uncommon_color", d.uncommonColor, false,
								() -> c(feature).uncommonColor, v -> c(feature).uncommonColor = v | 0xFF000000))
						.option(Opts.color("rarity.rare_color", d.rareColor, false,
								() -> c(feature).rareColor, v -> c(feature).rareColor = v | 0xFF000000))
						.option(Opts.color("rarity.epic_color", d.epicColor, false,
								() -> c(feature).epicColor, v -> c(feature).epicColor = v | 0xFF000000))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.rarity.animation"))
						.option(Opts.bool("rarity.pulse", d.pulse, () -> c(feature).pulse, v -> c(feature).pulse = v))
						.option(Opts.enumOpt("rarity.pulse_from", RarityLevel.class, d.pulseFrom,
								() -> c(feature).pulseFrom, v -> c(feature).pulseFrom = v))
						.option(Opts.slider("rarity.pulse_speed", d.pulseSpeed, 0.1f, 3f, 0.1f,
								() -> c(feature).pulseSpeed, v -> c(feature).pulseSpeed = v))
						.option(Opts.percent("rarity.pulse_strength", d.pulseStrength,
								() -> c(feature).pulseStrength, v -> c(feature).pulseStrength = v))
						.option(Opts.bool("rarity.shimmer", d.shimmer, () -> c(feature).shimmer, v -> c(feature).shimmer = v))
						.option(Opts.enumOpt("rarity.shimmer_from", RarityLevel.class, d.shimmerFrom,
								() -> c(feature).shimmerFrom, v -> c(feature).shimmerFrom = v))
						.option(Opts.slider("rarity.shimmer_interval", d.shimmerInterval, 1f, 15f, 0.5f,
								() -> c(feature).shimmerInterval, v -> c(feature).shimmerInterval = v))
						.option(Opts.bool("rarity.hover_highlight", d.hoverHighlight,
								() -> c(feature).hoverHighlight, v -> c(feature).hoverHighlight = v))
						.option(Opts.bool("rarity.rainbow_epic", d.rainbowEpic, () -> c(feature).rainbowEpic, v -> c(feature).rainbowEpic = v))
						.option(Opts.slider("rarity.rainbow_speed", d.rainbowSpeed, 0.05f, 2f, 0.05f,
								() -> c(feature).rainbowSpeed, v -> c(feature).rainbowSpeed = v))
						.build())
				.build();
	}

	private static RarityConfig c(RarityFeature feature) {
		return feature.config();
	}
}
