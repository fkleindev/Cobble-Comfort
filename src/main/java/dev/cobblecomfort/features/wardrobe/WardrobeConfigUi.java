package dev.cobblecomfort.features.wardrobe;

import dev.cobblecomfort.core.config.Opts;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class WardrobeConfigUi {
	private WardrobeConfigUi() {
	}

	public static ConfigCategory buildCategory(WardrobeFeature feature) {
		WardrobeConfig d = new WardrobeConfig();
		return ConfigCategory.createBuilder()
				.name(feature.name())
				.option(Opts.bool("wardrobe.enabled", d.enabled, () -> c(feature).enabled, v -> c(feature).enabled = v))
				.option(ButtonOption.createBuilder()
						.name(Opts.name("wardrobe.open"))
						.description(Opts.desc("wardrobe.open"))
						.action(screen -> feature.open(Minecraft.getInstance().gui.screen()))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.wardrobe.badges"))
						.option(Opts.bool("wardrobe.show_own", d.showOwn, () -> c(feature).showOwn, v -> c(feature).showOwn = v))
						.option(Opts.bool("wardrobe.show_others", d.showOthers, () -> c(feature).showOthers, v -> c(feature).showOthers = v))
						.option(Opts.bool("wardrobe.share", d.share, () -> c(feature).share, v -> c(feature).share = v))
						.option(Opts.bool("wardrobe.announce", d.announce, () -> c(feature).announce, v -> c(feature).announce = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.wardrobe.hats"))
						.option(Opts.percent("wardrobe.hat_swing", d.hatSwing, () -> c(feature).hatSwing, v -> c(feature).hatSwing = v))
						.option(Opts.bool("wardrobe.petals", d.petals, () -> c(feature).petals, v -> c(feature).petals = v))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.translatable("cobblecomfort.group.wardrobe.buttons"))
						.option(Opts.bool("wardrobe.inventory_button", d.inventoryButton, () -> c(feature).inventoryButton, v -> c(feature).inventoryButton = v))
						.option(Opts.bool("wardrobe.map_button", d.mapButton, () -> c(feature).mapButton, v -> c(feature).mapButton = v))
						.build())
				.build();
	}

	private static WardrobeConfig c(WardrobeFeature feature) {
		return feature.config();
	}
}
