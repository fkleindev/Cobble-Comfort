package dev.cobblecomfort.features.lookinfo;

import dev.cobblecomfort.core.config.Opts;
import dev.cobblecomfort.features.hud.anim.Easing;
import dev.cobblecomfort.features.hud.anim.ShowAnimation;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class LookInfoConfigUi {
	private LookInfoConfigUi() {
	}

	public static ConfigCategory buildCategory(LookInfoFeature feature) {
		LookInfoConfig d = new LookInfoConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("look.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.look.targets"))
						.option(Opts.bool("look.show_blocks", d.showBlocks, () -> c(feature).showBlocks, v -> c(feature).showBlocks = v))
						.option(Opts.bool("look.show_entities", d.showEntities, () -> c(feature).showEntities, v -> c(feature).showEntities = v))
						.option(Opts.bool("look.show_fluids", d.showFluids, () -> c(feature).showFluids, v -> c(feature).showFluids = v))
						.option(Opts.bool("look.only_when_sneaking", d.onlyWhenSneaking,
								() -> c(feature).onlyWhenSneaking, v -> c(feature).onlyWhenSneaking = v))
						.option(Opts.bool("look.hide_in_spectator", d.hideInSpectator,
								() -> c(feature).hideInSpectator, v -> c(feature).hideInSpectator = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.look.content"))
						.option(Opts.bool("look.show_icon", d.showIcon, () -> c(feature).showIcon, v -> c(feature).showIcon = v))
						.option(Opts.bool("look.show_mod_name", d.showModName, () -> c(feature).showModName, v -> c(feature).showModName = v))
						.option(Opts.bool("look.show_harvest_tool", d.showHarvestTool,
								() -> c(feature).showHarvestTool, v -> c(feature).showHarvestTool = v))
						.option(Opts.bool("look.show_growth", d.showGrowth, () -> c(feature).showGrowth, v -> c(feature).showGrowth = v))
						.option(Opts.bool("look.show_redstone", d.showRedstone, () -> c(feature).showRedstone, v -> c(feature).showRedstone = v))
						.option(Opts.bool("look.show_break_progress", d.showBreakProgress,
								() -> c(feature).showBreakProgress, v -> c(feature).showBreakProgress = v))
						.option(Opts.bool("look.show_block_states", d.showBlockStates,
								() -> c(feature).showBlockStates, v -> c(feature).showBlockStates = v))
						.option(Opts.enumOpt("look.health_display", HealthDisplay.class, d.healthDisplay,
								() -> c(feature).healthDisplay, v -> c(feature).healthDisplay = v))
						.option(Opts.intSlider("look.max_hearts", d.maxHearts, 5, 50, 1,
								() -> c(feature).maxHearts, v -> c(feature).maxHearts = v))
						.option(Opts.bool("look.show_armor", d.showArmor, () -> c(feature).showArmor, v -> c(feature).showArmor = v))
						.option(Opts.bool("look.show_entity_details", d.showEntityDetails,
								() -> c(feature).showEntityDetails, v -> c(feature).showEntityDetails = v))
						.option(Opts.bool("look.show_horse_stats", d.showHorseStats,
								() -> c(feature).showHorseStats, v -> c(feature).showHorseStats = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.look.style"))
						.option(Opts.enumOpt("look.layout", LookLayout.class, d.layout, () -> c(feature).layout, v -> c(feature).layout = v))
						.option(Opts.color("look.background_color", d.backgroundColor, true,
								() -> c(feature).backgroundColor, v -> c(feature).backgroundColor = v))
						.option(Opts.bool("look.border", d.border, () -> c(feature).border, v -> c(feature).border = v))
						.option(Opts.color("look.border_color_top", d.borderColorTop, true,
								() -> c(feature).borderColorTop, v -> c(feature).borderColorTop = v))
						.option(Opts.color("look.border_color_bottom", d.borderColorBottom, true,
								() -> c(feature).borderColorBottom, v -> c(feature).borderColorBottom = v))
						.option(Opts.intSlider("look.corner_radius", d.cornerRadius, 0, 10, 1,
								() -> c(feature).cornerRadius, v -> c(feature).cornerRadius = v))
						.option(Opts.intSlider("look.padding", d.padding, 0, 12, 1, () -> c(feature).padding, v -> c(feature).padding = v))
						.option(Opts.color("look.name_color", d.nameColor, false,
								() -> c(feature).nameColor, v -> c(feature).nameColor = v | 0xFF000000))
						.option(Opts.color("look.detail_color", d.detailColor, false,
								() -> c(feature).detailColor, v -> c(feature).detailColor = v | 0xFF000000))
						.option(Opts.color("look.mod_color", d.modColor, false,
								() -> c(feature).modColor, v -> c(feature).modColor = v | 0xFF000000))
						.option(Opts.bool("look.text_shadow", d.textShadow, () -> c(feature).textShadow, v -> c(feature).textShadow = v))
						.option(Opts.intSlider("look.bar_gap", d.barGap, 0, 60, 1, () -> c(feature).barGap, v -> c(feature).barGap = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.look.animation"))
						.option(Opts.enumOpt("look.animation", ShowAnimation.class, d.animation,
								() -> c(feature).animation, v -> c(feature).animation = v))
						.option(Opts.enumOpt("look.easing", Easing.class, d.easing, () -> c(feature).easing, v -> c(feature).easing = v))
						.option(Opts.slider("look.animation_duration", d.animationDuration, 0.05f, 1f, 0.05f,
								() -> c(feature).animationDuration, v -> c(feature).animationDuration = v))
						.option(Opts.slider("look.linger_time", d.lingerTime, 0f, 3f, 0.05f,
								() -> c(feature).lingerTime, v -> c(feature).lingerTime = v))
						.option(Opts.bool("look.smooth_resize", d.smoothResize,
								() -> c(feature).smoothResize, v -> c(feature).smoothResize = v))
						.build())
				.build();
	}

	private static LookInfoConfig c(LookInfoFeature feature) {
		return feature.config();
	}
}
