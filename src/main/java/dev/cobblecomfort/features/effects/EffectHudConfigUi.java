package dev.cobblecomfort.features.effects;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class EffectHudConfigUi {
	private EffectHudConfigUi() {
	}

	public static ConfigCategory buildCategory(EffectHudFeature feature) {
		EffectHudConfig d = new EffectHudConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("effect_hud.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.effect_hud.layout"))
						.option(Opts.enumOpt("effect_hud.style", EffectStyle.class, d.style, () -> c(feature).style, v -> c(feature).style = v))
						.option(Opts.intSlider("effect_hud.list_width", d.listWidth, 80, 200, 5,
								() -> c(feature).listWidth, v -> c(feature).listWidth = v))
						.option(Opts.intSlider("effect_hud.per_row", d.perRow, 1, 16, 1, () -> c(feature).perRow, v -> c(feature).perRow = v))
						.option(Opts.bool("effect_hud.separate_groups", d.separateGroups,
								() -> c(feature).separateGroups, v -> c(feature).separateGroups = v))
						.option(Opts.enumOpt("effect_hud.sort", EffectSort.class, d.sort, () -> c(feature).sort, v -> c(feature).sort = v))
						.option(Opts.bool("effect_hud.hide_iconless", d.hideIconless,
								() -> c(feature).hideIconless, v -> c(feature).hideIconless = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.effect_hud.look"))
						.option(Opts.bool("effect_hud.background", d.background, () -> c(feature).background, v -> c(feature).background = v))
						.option(Opts.color("effect_hud.background_color", d.backgroundColor, true,
								() -> c(feature).backgroundColor, v -> c(feature).backgroundColor = v))
						.option(Opts.bool("effect_hud.category_mark", d.categoryMark,
								() -> c(feature).categoryMark, v -> c(feature).categoryMark = v))
						.option(Opts.color("effect_hud.beneficial_color", d.beneficialColor, false,
								() -> c(feature).beneficialColor, v -> c(feature).beneficialColor = v | 0xFF000000))
						.option(Opts.color("effect_hud.harmful_color", d.harmfulColor, false,
								() -> c(feature).harmfulColor, v -> c(feature).harmfulColor = v | 0xFF000000))
						.option(Opts.bool("effect_hud.effect_color_bars", d.effectColorBars,
								() -> c(feature).effectColorBars, v -> c(feature).effectColorBars = v))
						.option(Opts.bool("effect_hud.show_level", d.showLevel, () -> c(feature).showLevel, v -> c(feature).showLevel = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.effect_hud.warning"))
						.option(Opts.bool("effect_hud.warn", d.warn, () -> c(feature).warn, v -> c(feature).warn = v))
						.option(Opts.intSlider("effect_hud.warn_seconds", d.warnSeconds, 3, 60, 1,
								() -> c(feature).warnSeconds, v -> c(feature).warnSeconds = v))
						.option(Opts.bool("effect_hud.warn_sound", d.warnSound, () -> c(feature).warnSound, v -> c(feature).warnSound = v))
						.option(Opts.percent("effect_hud.warn_volume", d.warnVolume, () -> c(feature).warnVolume, v -> c(feature).warnVolume = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.effect_hud.animation"))
						.option(Opts.bool("effect_hud.animations", d.animations, () -> c(feature).animations, v -> c(feature).animations = v))
						.option(Opts.slider("effect_hud.anim_speed", d.animSpeed, 0.25f, 3f, 0.25f,
								() -> c(feature).animSpeed, v -> c(feature).animSpeed = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.effect_hud.inventory"))
						.option(Opts.bool("effect_hud.inventory", d.inventory, () -> c(feature).inventory, v -> c(feature).inventory = v))
						.option(Opts.bool("effect_hud.inventory_tooltip", d.inventoryTooltip,
								() -> c(feature).inventoryTooltip, v -> c(feature).inventoryTooltip = v))
						.build())
				.build();
	}

	private static EffectHudConfig c(EffectHudFeature feature) {
		return feature.config();
	}
}
