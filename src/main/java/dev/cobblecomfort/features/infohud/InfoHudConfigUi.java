package dev.cobblecomfort.features.infohud;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public final class InfoHudConfigUi {
	private InfoHudConfigUi() {
	}

	public static ConfigCategory buildCategory(InfoHudFeature feature) {
		InfoHudConfig d = new InfoHudConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("info_hud.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(ListOption.<InfoEntry>createBuilder()
						.name(Opts.name("info_hud.entries"))
						.description(OptionDescription.of(Component.translatable("cobblecomfort.option.info_hud.entries.desc")))
						.binding(d.entries, () -> c(feature).entries, v -> c(feature).entries = new ArrayList<>(v))
						.controller(opt -> EnumControllerBuilder.create(opt).enumClass(InfoEntry.class))
						.initial(InfoEntry.CHUNK)
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.info_hud.content"))
						.option(Opts.bool("info_hud.other_dimension", d.otherDimensionCoords,
								() -> c(feature).otherDimensionCoords, v -> c(feature).otherDimensionCoords = v))
						.option(Opts.bool("info_hud.direction_degrees", d.directionDegrees,
								() -> c(feature).directionDegrees, v -> c(feature).directionDegrees = v))
						.option(Opts.bool("info_hud.sleep_hint", d.sleepHint, () -> c(feature).sleepHint, v -> c(feature).sleepHint = v))
						.option(Opts.bool("info_hud.color_values", d.colorValues, () -> c(feature).colorValues, v -> c(feature).colorValues = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.info_hud.look"))
						.option(Opts.enumOpt("info_hud.style", InfoStyle.class, d.style, () -> c(feature).style, v -> c(feature).style = v))
						.option(Opts.bool("info_hud.icons", d.icons, () -> c(feature).icons, v -> c(feature).icons = v))
						.option(Opts.color("info_hud.background_color", d.backgroundColor, true,
								() -> c(feature).backgroundColor, v -> c(feature).backgroundColor = v))
						.option(Opts.color("info_hud.label_color", d.labelColor, false,
								() -> c(feature).labelColor, v -> c(feature).labelColor = v | 0xFF000000))
						.option(Opts.color("info_hud.value_color", d.valueColor, false,
								() -> c(feature).valueColor, v -> c(feature).valueColor = v | 0xFF000000))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.info_hud.behaviour"))
						.option(Opts.bool("info_hud.hide_with_debug", d.hideWithDebug,
								() -> c(feature).hideWithDebug, v -> c(feature).hideWithDebug = v))
						.option(Opts.bool("info_hud.animate", d.animate, () -> c(feature).animate, v -> c(feature).animate = v))
						.build())
				.build();
	}

	private static InfoHudConfig c(InfoHudFeature feature) {
		return feature.config();
	}
}
