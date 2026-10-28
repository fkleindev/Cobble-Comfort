package dev.cobblecomfort.core.config;

import dev.cobblecomfort.core.Feature;
import dev.cobblecomfort.core.FeatureManager;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CobbleConfigScreen {
	private CobbleConfigScreen() {
	}

	public static Screen create(Screen parent) {
		GeneralConfig general = FeatureManager.general();
		YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
				.title(Component.translatable("cobblecomfort.config.title"))
				.save(FeatureManager::saveAll)
				.category(ConfigCategory.createBuilder()
						.name(Component.translatable("cobblecomfort.category.general"))
						.option(Opts.bool("general.enabled", true, () -> general.enabled, v -> general.enabled = v))
						.build());
		for (Feature<?> feature : FeatureManager.features()) {
			builder.category(feature.buildCategory());
		}
		return builder.build().generateScreen(parent);
	}
}
