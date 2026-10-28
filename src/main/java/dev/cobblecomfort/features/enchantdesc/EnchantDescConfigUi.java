package dev.cobblecomfort.features.enchantdesc;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class EnchantDescConfigUi {
	private EnchantDescConfigUi() {
	}

	public static ConfigCategory buildCategory(EnchantDescFeature feature) {
		EnchantDescConfig d = new EnchantDescConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("enchant_desc.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.enchant_desc.when"))
						.option(Opts.enumOpt("enchant_desc.display_mode", DisplayMode.class, d.displayMode,
								() -> c(feature).displayMode, v -> c(feature).displayMode = v))
						.option(Opts.bool("enchant_desc.shift_hint", d.shiftHint, () -> c(feature).shiftHint, v -> c(feature).shiftHint = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.enchant_desc.content"))
						.option(Opts.bool("enchant_desc.show_description", d.showDescription,
								() -> c(feature).showDescription, v -> c(feature).showDescription = v))
						.option(Opts.bool("enchant_desc.show_max_level", d.showMaxLevel,
								() -> c(feature).showMaxLevel, v -> c(feature).showMaxLevel = v))
						.option(Opts.bool("enchant_desc.max_level_always", d.maxLevelAlways,
								() -> c(feature).maxLevelAlways, v -> c(feature).maxLevelAlways = v))
						.option(Opts.bool("enchant_desc.highlight_max_level", d.highlightMaxLevel,
								() -> c(feature).highlightMaxLevel, v -> c(feature).highlightMaxLevel = v))
						.option(Opts.bool("enchant_desc.show_applicable", d.showApplicable,
								() -> c(feature).showApplicable, v -> c(feature).showApplicable = v))
						.option(Opts.bool("enchant_desc.show_conflicts", d.showConflicts,
								() -> c(feature).showConflicts, v -> c(feature).showConflicts = v))
						.option(Opts.intSlider("enchant_desc.max_listed", d.maxListed, 1, 15, 1,
								() -> c(feature).maxListed, v -> c(feature).maxListed = v))
						.option(Opts.bool("enchant_desc.mark_curses", d.markCurses, () -> c(feature).markCurses, v -> c(feature).markCurses = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.enchant_desc.look"))
						.option(Opts.intSlider("enchant_desc.indent", d.indent, 0, 8, 1, () -> c(feature).indent, v -> c(feature).indent = v))
						.option(Opts.intSlider("enchant_desc.wrap_width", d.wrapWidth, 100, 400, 10,
								() -> c(feature).wrapWidth, v -> c(feature).wrapWidth = v))
						.option(Opts.color("enchant_desc.description_color", d.descriptionColor, false,
								() -> c(feature).descriptionColor, v -> c(feature).descriptionColor = v | 0xFF000000))
						.option(Opts.color("enchant_desc.label_color", d.labelColor, false,
								() -> c(feature).labelColor, v -> c(feature).labelColor = v | 0xFF000000))
						.option(Opts.color("enchant_desc.extra_color", d.extraColor, false,
								() -> c(feature).extraColor, v -> c(feature).extraColor = v | 0xFF000000))
						.option(Opts.color("enchant_desc.max_level_color", d.maxLevelColor, false,
								() -> c(feature).maxLevelColor, v -> c(feature).maxLevelColor = v | 0xFF000000))
						.option(Opts.color("enchant_desc.curse_color", d.curseColor, false,
								() -> c(feature).curseColor, v -> c(feature).curseColor = v | 0xFF000000))
						.option(Opts.color("enchant_desc.hint_color", d.hintColor, false,
								() -> c(feature).hintColor, v -> c(feature).hintColor = v | 0xFF000000))
						.build())
				.build();
	}

	private static EnchantDescConfig c(EnchantDescFeature feature) {
		return feature.config();
	}
}
