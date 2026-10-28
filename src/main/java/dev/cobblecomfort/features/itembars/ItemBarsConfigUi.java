package dev.cobblecomfort.features.itembars;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public final class ItemBarsConfigUi {
	private ItemBarsConfigUi() {
	}

	public static ConfigCategory buildCategory(ItemBarsFeature feature) {
		ItemBarsConfig d = new ItemBarsConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("item_bars.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.item_bars.durability"))
						.option(Opts.bool("item_bars.smooth_durability", d.smoothDurability,
								() -> c(feature).smoothDurability, v -> c(feature).smoothDurability = v))
						.option(Opts.bool("item_bars.break_warning", d.breakWarning, () -> c(feature).breakWarning, v -> c(feature).breakWarning = v))
						.option(Opts.percent("item_bars.warn_below", d.warnBelow, () -> c(feature).warnBelow, v -> c(feature).warnBelow = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.item_bars.cooldown"))
						.option(Opts.bool("item_bars.cooldown_ring", d.cooldownRing, () -> c(feature).cooldownRing, v -> c(feature).cooldownRing = v))
						.option(Opts.color("item_bars.ring_color", d.ringColor, false,
								() -> c(feature).ringColor, v -> c(feature).ringColor = v | 0xFF000000))
						.option(Opts.bool("item_bars.cooldown_flash", d.cooldownFlash, () -> c(feature).cooldownFlash, v -> c(feature).cooldownFlash = v))
						.build())
				.build();
	}

	private static ItemBarsConfig c(ItemBarsFeature feature) {
		return feature.config();
	}
}
