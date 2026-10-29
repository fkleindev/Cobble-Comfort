package dev.cobblecomfort.features.invtools;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class InventoryToolsConfigUi {
	private InventoryToolsConfigUi() {
	}

	public static ConfigCategory buildCategory(InventoryToolsFeature feature) {
		InventoryToolsConfig d = new InventoryToolsConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("invtools.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.invtools.search"))
						.option(Opts.bool("invtools.search", d.search, () -> c(feature).search, v -> c(feature).search = v))
						.option(Opts.bool("invtools.search_tooltip", d.searchTooltip,
								() -> c(feature).searchTooltip, v -> c(feature).searchTooltip = v))
						.option(Opts.bool("invtools.search_shulkers", d.searchShulkers,
								() -> c(feature).searchShulkers, v -> c(feature).searchShulkers = v))
						.option(Opts.bool("invtools.remember_query", d.rememberQuery,
								() -> c(feature).rememberQuery, v -> c(feature).rememberQuery = v))
						.option(Opts.color("invtools.highlight_color", d.highlightColor, false,
								() -> c(feature).highlightColor, v -> c(feature).highlightColor = v | 0xFF000000))
						.option(Opts.color("invtools.dim_color", d.dimColor, true, () -> c(feature).dimColor, v -> c(feature).dimColor = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.invtools.sort"))
						.option(Opts.bool("invtools.sort_buttons", d.sortButtons, () -> c(feature).sortButtons, v -> c(feature).sortButtons = v))
						.option(Opts.bool("invtools.middle_click_sort", d.middleClickSort,
								() -> c(feature).middleClickSort, v -> c(feature).middleClickSort = v))
						.option(Opts.enumOpt("invtools.sort_order", SortOrder.class, d.sortOrder,
								() -> c(feature).sortOrder, v -> c(feature).sortOrder = v))
						.option(Opts.bool("invtools.sort_hotbar", d.sortHotbar, () -> c(feature).sortHotbar, v -> c(feature).sortHotbar = v))
						.option(Opts.intSlider("invtools.actions_per_tick", d.actionsPerTick, 0, 20, 1,
								() -> c(feature).actionsPerTick, v -> c(feature).actionsPerTick = v))
						.option(Opts.bool("invtools.sort_animation", d.sortAnimation,
								() -> c(feature).sortAnimation, v -> c(feature).sortAnimation = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.invtools.transfer"))
						.option(Opts.bool("invtools.transfer_buttons", d.transferButtons,
								() -> c(feature).transferButtons, v -> c(feature).transferButtons = v))
						.option(Opts.bool("invtools.deposit_hotbar", d.depositHotbar,
								() -> c(feature).depositHotbar, v -> c(feature).depositHotbar = v))
						.option(Opts.bool("invtools.shift_drag", d.shiftDrag, () -> c(feature).shiftDrag, v -> c(feature).shiftDrag = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.invtools.locks"))
						.option(Opts.bool("invtools.locks", d.locks, () -> c(feature).locks, v -> c(feature).locks = v))
						.option(Opts.bool("invtools.lock_protection", d.lockProtection,
								() -> c(feature).lockProtection, v -> c(feature).lockProtection = v))
						.option(Opts.bool("invtools.lock_world_drop", d.lockWorldDrop,
								() -> c(feature).lockWorldDrop, v -> c(feature).lockWorldDrop = v))
						.option(Opts.bool("invtools.lock_in_hotbar", d.lockInHotbar,
								() -> c(feature).lockInHotbar, v -> c(feature).lockInHotbar = v))
						.option(Opts.color("invtools.lock_color", d.lockColor, false,
								() -> c(feature).lockColor, v -> c(feature).lockColor = v | 0xFF000000))
						.build())
				.build();
	}

	private static InventoryToolsConfig c(InventoryToolsFeature feature) {
		return feature.config();
	}
}
